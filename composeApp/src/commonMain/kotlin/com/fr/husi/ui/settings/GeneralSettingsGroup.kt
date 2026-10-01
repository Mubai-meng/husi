package com.fr.husi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fr.husi.Key
import com.fr.husi.bg.BackendState
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.IconMaskShapes
import com.fr.husi.compose.ListPreference
import com.fr.husi.compose.collectAsStateWithLifecycle
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.Preference
import com.fr.husi.compose.SliderPreference
import com.fr.husi.compose.SwitchPreference
import com.fr.husi.compose.TextButton
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.material3.Icon
import com.fr.husi.compose.material3.Surface
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.theme.themeString
import com.fr.husi.compose.theme.themes
import com.fr.husi.database.DataStore
import com.fr.husi.database.preference.PreferenceProxy
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.ktx.intListN
import com.fr.husi.logLevelString
import com.fr.husi.platform.PlatformInfo
import com.fr.husi.repository.resolveRepository
import com.fr.husi.resources.Res
import com.fr.husi.resources.always_show_address
import com.fr.husi.resources.always_show_address_sum
import com.fr.husi.resources.auto
import com.fr.husi.resources.blurred_address
import com.fr.husi.resources.bug_report
import com.fr.husi.resources.cancel
import com.fr.husi.resources.center_focus_weak
import com.fr.husi.resources.check
import com.fr.husi.resources.color_lens
import com.fr.husi.resources.description
import com.fr.husi.resources.developer_board
import com.fr.husi.resources.developer_mode
import com.fr.husi.resources.disable
import com.fr.husi.resources.enable
import com.fr.husi.resources.follow_system
import com.fr.husi.resources.insecure_warn
import com.fr.husi.resources.language
import com.fr.husi.resources.language_system_default
import com.fr.husi.resources.log_level
import com.fr.husi.resources.long_click_to_see_name
import com.fr.husi.resources.max_log_line
import com.fr.husi.resources.mtu
import com.fr.husi.resources.night_mode
import com.fr.husi.resources.profile_traffic_statistics
import com.fr.husi.resources.profile_traffic_statistics_summary
import com.fr.husi.resources.public_icon
import com.fr.husi.resources.security
import com.fr.husi.resources.service_mode
import com.fr.husi.resources.service_mode_proxy
import com.fr.husi.resources.service_mode_vpn
import com.fr.husi.resources.show_direct_speed
import com.fr.husi.resources.show_direct_speed_sum
import com.fr.husi.resources.shutter_speed
import com.fr.husi.resources.speed
import com.fr.husi.resources.speed_interval
import com.fr.husi.resources.theme
import com.fr.husi.resources.traffic
import com.fr.husi.resources.transgender
import com.fr.husi.resources.translate
import com.fr.husi.resources.wb_sunny
import com.fr.husi.ui.AppLanguage
import com.fr.husi.ui.AutoConnectPreference
import com.fr.husi.ui.MeteredNetworkPreference
import com.fr.husi.ui.PlatformGeneralOptions
import com.fr.husi.ui.PlatformSecurityOptions
import com.fr.husi.ui.StringOrRes
import com.fr.husi.ui.getStringOrRes
import com.fr.husi.ui.rememberAppLanguageController
import com.fr.husi.ui.rememberApplyNightMode
import com.fr.husi.ui.rememberThemeExtraColors
import com.fr.husi.ui.stringOrRes
import kotlinx.coroutines.runBlocking
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
private fun ColorPickerPreference(
    proxy: PreferenceProxy<Int>,
    title: @Composable () -> Unit,
    enabled: Boolean = true,
) {
    val currentTheme by proxy.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    val extraColors = rememberThemeExtraColors()
    Preference(
        title = { title() },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        icon = {
            MaskedIcon(
                Res.drawable.color_lens,
                color = IconMaskColors.IconLightOrange,
            )
        },
        summary = { Text(stringResource(themeString(currentTheme))) },
        widgetContainer = {
            Box(modifier = Modifier.padding(end = 8.dp)) {
                Circle(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
        },
        onClick = { showDialog = true },
    )

    if (showDialog) {
        val colors = themes + extraColors

        BasicAlertDialog(
            onDismissRequest = { showDialog = false },
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.theme),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (PlatformInfo.isAndroid) Text(
                        text = stringResource(Res.string.long_click_to_see_name),
                        modifier = Modifier.padding(bottom = 16.dp),
                        style = MaterialTheme.typography.labelSmallEmphasized,
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        items(
                            count = colors.size,
                            key = { index -> index },
                            contentType = { 0 },
                        ) { index ->
                            val theme = index + 1
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable {
                                        proxy.setBlocking(theme)
                                        showDialog = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                        TooltipAnchorPosition.Above,
                                    ),
                                    tooltip = {
                                        PlainTooltip {
                                            Text(stringResource(themeString(theme)))
                                        }
                                    },
                                    state = rememberTooltipState(),
                                ) {
                                    Circle(
                                        modifier = Modifier.size(48.dp),
                                        color = colors[index],
                                        selected = currentTheme == theme,
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(stringResource(Res.string.cancel)) {
                            showDialog = false
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GeneralSettingsGroup(
    needReload: () -> Unit,
    needRestart: () -> Unit,
    showMessage: (String) -> Unit,
) {
    val applyNightMode = rememberApplyNightMode()
    val isExpertState by DataStore.isExpert.collectAsStateWithLifecycle()

    AutoConnectPreference(showMessage = showMessage)

    ColorPickerPreference(
        proxy = DataStore.appTheme,
        title = { Text(stringResource(Res.string.theme)) },
    )

    fun nightString(index: Int): StringResource = when (index) {
        0 -> Res.string.follow_system
        1 -> Res.string.enable
        2 -> Res.string.disable
        3 -> Res.string.auto
        else -> Res.string.follow_system
    }

    val nightValue by DataStore.nightTheme.collectAsStateWithLifecycle()
    ListPreference(
        value = nightValue,
        onValueChange = {
            DataStore.nightTheme.setBlocking(it)
            applyNightMode(it)
        },
        values = intListN(4),
        title = { Text(stringResource(Res.string.night_mode)) },
        icon = {
            MaskedIcon(
                Res.drawable.wb_sunny,
                color = IconMaskColors.IconLightOrange,
            )
        },
        summary = { Text(stringResource(nightString(nightValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(nightString(it))) },
    )

    fun getLanguageDisplayName(tag: String): String =
        AppLanguage.fromTag(tag)?.displayName ?: runBlocking {
            resolveRepository().getString(Res.string.language_system_default)
        }

    val languageValues = AppLanguage.entries.map { it.tag }
    val languageController = rememberAppLanguageController(defaultTag = "")
    val appLanguage by languageController.flow.collectAsStateWithLifecycle(languageController.value)
    val selectedLanguage = if (appLanguage in languageValues) appLanguage else ""
    ListPreference(
        value = selectedLanguage,
        onValueChange = { languageController.value = it },
        values = languageValues,
        title = { Text(stringResource(Res.string.language)) },
        icon = {
            MaskedIcon(Res.drawable.translate, color = IconMaskColors.IconLavender)
        },
        summary = { Text(getLanguageDisplayName(selectedLanguage)) },
        type = ListPreferenceType.ALERT_DIALOG,
        valueToText = { AnnotatedString(getLanguageDisplayName(it)) },
    )

    fun serviceModeText(mode: String): StringResource = when (mode) {
        Key.MODE_VPN -> Res.string.service_mode_vpn
        Key.MODE_PROXY -> Res.string.service_mode_proxy
        else -> Res.string.service_mode_vpn
    }

    val serviceModeValue by DataStore.serviceMode.collectAsStateWithLifecycle()
    val serviceStatus by BackendState.status.collectAsStateWithLifecycle()
    ListPreference(
        value = serviceModeValue,
        onValueChange = { mode ->
            DataStore.serviceMode.setBlocking(mode)
            if (serviceStatus.state.canStop) {
                resolveRepository().reloadService()
            }
        },
        values = listOf(Key.MODE_VPN, Key.MODE_PROXY),
        title = { Text(stringResource(Res.string.service_mode)) },
        icon = {
            MaskedIcon(
                Res.drawable.developer_mode,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(stringResource(serviceModeText(serviceModeValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(serviceModeText(it))) },
    )

    val mtuValue by DataStore.mtu.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = mtuValue,
        onValueChange = {
            DataStore.mtu.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.mtu)) },
        textToValue = { it.toIntOrNull() ?: 9000 },
        icon = {
            MaskedIcon(
                Res.drawable.public_icon,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(mtuValue.toString()) },
        valueToText = { it.toString() },
    )
    PlatformGeneralOptions(needReload)

    fun speedIntervalText(ms: Int): StringOrRes = when (ms) {
        0 -> StringOrRes.Res(Res.string.disable)
        500 -> StringOrRes.Direct("500ms")
        1000 -> StringOrRes.Direct("1s")
        3000 -> StringOrRes.Direct("3s")
        10000 -> StringOrRes.Direct("10s")
        else -> StringOrRes.Direct("1s")
    }

    val speedIntervalValue by DataStore.speedInterval.collectAsStateWithLifecycle()
    ListPreference(
        value = speedIntervalValue,
        onValueChange = { DataStore.speedInterval.setBlocking(it) },
        values = listOf(0, 500, 1000, 3000, 10000),
        title = { Text(stringResource(Res.string.speed_interval)) },
        icon = {
            MaskedIcon(
                Res.drawable.shutter_speed,
                color = IconMaskColors.IconLightPink,
            )
        },
        summary = { Text(stringOrRes(speedIntervalText(speedIntervalValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = {
            val text = runBlocking { getStringOrRes(speedIntervalText(it)) }
            AnnotatedString(text)
        },
    )

    val profileTrafficStatisticsValue by DataStore.profileTrafficStatistics.collectAsStateWithLifecycle()
    SwitchPreference(
        value = profileTrafficStatisticsValue,
        onValueChange = { DataStore.profileTrafficStatistics.setBlocking(it) },
        title = { Text(stringResource(Res.string.profile_traffic_statistics)) },
        icon = {
            MaskedIcon(
                Res.drawable.traffic,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringResource(Res.string.profile_traffic_statistics_summary)) },
        enabled = speedIntervalValue != 0,
    )

    val showDirectSpeedValue by DataStore.showDirectSpeed.collectAsStateWithLifecycle()
    SwitchPreference(
        value = showDirectSpeedValue,
        onValueChange = { DataStore.showDirectSpeed.setBlocking(it) },
        title = { Text(stringResource(Res.string.show_direct_speed)) },
        icon = {
            MaskedIcon(Res.drawable.speed, color = IconMaskColors.IconLightPink)
        },
        summary = { Text(stringResource(Res.string.show_direct_speed_sum)) },
        enabled = speedIntervalValue != 0,
    )

    val alwaysShowAddressValue by DataStore.alwaysShowAddress.collectAsStateWithLifecycle()
    SwitchPreference(
        value = alwaysShowAddressValue,
        onValueChange = { DataStore.alwaysShowAddress.setBlocking(it) },
        title = { Text(stringResource(Res.string.always_show_address)) },
        icon = {
            MaskedIcon(
                Res.drawable.center_focus_weak,
                color = IconMaskColors.IconCoral,
            )
        },
        summary = { Text(stringResource(Res.string.always_show_address_sum)) },
    )

    val blurredAddressValue by DataStore.blurredAddress.collectAsStateWithLifecycle()
    SwitchPreference(
        value = blurredAddressValue,
        onValueChange = { DataStore.blurredAddress.setBlocking(it) },
        title = { Text(stringResource(Res.string.blurred_address)) },
        icon = {
            MaskedIcon(
                Res.drawable.transgender,
                color = IconMaskColors.IconLavender,
            )
        },
        enabled = alwaysShowAddressValue,
    )

    val securityAdvisoryValue by DataStore.securityAdvisory.collectAsStateWithLifecycle()
    SwitchPreference(
        value = securityAdvisoryValue,
        onValueChange = { DataStore.securityAdvisory.setBlocking(it) },
        title = { Text(stringResource(Res.string.insecure_warn)) },
        icon = {
            MaskedIcon(
                Res.drawable.security,
                color = IconMaskColors.IconCoral,
                shape = IconMaskShapes.risk(),
            )
        },
    )
    PlatformSecurityOptions()
    MeteredNetworkPreference(needReload)

    val logLevelValue by DataStore.logLevel.collectAsStateWithLifecycle()
    ListPreference(
        value = logLevelValue,
        onValueChange = {
            DataStore.logLevel.setBlocking(it)
            needRestart()
        },
        values = intListN(7),
        title = { Text(stringResource(Res.string.log_level)) },
        icon = {
            MaskedIcon(
                Res.drawable.bug_report,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(logLevelString(logLevelValue)) },
        type = ListPreferenceType.ALERT_DIALOG,
        valueToText = { AnnotatedString(logLevelString(it)) },
    )

    val maxLogLineValue by DataStore.logMaxLine.collectAsStateWithLifecycle()
    var previewValue by remember { mutableFloatStateOf(maxLogLineValue.toFloat()) }
    SliderPreference(
        value = maxLogLineValue.toFloat(),
        onValueChange = { DataStore.logMaxLine.setBlocking(it.toInt()) },
        sliderValue = previewValue,
        onSliderValueChange = { previewValue = it },
        title = { Text(stringResource(Res.string.max_log_line)) },
        valueRange = 1024f..1024f * 64f,
        valueSteps = 128,
        icon = {
            MaskedIcon(
                Res.drawable.description,
                color = IconMaskColors.IconWarmGray,
            )
        },
        valueText = { Text(previewValue.toInt().toString()) },
    )
    if (isExpertState) {
        val debugListenValue by DataStore.debugListen.collectAsStateWithLifecycle()
        TextFieldPreference(
            value = debugListenValue,
            onValueChange = {
                DataStore.debugListen.setBlocking(it)
                needReload()
            },
            title = { Text("pprof listen") },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.developer_board,
                    color = IconMaskColors.IconCoral,
                    shape = IconMaskShapes.risk(),
                )
            },
            summary = { Text(contentOrUnset(debugListenValue)) },
            valueToText = { it },
        )
    }
}

@Composable
private fun Circle(
    modifier: Modifier = Modifier,
    color: Color,
    selected: Boolean = false,
) {
    Box(
        modifier = modifier.background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = vectorResource(Res.drawable.check),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
