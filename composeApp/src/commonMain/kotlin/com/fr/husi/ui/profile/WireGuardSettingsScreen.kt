package com.fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.IconMaskShapes
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.MultilineTextField
import com.fr.husi.compose.PasswordPreference
import com.fr.husi.compose.PreferenceCategory
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.UIntegerTextField
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.resources.Res
import com.fr.husi.resources.copyright
import com.fr.husi.resources.directions_boat
import com.fr.husi.resources.domain
import com.fr.husi.resources.emoji_symbols
import com.fr.husi.resources.fingerprint
import com.fr.husi.resources.listen_port
import com.fr.husi.resources.mtu
import com.fr.husi.resources.persistent_keepalive_interval
import com.fr.husi.resources.pre_shared_key
import com.fr.husi.resources.profile_config
import com.fr.husi.resources.profile_name
import com.fr.husi.resources.proxy_cat
import com.fr.husi.resources.public_icon
import com.fr.husi.resources.replay
import com.fr.husi.resources.reserved
import com.fr.husi.resources.router
import com.fr.husi.resources.server_address
import com.fr.husi.resources.server_port
import com.fr.husi.resources.ssh_private_key
import com.fr.husi.resources.stream
import com.fr.husi.resources.vpn_key
import com.fr.husi.resources.wireguard_local_address
import com.fr.husi.resources.wireguard_public_key
import com.fr.husi.ui.NavRoutes
import org.jetbrains.compose.resources.stringResource

@Composable
fun WireGuardSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: WireGuardSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        WireGuardSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        wireGuardSettings(uiState as WireGuardUiState, viewModel)
    }
}

private fun LazyListScope.wireGuardSettings(
    uiState: WireGuardUiState,
    viewModel: WireGuardSettingsViewModel,
) {
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
            textToValue = { it.toIntOrNull() ?: 51820 },
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
        TextFieldPreference(
            value = uiState.localAddress,
            onValueChange = { viewModel.setLocalAddress(it) },
            title = { Text(stringResource(Res.string.wireguard_local_address)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.domain,
                    color = IconMaskColors.IconLavender,
                )
            },
            summary = { Text(contentOrUnset(uiState.localAddress)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
        PasswordPreference(
            value = uiState.privateKey,
            onValueChange = { viewModel.setPrivateKey(it) },
            title = { Text(stringResource(Res.string.ssh_private_key)) },
            icon = {
                MaskedIcon(
                    Res.drawable.vpn_key,
                    color = IconMaskColors.IconLightGreen,
                    shape = IconMaskShapes.credential(),
                )
            },
        )
        TextFieldPreference(
            value = uiState.publicKey,
            onValueChange = { viewModel.setPublicKey(it) },
            title = { Text(stringResource(Res.string.wireguard_public_key)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.copyright, color = IconMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.publicKey)) },
            valueToText = { it },
        )
        PasswordPreference(
            value = uiState.preSharedKey,
            onValueChange = { viewModel.setPreSharedKey(it) },
            title = { Text(stringResource(Res.string.pre_shared_key)) },
            icon = {
                MaskedIcon(
                    resource = Res.drawable.vpn_key,
                    color = IconMaskColors.IconCoral,
                    shape = IconMaskShapes.risk(),
                )
            },
        )
        TextFieldPreference(
            value = uiState.mtu,
            onValueChange = { viewModel.setMtu(it) },
            title = { Text(stringResource(Res.string.mtu)) },
            textToValue = { it.toIntOrNull() ?: 1420 },
            icon = {
                MaskedIcon(
                    Res.drawable.public_icon,
                    color = IconMaskColors.IconLightYellow,
                )
            },
            summary = { Text(contentOrUnset(uiState.mtu.toString())) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        TextFieldPreference(
            value = uiState.reserved,
            onValueChange = { viewModel.setReserved(it) },
            title = { Text(stringResource(Res.string.reserved)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    resource = Res.drawable.fingerprint,
                    color = IconMaskColors.IconCoral,
                    shape = IconMaskShapes.route(),
                )
            },
            summary = { Text(contentOrUnset(uiState.reserved)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
        TextFieldPreference(
            value = uiState.listenPort,
            onValueChange = { viewModel.setListenPort(it) },
            title = { Text(stringResource(Res.string.listen_port)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                MaskedIcon(
                    Res.drawable.stream,
                    color = IconMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(uiState.listenPort)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        TextFieldPreference(
            value = uiState.persistentKeepaliveInterval,
            onValueChange = { viewModel.setPersistentKeepaliveInterval(it) },
            title = { Text(stringResource(Res.string.persistent_keepalive_interval)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                MaskedIcon(
                    Res.drawable.replay,
                    color = IconMaskColors.IconLightOrange,
                )
            },
            summary = {
                Text(contentOrUnset(uiState.persistentKeepaliveInterval))
            },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }
}
