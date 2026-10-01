package com.fr.husi.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
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
import com.fr.husi.compose.ValidatedTextField
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.fmt.hysteria.HysteriaBean
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.ktx.intListN
import com.fr.husi.ktx.readableMessage
import com.fr.husi.libcore.Libcore
import com.fr.husi.resources.Res
import com.fr.husi.resources.allow_insecure
import com.fr.husi.resources.allow_insecure_sum
import com.fr.husi.resources.alpn
import com.fr.husi.resources.block
import com.fr.husi.resources.cert_public_key_sha256
import com.fr.husi.resources.certificate_sha256
import com.fr.husi.resources.certificates
import com.fr.husi.resources.client_certificate
import com.fr.husi.resources.client_key
import com.fr.husi.resources.compare_arrows
import com.fr.husi.resources.copyright
import com.fr.husi.resources.directions_boat
import com.fr.husi.resources.domino_mask
import com.fr.husi.resources.ech
import com.fr.husi.resources.ech_config
import com.fr.husi.resources.ech_query_server_name
import com.fr.husi.resources.emoji_symbols
import com.fr.husi.resources.enable
import com.fr.husi.resources.encrypted
import com.fr.husi.resources.enhanced_encryption
import com.fr.husi.resources.hop_interval
import com.fr.husi.resources.hysteria2_disable_chrome_parrot
import com.fr.husi.resources.hysteria2_disable_chrome_parrot_sum
import com.fr.husi.resources.hysteria2_gecko_max_packet_size
import com.fr.husi.resources.hysteria2_gecko_min_packet_size
import com.fr.husi.resources.hysteria2_obfs_type
import com.fr.husi.resources.hysteria_auth_payload
import com.fr.husi.resources.hysteria_auth_type
import com.fr.husi.resources.hysteria_bbr_profile
import com.fr.husi.resources.hysteria_bbr_profile_aggressive
import com.fr.husi.resources.hysteria_bbr_profile_conservative
import com.fr.husi.resources.hysteria_bbr_profile_standard
import com.fr.husi.resources.hysteria_hop_interval_range_hint
import com.fr.husi.resources.hysteria_obfs
import com.fr.husi.resources.layers
import com.fr.husi.resources.lock
import com.fr.husi.resources.multiple_stop
import com.fr.husi.resources.mutual_tls
import com.fr.husi.resources.nfc
import com.fr.husi.resources.not_set
import com.fr.husi.resources.password
import com.fr.husi.resources.plugin_disabled
import com.fr.husi.resources.profile_config
import com.fr.husi.resources.profile_name
import com.fr.husi.resources.protocol
import com.fr.husi.resources.protocol_version
import com.fr.husi.resources.proxy_cat
import com.fr.husi.resources.quic
import com.fr.husi.resources.quic_connection_receive_window
import com.fr.husi.resources.quic_disable_path_mtu_discovery
import com.fr.husi.resources.quic_idle_timeout
import com.fr.husi.resources.quic_initial_packet_size
import com.fr.husi.resources.quic_keep_alive_period
import com.fr.husi.resources.quic_max_concurrent_streams
import com.fr.husi.resources.quic_stream_receive_window
import com.fr.husi.resources.router
import com.fr.husi.resources.search
import com.fr.husi.resources.security
import com.fr.husi.resources.server_address
import com.fr.husi.resources.server_port
import com.fr.husi.resources.sni
import com.fr.husi.resources.texture
import com.fr.husi.resources.timelapse
import com.fr.husi.resources.toc
import com.fr.husi.resources.transform
import com.fr.husi.resources.tuic_congestion_controller
import com.fr.husi.resources.tuic_disable_sni
import com.fr.husi.resources.type_specimen
import com.fr.husi.resources.update
import com.fr.husi.resources.vpn_key
import com.fr.husi.resources.wb_sunny
import com.fr.husi.ui.NavRoutes
import com.fr.husi.ui.StringOrRes
import com.fr.husi.ui.stringOrRes
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun HysteriaSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: HysteriaSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        HysteriaSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        hysteriaSettings(uiState as HysteriaUiState, viewModel)
    }
}

