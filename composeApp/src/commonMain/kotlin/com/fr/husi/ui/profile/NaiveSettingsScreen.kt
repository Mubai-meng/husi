package com.fr.husi.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.fr.husi.compose.HostTextField
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.IconMaskShapes
import com.fr.husi.compose.ListPreference
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.MultilineTextField
import com.fr.husi.compose.PasswordPreference
import com.fr.husi.compose.PreferenceCategory
import com.fr.husi.compose.SwitchPreference
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.UIntegerTextField
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.resources.Res
import com.fr.husi.resources.code
import com.fr.husi.resources.copyright
import com.fr.husi.resources.directions_boat
import com.fr.husi.resources.disable_post_quantum
import com.fr.husi.resources.ech
import com.fr.husi.resources.ech_config
import com.fr.husi.resources.ech_query_server_name
import com.fr.husi.resources.emoji_symbols
import com.fr.husi.resources.enable
import com.fr.husi.resources.experimental_settings
import com.fr.husi.resources.extra_headers
import com.fr.husi.resources.grain
import com.fr.husi.resources.https
import com.fr.husi.resources.naive_idle_timeout
import com.fr.husi.resources.naive_insecure_concurrency
import com.fr.husi.resources.naive_insecure_concurrency_summary
import com.fr.husi.resources.naive_tunnel_timeout
import com.fr.husi.resources.nfc
import com.fr.husi.resources.not_set
import com.fr.husi.resources.password_opt
import com.fr.husi.resources.person
import com.fr.husi.resources.profile_config
import com.fr.husi.resources.profile_name
import com.fr.husi.resources.protocol
import com.fr.husi.resources.proxy_cat
import com.fr.husi.resources.router
import com.fr.husi.resources.search
import com.fr.husi.resources.security
import com.fr.husi.resources.server_address
import com.fr.husi.resources.server_port
import com.fr.husi.resources.sni
import com.fr.husi.resources.speed
import com.fr.husi.resources.timelapse
import com.fr.husi.resources.traffic
import com.fr.husi.resources.tuic_congestion_controller
import com.fr.husi.resources.udp_over_tcp
import com.fr.husi.resources.username_opt
import com.fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.stringResource

@Composable
fun NaiveSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: NaiveSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        NaiveSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        naiveSettings(uiState as NaiveUiState, viewModel)
    }
}

