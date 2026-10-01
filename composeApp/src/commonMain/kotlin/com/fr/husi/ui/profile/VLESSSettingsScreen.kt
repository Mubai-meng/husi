package com.fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import com.fr.husi.compose.ListPreference
import com.fr.husi.compose.MultilineTextField
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.ktx.intListN
import com.fr.husi.resources.Res
import com.fr.husi.resources.encrypted
import com.fr.husi.resources.encryption
import com.fr.husi.resources.not_set
import com.fr.husi.resources.outbox
import com.fr.husi.resources.packet_encoding
import com.fr.husi.resources.person
import com.fr.husi.resources.profile_config
import com.fr.husi.resources.stream
import com.fr.husi.resources.uuid
import com.fr.husi.resources.xtls_flow
import com.fr.husi.ui.NavRoutes
import com.fr.husi.ui.StringOrRes
import com.fr.husi.ui.stringOrRes
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.stringResource

@Composable
fun VLESSSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: VLESSSettingsViewModel = profileEditorViewModel(
        profileId = profileId,
        isSubscription = isSubscription,
    ) {
        VLESSSettingsViewModel()
    }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, scrollTo ->
        vlessSettings(uiState as VLESSUiState, viewModel, scrollTo)
    }
}

private fun LazyListScope.vlessSettings(
    uiState: VLESSUiState,
    viewModel: VLESSSettingsViewModel,
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
        ListPreference(
            value = uiState.flow,
            onValueChange = { viewModel.setFlow(it) },
            values = listOf("", "xtls-rprx-vision"),
            title = { Text(stringResource(Res.string.xtls_flow)) },
            icon = {
                MaskedIcon(
                    Res.drawable.stream,
                    color = IconMaskColors.IconLightBlue,
                )
            },
            summary = { Text(contentOrUnset(uiState.flow)) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(it) },
        )
        TextFieldPreference(
            value = uiState.encryption,
            onValueChange = { viewModel.setEncryption(it) },
            title = { Text(stringResource(Res.string.encryption)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    resource = Res.drawable.encrypted,
                    color = IconMaskColors.IconCoral,
                )
            },
            summary = { Text(contentOrUnset(uiState.encryption)) },
            valueToText = { it },
            textField = { value, onValueChange, onOk ->
                MultilineTextField(value, onValueChange, onOk)
            },
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
}
