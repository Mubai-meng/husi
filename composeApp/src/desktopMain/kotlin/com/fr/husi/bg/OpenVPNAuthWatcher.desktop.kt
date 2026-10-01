package com.fr.husi.bg

import com.fr.husi.resources.Res
import com.fr.husi.resources.openvpn_authentication
import com.fr.husi.vpn.firstVpnAuthPending
import kotlinx.coroutines.flow.map

internal object OpenVPNAuthWatcher {

    private val watcher = DesktopVpnAuthWatcher(
        title = Res.string.openvpn_authentication,
        logLabel = "openvpn auth watcher",
        pending = {
            subscribeOpenVPNStatus().map { update ->
                firstVpnAuthPending(
                    endpoints = update.endpointsList,
                    state = { it.state },
                    challengeId = { status ->
                        status.challenge.takeIf { status.hasChallenge() }?.id
                    },
                    tag = { it.endpointTag },
                )
            }
        },
    )

    fun start() = watcher.start()

    fun stop() = watcher.stop()
}
