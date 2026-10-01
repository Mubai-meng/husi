package com.fr.husi.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fr.husi.compose.BoxedVerticalScrollbar
import com.fr.husi.compose.ProvidePreferenceLocals
import com.fr.husi.compose.collectAsStateWithLifecycle
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.Preference
import com.fr.husi.compose.PreferenceCategory
import com.fr.husi.compose.fadingEdge
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.compose.SagerFabClearance
import com.fr.husi.compose.plus
import com.fr.husi.compose.withNavigation
import com.fr.husi.database.DataStore
import com.fr.husi.platform.PlatformInfo
import com.fr.husi.resources.Res
import com.fr.husi.resources.app_update_settings
import com.fr.husi.resources.backup
import com.fr.husi.resources.bug_report
import com.fr.husi.resources.cag_dns
import com.fr.husi.resources.cag_misc
import com.fr.husi.resources.cast_connected
import com.fr.husi.resources.developer_mode
import com.fr.husi.resources.dns
import com.fr.husi.resources.file_export
import com.fr.husi.resources.flight_takeoff
import com.fr.husi.resources.general_settings
import com.fr.husi.resources.inbound_settings
import com.fr.husi.resources.info
import com.fr.husi.resources.menu_about
import com.fr.husi.resources.more
import com.fr.husi.resources.nat
import com.fr.husi.resources.nfc
import com.fr.husi.resources.ntp_category
import com.fr.husi.resources.phonelink_ring
import com.fr.husi.resources.plugin
import com.fr.husi.resources.protocol_settings
import com.fr.husi.resources.remote_control
import com.fr.husi.resources.route_options
import com.fr.husi.resources.router
import com.fr.husi.resources.settings
import com.fr.husi.resources.system_daemon
import com.fr.husi.resources.timelapse
import com.fr.husi.resources.tools_network
import com.fr.husi.resources.update
import com.fr.husi.resources.wifi
import com.fr.husi.ui.NavRoutes
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    openSettingsPage: (NavRoutes.SettingsPage.Kind) -> Unit,
    openTool: (NavRoutes.ToolsPage) -> Unit,
    openPlugin: () -> Unit,
    openAbout: () -> Unit,
    openRemoteControl: () -> Unit,
) {
    val listState = rememberLazyListState()
    val isExpert by DataStore.isExpert.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        ProvidePreferenceLocals {
            val contentPadding = innerPadding.withNavigation() +
                PaddingValues(bottom = SagerFabClearance)
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .fadingEdge(listState),
                    contentPadding = contentPadding,
                ) {
                    preferenceGroup {
                        Preference(
                            title = { Text(stringResource(Res.string.general_settings)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.settings,
                                    color = IconMaskColors.IconLightBlue,
                                )
                            },
                            onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.General) },
                        )
                        if (!PlatformInfo.isAndroid) {
                            Preference(
                                title = { Text(stringResource(Res.string.system_daemon)) },
                                icon = {
                                    MaskedIcon(
                                        Res.drawable.developer_mode,
                                        color = IconMaskColors.IconLavender,
                                    )
                                },
                                onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.Daemon) },
                            )
                        }
                        Preference(
                            title = { Text(stringResource(Res.string.route_options)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.router,
                                    color = IconMaskColors.IconLightGreen,
                                )
                            },
                            onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.Route) },
                        )
                        Preference(
                            title = { Text(stringResource(Res.string.protocol_settings)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.flight_takeoff,
                                    color = IconMaskColors.IconLightYellow,
                                )
                            },
                            onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.Protocol) },
                        )
                        Preference(
                            title = { Text(stringResource(Res.string.cag_dns)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.dns,
                                    color = IconMaskColors.IconCyan,
                                )
                            },
                            onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.Dns) },
                        )
                        Preference(
                            title = { Text(stringResource(Res.string.inbound_settings)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.nat,
                                    color = IconMaskColors.IconCoral,
                                )
                            },
                            onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.Inbound) },
                        )
                        Preference(
                            title = { Text(stringResource(Res.string.cag_misc)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.cast_connected,
                                    color = IconMaskColors.IconWarmGray,
                                )
                            },
                            onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.Misc) },
                        )
                        Preference(
                            title = { Text(stringResource(Res.string.ntp_category)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.timelapse,
                                    color = IconMaskColors.IconLightPink,
                                )
                            },
                            onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.Ntp) },
                        )
                        if (PlatformInfo.isAndroid) {
                            Preference(
                                title = { Text(stringResource(Res.string.app_update_settings)) },
                                icon = {
                                    MaskedIcon(
                                        Res.drawable.update,
                                        color = IconMaskColors.IconLightGreen,
                                    )
                                },
                                onClick = { openSettingsPage(NavRoutes.SettingsPage.Kind.AppUpdate) },
                            )
                        }
                    }

                    item { PreferenceCategory(text = { Text(stringResource(Res.string.more)) }) }
                    preferenceGroup {
                        Preference(
                            title = { Text(stringResource(Res.string.remote_control)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.phonelink_ring,
                                    color = IconMaskColors.IconCyan,
                                )
                            },
                            onClick = openRemoteControl,
                        )
                        Preference(
                            title = { Text(stringResource(Res.string.tools_network)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.wifi,
                                    color = IconMaskColors.IconLightBlue,
                                )
                            },
                            onClick = { openTool(NavRoutes.ToolsPage.Network) },
                        )
                        Preference(
                            title = { Text(stringResource(Res.string.backup)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.file_export,
                                    color = IconMaskColors.IconLightYellow,
                                )
                            },
                            onClick = { openTool(NavRoutes.ToolsPage.Backup) },
                        )
                        if (isExpert) {
                            Preference(
                                title = { Text("DEBUG") },
                                icon = {
                                    MaskedIcon(
                                        Res.drawable.bug_report,
                                        color = IconMaskColors.IconCoral,
                                    )
                                },
                                onClick = { openTool(NavRoutes.ToolsPage.Debug) },
                            )
                        }
                        Preference(
                            title = { Text(stringResource(Res.string.plugin)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.nfc,
                                    color = IconMaskColors.IconCyan,
                                )
                            },
                            onClick = openPlugin,
                        )
                        Preference(
                            title = { Text(stringResource(Res.string.menu_about)) },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.info,
                                    color = IconMaskColors.IconLavender,
                                )
                            },
                            onClick = openAbout,
                        )
                    }
                }

                BoxedVerticalScrollbar(
                    modifier = Modifier
                        .padding(contentPadding)
                        .fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    style = defaultMaterialScrollbarStyle().copy(
                        thickness = 12.dp,
                    ),
                )
            }
        }
    }
}
