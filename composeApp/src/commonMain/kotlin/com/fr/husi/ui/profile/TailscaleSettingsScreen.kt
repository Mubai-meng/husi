package com.fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.fr.husi.Key
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.IconMaskShapes
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.MultilineTextField
import com.fr.husi.compose.PasswordPreference
import com.fr.husi.compose.PreferenceCategory
import com.fr.husi.compose.SwitchPreference
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.UIntegerTextField
import com.fr.husi.compose.collectAdvancedFeatureAsStateWithLifecycle
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.database.DataStore
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.platform.PlatformInfo
import com.fr.husi.resources.Res
import com.fr.husi.resources.add_road
import com.fr.husi.resources.code
import com.fr.husi.resources.domain
import com.fr.husi.resources.download
import com.fr.husi.resources.emoji_symbols
import com.fr.husi.resources.flight_takeoff
import com.fr.husi.resources.folder_open
import com.fr.husi.resources.home
import com.fr.husi.resources.label
import com.fr.husi.resources.link
import com.fr.husi.resources.listen_port
import com.fr.husi.resources.nat
import com.fr.husi.resources.outbond
import com.fr.husi.resources.profile_config
import com.fr.husi.resources.profile_name
import com.fr.husi.resources.proxy_cat
import com.fr.husi.resources.route
import com.fr.husi.resources.stream
import com.fr.husi.resources.tailscale_accept_routes
import com.fr.husi.resources.tailscale_advertise_exit_node
import com.fr.husi.resources.tailscale_advertise_fake_dns_routes
import com.fr.husi.resources.tailscale_advertise_fake_dns_routes_summary
import com.fr.husi.resources.tailscale_advertise_routes
import com.fr.husi.resources.tailscale_advertise_tags
import com.fr.husi.resources.tailscale_auth_key
import com.fr.husi.resources.tailscale_control_url
import com.fr.husi.resources.tailscale_ephemeral
import com.fr.husi.resources.tailscale_exit_node
import com.fr.husi.resources.tailscale_exit_node_allow_lan_access
import com.fr.husi.resources.tailscale_hostname
import com.fr.husi.resources.tailscale_ssh_server
import com.fr.husi.resources.tailscale_state_directory
import com.fr.husi.resources.taildrop_directory
import com.fr.husi.resources.timelapse
import com.fr.husi.resources.vpn_key
import com.fr.husi.ui.NavRoutes
import org.jetbrains.compose.resources.stringResource

@Composable
fun TailscaleSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: TailscaleSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        TailscaleSettingsViewModel()
    }
    val sshServerFeature by collectAdvancedFeatureAsStateWithLifecycle(Key.ADVANCED_TAILSCALE_SSH_SERVER)

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        tailscaleSettings(uiState as TailscaleUiState, viewModel, sshServerFeature != null)
    }
}

