package com.fr.husi.group

import com.fr.husi.database.DataStore
import com.fr.husi.database.ProxyGroup
import com.fr.husi.database.SubscriptionBean
import com.fr.husi.fmt.AbstractBean
import com.fr.husi.fmt.hysteria.parseHysteria1Json
import com.fr.husi.fmt.openconnect.parseOpenConnectConfig
import com.fr.husi.fmt.openvpn.looksLikeOpenVPNConfig
import com.fr.husi.fmt.openvpn.parseOpenVPNConfig
import com.fr.husi.fmt.parseOutbound
import com.fr.husi.fmt.shadowsocks.parseShadowsocks
import com.fr.husi.fmt.trojan.TrojanBean
import com.fr.husi.fmt.v2ray.StandardV2RayBean
import com.fr.husi.fmt.wireguard.parseWireGuardConfig
import com.fr.husi.ktx.JSONMap
import com.fr.husi.ktx.Logs
import com.fr.husi.ktx.SubscriptionFoundException
import com.fr.husi.ktx.b64DecodeToString
import com.fr.husi.ktx.generateUserAgent
import com.fr.husi.ktx.isIpAddress
import com.fr.husi.ktx.kxs
import com.fr.husi.ktx.parseProxies
import com.fr.husi.ktx.toJsonMapKxs
import com.fr.husi.libcore.NO_OVERALL_TIMEOUT_MS
import com.fr.husi.libcore.resolveHttpClientFactory
import com.fr.husi.repository.resolveRepository
import com.fr.husi.resources.Res
import com.fr.husi.resources.no_proxies_found
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Suppress("EXPERIMENTAL_API_USAGE", "UNCHECKED_CAST")
object RawUpdater : GroupUpdater() {

