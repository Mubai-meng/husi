package com.fr.husi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.AnnotatedString
import com.fr.husi.ProtocolProvider
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.collectAsStateWithLifecycle
import com.fr.husi.compose.ListPreference
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.UIntegerTextField
import com.fr.husi.compose.material3.Text
import com.fr.husi.database.DataStore
import com.fr.husi.resources.Res
import com.fr.husi.resources.download
import com.fr.husi.resources.file_upload
import com.fr.husi.resources.flight_takeoff
import com.fr.husi.resources.hysteria2_provider
import com.fr.husi.resources.hysteria_download_mbps
import com.fr.husi.resources.hysteria_upload_mbps
import com.fr.husi.resources.juicity_provider
import com.fr.husi.resources.plugin
import com.fr.husi.resources.provider_naive
import com.fr.husi.ui.StringOrRes
import com.fr.husi.ui.stringOrRes
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProtocolSettingsGroup(
    needReload: () -> Unit,
) {
    val uploadSpeedValue by DataStore.uploadSpeed.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = uploadSpeedValue,
        onValueChange = {
            DataStore.uploadSpeed.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.hysteria_upload_mbps)) },
        textToValue = { it.toIntOrNull() ?: 0 },
        icon = {
            MaskedIcon(
                Res.drawable.file_upload,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(uploadSpeedValue.toString()) },
        valueToText = { it.toString() },
    ) { value, onValueChange, onOk ->
        UIntegerTextField(value, onValueChange, onOk)
    }

    val downloadSpeedValue by DataStore.downloadSpeed.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = downloadSpeedValue,
        onValueChange = {
            DataStore.downloadSpeed.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.hysteria_download_mbps)) },
        textToValue = { it.toIntOrNull() ?: 0 },
        icon = {
            MaskedIcon(Res.drawable.download, color = IconMaskColors.IconLightBlue)
        },
        summary = { Text(downloadSpeedValue.toString()) },
        valueToText = { it.toString() },
    ) { value, onValueChange, onOk ->
        UIntegerTextField(value, onValueChange, onOk)
    }

    fun pluginProviderText(index: Int): StringOrRes = when (index) {
        ProtocolProvider.CORE -> StringOrRes.Direct("sing-box")
        ProtocolProvider.PLUGIN -> StringOrRes.Res(Res.string.plugin)
        else -> StringOrRes.Direct("sing-box")
    }

    val hysteria2ProviderValue by DataStore.providerHysteria2.collectAsStateWithLifecycle()
    ListPreference(
        value = hysteria2ProviderValue,
        onValueChange = {
            DataStore.providerHysteria2.setBlocking(it)
            needReload()
        },
        values = listOf(ProtocolProvider.CORE, ProtocolProvider.PLUGIN),
        title = { Text(stringResource(Res.string.hysteria2_provider)) },
        icon = {
            MaskedIcon(
                Res.drawable.flight_takeoff,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringOrRes(pluginProviderText(hysteria2ProviderValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringOrRes(pluginProviderText(it))) },
    )

    val juicityProviderValue by DataStore.providerJuicity.collectAsStateWithLifecycle()
    ListPreference(
        value = juicityProviderValue,
        onValueChange = {
            DataStore.providerJuicity.setBlocking(it)
            needReload()
        },
        values = listOf(ProtocolProvider.CORE, ProtocolProvider.PLUGIN),
        title = { Text(stringResource(Res.string.juicity_provider)) },
        icon = {
            MaskedIcon(
                Res.drawable.flight_takeoff,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringOrRes(pluginProviderText(juicityProviderValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringOrRes(pluginProviderText(it))) },
    )

    val naiveProviderValue by DataStore.providerNaive.collectAsStateWithLifecycle()
    ListPreference(
        value = naiveProviderValue,
        onValueChange = {
            DataStore.providerNaive.setBlocking(it)
            needReload()
        },
        values = listOf(ProtocolProvider.CORE, ProtocolProvider.PLUGIN),
        title = { Text(stringResource(Res.string.provider_naive)) },
        icon = {
            MaskedIcon(
                Res.drawable.flight_takeoff,
                color = IconMaskColors.IconLightYellow,
            )
        },
        summary = { Text(stringOrRes(pluginProviderText(naiveProviderValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringOrRes(pluginProviderText(it))) },
    )
}
