package com.fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import com.fr.husi.compose.DurationTextField
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
import com.fr.husi.fmt.masque.MASQUEBean
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.platform.PlatformInfo
import com.fr.husi.resources.Res
import com.fr.husi.resources.allow_insecure
import com.fr.husi.resources.alpn
import com.fr.husi.resources.block
import com.fr.husi.resources.cert_public_key_sha256
import com.fr.husi.resources.certificates
import com.fr.husi.resources.client_certificate
import com.fr.husi.resources.client_key
import com.fr.husi.resources.code
import com.fr.husi.resources.computer_cancel
import com.fr.husi.resources.copyright
import com.fr.husi.resources.directions_boat
import com.fr.husi.resources.disable_version_fallback
import com.fr.husi.resources.domino_mask
import com.fr.husi.resources.ech
import com.fr.husi.resources.ech_config
import com.fr.husi.resources.ech_query_server_name
import com.fr.husi.resources.emoji_symbols
import com.fr.husi.resources.fingerprint
import com.fr.husi.resources.http_headers
import com.fr.husi.resources.http_path
import com.fr.husi.resources.lock
import com.fr.husi.resources.lock_open
import com.fr.husi.resources.mtu
import com.fr.husi.resources.mutual_tls
import com.fr.husi.resources.nfc
import com.fr.husi.resources.numbers
import com.fr.husi.resources.password
import com.fr.husi.resources.password_opt
import com.fr.husi.resources.person
import com.fr.husi.resources.profile_config
import com.fr.husi.resources.profile_name
import com.fr.husi.resources.proxy_cat
import com.fr.husi.resources.route
import com.fr.husi.resources.router
import com.fr.husi.resources.search
import com.fr.husi.resources.security
import com.fr.husi.resources.security_settings
import com.fr.husi.resources.server_address
import com.fr.husi.resources.server_port
import com.fr.husi.resources.sni
import com.fr.husi.resources.texture
import com.fr.husi.resources.timelapse
import com.fr.husi.resources.tls
import com.fr.husi.resources.tls_fragment
import com.fr.husi.resources.tls_fragment_fallback_delay
import com.fr.husi.resources.tls_record_fragment
import com.fr.husi.resources.tls_spoof
import com.fr.husi.resources.tls_spoof_method
import com.fr.husi.resources.toc
import com.fr.husi.resources.tuic_disable_sni
import com.fr.husi.resources.username_opt
import com.fr.husi.resources.utls_fingerprint
import com.fr.husi.resources.vpn_key
import com.fr.husi.resources.wb_sunny
import com.fr.husi.ui.NavRoutes
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.stringResource

@Composable
fun MASQUESettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: MASQUESettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        MASQUESettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        masqueSettings(uiState as MASQUEUiState, viewModel)
    }
}

