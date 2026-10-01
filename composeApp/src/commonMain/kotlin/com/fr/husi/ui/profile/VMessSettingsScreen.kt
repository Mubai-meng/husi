package com.fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import com.fr.husi.compose.ListPreference
import com.fr.husi.compose.PreferenceCategory
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.SwitchPreference
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.UIntegerTextField
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.ktx.intListN
import com.fr.husi.resources.Res
import com.fr.husi.resources.alter_id
import com.fr.husi.resources.alternate_email
import com.fr.husi.resources.authenticated_length
import com.fr.husi.resources.encryption
import com.fr.husi.resources.enhanced_encryption
import com.fr.husi.resources.experimental_authenticated_length
import com.fr.husi.resources.experimental_settings
import com.fr.husi.resources.not_set
import com.fr.husi.resources.outbox
import com.fr.husi.resources.packet_encoding
import com.fr.husi.resources.person
import com.fr.husi.resources.profile_config
import com.fr.husi.resources.security
import com.fr.husi.resources.uuid
import com.fr.husi.ui.NavRoutes
import com.fr.husi.ui.StringOrRes
import com.fr.husi.ui.stringOrRes
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.stringResource

@Composable
fun VMessSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: VMessSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        VMessSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, scrollTo ->
        vmessSettings(uiState as VMessUiState, viewModel, scrollTo)
    }
}

private fun LazyListScope.vmessSettings(
    uiState: VMessUiState,
    viewModel: VMessSettingsViewModel,
    scrollTo: (String) -> Unit,
) {
    headSettings(uiState, viewModel)
    preferenceGroup {
        TextFieldPreference(
            value = uiState.uuid,
            onValueChange = { viewModel.setUUID(it) },
            title = { Text(stringResource(Res.string.uuid)) },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.person, color = IconMaskColors.IconCyan)
            },
            summary = { Text(contentOrUnset(uiState.uuid)) },
            valueToText = { it },
        )
        TextFieldPreference(
            value = uiState.alterID,
            onValueChange = { viewModel.setAlterID(it) },
            title = { Text(stringResource(Res.string.alter_id)) },
            textToValue = { it.toIntOrNull() ?: 0 },
            icon = {
                MaskedIcon(
                    Res.drawable.alternate_email,
                    color = IconMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(uiState.alterID)) },
            valueToText = { it.toString() },
            textField = { value, onValueChange, onOk ->
                UIntegerTextField(value, onValueChange, onOk)
            },
        )
        ListPreference(
            value = uiState.encryption,
            onValueChange = { viewModel.setEncryption(it) },
            values = listOf("auto", "aes-128-gcm", "chacha20-poly1305", "none", "zero"),
            title = { Text(stringResource(Res.string.encryption)) },
            icon = {
                MaskedIcon(
                    Res.drawable.enhanced_encryption,
                    color = IconMaskColors.IconLightOrange,
                )
            },
            summary = { Text(contentOrUnset(uiState.encryption)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
        fun packetEncodingName(packetEncoding: Int): StringOrRes = when (packetEncoding) {
            0 -> StringOrRes.Res(Res.string.not_set)
            1 -> StringOrRes.Direct("packetaddr")
            2 -> StringOrRes.Direct("XUDP")
            else -> error("impossible")
        }
        ListPreference(
            value = uiState.packetEncoding,
            onValueChange = { viewModel.setPacketEncoding(it) },
            values = intListN(3),
            title = { Text(stringResource(Res.string.packet_encoding)) },
            icon = {
                MaskedIcon(
                    Res.drawable.outbox,
                    color = IconMaskColors.IconLavender,
                )
            },
            summary = { Text(stringOrRes(packetEncodingName(uiState.packetEncoding))) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(stringOrRes(packetEncodingName(it))) },
        )
    }

    transportSettings(uiState, viewModel)
    muxSettings(uiState, viewModel)
    tlsSettings(uiState, viewModel, scrollTo)

    item("category_experimental") {
        PreferenceCategory(
            text = { Text(stringResource(Res.string.experimental_settings)) },
        )
    }
    preferenceGroup(key = "authenticated_length") {
        SwitchPreference(
            value = uiState.authenticatedLength,
            onValueChange = { viewModel.setAuthenticatedLength(it) },
            title = { Text(stringResource(Res.string.authenticated_length)) },
            icon = {
                MaskedIcon(Res.drawable.security, color = IconMaskColors.IconCoral)
            },
            summary = { Text(stringResource(Res.string.experimental_authenticated_length)) },
        )
    }
}
