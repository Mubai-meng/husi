package com.fr.husi.fmt

import com.fr.husi.database.ProxyEntity
import com.fr.husi.database.ProxyGroup
import com.fr.husi.ktx.b64Decode
import com.fr.husi.ktx.b64EncodeUrlSafe
import com.fr.husi.ktx.zlibCompress
import com.fr.husi.ktx.zlibDecompress

fun parseUniversal(link: String): AbstractBean {
    return if (link.contains("?")) {
        val type = link.substringAfter("husi://").substringBefore("?")
        ProxyEntity(type = TypeMap[type] ?: error("Type $type not found")).apply {
            putByteArray(link.substringAfter("?").b64Decode().zlibDecompress())
        }.requireBean()
    } else {
        val type = link.substringAfter("husi://").substringBefore(":")
        ProxyEntity(type = TypeMap[type] ?: error("Type $type not found")).apply {
            putByteArray(link.substringAfter(":").substringAfter(":").b64Decode())
        }.requireBean()
    }
}

fun AbstractBean.toUniversalLink(): String {
    var link = "husi://"
    val type = ProxyEntity().putBean(this).type
    link += TypeMap.reversed[type] ?: error("Type $type not found")
    link += "?"
    link += BeanConverters.serialize(this).zlibCompress(9).b64EncodeUrlSafe()
    return link
}


fun ProxyGroup.toUniversalLink(): String {
    var link = "husi://subscription?"
    export = true
    link += BeanConverters.serialize(this).zlibCompress(9).b64EncodeUrlSafe()
    export = false
    return link
}
