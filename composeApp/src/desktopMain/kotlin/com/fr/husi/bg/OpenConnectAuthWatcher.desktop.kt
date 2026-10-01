package com.fr.husi.bg

import com.fr.husi.resources.Res
import com.fr.husi.resources.openconnect_authentication
import com.fr.husi.vpn.firstVpnAuthPending
import kotlinx.coroutines.flow.map

internal object OpenConnectAuthWatcher {

    private val watcher = DesktopVpnAuthWatcher(
        title = Res.string.openconnect_authentication,
        logLabel = "openconnect auth watcher",
        pending = {
            subscribeOpenConnectStatus().map { update ->
                firstVpnAuthPending(
                    endpoints = update.endpointsList,
                    state = { it.state },
                    challengeId = { status ->
                        status.authChallenge.takeIf { status.hasAuthChallenge() }?.id
                    },
                    tag = { it.endpointTag },
                )
            }
        },
    )

    fun start() = watcher.start()

    fun stop() = watcher.stop()
}
