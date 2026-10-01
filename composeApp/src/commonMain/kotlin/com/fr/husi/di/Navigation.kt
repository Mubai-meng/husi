@file:OptIn(KoinExperimentalAPI::class)

package com.fr.husi.di

import com.fr.husi.results.LocalResultEventBus
import com.fr.husi.ui.AboutScreen
import com.fr.husi.ui.AssetEditScreen
import com.fr.husi.ui.AssetsScreen
import com.fr.husi.ui.GroupScreen
import com.fr.husi.ui.GroupSettingsScreen
import com.fr.husi.ui.LibrariesScreen
import com.fr.husi.ui.LocalNavigator
import com.fr.husi.ui.LogcatScreen
import com.fr.husi.ui.MainScreenScope
import com.fr.husi.ui.MainViewModel
import com.fr.husi.ui.SnackbarEmitter
import com.fr.husi.ui.NavRoutes
import com.fr.husi.ui.PluginScreen
import com.fr.husi.ui.ProfilePickerController
import com.fr.husi.ui.RouteScreen
import com.fr.husi.ui.RouteSettingsScreen
import com.fr.husi.ui.configuration.ConfigurationScreen
import com.fr.husi.ui.dashboard.DashboardScreen
import com.fr.husi.ui.jsoneditor.ConfigEditScreen
import com.fr.husi.ui.profile.ProfileEditorResult
import com.fr.husi.ui.profile.ProfileEditorScreen
import com.fr.husi.ui.profile.SIP003EditorScreen
import com.fr.husi.ui.remote.RemoteControlScreen
import com.fr.husi.ui.remote.RemoteServerEditScreen
import com.fr.husi.ui.settings.SettingsPageScreen
import com.fr.husi.ui.settings.SettingsScreen
import com.fr.husi.ui.tools.BackupScreen
import com.fr.husi.ui.tools.DebugScreen
import com.fr.husi.ui.tools.GetCertScreen
import com.fr.husi.ui.tools.NetworkQualityScreen
import com.fr.husi.ui.tools.NetworkScreen
import com.fr.husi.ui.tools.RuleSetMatchScreen
import com.fr.husi.ui.tools.StunScreen
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.module.dsl.scopedOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.koin.dsl.navigation3.navigation
import org.koin.dsl.onClose

