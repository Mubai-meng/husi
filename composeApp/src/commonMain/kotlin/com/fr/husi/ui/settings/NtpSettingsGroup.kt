package com.fr.husi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.fr.husi.compose.DurationTextField
import com.fr.husi.compose.collectAsStateWithLifecycle
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.PortTextField
import com.fr.husi.compose.SwitchPreference
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.material3.Text
import com.fr.husi.database.DataStore
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.resources.Res
import com.fr.husi.resources.directions_boat
import com.fr.husi.resources.enable_ntp
import com.fr.husi.resources.flip_camera_android
import com.fr.husi.resources.ntp_server_address
import com.fr.husi.resources.ntp_server_port
import com.fr.husi.resources.ntp_sum
import com.fr.husi.resources.ntp_sync_interval
import com.fr.husi.resources.router
import com.fr.husi.resources.timelapse
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NtpSettingsGroup(
    needReload: () -> Unit,
) {
    val enableNtpValue by DataStore.ntpEnable.collectAsStateWithLifecycle()
    SwitchPreference(
        value = enableNtpValue,
        onValueChange = {
            DataStore.ntpEnable.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.enable_ntp)) },
        icon = {
            MaskedIcon(
                Res.drawable.timelapse,
                color = IconMaskColors.IconLightPink,
            )
        },
        summary = { Text(stringResource(Res.string.ntp_sum)) },
    )

    val ntpServerValue by DataStore.ntpAddress.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = ntpServerValue,
        onValueChange = {
            DataStore.ntpAddress.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.ntp_server_address)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.router, color = IconMaskColors.IconLightBlue)
        },
        summary = { Text(contentOrUnset(ntpServerValue)) },
        valueToText = { it },
        enabled = enableNtpValue,
    )

    val ntpPortValue by DataStore.ntpPort.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = ntpPortValue,
        onValueChange = {
            DataStore.ntpPort.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.ntp_server_port)) },
        textToValue = { it.toIntOrNull() ?: 123 },
        icon = {
            MaskedIcon(
                Res.drawable.directions_boat,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(ntpPortValue.toString()) },
        valueToText = { it.toString() },
        enabled = enableNtpValue,
    ) { value, onValueChange, onOk ->
        PortTextField(value, onValueChange, onOk)
    }

    val ntpIntervalValue by DataStore.ntpInterval.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = ntpIntervalValue,
        onValueChange = {
            DataStore.ntpInterval.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.ntp_sync_interval)) },
        textToValue = { it },
        icon = {
            MaskedIcon(
                Res.drawable.flip_camera_android,
                color = IconMaskColors.IconCyan,
            )
        },
        summary = { Text(contentOrUnset(ntpIntervalValue)) },
        valueToText = { it },
        enabled = enableNtpValue,
    ) { value, onValueChange, onOk ->
        DurationTextField(value, onValueChange, onOk)
    }
}
