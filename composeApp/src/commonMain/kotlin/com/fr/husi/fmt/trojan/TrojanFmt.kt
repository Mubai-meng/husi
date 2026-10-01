package com.fr.husi.fmt.trojan

import com.fr.husi.fmt.v2ray.parseDuckSoft
import com.fr.husi.ktx.parseBoolean
import com.fr.husi.ktx.queryParameterNotBlank
import com.fr.husi.libcore.Libcore

fun parseTrojan(link: String): TrojanBean {
    val url = Libcore.parseURL(link)
    return TrojanBean().apply {
        parseDuckSoft(url)
        allowInsecure = url.parseBoolean("allowInsecure")
        url.queryParameterNotBlank("peer")?.let {
            sni = it
        }
    }

}
