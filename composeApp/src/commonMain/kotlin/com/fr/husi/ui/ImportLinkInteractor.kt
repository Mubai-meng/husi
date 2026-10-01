package com.fr.husi.ui

import com.fr.husi.GroupType
import com.fr.husi.SubscriptionType
import com.fr.husi.database.DataStore
import com.fr.husi.database.GroupManager
import com.fr.husi.database.ProfileManager
import com.fr.husi.database.ProxyGroup
import com.fr.husi.database.SubscriptionBean
import com.fr.husi.fmt.AbstractBean
import com.fr.husi.fmt.BeanConverters
import com.fr.husi.group.GroupUpdater
import com.fr.husi.ktx.b64Decode
import com.fr.husi.ktx.blankAsNull
import com.fr.husi.ktx.defaultOr
import com.fr.husi.ktx.parseProxies
import com.fr.husi.ktx.zlibDecompress
import com.fr.husi.libcore.Libcore

sealed interface ImportLinkPreview {
    object Ignore : ImportLinkPreview
    class Subscription(val group: ProxyGroup) : ImportLinkPreview
    class Profiles(val proxies: List<AbstractBean>) : ImportLinkPreview
}

fun isSubscriptionUri(uri: String): Boolean {
    return uri.startsWith("sing-box://import-remote-profile?") ||
            uri.startsWith("husi://subscription?")
}

class ImportLinkInteractor {

    suspend fun parseUri(uri: String): ImportLinkPreview {
        return if (isSubscriptionUri(uri)) {
            val group = parseSubscription(uri)
            if (group == null) ImportLinkPreview.Ignore else ImportLinkPreview.Subscription(group)
        } else {
            ImportLinkPreview.Profiles(parseProfiles(uri))
        }
    }

    fun parseSubscription(uri: String): ProxyGroup? {
        if (
            uri.startsWith("husi://") && !uri.startsWith("husi://subscription?") ||
            uri.startsWith("sing-box://") && !uri.startsWith("sing-box://import-remote-profile?")
        ) {
            return null
        }

        val urlForQuery = Libcore.parseURL(uri)
        val group: ProxyGroup
        val url = defaultOr(
            "",
            { urlForQuery.queryParameter("url") },
            {
                when (urlForQuery.scheme) {
                    "http", "https" -> uri
                    else -> null
                }
            },
        )
        if (url.isNotBlank()) {
            group = ProxyGroup(type = GroupType.SUBSCRIPTION)
            group.subscription = SubscriptionBean().apply {
                // cleartext format
                link = url
                type = when (urlForQuery.queryParameter("type")?.lowercase()) {
                    "oocv1" -> SubscriptionType.OOCv1
                    "sip008" -> SubscriptionType.SIP008
                    else -> SubscriptionType.RAW
                }
            }

            group.name = defaultOr(
                "",
                { urlForQuery.queryParameter("name") },
                { urlForQuery.fragment },
            )
        } else {
            val data =
                uri.substringAfter('?', "").substringBefore('#').blankAsNull() ?: return null
            group = BeanConverters.deserialize(
                ProxyGroup().apply { export = true },
                data.b64Decode().zlibDecompress(),
            ).apply {
                export = false
            }
        }

        if (group.name.isNullOrBlank() && group.subscription?.link.isNullOrBlank() && group.subscription?.token.isNullOrBlank()) {
            return null
        }
        group.name = group.name.blankAsNull() ?: ("Subscription #" + System.currentTimeMillis())
        return group
    }

    suspend fun parseProfiles(uri: String): List<AbstractBean> {
        return parseProxies(uri)
    }

    suspend fun createSubscriptionGroup(group: ProxyGroup): ProxyGroup {
        return GroupManager.createGroup(group)
    }

    suspend fun importSubscription(group: ProxyGroup) {
        val createdGroup = createSubscriptionGroup(group)
        GroupUpdater.executeUpdate(createdGroup, true)
    }

    suspend fun importProfiles(proxies: List<AbstractBean>): Int {
        val targetId = DataStore.selectedGroupForImport()
        for (proxy in proxies) {
            ProfileManager.createProfile(targetId, proxy)
        }
        DataStore.selectedGroup.set(targetId)
        return proxies.size
    }
}
