package com.fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.IconMaskShapes
import com.fr.husi.compose.ListPreference
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.MultilineTextField
import com.fr.husi.compose.PasswordPreference
import com.fr.husi.compose.PreferenceCategory
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.UIntegerTextField
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.fmt.mieru.MieruBean
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.ktx.intListN
import com.fr.husi.resources.Res
import com.fr.husi.resources.compare_arrows
import com.fr.husi.resources.directions_boat
import com.fr.husi.resources.emoji_symbols
import com.fr.husi.resources.high
import com.fr.husi.resources.low
import com.fr.husi.resources.middle
import com.fr.husi.resources.mtu
import com.fr.husi.resources.mux_preference
import com.fr.husi.resources.not_set
import com.fr.husi.resources.off
import com.fr.husi.resources.pattern
import com.fr.husi.resources.person
import com.fr.husi.resources.profile_config
import com.fr.husi.resources.profile_name
import com.fr.husi.resources.protocol
import com.fr.husi.resources.proxy_cat
import com.fr.husi.resources.public_icon
import com.fr.husi.resources.router
import com.fr.husi.resources.server_address
import com.fr.husi.resources.server_port
import com.fr.husi.resources.traffic_pattern
import com.fr.husi.resources.username
import com.fr.husi.resources.vpn_key
import com.fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun MieruSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: MieruSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        MieruSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        mieruSettings(uiState as MieruUiState, viewModel)
    }
}

private fun LazyListScope.mieruSettings(
    uiState: MieruUiState,
    viewModel: MieruSettingsViewModel,
) {
    val protocols = listOf("TCP", "UDP")

    preferenceGroup {
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
    preferenceGroup {
        TextFieldPreference(
            value = uiState.address,
            onValueChange = { viewModel.setAddress(it) },
            title = { Text(stringResource(Res.string.server_address)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.router,
                    color = IconMaskColors.IconLightBlue,
                )
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
                    color = IconMaskColors.IconLightOrange,
                )
            },
            summary = { Text(contentOrUnset(uiState.port)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        ListPreference(
            value = uiState.protocol,
            values = protocols,
            onValueChange = { viewModel.setProtocol(it) },
            title = { Text(stringResource(Res.string.protocol)) },
            icon = {
                MaskedIcon(
                    Res.drawable.compare_arrows,
                    color = IconMaskColors.IconLavender,
                )
            },
            summary = { Text(contentOrUnset(uiState.protocol.uppercase())) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
        TextFieldPreference(
            value = uiState.username,
            onValueChange = { viewModel.setUsername(it) },
            title = { Text(stringResource(Res.string.username)) },
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
            icon = {
                MaskedIcon(
                    Res.drawable.vpn_key,
                    color = IconMaskColors.IconWarmGray,
                )
            },
        )
        if (uiState.protocol == MieruBean.PROTOCOL_UDP) {
            TextFieldPreference(
                value = uiState.mtu,
                onValueChange = { viewModel.setMtu(it) },
                title = { Text(stringResource(Res.string.mtu)) },
                textToValue = { it.toIntOrNull() ?: 1400 },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.public_icon,
                        color = IconMaskColors.IconLightGreen,
                        shape = IconMaskShapes.route(),
                    )
                },
                summary = { Text(contentOrUnset(uiState.mtu)) },
                valueToText = { it.toString() },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                },
            )
        }
        ListPreference(
            value = uiState.muxNumber,
            values = intListN(4),
            onValueChange = { viewModel.setMuxNumber(it) },
            title = { Text(stringResource(Res.string.mux_preference)) },
            icon = {
                MaskedIcon(
                    Res.drawable.compare_arrows,
                    color = IconMaskColors.IconLightYellow,
                    shape = IconMaskShapes.route(),
                )
            },
            summary = {
                val muxSummary: StringResource = when (uiState.muxNumber) {
                    0 -> Res.string.off
                    1 -> Res.string.low
                    2 -> Res.string.middle
                    3 -> Res.string.high
                    else -> Res.string.not_set
                }
                Text(stringResource(muxSummary))
            },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = {
                val muxSummary: StringResource = when (it) {
                    0 -> Res.string.off
                    1 -> Res.string.low
                    2 -> Res.string.middle
                    3 -> Res.string.high
                    else -> Res.string.not_set
                }
                AnnotatedString(stringResource(muxSummary))
            },
        )
        TextFieldPreference(
            value = uiState.trafficPattern,
            onValueChange = viewModel::setTrafficPattern,
            title = { Text(stringResource(Res.string.traffic_pattern)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.pattern, color = IconMaskColors.IconCoral)
            },
            summary = { Text(contentOrUnset(uiState.trafficPattern)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }
}
