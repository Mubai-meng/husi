package com.fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.MultilineTextField
import com.fr.husi.compose.PasswordPreference
import com.fr.husi.compose.SwitchPreference
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.fmt.HttpVersion
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.resources.Res
import com.fr.husi.resources.block
import com.fr.husi.resources.code
import com.fr.husi.resources.disable_version_fallback
import com.fr.husi.resources.http_headers
import com.fr.husi.resources.http_host
import com.fr.husi.resources.http_path
import com.fr.husi.resources.language
import com.fr.husi.resources.password
import com.fr.husi.resources.password_opt
import com.fr.husi.resources.person
import com.fr.husi.resources.profile_config
import com.fr.husi.resources.route
import com.fr.husi.resources.username_opt
import com.fr.husi.ui.NavRoutes
import org.jetbrains.compose.resources.stringResource

@Composable
fun HttpSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: HttpSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        HttpSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, scrollTo ->
        httpSettings(uiState as HttpUiState, viewModel, scrollTo)
    }
}

private fun LazyListScope.httpSettings(
    uiState: HttpUiState,
    viewModel: HttpSettingsViewModel,
    scrollTo: (String) -> Unit,
) {
    headSettings(uiState, viewModel)
    preferenceGroup {
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
            icon = {
                MaskedIcon(
                    Res.drawable.password,
                    color = IconMaskColors.IconWarmGray,
                )
            },
        )
    }
    val isHttp1 = uiState.httpVersion == HttpVersion.HTTP_1
    preferenceGroup {
        HttpVersionPreference(
            value = uiState.httpVersion,
            isTLS = uiState.isTLS,
            onValueChange = { viewModel.setHttpVersion(it) },
        )
        if (isHttp1) {
            TextFieldPreference(
                value = uiState.host,
                onValueChange = { viewModel.setHost(it) },
                title = { Text(stringResource(Res.string.http_host)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.language,
                        color = IconMaskColors.IconLightBlue,
                    )
                },
                summary = { Text(contentOrUnset(uiState.host)) },
                valueToText = { it },
            )
            TextFieldPreference(
                value = uiState.path,
                onValueChange = { viewModel.setPath(it) },
                title = { Text(stringResource(Res.string.http_path)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.route,
                        color = IconMaskColors.IconLightOrange,
                    )
                },
                summary = { Text(contentOrUnset(uiState.path)) },
                valueToText = { it },
            )
        } else {
            SwitchPreference(
                value = uiState.disableVersionFallback,
                onValueChange = { viewModel.setDisableVersionFallback(it) },
                enabled = uiState.isTLS,
                title = { Text(stringResource(Res.string.disable_version_fallback)) },
                icon = {
                    MaskedIcon(Res.drawable.block, IconMaskColors.IconCoral)
                },
            )
        }
        TextFieldPreference(
            value = uiState.headers,
            onValueChange = { viewModel.setHeaders(it) },
            title = { Text(stringResource(Res.string.http_headers)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.code, color = IconMaskColors.IconLavender)
            },
            summary = { Text(contentOrUnset(uiState.headers)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
        )
    }

    tlsSettings(uiState, viewModel, scrollTo)
}