internal val commonNavigationModule = module {
    scope<MainScreenScope> {
        scopedOf(::MainViewModel) onClose { it?.close() }
        scopedOf(::ProfilePickerController)
        scopedOf(::SnackbarEmitter)

        navigation<NavRoutes.Configuration> { _ ->
            val viewModel = get<MainViewModel>()
            val navigator = LocalNavigator.current
            ConfigurationScreen(
                mainViewModel = viewModel,
                onOpenGroups = { navigator.navigateTo(NavRoutes.Groups) },
                onOpenGroupSettings = { groupId ->
                    navigator.navigateTo(NavRoutes.GroupSettings(groupId = groupId))
                },
                onOpenProfileEditor = navigator::navigateTo,
            )
        }

        navigation<NavRoutes.Groups> { _ ->
            val viewModel = get<MainViewModel>()
            val navigator = LocalNavigator.current
            GroupScreen(
                mainViewModel = viewModel,
                onBackPress = { navigator.popBackStack() },
                openGroupSettings = { groupId ->
                    navigator.navigateTo(NavRoutes.GroupSettings(groupId = groupId))
                },
            )
        }

        navigation<NavRoutes.Route> { _ ->
            val navigator = LocalNavigator.current
            RouteScreen(
                openRouteSettings = { routeId ->
                    navigator.navigateTo(NavRoutes.RouteSettings(routeId = routeId))
                },
                openAssets = {
                    navigator.navigateTo(NavRoutes.Assets)
                },
            )
        }

        navigation<NavRoutes.Settings> { _ ->
            val navigator = LocalNavigator.current
            SettingsScreen(
                openSettingsPage = { kind ->
                    navigator.navigateTo(NavRoutes.SettingsPage(kind))
                },
                openTool = navigator::navigateTo,
                openPlugin = { navigator.navigateTo(NavRoutes.Plugin) },
                openAbout = { navigator.navigateTo(NavRoutes.About) },
                openRemoteControl = { navigator.navigateTo(NavRoutes.RemoteControl) },
            )
        }

        navigation<NavRoutes.RemoteControl> { _ ->
            val navigator = LocalNavigator.current
            RemoteControlScreen(
                onBackPress = { navigator.popBackStack() },
                onEditServer = { id ->
                    navigator.navigateTo(NavRoutes.RemoteServerEdit(id = id))
                },
            )
        }

        navigation<NavRoutes.RemoteServerEdit> { route ->
            val navigator = LocalNavigator.current
            RemoteServerEditScreen(
                serverId = route.id,
                onBackPress = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.SettingsPage> { route ->
            val navigator = LocalNavigator.current
            SettingsPageScreen(
                kind = route.kind,
                onBackPress = { navigator.popBackStack() },
                openAppManager = { navigator.navigateTo(NavRoutes.AppManager) },
            )
        }

        navigation<NavRoutes.Plugin> { _ ->
            val navigator = LocalNavigator.current
            PluginScreen(
                onBackPress = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.Log> { _ ->
            val navigator = LocalNavigator.current
            LogcatScreen(
                onOpenRemoteControl = { navigator.navigateTo(NavRoutes.RemoteControl) },
            )
        }

        navigation<NavRoutes.Dashboard> { _ ->
            val navigator = LocalNavigator.current
            DashboardScreen(
                openConnectController = get(),
                openVPNController = get(),
                openRouteSettings = { initialState ->
                    navigator.navigateTo(
                        NavRoutes.RouteSettings(
                            routeId = -1L,
                            useDraft = true,
                            initialState = initialState,
                        ),
                    )
                },
                onOpenRemoteControl = { navigator.navigateTo(NavRoutes.RemoteControl) },
            )
        }

        navigation<NavRoutes.ProfileEditor> { route ->
            val navigator = LocalNavigator.current
            val profilePickerController = get<ProfilePickerController>()
            val resultBus = LocalResultEventBus.current
            ProfileEditorScreen(
                type = route.type,
                profileId = route.id,
                isSubscription = route.subscription,
                onOpenProfileSelect = profilePickerController::open,
                onOpenConfigEditor = navigator::navigateTo,
                onOpenSIP003Editor = navigator::navigateTo,
                onResult = { updated ->
                    resultBus.sendResult(result = ProfileEditorResult(profileId = route.id, updated = updated))
                    navigator.popBackStack()
                },
            )
        }

        navigation<NavRoutes.GroupSettings> { route ->
            val navigator = LocalNavigator.current
            GroupSettingsScreen(
                groupId = route.groupId,
                onBackPress = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.RouteSettings> { route ->
            val navigator = LocalNavigator.current
            val profilePickerController = get<ProfilePickerController>()
            RouteSettingsScreen(
                routeId = route.routeId,
                initialState = route.initialState.takeIf { route.useDraft },
                onBackPress = { navigator.popBackStack() },
                onSaved = { navigator.popBackStack() },
                onOpenProfileSelect = profilePickerController::open,
                onOpenAppList = navigator::navigateTo,
                onOpenConfigEditor = navigator::navigateTo,
            )
        }

        navigation<NavRoutes.ConfigEditor> { route ->
            val navigator = LocalNavigator.current
            ConfigEditScreen(
                initialText = route.initialText,
                resultKey = route.resultKey,
                schema = route.schema,
                onBack = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.SIP003Editor> { route ->
            val navigator = LocalNavigator.current
            SIP003EditorScreen(
                pluginName = route.pluginName,
                initialOpts = route.initialOpts,
                resultKey = route.resultKey,
                onBack = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.Assets> { _ ->
            val navigator = LocalNavigator.current
            AssetsScreen(
                onBackPress = { navigator.popBackStack() },
                onOpenAssetEditor = navigator::navigateTo,
            )
        }

        navigation<NavRoutes.AssetEdit> { route ->
            val navigator = LocalNavigator.current
            AssetEditScreen(
                assetName = route.assetName,
                resultKey = route.resultKey,
                onBack = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.ToolsPage.Network> { _ ->
            val navigator = LocalNavigator.current
            NetworkScreen(
                onBackPress = { navigator.popBackStack() },
                onOpenTool = navigator::navigateTo,
            )
        }

        navigation<NavRoutes.ToolsPage.Backup> { _ ->
            val navigator = LocalNavigator.current
            BackupScreen(
                onBackPress = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.ToolsPage.Debug> { _ ->
            val navigator = LocalNavigator.current
            DebugScreen(
                onBackPress = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.ToolsPage.Stun> { _ ->
            val navigator = LocalNavigator.current
            StunScreen(
                onBackPress = { navigator.popBackStack() },
                onOpenRemoteControl = { navigator.navigateTo(NavRoutes.RemoteControl) },
            )
        }

        navigation<NavRoutes.ToolsPage.GetCert> { _ ->
            val navigator = LocalNavigator.current
            GetCertScreen(
                onBack = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.ToolsPage.NetworkQuality> { _ ->
            val navigator = LocalNavigator.current
            NetworkQualityScreen(
                onBackPress = { navigator.popBackStack() },
                onOpenRemoteControl = { navigator.navigateTo(NavRoutes.RemoteControl) },
            )
        }

        navigation<NavRoutes.ToolsPage.RuleSetMatch> { _ ->
            val navigator = LocalNavigator.current
            RuleSetMatchScreen(
                onBackPress = { navigator.popBackStack() },
            )
        }

        navigation<NavRoutes.About> { _ ->
            val navigator = LocalNavigator.current
            AboutScreen(
                onBackPress = { navigator.popBackStack() },
                onNavigateToLibraries = {
                    navigator.navigateTo(NavRoutes.Libraries)
                },
            )
        }

        navigation<NavRoutes.Libraries> { _ ->
            val navigator = LocalNavigator.current
            LibrariesScreen(
                onBackPress = { navigator.popBackStack() },
            )
        }
    }
}