    override suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        byUser: Boolean,
        warnings: MutableList<GroupUpdateWarning>,
    ): GroupUpdateResult.Success {

        val contentText: String
        var userInfo = ""
        if (subscription.link.startsWith("content://")) {
            contentText = readContentUri(subscription.link) ?: errNotFound()
        } else {
            val response = resolveHttpClientFactory().newHttpClient().apply {
                if (DataStore.serviceState.connected) {
                    useSocks5(
                        DataStore.mixedPort.get(),
                        DataStore.inboundUsername.get(),
                        DataStore.inboundPassword.get(),
                    )
                }
                if (subscription.ageIdentity.isNotBlank()) {
                    setAgeKey(subscription.ageIdentity)
                }
            }.newRequest().apply {
                setURL(subscription.link)
                setUserAgent(generateUserAgent(subscription.customUserAgent))
                // Subscription content can be large and the server can be slow; the
                // default overall deadline (TCPTimeout) kills the body read midway.
                // Same treatment as rule set / app update downloads: no overall
                // deadline, connection setup still times out, stall reader still
                // fails stalled transfers.
                setTimeout(NO_OVERALL_TIMEOUT_MS)
            }.execute()
            contentText = response.contentString
            userInfo = response.getHeader("Subscription-Userinfo")
        }

        val proxies = parseRaw(contentText) ?: errNotFound()

        if (!subscription.link.startsWith("content://")) {
            // https://github.com/crossutility/Quantumult/blob/master/extra-subscription-feature.md
            // Subscription-Userinfo: upload=2375927198; download=12983696043; total=1099511627776; expire=1862111613
            // Be careful that some value may be empty.
            if (userInfo.isNotBlank()) {
                var used = 0L
                var total = 0L
                var expired = 0L
                for (info in userInfo.split(";")) {
                    info.split("=", limit = 2).let {
                        if (it.size != 2) return@let
                        val key = it[0].trim()
                        val value = it[1].trim().toLongOrNull() ?: 0
                        when (key) {
                            "upload", "download" -> used += value
                            "total" -> total = value
                            "expire" -> expired = value
                        }
                    }
                }
                subscription.apply {
                    bytesUsed = used
                    bytesRemaining = total - used
                    expiryDate = expired
                }
            }
        }

        return tidyProxies(proxies, subscription, proxyGroup, byUser, warnings)
    }
    @Suppress("UNCHECKED_CAST")
    suspend fun parseRaw(text: String, fileName: String = ""): List<AbstractBean>? {

        val proxies = mutableListOf<AbstractBean>()

        runCatching {
            parseOpenConnectConfig(text)
        }.onSuccess { bean ->
            if (fileName.isNotBlank()) bean.name = fileName.removeSuffix(".conf")
            return listOf(bean)
        }

        if (looksLikeOpenVPNConfig(text)) {
            parseOpenVPNConfig(text).let { bean ->
                if (fileName.isNotBlank()) bean.name = fileName.removeSuffix(".ovpn")
                return listOf(bean)
            }
        }

        if (text.contains("[Interface]")) {
            // wireguard
            try {
                val beans = parseWireGuardConfig(text)
                val hasFileName = fileName.isNotBlank()
                for ((i, bean) in beans.withIndex()) {
                    if (hasFileName) bean.name = bean.name.removeSuffix(".conf")
                    if (beans.size > 1) bean.name += i
                }
                proxies.addAll(beans)
                return proxies
            } catch (e: Exception) {
                Logs.w(e)
            }
        }

        val trimmed = text.trimStart()

        // Clash YAML：只解析 proxies: 节点列表（groups/rules 不转换）
        if (text.contains("proxies:")) {
            try {
                ClashImporter.parse(text).takeIf { it.isNotEmpty() }?.let { return it }
            } catch (e: Exception) {
                Logs.w(e)
            }
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                val element = kxs.parseToJsonElement(text)
                if (element is JsonPrimitive) error("unexpected JSON primitive")
                parseJSON(element).takeIf { it.isNotEmpty() }?.let { return it }
            } catch (e: Exception) {
                Logs.w(e)
            }
        }

        if (!text.contains("://")) {
            try {
                parseProxies(text.b64DecodeToString()).takeIf { it.isNotEmpty() }?.let { return it }
            } catch (e: Exception) {
                Logs.w(e)
            }
        }

        try {
            parseProxies(text).takeIf { it.isNotEmpty() }?.let { return it }
        } catch (e: SubscriptionFoundException) {
            throw e
        } catch (e: Exception) {
            Logs.w(e)
        }

        return null
    }

    fun parseJSON(element: JsonElement): List<AbstractBean> {
        val proxies = ArrayList<AbstractBean>()

        if (element is JsonObject) {
            val json = element.toJsonMapKxs()
            when {
                // 机场分享格式（如蜂鸟）：{"name": "...", "_plain": "Trojan,host,port,password,cipher"}
                "_plain" in json -> {
                    parsePlainShare(json)?.let { proxies.add(it) }
                }

                "outbounds" in json || "endpoints" in json -> {
                    val outbounds = json["outbounds"] as? List<*>
                    val endpoints = json["endpoints"] as? List<*>
                    var length = outbounds?.size ?: 0
                    endpoints?.size?.let { length += it }
                    if (length == 0) {
                        errNotFound<Unit>()
                    }

                    fun add(outbound: Any?) {
                        val map = outbound as? JSONMap ?: return
                        parseOutbound(map)?.let {
                            proxies.add(it)
                        }
                    }
                    outbounds?.forEach { outbound ->
                        try {
                            add(outbound)
                        } catch (e: Exception) {
                            Logs.w(e)
                        }
                    }
                    endpoints?.forEach { endpoint ->
                        try {
                            add(endpoint)
                        } catch (e: Exception) {
                            Logs.w(e)
                        }
                    }
                }

                "server" in json && ("server_port" in json || "server_ports" in json) -> {
                    return parseOutbound(json)?.let {
                        listOf(it)
                    } ?: errNotFound()
                }

                "peers" in json -> return parseOutbound(json)?.let {
                    listOf(it)
                } ?: errNotFound()

                "server" in json && ("up" in json || "up_mbps" in json) -> {
                    return listOf(json.parseHysteria1Json())
                }

                "method" in json -> {
                    return listOf(json.parseShadowsocks())
                }

                "version" in json && "servers" in json -> {
                    val servers = json["servers"] as? List<*>
                    servers?.forEach {
                        val server = it as? JSONMap ?: return@forEach
                        proxies.add(server.parseShadowsocks())
                    }
                }

                else -> {
                    errNotFound()
                }
            }
        } else if (element is JsonArray) {
            for (item in element) {
                if (item is JsonObject) {
                    proxies.addAll(parseJSON(item))
                }
            }
        }

        proxies.forEach {
            it.initializeDefaultValues()
            if (it is StandardV2RayBean) {
                if (it.isTLS && it.sni.isBlank() && it.host.isNotBlank() && !it.host.isIpAddress()) {
                    it.sni = it.host
                }
            }
        }
        return proxies
    }

    /**
     * 机场节点分享格式（单节点 JSON 内嵌 `_plain` 字段）：
     *   {"name": "节点名", "_plain": "Trojan,host,port,password,chacha20"}
     * 分段含义：[0]=类型 [1]=服务器 [2]=端口 [3]=密码 [4]=加密方式（trojan 忽略，恒为 TLS）
     */
    private fun parsePlainShare(json: JSONMap): AbstractBean? {
        val plain = (json["_plain"] as? String)?.trim() ?: return null
        val parts = plain.split(",")
        if (parts.size < 4) {
            Logs.w("raw updater: _plain too short: $plain")
            return null
        }
        val type = parts[0].trim().lowercase()
        val name = (json["name"] as? String) ?: ""
        return when (type) {
            "trojan" -> {
                val port = parts[2].trim().toIntOrNull() ?: run {
                    Logs.w("raw updater: _plain invalid port: $plain")
                    return null
                }
                TrojanBean().apply {
                    serverAddress = parts[1].trim()
                    serverPort = port
                    password = parts[3].trim()
                    security = "tls"
                    sni = serverAddress.takeIf { !it.isIpAddress() } ?: ""
                    this.name = name
                }
            }
            else -> {
                Logs.w("raw updater: unsupported _plain type \"$type\", skipped")
                null
            }
        }
    }

    private inline fun <reified T> errNotFound(): T = runBlocking {
        error(resolveRepository().getString(Res.string.no_proxies_found))
    }
}
