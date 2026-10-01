package com.fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.util.fastCoerceAtLeast
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.IconMaskShapes
import com.fr.husi.compose.ListPreference
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.MultilineTextField
import com.fr.husi.compose.PasswordPreference
import com.fr.husi.compose.PreferenceCategory
import com.fr.husi.compose.SliderPreference
import com.fr.husi.compose.SwitchPreference
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.UIntegerTextField
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.fmt.shadowquic.ShadowQUICBean
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.resources.Res
import com.fr.husi.resources.action_shadowquic
import com.fr.husi.resources.action_sunnyquic
import com.fr.husi.resources.alpn
import com.fr.husi.resources.blackhole_detection
import com.fr.husi.resources.brightness_4
import com.fr.husi.resources.bug_report
import com.fr.husi.resources.certificates
import com.fr.husi.resources.compare_arrows
import com.fr.husi.resources.copyright
import com.fr.husi.resources.developer_board
import com.fr.husi.resources.directions_boat
import com.fr.husi.resources.emoji_symbols
import com.fr.husi.resources.extra_paths
import com.fr.husi.resources.extra_paths_max
import com.fr.husi.resources.flight_takeoff
import com.fr.husi.resources.grid_on
import com.fr.husi.resources.gso
import com.fr.husi.resources.initial_mtu
import com.fr.husi.resources.minimum_mtu
import com.fr.husi.resources.mtu_discovery
import com.fr.husi.resources.multiple_stop
import com.fr.husi.resources.nat
import com.fr.husi.resources.persistent_keepalive_interval
import com.fr.husi.resources.profile_config
import com.fr.husi.resources.profile_name
import com.fr.husi.resources.protocol
import com.fr.husi.resources.proxy_cat
import com.fr.husi.resources.public_icon
import com.fr.husi.resources.router
import com.fr.husi.resources.search
import com.fr.husi.resources.segment
import com.fr.husi.resources.server_address
import com.fr.husi.resources.server_port
import com.fr.husi.resources.sni
import com.fr.husi.resources.texture
import com.fr.husi.resources.timer
import com.fr.husi.resources.toc
import com.fr.husi.resources.tuic_congestion_controller
import com.fr.husi.resources.tuic_reduce_rtt
import com.fr.husi.resources.udp_over_stream
import com.fr.husi.resources.username
import com.fr.husi.resources.wb_sunny
import com.fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
fun ShadowQUICSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: ShadowQUICSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        ShadowQUICSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        shadowQuicSettings(uiState as ShadowQUICUiState, viewModel)
    }
}

