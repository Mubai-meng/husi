package com.fr.husi.di

import com.fr.husi.PLUGIN_CA_FILE
import com.fr.husi.bg.AppUpdateAutoChecker
import com.fr.husi.compose.material3.PlatformMaterialApi
import com.fr.husi.compose.theme.PlatformThemeApi
import com.fr.husi.core.CoreClient
import com.fr.husi.core.CoreVersionCache
import com.fr.husi.core.KurpcCoreClient
import com.fr.husi.core.rootCertificatesSocketFactory
import com.fr.husi.core.remote.RemoteClientFactory
import com.fr.husi.core.remote.RemoteControlManager
import com.fr.husi.database.SagerDatabase
import com.fr.husi.repository.Repository
import com.fr.husi.tailscale.TaildropSendManager
import com.fr.husi.tailscale.TailscaleSSHSessionManager
import com.fr.husi.tailscale.TailscaleStatusRepository
import com.fr.husi.tailscale.dashboardNoticeCounts
import com.fr.husi.ui.ImportLinkInteractor
import com.fr.husi.ui.dashboard.DashboardNoticeTracker
import com.fr.husi.ui.dashboard.asNoticeCounts
import com.fr.husi.ui.openconnect.OpenConnectAuthController
import com.fr.husi.ui.openvpn.OpenVPNAuthController
import kotlinx.coroutines.flow.map
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

private fun commonUiModule() = module {
    single<PlatformMaterialApi> { platformMaterialApi() }
    single<PlatformThemeApi> { platformThemeApi() }
    // Resolve the socket base path on every dial so CoreHostController can
    // switch between the session working dir and the system daemon path.
    single<CoreClient> {
        val repository = get<Repository>()
        KurpcCoreClient.local { coreClientBasePath(repository) }
    }
    single { CoreVersionCache(coreClient = get()) }
    single {
        // Written at startup from the user's certificate provider: by loadCA() on Android, by
        // the core host controller on desktop.
        val rootCertificates = get<Repository>().externalAssetsDir.resolve(PLUGIN_CA_FILE)
        RemoteControlManager(
            localClient = get(),
            dao = SagerDatabase.remoteServerDao,
            remoteClientFactory = RemoteClientFactory { url, secret ->
                KurpcCoreClient.remote(url, secret) { rootCertificatesSocketFactory(rootCertificates) }
            },
        )
    }
    single { AppUpdateAutoChecker() }
    singleOf(::ImportLinkInteractor)
    single { OpenConnectAuthController(targets = get<RemoteControlManager>().runningTarget) }
    single { OpenVPNAuthController(targets = get<RemoteControlManager>().runningTarget) }
    single {
        DashboardNoticeTracker(
            noticeCounts = listOf(
                get<OpenConnectAuthController>().pendingChallengeKeys.map { it.asNoticeCounts() },
                get<OpenVPNAuthController>().pendingChallengeKeys.map { it.asNoticeCounts() },
                get<TailscaleStatusRepository>().snapshot.map { it.endpoints.dashboardNoticeCounts() },
            ),
        )
    }
    single { TailscaleStatusRepository(targets = get<RemoteControlManager>().runningTarget) }
    single { TaildropSendManager(targets = get<RemoteControlManager>().runningTarget) }
    single { TailscaleSSHSessionManager(targets = get<RemoteControlManager>().runningTarget) }
}

/**
 * Directory that holds `api.sock` for [KurpcCoreClient] (or, for the Windows daemon, the pipe
 * path itself). Android: the files dir, which `initCore` makes the core's internal assets path.
 * Desktop: the session host working dir under the data directory, or the daemon's.
 */
internal expect fun coreClientBasePath(repository: Repository): String

internal expect fun platformMaterialApi(): PlatformMaterialApi
internal expect fun platformThemeApi(): PlatformThemeApi
internal expect fun platformRepositoryModule(repository: Repository): Module
internal expect fun platformKoinModules(): List<Module>

fun initHusiKoin(repository: Repository) {
    if (GlobalContext.getOrNull() != null) return
    startKoin {
        modules(
            listOf(platformRepositoryModule(repository), commonUiModule(), commonNavigationModule) +
                platformKoinModules(),
        )
    }
}
