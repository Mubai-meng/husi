package com.fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import com.fr.husi.compose.ListPreference
import com.fr.husi.compose.PasswordPreference
import com.fr.husi.compose.PreferenceCategory
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.SwitchPreference
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.UIntegerTextField
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.fmt.snell.SnellBean
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.resources.Res
import com.fr.husi.resources.directions_boat
import com.fr.husi.resources.emoji_symbols
import com.fr.husi.resources.enhanced_encryption
import com.fr.husi.resources.grid_3x3
import com.fr.husi.resources.http_host
import com.fr.husi.resources.obfs_mode
import com.fr.husi.resources.password
import com.fr.husi.resources.pre_shared_key
import com.fr.husi.resources.profile_config
import com.fr.husi.resources.profile_name
import com.fr.husi.resources.protocol_version
import com.fr.husi.resources.proxy_cat
import com.fr.husi.resources.router
import com.fr.husi.resources.security
import com.fr.husi.resources.server_address
import com.fr.husi.resources.server_port
import com.fr.husi.resources.settings
import com.fr.husi.resources.snell_mode
import com.fr.husi.resources.snell_reuse
import com.fr.husi.resources.snell_user_key
import com.fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.stringResource

@Composable
fun SnellSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: SnellSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        SnellSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        snellSettings(uiState as SnellUiState, viewModel)
    }
}

private fun LazyListScope.snellSettings(
    uiState: SnellUiState,
    viewModel: SnellSettingsViewModel,
) {
    val versions = listOf(SnellBean.VERSION_4, SnellBean.VERSION_6)
    fun versionText(version: Int) = when (version) {
        SnellBean.VERSION_4 -> "v4 (5)"
        else -> "v$version"
    }

    val obfsModes = listOf("", "http", "tls")
    val snellModes = listOf("default", "unshaped", "unsafe-raw")
    fun snellModeText(mode: String) = mode.ifBlank { "default" }

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
            value = uiState.version,
            values = versions,
            onValueChange = { viewModel.setVersion(it) },
            title = { Text(stringResource(Res.string.protocol_version)) },
            icon = {
                MaskedIcon(
                    Res.drawable.security,
                    color = IconMaskColors.IconLavender,
                )
            },
            summary = { Text(versionText(uiState.version)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(versionText(it)) },
        )
        PasswordPreference(
            value = uiState.psk,
            onValueChange = { viewModel.setPsk(it) },
            title = { Text(stringResource(Res.string.pre_shared_key)) },
            icon = {
                MaskedIcon(
                    Res.drawable.password,
                    color = IconMaskColors.IconWarmGray,
                )
            },
        )
        PasswordPreference(
            value = uiState.userKey,
            onValueChange = { viewModel.setUserKey(it) },
            title = { Text(stringResource(Res.string.snell_user_key)) },
            icon = {
                MaskedIcon(
                    Res.drawable.enhanced_encryption,
                    color = IconMaskColors.IconCoral,
                )
            },
        )
    }

    item("category_options") {
        PreferenceCategory(text = { Text(stringResource(Res.string.settings)) })
    }
    preferenceGroup {
        SwitchPreference(
            value = uiState.reuse,
            onValueChange = { viewModel.setReuse(it) },
            title = { Text(stringResource(Res.string.snell_reuse)) },
            icon = {
                MaskedIcon(
                    Res.drawable.grid_3x3,
                    color = IconMaskColors.IconLightBlue,
                )
            },
        )
    }

    when (uiState.version) {
        SnellBean.VERSION_4 -> {
            preferenceGroup {
                ListPreference(
                    value = uiState.obfsMode,
                    values = obfsModes,
                    onValueChange = { viewModel.setObfsMode(it) },
                    title = { Text(stringResource(Res.string.obfs_mode)) },
                    icon = {
                        MaskedIcon(
                            Res.drawable.settings,
                            color = IconMaskColors.IconLightGreen,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.obfsMode)) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(contentOrUnset(it)) },
                )
                TextFieldPreference(
                    value = uiState.obfsHost,
                    onValueChange = { viewModel.setObfsHost(it) },
                    title = { Text(stringResource(Res.string.http_host)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.router,
                            color = IconMaskColors.IconLightOrange,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.obfsHost)) },
                    valueToText = { it },
                )
            }
        }

        SnellBean.VERSION_6 -> {
            preferenceGroup {
                ListPreference(
                    value = snellModeText(uiState.mode),
                    values = snellModes,
                    onValueChange = { viewModel.setMode(it) },
                    title = { Text(stringResource(Res.string.snell_mode)) },
                    icon = {
                        MaskedIcon(
                            Res.drawable.settings,
                            color = IconMaskColors.IconLightGreen,
                        )
                    },
                    summary = { Text(snellModeText(uiState.mode)) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(it) },
                )
            }
        }
    }

}