private fun LazyListScope.shadowQuicSettings(
    uiState: ShadowQUICUiState,
    viewModel: ShadowQUICSettingsViewModel,
) {
    val congestionControls = listOf(
        "bbr",
        "cubic",
        "new-reno",
        ShadowQUICBean.CONGESTION_CONTROL_BRUTAL,
    )

    preferenceGroup(key = "name") {
        TextFieldPreference(
            value = uiState.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.profile_name)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.emoji_symbols,
                    color = IconMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(uiState.name)) },
            valueToText = { it },
        )
    }

    item("category_proxy") {
        PreferenceCategory(text = { Text(stringResource(Res.string.proxy_cat)) })
    }
    preferenceGroup(key = "address") {
        TextFieldPreference(
            value = uiState.address,
            onValueChange = { viewModel.setAddress(it) },
            title = { Text(stringResource(Res.string.server_address)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.router, color = IconMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.address)) },
            valueToText = { it },
        )
        TextFieldPreference(
            value = uiState.port,
            onValueChange = { viewModel.setPort(it) },
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it.toIntOrNull() ?: 443 },
            icon = {
                MaskedIcon(
                    Res.drawable.directions_boat,
                    color = IconMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(uiState.port)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        fun subProtocolText(subProtocol: Int) = when (subProtocol) {
            ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC -> Res.string.action_shadowquic
            ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC -> Res.string.action_sunnyquic
            else -> error("impossible")
        }
        ListPreference(
            value = uiState.subProtocol,
            values = listOf(
                ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC,
                ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC,
            ),
            onValueChange = { viewModel.setSubProtocol(it) },
            title = { Text(stringResource(Res.string.protocol)) },
            icon = {
                MaskedIcon(
                    if (uiState.subProtocol == ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC) {
                        Res.drawable.brightness_4
                    } else {
                        Res.drawable.wb_sunny
                    },
                )
            },
            summary = { Text(stringResource(subProtocolText(uiState.subProtocol))) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(stringResource(subProtocolText(it))) },
        )
        TextFieldPreference(
            value = uiState.username,
            onValueChange = { viewModel.setUsername(it) },
            title = { Text(stringResource(Res.string.username)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.texture, color = IconMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.username)) },
            valueToText = { it },
        )
        PasswordPreference(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
        )
        TextFieldPreference(
            value = uiState.alpn,
            onValueChange = { viewModel.setAlpn(it) },
            title = { Text(stringResource(Res.string.alpn)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.toc, color = IconMaskColors.IconLightBlue)
            },
            summary = { Text(contentOrUnset(uiState.alpn)) },
            valueToText = { it },
        )
        ListPreference(
            value = uiState.congestionControl,
            values = congestionControls,
            onValueChange = { viewModel.setCongestionControl(it) },
            title = { Text(stringResource(Res.string.tuic_congestion_controller)) },
            icon = {
                MaskedIcon(
                    resource = Res.drawable.compare_arrows,
                    color = IconMaskColors.IconLightGreen,
                )
            },
            summary = { Text(uiState.congestionControl) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
        TextFieldPreference(
            value = uiState.sni,
            onValueChange = { viewModel.setSni(it) },
            title = { Text(stringResource(Res.string.sni)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.copyright, color = IconMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.sni)) },
            valueToText = { it },
        )
        SwitchPreference(
            value = uiState.zeroRTT,
            onValueChange = { viewModel.setZeroRTT(it) },
            title = { Text(stringResource(Res.string.tuic_reduce_rtt)) },
            icon = {
                MaskedIcon(
                    resource = Res.drawable.flight_takeoff,
                    color = IconMaskColors.IconCoral,
                    shape = IconMaskShapes.risk(),
                )
            },
        )
        TextFieldPreference(
            value = uiState.initialMtu,
            onValueChange = { viewModel.setInitialMtu(it) },
            title = { Text(stringResource(Res.string.initial_mtu)) },
            textToValue = { it.toIntOrNull() ?: 1300 },
            icon = {
                MaskedIcon(
                    resource = Res.drawable.public_icon,
                    color = IconMaskColors.IconWarmGray,
                )
            },
            summary = { Text(contentOrUnset(uiState.initialMtu)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        TextFieldPreference(
            value = uiState.minMtu,
            onValueChange = { viewModel.setMinMtu(it) },
            title = { Text(stringResource(Res.string.minimum_mtu)) },
            textToValue = { it.toIntOrNull() ?: 1290 },
            icon = {
                MaskedIcon(
                    resource = Res.drawable.developer_board,
                    color = IconMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(uiState.minMtu)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        SwitchPreference(
            value = uiState.udpOverStream,
            onValueChange = { viewModel.setUdpOverStream(it) },
            title = { Text(stringResource(Res.string.udp_over_stream)) },
            icon = {
                MaskedIcon(Res.drawable.nat, IconMaskColors.IconLightPink, IconMaskShapes.route())
            },
        )
        SwitchPreference(
            value = uiState.gso,
            onValueChange = { viewModel.setGso(it) },
            title = { Text(stringResource(Res.string.gso)) },
            icon = {
                MaskedIcon(Res.drawable.segment, IconMaskColors.IconWarmGray)
            },
        )
        TextFieldPreference(
            value = uiState.keepAliveInterval,
            onValueChange = { viewModel.setKeepAliveInterval(it) },
            title = { Text(stringResource(Res.string.persistent_keepalive_interval)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                MaskedIcon(Res.drawable.timer, IconMaskColors.IconLightPink)
            },
            summary = { Text(contentOrUnset(uiState.keepAliveInterval)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        SwitchPreference(
            value = uiState.mtuDiscovery,
            onValueChange = { viewModel.setMtuDiscovery(it) },
            title = { Text(stringResource(Res.string.mtu_discovery)) },
            icon = {
                MaskedIcon(Res.drawable.search, IconMaskColors.IconLightBlue)
            },
        )
        if (uiState.subProtocol == ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC) {
            SwitchPreference(
                value = uiState.blackholeDetection,
                onValueChange = { viewModel.setBlackholeDetection(it) },
                title = { Text(stringResource(Res.string.blackhole_detection)) },
                enabled = uiState.mtuDiscovery,
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.bug_report,
                        color = IconMaskColors.IconLavender,
                        shape = IconMaskShapes.route(),
                    )
                },
            )
        }
    }

    if (uiState.subProtocol == ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC) {
        preferenceGroup(key = "extra_paths") {
            TextFieldPreference(
                value = uiState.extraPaths,
                onValueChange = { viewModel.setExtraPaths(it) },
                title = { Text(stringResource(Res.string.extra_paths)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.grid_on,
                        color = IconMaskColors.IconLightYellow,
                        shape = IconMaskShapes.risk(),
                    )
                },
                summary = { Text(contentOrUnset(uiState.extraPaths)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            TextFieldPreference(
                value = uiState.certificates,
                onValueChange = { viewModel.setCertificates(it) },
                title = { Text(stringResource(Res.string.certificates)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.copyright,
                        color = IconMaskColors.IconCyan,
                        shape = IconMaskShapes.credential(),
                    )
                },
                summary = { Text(contentOrUnset(uiState.certificates)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            val maxPathsFloat = uiState.maxPaths.toFloat()
            val currentPathCount = uiState.extraPaths.lines().count { it.isNotBlank() }
            var previewValue by remember { mutableFloatStateOf(maxPathsFloat) }
            SliderPreference(
                value = maxPathsFloat,
                onValueChange = { viewModel.setMaxPaths(it.roundToInt()) },
                sliderValue = previewValue,
                onSliderValueChange = { previewValue = it },
                title = { Text(stringResource(Res.string.extra_paths_max)) },
                valueRange = 0f..currentPathCount.toFloat(),
                valueSteps = currentPathCount.fastCoerceAtLeast(1) - 1,
                enabled = currentPathCount > 0,
                icon = {
                    MaskedIcon(
                        Res.drawable.multiple_stop,
                        color = IconMaskColors.IconCyan,
                    )
                },
                summary = { Text(contentOrUnset(uiState.maxPaths)) },
                valueText = { Text(previewValue.roundToInt().toString()) },
            )
        }
    }
}