private fun LazyListScope.masqueSettings(
    uiState: MASQUEUiState,
    viewModel: MASQUESettingsViewModel,
) {
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
            icon = {
                MaskedIcon(
                    Res.drawable.password,
                    color = IconMaskColors.IconWarmGray,
                )
            },
        )
    }

    preferenceGroup(key = "http") {
        HttpVersionPreference(
            value = uiState.httpVersion,
            isTLS = uiState.enableTLS,
            onValueChange = { viewModel.setHttpVersion(it) },
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
        SwitchPreference(
            value = uiState.disableVersionFallback,
            onValueChange = { viewModel.setDisableVersionFallback(it) },
            enabled = uiState.enableTLS,
            title = { Text(stringResource(Res.string.disable_version_fallback)) },
            icon = {
                MaskedIcon(Res.drawable.block, IconMaskColors.IconCoral)
            },
        )
    }

    preferenceGroup(key = "mtu") {
        TextFieldPreference(
            value = uiState.mtu,
            onValueChange = { viewModel.setMtu(it) },
            title = { Text(stringResource(Res.string.mtu)) },
            textToValue = { it.toIntOrNull() ?: MASQUEBean.DEFAULT_MTU },
            icon = {
                MaskedIcon(
                    resource = Res.drawable.numbers,
                    color = IconMaskColors.IconLightGreen,
                )
            },
            summary = { Text(contentOrUnset(uiState.mtu)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }

    item("category_tls") {
        PreferenceCategory(text = { Text(stringResource(Res.string.security_settings)) })
    }
    preferenceGroup(key = "enable_tls") {
        SwitchPreference(
            value = uiState.enableTLS,
            onValueChange = { viewModel.setEnableTLS(it) },
            title = { Text(stringResource(Res.string.tls)) },
            icon = {
                MaskedIcon(Res.drawable.lock, color = IconMaskColors.IconCyan)
            },
        )
    }
    if (uiState.enableTLS) {
        preferenceGroup(key = "server_name") {
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
                value = uiState.allowInsecure,
                onValueChange = { viewModel.setAllowInsecure(it) },
                title = { Text(stringResource(Res.string.allow_insecure)) },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.lock_open,
                        color = IconMaskColors.IconCoral,
                        shape = IconMaskShapes.risk(),
                    )
                },
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
                        resource = Res.drawable.vpn_key,
                        color = IconMaskColors.IconLightOrange,
                        shape = IconMaskShapes.credential(),
                    )
                },
                summary = { Text(contentOrUnset(uiState.certificates)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            TextFieldPreference(
                value = uiState.certPublicKeySha256,
                onValueChange = { viewModel.setCertPublicKeySha256(it) },
                title = { Text(stringResource(Res.string.cert_public_key_sha256)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(Res.drawable.wb_sunny, IconMaskColors.IconLightYellow)
                },
                summary = { Text(contentOrUnset(uiState.certPublicKeySha256)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            ListPreference(
                value = uiState.utlsFingerprint,
                values = fingerprints,
                onValueChange = { viewModel.setUtlsFingerprint(it) },
                title = { Text(stringResource(Res.string.utls_fingerprint)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.fingerprint,
                        color = IconMaskColors.IconCyan,
                    )
                },
                summary = { Text(contentOrUnset(uiState.utlsFingerprint)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(it) },
            )
        }
        if (!PlatformInfo.isAndroid) {
            preferenceGroup(key = "tls_spoof") {
                TextFieldPreference(
                    value = uiState.tlsSpoof,
                    onValueChange = { viewModel.setTlsSpoof(it) },
                    title = { Text(stringResource(Res.string.tls_spoof)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.domino_mask,
                            color = IconMaskColors.IconWarmGray,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.tlsSpoof)) },
                    valueToText = { it },
                )
                ListPreference(
                    value = uiState.tlsSpoofMethod,
                    values = tlsSpoofMethod,
                    onValueChange = { viewModel.setTlsSpoofMethod(it) },
                    title = { Text(stringResource(Res.string.tls_spoof_method)) },
                    enabled = uiState.tlsSpoof.isNotBlank(),
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.computer_cancel,
                            color = IconMaskColors.IconLightYellow,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.tlsSpoofMethod)) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(it) },
                )
            }
        }
        preferenceGroup(key = "disable_sni") {
            SwitchPreference(
                value = uiState.disableSNI,
                onValueChange = { viewModel.setDisableSNI(it) },
                title = { Text(stringResource(Res.string.tuic_disable_sni)) },
                icon = {
                    MaskedIcon(Res.drawable.block, color = IconMaskColors.IconWarmGray)
                },
            )
            SwitchPreference(
                value = uiState.tlsFragment,
                onValueChange = { viewModel.setTlsFragment(it) },
                title = { Text(stringResource(Res.string.tls_fragment)) },
                enabled = !uiState.tlsRecordFragment,
                icon = {
                    MaskedIcon(Res.drawable.texture, color = IconMaskColors.IconLightBlue)
                },
            )
            TextFieldPreference(
                value = uiState.tlsFragmentFallbackDelay,
                onValueChange = { viewModel.setTlsFragmentFallbackDelay(it) },
                title = { Text(stringResource(Res.string.tls_fragment_fallback_delay)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(Res.drawable.timelapse, color = IconMaskColors.IconLightOrange)
                },
                enabled = uiState.tlsFragment,
                summary = { Text(contentOrUnset(uiState.tlsFragmentFallbackDelay)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    DurationTextField(value, onValueChange, onOk)
                },
            )
            SwitchPreference(
                value = uiState.tlsRecordFragment,
                onValueChange = { viewModel.setTlsRecordFragment(it) },
                title = { Text(stringResource(Res.string.tls_record_fragment)) },
                enabled = !uiState.tlsFragment,
                icon = {
                    MaskedIcon(Res.drawable.wb_sunny, color = IconMaskColors.IconLavender)
                },
            )
        }

        item("category_ech") {
            PreferenceCategory(text = { Text(stringResource(Res.string.ech)) })
        }
        preferenceGroup(key = "ech") {
            SwitchPreference(
                value = uiState.ech,
                onValueChange = { viewModel.setEch(it) },
                title = { Text(stringResource(Res.string.ech)) },
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
                    MaskedIcon(
                        resource = Res.drawable.nfc,
                        color = IconMaskColors.IconLightYellow,
                        shape = IconMaskShapes.credential(),
                    )
                },
                enabled = uiState.ech,
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
                    MaskedIcon(Res.drawable.search, color = IconMaskColors.IconCyan)
                },
                enabled = uiState.ech,
                summary = { Text(contentOrUnset(uiState.echQueryServerName)) },
                valueToText = { it },
            )
        }

        item("category_mtls") {
            PreferenceCategory(text = { Text(stringResource(Res.string.mutual_tls)) })
        }
        preferenceGroup(key = "mtls_cert") {
            TextFieldPreference(
                value = uiState.clientCert,
                onValueChange = { viewModel.setClientCert(it) },
                title = { Text(stringResource(Res.string.client_certificate)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.lock,
                        color = IconMaskColors.IconCyan,
                        shape = IconMaskShapes.credential(),
                    )
                },
                summary = { Text(contentOrUnset(uiState.clientCert)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
            TextFieldPreference(
                value = uiState.clientKey,
                onValueChange = { viewModel.setClientKey(it) },
                title = { Text(stringResource(Res.string.client_key)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.vpn_key,
                        color = IconMaskColors.IconCyan,
                        shape = IconMaskShapes.credential(),
                    )
                },
                summary = { Text(contentOrUnset(uiState.clientKey)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    MultilineTextField(value, onValueChange, onOk)
                },
            )
        }
    }
}