private fun LazyListScope.tailscaleSettings(
    uiState: TailscaleUiState,
    viewModel: TailscaleSettingsViewModel,
    isSSHServerEnabled: Boolean,
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
            value = uiState.controlURL,
            onValueChange = { viewModel.setControlURL(it) },
            title = { Text(stringResource(Res.string.tailscale_control_url)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.link,
                    color = IconMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(uiState.controlURL)) },
            valueToText = { it },
        )
        PasswordPreference(
            value = uiState.authKey,
            onValueChange = { viewModel.setAuthKey(it) },
            title = { Text(stringResource(Res.string.tailscale_auth_key)) },
            icon = {
                MaskedIcon(
                    Res.drawable.vpn_key,
                    color = IconMaskColors.IconLightGreen,
                    shape = IconMaskShapes.credential(),
                )
            },
        )
        TextFieldPreference(
            value = uiState.hostname,
            onValueChange = { viewModel.setHostname(it) },
            title = { Text(stringResource(Res.string.tailscale_hostname)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.domain,
                    color = IconMaskColors.IconLavender,
                )
            },
            summary = { Text(contentOrUnset(uiState.hostname)) },
            valueToText = { it },
        )
        TextFieldPreference(
            value = uiState.stateDirectory,
            onValueChange = { viewModel.setStateDirectory(it) },
            title = { Text(stringResource(Res.string.tailscale_state_directory)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.folder_open,
                    color = IconMaskColors.IconLightYellow,
                )
            },
            summary = { Text(contentOrUnset(uiState.stateDirectory)) },
            valueToText = { it },
        )
        SwitchPreference(
            value = uiState.ephemeral,
            onValueChange = { viewModel.setEphemeral(it) },
            title = { Text(stringResource(Res.string.tailscale_ephemeral)) },
            icon = {
                MaskedIcon(
                    Res.drawable.timelapse,
                    color = IconMaskColors.IconLightOrange,
                )
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
    }

    preferenceGroup {
        SwitchPreference(
            value = uiState.acceptRoutes,
            onValueChange = { viewModel.setAcceptRoutes(it) },
            title = { Text(stringResource(Res.string.tailscale_accept_routes)) },
            icon = {
                MaskedIcon(
                    Res.drawable.route,
                    color = IconMaskColors.IconCyan,
                    shape = IconMaskShapes.route(),
                )
            },
        )
        TextFieldPreference(
            value = uiState.exitNode,
            onValueChange = { viewModel.setExitNode(it) },
            title = { Text(stringResource(Res.string.tailscale_exit_node)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.outbond,
                    color = IconMaskColors.IconLightGreen,
                )
            },
            summary = { Text(contentOrUnset(uiState.exitNode)) },
            valueToText = { it },
        )
        SwitchPreference(
            value = uiState.exitNodeAllowLANAccess,
            onValueChange = { viewModel.setExitNodeAllowLANAccess(it) },
            title = { Text(stringResource(Res.string.tailscale_exit_node_allow_lan_access)) },
            icon = {
                MaskedIcon(
                    Res.drawable.home,
                    color = IconMaskColors.IconLightYellow,
                )
            },
        )
        TextFieldPreference(
            value = uiState.advertiseRoutes,
            onValueChange = { viewModel.setAdvertiseRoutes(it) },
            title = { Text(stringResource(Res.string.tailscale_advertise_routes)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.add_road,
                    color = IconMaskColors.IconLavender,
                )
            },
            summary = { Text(contentOrUnset(uiState.advertiseRoutes)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
        SwitchPreference(
            value = uiState.advertiseFakeDNSRoutes,
            onValueChange = { viewModel.setAdvertiseFakeDNSRoutes(it) },
            title = { Text(stringResource(Res.string.tailscale_advertise_fake_dns_routes)) },
            icon = {
                MaskedIcon(
                    Res.drawable.nat,
                    color = IconMaskColors.IconCyan,
                    shape = IconMaskShapes.route(),
                )
            },
            summary = { Text(stringResource(Res.string.tailscale_advertise_fake_dns_routes_summary)) },
        )
        SwitchPreference(
            value = uiState.advertiseExitNode,
            onValueChange = { viewModel.setAdvertiseExitNode(it) },
            title = { Text(stringResource(Res.string.tailscale_advertise_exit_node)) },
            icon = {
                MaskedIcon(
                    Res.drawable.flight_takeoff,
                    color = IconMaskColors.IconLightOrange,
                )
            },
        )
        TextFieldPreference(
            value = uiState.advertiseTags,
            onValueChange = { viewModel.setAdvertiseTags(it) },
            title = { Text(stringResource(Res.string.tailscale_advertise_tags)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.label,
                    color = IconMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(uiState.advertiseTags)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }

    // Android has no SSH server support, and serves received files from the default directory
    // through its documents provider.
    if (PlatformInfo.isAndroid) return

    preferenceGroup {
        if (isSSHServerEnabled) {
            SwitchPreference(
                value = uiState.sshServer,
                onValueChange = { viewModel.setSSHServer(it) },
                title = { Text(stringResource(Res.string.tailscale_ssh_server)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.code,
                        color = IconMaskColors.IconCoral,
                        shape = IconMaskShapes.risk(),
                    )
                },
            )
        }
        TextFieldPreference(
            value = uiState.taildropDirectory,
            onValueChange = { viewModel.setTaildropDirectory(it) },
            title = { Text(stringResource(Res.string.taildrop_directory)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.download,
                    color = IconMaskColors.IconLightGreen,
                )
            },
            summary = { Text(contentOrUnset(uiState.taildropDirectory)) },
            valueToText = { it },
        )
    }
}
