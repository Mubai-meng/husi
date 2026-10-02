package com.fr.husi.group

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlList
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNull
import com.charleskorn.kaml.YamlNode
import com.charleskorn.kaml.YamlScalar
import com.fr.husi.fmt.AbstractBean
import com.fr.husi.fmt.hysteria.HysteriaBean
import com.fr.husi.fmt.shadowsocks.ShadowsocksBean
import com.fr.husi.fmt.trojan.TrojanBean
import com.fr.husi.ktx.Logs
import com.fr.husi.ktx.isIpAddress

/**
 * Clash YAML 导入器（非侵入式新增，便于向上游合并）。
 *
 * 只解析顶层 `proxies:` 列表中的节点；`proxy-groups` / `rules` 等编排信息
 * 与 Husi 的分组模型不兼容，导入时忽略。
 *
 * 支持的节点类型：
 * - trojan:    name/server/port/password/sni/skip-cert-verify/udp
 * - ss:        name/server/port/cipher/password/plugin(obfs 及 plugin-opts)
 * - hysteria2: name/server/port(/ports 端口跳跃)/password/sni/obfs/obfs-password/skip-cert-verify
 * 其余类型记录日志后跳过，不中断整批导入。
 */
internal object ClashImporter {

    private const val TAG = "clash-import"

    fun looksLikeClash(text: String): Boolean = text.contains("proxies:")

    fun parse(text: String): List<AbstractBean> {
        val root = Yaml.default.parseToYamlNode(text)
        val proxiesList = (root as? YamlMap)?.node("proxies") as? YamlList ?: return emptyList()

        val beans = ArrayList<AbstractBean>()
        for (item in proxiesList.items) {
            val map = item as? YamlMap ?: continue
            val type = map.str("type")?.lowercase()
            if (type == null) {
                Logs.w("$TAG: proxy entry without type, skipped")
                continue
            }
            try {
                when (type) {
                    "trojan" -> beans.add(parseTrojan(map))
                    "ss" -> beans.add(parseShadowsocks(map))
                    "hysteria2" -> beans.add(parseHysteria2(map))
                    else -> Logs.w("$TAG: unsupported proxy type \"$type\", skipped")
                }
            } catch (e: Exception) {
                Logs.w(e)
                Logs.w("$TAG: failed to parse proxy \"${map.str("name") ?: "?"}\", skipped")
            }
        }
        return beans
    }

    private fun parseTrojan(map: YamlMap): TrojanBean {
        val bean = TrojanBean()
        bean.serverAddress = map.str("server") ?: error("trojan proxy missing server")
        bean.serverPort = map.int("port") ?: error("trojan proxy missing port")
        bean.password = map.str("password") ?: error("trojan proxy missing password")
        bean.security = "tls"
        bean.name = map.str("name") ?: ""
        bean.sni = map.str("sni") ?: ""
        bean.allowInsecure = map.bool("skip-cert-verify") ?: false
        if (bean.sni.isBlank() && !bean.serverAddress.isIpAddress()) {
            bean.sni = bean.serverAddress
        }
        bean.initializeDefaultValues()
        return bean
    }

    private fun parseShadowsocks(map: YamlMap): ShadowsocksBean {
        val bean = ShadowsocksBean()
        bean.serverAddress = map.str("server") ?: error("ss proxy missing server")
        bean.serverPort = map.int("port") ?: error("ss proxy missing port")
        bean.method = map.str("cipher") ?: error("ss proxy missing cipher")
        bean.password = map.str("password") ?: ""
        bean.name = map.str("name") ?: ""
        parsePlugin(map)?.let { bean.plugin = it }
        bean.initializeDefaultValues()
        return bean
    }

    /**
     * Clash hysteria2 → HysteriaBean(PROTOCOL_VERSION_2)。
     * `ports`（端口跳跃，如 "1000-2000" 或逗号组合）优先于单值 `port`。
     */
    private fun parseHysteria2(map: YamlMap): HysteriaBean {
        val bean = HysteriaBean()
        bean.protocolVersion = HysteriaBean.PROTOCOL_VERSION_2
        bean.serverAddress = map.str("server") ?: error("hysteria2 proxy missing server")
        bean.serverPorts = map.str("ports")
            ?: map.int("port")?.toString()
            ?: error("hysteria2 proxy missing port")
        bean.authPayload = map.str("password") ?: ""
        bean.name = map.str("name") ?: ""
        bean.sni = map.str("sni") ?: ""
        bean.obfsType = map.str("obfs") ?: ""
        bean.obfsPassword = map.str("obfs-password") ?: ""
        bean.allowInsecure = map.bool("skip-cert-verify") ?: false
        if (bean.sni.isBlank() && !bean.serverAddress.isIpAddress()) {
            bean.sni = bean.serverAddress
        }
        bean.initializeDefaultValues()
        return bean
    }

    /**
     * Clash 的 obfs 插件 → SIP003 本地插件串：
     *   plugin: obfs
     *   plugin-opts: { mode: http, host: example.com }
     * → "obfs-local;obfs=http;obfs-host=example.com"
     */
    private fun parsePlugin(map: YamlMap): String? {
        val plugin = map.str("plugin")?.lowercase() ?: return null
        val opts = map.node("plugin-opts") as? YamlMap
        return when (plugin) {
            "obfs" -> buildString {
                append("obfs-local;obfs=")
                append(opts?.str("mode") ?: "http")
                val host = opts?.str("host")
                if (!host.isNullOrBlank()) append(";obfs-host=").append(host)
            }
            "v2ray-plugin" -> {
                Logs.w("$TAG: v2ray-plugin not supported in clash import, skipped for \"${map.str("name") ?: "?"}\"")
                null
            }
            else -> {
                Logs.w("$TAG: unsupported plugin \"$plugin\", skipped for \"${map.str("name") ?: "?"}\"")
                null
            }
        }
    }

    // ---- kaml YamlNode 取值辅助（走 entries 显式查找，不依赖 get() 泛型签名） ----

    private fun YamlMap.node(key: String): YamlNode? =
        entries.entries.firstOrNull { it.key.content == key }?.value

    private fun YamlMap.str(key: String): String? = when (val node = node(key)) {
        null, is YamlNull -> null
        is YamlScalar -> node.content
        else -> null
    }

    private fun YamlMap.int(key: String): Int? = str(key)?.trim()?.toIntOrNull()

    private fun YamlMap.bool(key: String): Boolean? = str(key)?.trim()?.lowercase()?.let {
        when (it) {
            "true", "yes", "on" -> true
            "false", "no", "off" -> false
            else -> null
        }
    }
}