private fun LazyListScope.naiveSettings(
    uiState: NaiveUiState,
    viewModel: NaiveSettingsViewModel,
) {
    val protos = listOf("https", "quic")

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
        TextFieldPreference(
            value = uiState.username,
            onValueChange = { viewModel.setUsername(it) },
            title = { Text(stringResource(Res.string.username_opt)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.person, color = IconMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.username)) },
            valueToText = { it },
        )
        PasswordPreference(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
            title = { Text(stringResource(Res.string.password_opt)) },
        )
        ListPreference(
            value = uiState.proto,
            values = protos,
            onValueChange = { viewModel.setProto(it) },
            title = { Text(stringResource(Res.string.protocol)) },
            icon = {
                MaskedIcon(Res.drawable.https, IconMaskColors.IconLightGreen)
            },
            summary = { Text(contentOrUnset(uiState.proto)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
        ListPreference(
            value = uiState.quicCongestionControl,
            values = congestionControlsWithEmpty,
            onValueChange = { viewModel.setQuicCongestionControl(it) },
            title = { Text(stringResource(Res.string.tuic_congestion_controller)) },
            enabled = uiState.proto == "quic",
            icon = {
                MaskedIcon(Res.drawable.traffic, IconMaskColors.IconLavender)
            },
            summary = { Text(contentOrUnset(uiState.quicCongestionControl)) },
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
        TextFieldPreference(
            value = uiState.extraHeaders,
            onValueChange = { viewModel.setExtraHeaders(it) },
            title = { Text(stringResource(Res.string.extra_headers)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.code, IconMaskColors.IconLightYellow)
            },
            summary = { Text(contentOrUnset(uiState.extraHeaders)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                HostTextField(value, onValueChange, onOk)
            },
        )
        TextFieldPreference(
            value = uiState.insecureConcurrency,
            onValueChange = { viewModel.setInsecureConcurrency(it) },
            title = { Text(stringResource(Res.string.naive_insecure_concurrency)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                MaskedIcon(Res.drawable.speed, IconMaskColors.IconCoral, IconMaskShapes.risk())
            },
            summary = {
                val text = if (uiState.insecureConcurrency == 0) {
                    stringResource(Res.string.not_set)
                } else {
                    uiState.insecureConcurrency.toString()
                }
                Text(text)
            },
            textField = { value, onValueChange, onOk ->
                Column {
                    Text(
                        text = stringResource(Res.string.naive_insecure_concurrency_summary),
                        modifier = Modifier.padding(16.dp),
                    )

                    UIntegerTextField(value, onValueChange, onOk)
                }
            },
        )
        TextFieldPreference(
            value = uiState.tunnelTimeout,
            onValueChange = { viewModel.setTunnelTimeout(it) },
            title = { Text(stringResource(Res.string.naive_tunnel_timeout)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                MaskedIcon(Res.drawable.timelapse, IconMaskColors.IconWarmGray)
            },
            summary = {
                val text = if (uiState.tunnelTimeout == 0) {
                    stringResource(Res.string.not_set)
                } else {
                    uiState.tunnelTimeout.toString()
                }
                Text(text)
            },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        TextFieldPreference(
            value = uiState.idleTimeout,
            onValueChange = { viewModel.setIdleTimeout(it) },
            title = { Text(stringResource(Res.string.naive_idle_timeout)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                MaskedIcon(Res.drawable.timelapse, IconMaskColors.IconWarmGray)
            },
            summary = {
                val text = if (uiState.idleTimeout == 0) {
                    stringResource(Res.string.not_set)
                } else {
                    uiState.idleTimeout.toString()
                }
                Text(text)
            },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }

    item("category_experimental") {
        PreferenceCategory(
            text = { Text(stringResource(Res.string.experimental_settings)) },
        )
    }
    preferenceGroup(key = "udp_over_tcp") {
        SwitchPreference(
            value = uiState.udpOverTcp,
            onValueChange = { viewModel.setUdpOverTcp(it) },
            title = { Text(stringResource(Res.string.udp_over_tcp)) },
            icon = { Spacer(Modifier.size(24.dp)) },
        )
        SwitchPreference(
            value = uiState.noPostQuantum,
            onValueChange = { viewModel.setNoPostQuantum(it) },
            title = { Text(stringResource(Res.string.disable_post_quantum)) },
            icon = {
                MaskedIcon(Res.drawable.grain, IconMaskColors.IconWarmGray)
            },
        )
    }

    item("category_ech") {
        PreferenceCategory(text = { Text(stringResource(Res.string.ech)) })
    }
    preferenceGroup(key = "ech") {
        SwitchPreference(
            value = uiState.enableEch,
            onValueChange = { viewModel.setEnableEch(it) },
            title = { Text(stringResource(Res.string.enable)) },
            icon = {
                MaskedIcon(Res.drawable.security, IconMaskColors.IconCoral, IconMaskShapes.risk())
            },
        )
        TextFieldPreference(
            value = uiState.echConfig,
            onValueChange = { viewModel.setEchConfig(it) },
            title = { Text(stringResource(Res.string.ech_config)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.nfc, IconMaskColors.IconCyan, IconMaskShapes.credential())
            },
            enabled = uiState.enableEch,
            summary = { Text(contentOrUnset(uiState.echConfig)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
        TextFieldPreference(
            value = uiState.echQueryServerName,
            onValueChange = { viewModel.setEchQueryServerName(it) },
            title = { Text(stringResource(Res.string.ech_query_server_name)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.search, IconMaskColors.IconLightPink)
            },
            enabled = uiState.enableEch,
            summary = { Text(contentOrUnset(uiState.echQueryServerName)) },
            valueToText = { it },
        )
    }
}
