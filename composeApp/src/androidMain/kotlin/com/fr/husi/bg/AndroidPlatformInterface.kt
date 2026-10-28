package com.fr.husi.bg

import android.content.pm.PackageManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Process
import android.provider.Settings
import android.system.OsConstants
import com.fr.husi.ktx.Logs
import com.fr.husi.ktx.blankAsNull
import com.fr.husi.ktx.defaultOr
import com.fr.husi.ktx.toPrefix
import com.fr.husi.ktx.toStringIterator
import com.fr.husi.libcore.ConnectionOwner
import com.fr.husi.libcore.InterfaceUpdateListener
import com.fr.husi.libcore.Libcore
import com.fr.husi.libcore.LocalDNSTransport
import com.fr.husi.libcore.NetworkInterfaceIterator
import com.fr.husi.libcore.PlatformInterface
import com.fr.husi.libcore.WIFIState
import com.fr.husi.repository.resolveAndroidRepository
import com.fr.husi.utils.PackageCache
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.*
import com.fr.husi.libcore.NetworkInterface as LibcoreNetworkInterface

class AndroidPlatformInterface : PlatformInterface {

    //  libbox interface

    override fun autoDetectInterfaceControl(fd: Int): Boolean {
        return ServiceRegistry.vpnService?.protect(fd) == true
    }

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.setListener(listener)
    }

    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {
        DefaultNetworkMonitor.setListener(null)
    }

    override fun openTun(): Int {
        if (ServiceRegistry.vpnService == null) throw NullPointerException("no vpnService")
        return ServiceRegistry.vpnService!!.startVpn()
    }

    override fun findConnectionOwner(
        ipProtocol: Int,
        sourceAddress: String,
        sourcePort: Int,
        destinationAddress: String,
        destinationPort: Int,
    ): ConnectionOwner {
        // 内核查不到属主是常态竞态（短命 UDP/DNS 流、TCP 已关闭），
        // 连接列表每条都会查一次，刷 ERROR 日志没有价值。
        //
        // 2026-10-26 实机日志复核：2 秒内仍有 30+ 条
        // `ERROR [AndroidPlatformInterface] java.lang.IllegalStateException:
        // android: connection owner not found` —— 上一轮的"只抛错"注释落地时
        // 漏掉了 Logs.e(e)，异常仍按 ERROR 打印。这里保留抛错语义（Go 侧
        // route 的 processCache 会把这条 5-tuple 连同错误一起缓存，不会重复
        // 查询），但降级为 debug 日志，日志页不再被它淹掉。
        try {
            val uid = if (SDK_INT >= Build.VERSION_CODES.Q) {
                resolveAndroidRepository().connectivity.getConnectionOwnerUid(
                    ipProtocol,
                    InetSocketAddress(sourceAddress, sourcePort),
                    InetSocketAddress(destinationAddress, destinationPort),
                )
            } else {
                Libcore.findConnectionOwnerUid(
                    ipProtocol,
                    sourceAddress,
                    sourcePort,
                    destinationAddress,
                    destinationPort,
                )
            }
            if (uid == Process.INVALID_UID) error("android: connection owner not found")
            PackageCache.awaitLoadSync()
            val packages = PackageCache.uidMap[uid]
            return ConnectionOwner(uid, packages?.toStringIterator(packages.size))
        } catch (e: Exception) {
            Logs.d(
                "findConnectionOwner miss: $sourceAddress:$sourcePort -> " +
                    "$destinationAddress:$destinationPort",
                e,
            )
            throw e
        }
    }

    override fun readWIFIState(): WIFIState? {
        if (!resolveAndroidRepository().packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) return null
        // TODO API 34
        @Suppress("DEPRECATION") val wifiInfo = resolveAndroidRepository().wifi.connectionInfo ?: return null
        var ssid = wifiInfo.ssid
        if (ssid == "<unknown ssid>") return WifiState("", "")
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }
        return WifiState(ssid, wifiInfo.bssid)
    }

    override fun getInterfaces(): NetworkInterfaceIterator {
        @Suppress("DEPRECATION") val networks = resolveAndroidRepository().connectivity.allNetworks
        val networkInterfaces = NetworkInterface.getNetworkInterfaces().toList()
        val interfaces = mutableListOf<LibcoreNetworkInterface>()
        for (network in networks) {
            val boxInterface = LibcoreNetworkInterface()
            val linkProperties = resolveAndroidRepository().connectivity.getLinkProperties(network) ?: continue
            val networkCapabilities =
                resolveAndroidRepository().connectivity.getNetworkCapabilities(network) ?: continue
            boxInterface.name = linkProperties.interfaceName
            val networkInterface =
                networkInterfaces.find { it.name == boxInterface.name } ?: continue
            boxInterface.dnsServer = linkProperties.dnsServers.mapNotNull { it.hostAddress }.let {
                it.toStringIterator(it.size)
            }
            // LinkProperties joins the search domains with spaces.
            boxInterface.dnsSearchDomain = linkProperties.domains.orEmpty()
                .split(' ')
                .filter { it.isNotEmpty() }
                .let { it.toStringIterator(it.size) }
            boxInterface.type = when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Libcore.InterfaceTypeWIFI
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Libcore.InterfaceTypeCellular
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Libcore.InterfaceTypeEthernet
                else -> Libcore.InterfaceTypeOther
            }
            boxInterface.index = networkInterface.index
            runCatching {
                boxInterface.mtu = networkInterface.mtu
            }.onFailure { e ->
                Logs.e("failed to get mtu for interface ${boxInterface.name}", e)
            }
            boxInterface.addresses = networkInterface.interfaceAddresses.map {
                it.toPrefix()
            }.let {
                it.toStringIterator(it.size)
            }
            var dumpFlags = 0
            if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                dumpFlags = OsConstants.IFF_UP or OsConstants.IFF_RUNNING
            }
            if (networkInterface.isLoopback) {
                dumpFlags = dumpFlags or OsConstants.IFF_LOOPBACK
            }
            if (networkInterface.isPointToPoint) {
                dumpFlags = dumpFlags or OsConstants.IFF_POINTOPOINT
            }
            if (networkInterface.supportsMulticast()) {
                dumpFlags = dumpFlags or OsConstants.IFF_MULTICAST
            }
            boxInterface.flags = dumpFlags
            boxInterface.metered =
                !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            interfaces.add(boxInterface)
        }
        return InterfaceArray(interfaces.iterator(), interfaces.size)
    }

    override fun localDNSTransport(): LocalDNSTransport = LocalResolver

    override fun tailscaleHostname(): String {
        return defaultOr(
            "",
            {
                if (SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    Settings.Global.getString(
                        resolveAndroidRepository().context.contentResolver,
                        Settings.Global.DEVICE_NAME,
                    ).blankAsNull()
                } else {
                    null
                }
            },
            {
                val manu = Build.MANUFACTURER
                var model = Build.MODEL
                // Strip manufacturer from model.
                val index = model.lowercase(Locale.getDefault()).indexOf(manu.lowercase(Locale.getDefault()))
                if (index != -1) {
                    model = model.substring(index + manu.length).trim()
                }
                "$manu $model"
            },
        )
    }

    private class InterfaceArray(
        private val iterator: Iterator<LibcoreNetworkInterface>,
        private val size: Int,
    ) :
        NetworkInterfaceIterator {

        override fun hasNext(): Boolean {
            return iterator.hasNext()
        }

        override fun next(): LibcoreNetworkInterface {
            return iterator.next()
        }

        override fun length(): Int = size

    }

    private class WifiState(val mSSID: String, val mBSSID: String) : WIFIState {
        override fun getSSID(): String = mSSID
        override fun getBSSID(): String = mBSSID
    }
}