private fun LazyListScope.hysteriaSettings(
    uiState: HysteriaUiState,
    viewModel: HysteriaSettingsViewModel,
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
        ListPreference(
            value = uiState.protocolVersion,
            values = listOf(HysteriaBean.PROTOCOL_VERSION_1, HysteriaBean.PROTOCOL_VERSION_2),
            onValueChange = { viewModel.setProtocolVersion(it) },
            title = { Text(stringResource(Res.string.protocol_version)) },
            icon = {
                MaskedIcon(Res.drawable.update, color = IconMaskColors.IconCyan)
            },
            summary = { Text(uiState.protocolVersion.toString()) },
            type = ListPreferenceType.DROPDOWN_MENU,
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
            value = uiState.ports,
            onValueChange = { viewModel.setPorts(it) },
            title = { Text(stringResource(Res.string.server_port)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.directions_boat,
                    color = IconMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(uiState.ports)) },
            valueToText = { it },
        )
        TextFieldPreference(
            value = uiState.hopInterval,
            onValueChange = { viewModel.setHopInterval(it) },
            title = { Text(stringResource(Res.string.hop_interval)) },
            textToValue = { it },
            enabled = uiState.ports.toIntOrNull() == null,
            icon = {
                MaskedIcon(Res.drawable.timelapse, IconMaskColors.IconLightOrange)
            },
            summary = { Text(contentOrUnset(uiState.hopInterval)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                HopIntervalTextField(
                    value = value,
                    onValueChange = onValueChange,
                    onOk = onOk,
                    supportRange = uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2,
                )
            },
        )
        if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2) {
            fun obfsTypeName(type: String): StringOrRes = when (type) {
                HysteriaBean.OBFS_TYPE_NONE -> StringOrRes.Res(Res.string.plugin_disabled)
                HysteriaBean.OBFS_TYPE_SALAMANDER -> StringOrRes.Direct("Salamander")
                HysteriaBean.OBFS_TYPE_GECKO -> StringOrRes.Direct("Gecko")
                else -> StringOrRes.Direct(type)
            }
            ListPreference(
                value = uiState.obfsType,
                values = listOf(
                    HysteriaBean.OBFS_TYPE_NONE,
                    HysteriaBean.OBFS_TYPE_SALAMANDER,
                    HysteriaBean.OBFS_TYPE_GECKO,
                ),
                onValueChange = { viewModel.setObfsType(it) },
                title = { Text(stringResource(Res.string.hysteria2_obfs_type)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.type_specimen,
                        color = IconMaskColors.IconCyan,
                    )
                },
                summary = { Text(stringOrRes(obfsTypeName(uiState.obfsType))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(stringOrRes(obfsTypeName(it))) },
            )
            if (uiState.obfsType == HysteriaBean.OBFS_TYPE_GECKO) {
                TextFieldPreference(
                    value = uiState.geckoMinPacketSize,
                    onValueChange = { viewModel.setGeckoMinPacketSize(it) },
                    title = { Text(stringResource(Res.string.hysteria2_gecko_min_packet_size)) },
                    textToValue = { it.toIntOrNull() ?: 0 },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.texture,
                            color = IconMaskColors.IconWarmGray,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.geckoMinPacketSize)) },
                    valueToText = { it.toString() },
                    textField = { value, onValueChange, onOk ->
                        UIntegerTextField(value, onValueChange, onOk)
                    },
                )
                TextFieldPreference(
                    value = uiState.geckoMaxPacketSize,
                    onValueChange = { viewModel.setGeckoMaxPacketSize(it) },
                    title = { Text(stringResource(Res.string.hysteria2_gecko_max_packet_size)) },
                    textToValue = { it.toIntOrNull() ?: 0 },
                    icon = { Spacer(Modifier.size(24.dp)) },
                    summary = { Text(contentOrUnset(uiState.geckoMaxPacketSize)) },
                    valueToText = { it.toString() },
                    textField = { value, onValueChange, onOk ->
                        UIntegerTextField(value, onValueChange, onOk)
                    },
                )
            }
        }
        PasswordPreference(
            value = uiState.obfsPassword,
            onValueChange = { viewModel.setObfsPassword(it) },
            title = { Text(stringResource(Res.string.hysteria_obfs)) },
            enabled = uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1
                    || uiState.obfsType != HysteriaBean.OBFS_TYPE_NONE,
            icon = {
                MaskedIcon(Res.drawable.texture, color = IconMaskColors.IconCyan)
            },
        )
        if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
            fun authTypeName(type: Int): StringOrRes = when (type) {
                HysteriaBean.TYPE_NONE -> StringOrRes.Res(Res.string.plugin_disabled)
                HysteriaBean.TYPE_STRING -> StringOrRes.Direct("STRING")
                HysteriaBean.TYPE_BASE64 -> StringOrRes.Direct("BASE64")
                else -> error("impossible")
            }
            ListPreference(
                value = uiState.authType,
                values = intListN(3),
                onValueChange = { viewModel.setAuthType(it) },
                title = { Text(stringResource(Res.string.hysteria_auth_type)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.compare_arrows,
                        color = IconMaskColors.IconCyan,
                    )
                },
                summary = { Text(stringOrRes(authTypeName(uiState.authType))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(stringOrRes(authTypeName(it))) },
            )
        }
        if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1 &&
            uiState.authType != HysteriaBean.TYPE_NONE ||
            uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2
        ) {
            val titleRes = if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2) {
                Res.string.password
            } else {
                Res.string.hysteria_auth_payload
            }
            PasswordPreference(
                value = uiState.authPayload,
                onValueChange = { viewModel.setAuthPayload(it) },
                title = { Text(stringResource(titleRes)) },
            )
        }
        if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
            val protocolNames = remember {
                listOf(
                    "UDP",
                    "FakeTCP (Root Required)",
                    "WeChat Video",
                )
            }
            ListPreference(
                value = uiState.protocol,
                values = intListN(3),
                onValueChange = { viewModel.setProtocol(it) },
                title = { Text(stringResource(Res.string.protocol)) },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.layers,
                        color = IconMaskColors.IconLightGreen,
                    )
                },
                summary = { Text(protocolNames[uiState.protocol]) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(protocolNames[it]) },
            )
        }
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
        if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_1) {
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
        }
        TextFieldPreference(
            value = uiState.certificates,
            onValueChange = { viewModel.setCertificates(it) },
            title = { Text(stringResource(Res.string.certificates)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.vpn_key,
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
            value = uiState.certificateSha256,
            onValueChange = { viewModel.setCertificateSha256(it) },
            title = { Text(stringResource(Res.string.certificate_sha256)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.encrypted,
                    color = IconMaskColors.IconLightGreen,
                    shape = IconMaskShapes.credential(),
                )
            },
            summary = { Text(contentOrUnset(uiState.certificateSha256)) },
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
        SwitchPreference(
            value = uiState.allowInsecure,
            onValueChange = { viewModel.setAllowInsecure(it) },
            title = { Text(stringResource(Res.string.allow_insecure)) },
            summary = { Text(stringResource(Res.string.allow_insecure_sum)) },
            icon = {
                MaskedIcon(
                    Res.drawable.enhanced_encryption,
                    color = IconMaskColors.IconCoral,
                    shape = IconMaskShapes.risk(),
                )
            },
        )
        SwitchPreference(
            value = uiState.disableSNI,
            onValueChange = { viewModel.setDisableSNI(it) },
            title = { Text(stringResource(Res.string.tuic_disable_sni)) },
            icon = {
                MaskedIcon(Res.drawable.block, IconMaskColors.IconWarmGray)
            },
        )
    }
    item("category_quic") {
        PreferenceCategory(text = { Text(stringResource(Res.string.quic)) })
    }
    preferenceGroup(key = "stream_receive_window") {
        TextFieldPreference(
            value = uiState.streamReceiveWindow,
            onValueChange = { viewModel.setStreamReceiveWindow(it) },
            title = { Text(stringResource(Res.string.quic_stream_receive_window)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            enabled = uiState.disableChromeParrot,
            icon = {
                MaskedIcon(Res.drawable.texture, IconMaskColors.IconWarmGray)
            },
            summary = {
                val text = if (uiState.streamReceiveWindow == 0) {
                    stringResource(Res.string.not_set)
                } else {
                    uiState.streamReceiveWindow.toString()
                }
                Text(text)
            },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        TextFieldPreference(
            value = uiState.connectionReceiveWindow,
            onValueChange = { viewModel.setConnectionReceiveWindow(it) },
            title = { Text(stringResource(Res.string.quic_connection_receive_window)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            enabled = uiState.disableChromeParrot,
            icon = {
                MaskedIcon(Res.drawable.transform, IconMaskColors.IconWarmGray)
            },
            summary = {
                val text = if (uiState.connectionReceiveWindow == 0) {
                    stringResource(Res.string.not_set)
                } else {
                    uiState.connectionReceiveWindow.toString()
                }
                Text(text)
            },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        SwitchPreference(
            value = uiState.disableMtuDiscovery,
            onValueChange = { viewModel.setDisableMtuDiscovery(it) },
            title = { Text(stringResource(Res.string.quic_disable_path_mtu_discovery)) },
            icon = {
                MaskedIcon(
                    resource = Res.drawable.multiple_stop,
                    color = IconMaskColors.IconLightYellow,
                    shape = IconMaskShapes.route(),
                )
            },
        )
        if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2) {
            SwitchPreference(
                value = uiState.disableChromeParrot,
                onValueChange = { viewModel.setDisableChromeParrot(it) },
                title = { Text(stringResource(Res.string.hysteria2_disable_chrome_parrot)) },
                summary = { Text(stringResource(Res.string.hysteria2_disable_chrome_parrot_sum)) },
                icon = {
                    MaskedIcon(Res.drawable.domino_mask, IconMaskColors.IconWarmGray)
                },
            )
        }
        TextFieldPreference(
            value = uiState.idleTimeout,
            onValueChange = { viewModel.setIdleTimeout(it) },
            title = { Text(stringResource(Res.string.quic_idle_timeout)) },
            textToValue = { it },
            enabled = uiState.disableChromeParrot,
            icon = {
                MaskedIcon(Res.drawable.timelapse, IconMaskColors.IconWarmGray)
            },
            summary = { Text(contentOrUnset(uiState.idleTimeout)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                DurationTextField(value, onValueChange, onOk)
            },
        )
        TextFieldPreference(
            value = uiState.keepAlivePeriod,
            onValueChange = { viewModel.setKeepAlivePeriod(it) },
            title = { Text(stringResource(Res.string.quic_keep_alive_period)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.timelapse, IconMaskColors.IconWarmGray)
            },
            summary = { Text(contentOrUnset(uiState.keepAlivePeriod)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                DurationTextField(value, onValueChange, onOk)
            },
        )
        TextFieldPreference(
            value = uiState.maxConcurrentStreams,
            onValueChange = { viewModel.setMaxConcurrentStreams(it) },
            title = { Text(stringResource(Res.string.quic_max_concurrent_streams)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            enabled = uiState.disableChromeParrot,
            icon = {
                MaskedIcon(Res.drawable.transform, IconMaskColors.IconWarmGray)
            },
            summary = {
                val text = if (uiState.maxConcurrentStreams == 0) {
                    stringResource(Res.string.not_set)
                } else {
                    uiState.maxConcurrentStreams.toString()
                }
                Text(text)
            },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        TextFieldPreference(
            value = uiState.initialPacketSize,
            onValueChange = { viewModel.setInitialPacketSize(it) },
            title = { Text(stringResource(Res.string.quic_initial_packet_size)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            enabled = uiState.disableChromeParrot,
            icon = {
                MaskedIcon(Res.drawable.texture, IconMaskColors.IconWarmGray)
            },
            summary = {
                val text = if (uiState.initialPacketSize == 0) {
                    stringResource(Res.string.not_set)
                } else {
                    uiState.initialPacketSize.toString()
                }
                Text(text)
            },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
    }

    if (uiState.protocolVersion == HysteriaBean.PROTOCOL_VERSION_2) {
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
            val hysteriaCongestionControls = remember {
                listOf(
                    HysteriaBean.CONGESTION_CONTROL_BBR,
                    HysteriaBean.CONGESTION_CONTROL_RENO,
                )
            }

            fun congestionControlName(control: String): String = when (control) {
                HysteriaBean.CONGESTION_CONTROL_BBR -> "BBR"
                HysteriaBean.CONGESTION_CONTROL_RENO -> "Reno"
                else -> error("impossible")
            }
            ListPreference(
                value = uiState.congestionControl,
                values = hysteriaCongestionControls,
                onValueChange = { viewModel.setCongestionControl(it) },
                title = { Text(stringResource(Res.string.tuic_congestion_controller)) },
                icon = {
                    MaskedIcon(
                        resource = Res.drawable.compare_arrows,
                        color = IconMaskColors.IconLightGreen,
                    )
                },
                summary = { Text(congestionControlName(uiState.congestionControl)) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(congestionControlName(it)) },
            )

            if (uiState.congestionControl == HysteriaBean.CONGESTION_CONTROL_BBR) {
                fun bbrProfileName(profile: Int): StringResource = when (profile) {
                    HysteriaBean.BBR_PROFILE_CONSERVATIVE -> Res.string.hysteria_bbr_profile_conservative
                    HysteriaBean.BBR_PROFILE_STANDARD -> Res.string.hysteria_bbr_profile_standard
                    HysteriaBean.BBR_PROFILE_AGGRESSIVE -> Res.string.hysteria_bbr_profile_aggressive
                    else -> error("impossible")
                }
                ListPreference(
                    value = uiState.bbrProfile,
                    values = intListN(3),
                    onValueChange = { viewModel.setBBRProfile(it) },
                    title = { Text(stringResource(Res.string.hysteria_bbr_profile)) },
                    icon = {
                        MaskedIcon(
                            resource = Res.drawable.transform,
                            color = IconMaskColors.IconLavender,
                        )
                    },
                    summary = { Text(stringResource(bbrProfileName(uiState.bbrProfile))) },
                    type = ListPreferenceType.DROPDOWN_MENU,
                    valueToText = { AnnotatedString(stringResource(bbrProfileName(it))) },
                )
            }
        }
    }

    item("category_ech") {
        PreferenceCategory(text = { Text(stringResource(Res.string.ech)) })
    }
    preferenceGroup(key = "ech") {
        SwitchPreference(
            value = uiState.ech,
            onValueChange = { viewModel.setEch(it) },
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
}

@Composable
private fun HopIntervalTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onOk: () -> Unit,
    supportRange: Boolean,
) {
    if (!supportRange) {
        DurationTextField(value, onValueChange, onOk)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(Res.string.hysteria_hop_interval_range_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        ValidatedTextField(
            value = value,
            onValueChange = onValueChange,
            onOk = onOk,
            validator = { text ->
                when {
                    text.isBlank() -> null
                    text.lines().size > 1 -> "Unexpected new line"
                    text.count { it == '-' } > 1 -> "Only one '-' is allowed"
                    else -> {
                        val parts = text.split("-", limit = 2)
                        if (parts.any { it.isBlank() }) {
                            "Duration range is incomplete"
                        } else try {
                            for (part in parts) {
                                Libcore.parseDuration(part)
                            }
                            null
                        } catch (e: Exception) {
                            e.readableMessage
                        }
                    }
                }
            },
        )
    }
}
