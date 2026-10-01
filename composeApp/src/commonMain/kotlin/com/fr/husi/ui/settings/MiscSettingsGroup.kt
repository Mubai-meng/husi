package com.fr.husi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import com.fr.husi.CertProvider
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.collectAsStateWithLifecycle
import com.fr.husi.compose.IconMaskShapes
import com.fr.husi.compose.LinkOrContentTextField
import com.fr.husi.compose.ListPreference
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.SliderPreference
import com.fr.husi.compose.SwitchPreference
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.material3.Text
import com.fr.husi.database.DataStore
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.resources.Res
import com.fr.husi.resources.apps
import com.fr.husi.resources.cast_connected
import com.fr.husi.resources.cert_chrome
import com.fr.husi.resources.certificate_authority
import com.fr.husi.resources.connection_test_ignore_handshake_time
import com.fr.husi.resources.connection_test_unified_delay
import com.fr.husi.resources.connection_test_url
import com.fr.husi.resources.fast_forward
import com.fr.husi.resources.follow_system
import com.fr.husi.resources.mozilla
import com.fr.husi.resources.push_pin
import com.fr.husi.resources.question_mark
import com.fr.husi.resources.system_and_user
import com.fr.husi.resources.test_concurrency
import com.fr.husi.resources.test_timeout
import com.fr.husi.resources.timer
import com.fr.husi.ui.DisableProcessTextPreference
import com.fr.husi.ui.EnableTaskerPreference
import com.fr.husi.ui.HideLauncherIconPreference
import com.fr.husi.ui.PlatformMiscOptions
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MiscSettingsGroup(
    needReload: () -> Unit,
    needRestart: () -> Unit,
) {
    val connectionTestUrlValue by DataStore.connectionTestURL.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = connectionTestUrlValue,
        onValueChange = { DataStore.connectionTestURL.setBlocking(it) },
        title = { Text(stringResource(Res.string.connection_test_url)) },
        textToValue = { it },
        icon = {
            MaskedIcon(
                Res.drawable.cast_connected,
                color = IconMaskColors.IconCyan,
            )
        },
        summary = { Text(contentOrUnset(connectionTestUrlValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        LinkOrContentTextField(value, onValueChange, onOk)
    }

    val connectionTestConcurrentValue by DataStore.connectionTestConcurrent.collectAsStateWithLifecycle()
    var concurrentPreview by remember { mutableFloatStateOf(connectionTestConcurrentValue.toFloat()) }
    SliderPreference(
        value = connectionTestConcurrentValue.toFloat(),
        onValueChange = { DataStore.connectionTestConcurrent.setBlocking(it.toInt()) },
        sliderValue = concurrentPreview,
        onSliderValueChange = { concurrentPreview = it },
        title = { Text(stringResource(Res.string.test_concurrency)) },
        valueRange = 1f..32f,
        valueSteps = 32,
        icon = {
            MaskedIcon(
                Res.drawable.fast_forward,
                color = IconMaskColors.IconLightGreen,
            )
        },
        valueText = { Text(concurrentPreview.toInt().toString()) },
    )

    val connectionTestTimeoutValue by DataStore.connectionTestTimeout.collectAsStateWithLifecycle()
    var timeoutPreview by remember { mutableFloatStateOf(connectionTestTimeoutValue.toFloat()) }
    SliderPreference(
        value = connectionTestTimeoutValue.toFloat(),
        onValueChange = { DataStore.connectionTestTimeout.setBlocking(it.toInt()) },
        sliderValue = timeoutPreview,
        onSliderValueChange = { timeoutPreview = it },
        title = { Text(stringResource(Res.string.test_timeout)) },
        valueRange = 1024f..8192f,
        valueSteps = 20,
        icon = {
            MaskedIcon(Res.drawable.apps, color = IconMaskColors.IconWarmGray)
        },
        valueText = { Text(timeoutPreview.toInt().toString()) },
    )

    val connectionTestUnifiedDelay by DataStore.connectionTestUnifiedDelay.collectAsStateWithLifecycle()
    SwitchPreference(
        value = connectionTestUnifiedDelay,
        onValueChange = {
            DataStore.connectionTestUnifiedDelay.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.connection_test_unified_delay)) },
        icon = {
            MaskedIcon(Res.drawable.timer, IconMaskColors.IconLightGreen)
        },
    )

    val connectionTestIgnoreHandshakeTime by DataStore.connectionTestIgnoreHandshakeTime.collectAsStateWithLifecycle()
    SwitchPreference(
        value = connectionTestIgnoreHandshakeTime,
        onValueChange = {
            DataStore.connectionTestIgnoreHandshakeTime.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.connection_test_ignore_handshake_time)) },
        icon = {
            MaskedIcon(Res.drawable.question_mark, IconMaskColors.IconLightGreen)
        },
    )
    PlatformMiscOptions(needReload)

    val certProviderValue by DataStore.certProvider.collectAsStateWithLifecycle()

    fun certProviderTextRes(index: Int): StringResource = when (index) {
        CertProvider.SYSTEM -> Res.string.follow_system
        CertProvider.MOZILLA -> Res.string.mozilla
        CertProvider.SYSTEM_AND_USER -> Res.string.system_and_user
        CertProvider.CHROME -> Res.string.cert_chrome
        else -> Res.string.mozilla
    }
    ListPreference(
        value = certProviderValue,
        onValueChange = {
            DataStore.certProvider.setBlocking(it)
            needRestart()
        },
        values = listOf(
            CertProvider.SYSTEM,
            CertProvider.MOZILLA,
            CertProvider.SYSTEM_AND_USER,
            CertProvider.CHROME,
        ),
        title = { Text(stringResource(Res.string.certificate_authority)) },
        icon = {
            MaskedIcon(
                Res.drawable.push_pin,
                color = IconMaskColors.IconCoral,
                shape = IconMaskShapes.credential(),
            )
        },
        summary = { Text(stringResource(certProviderTextRes(certProviderValue))) },
        type = ListPreferenceType.DROPDOWN_MENU,
        valueToText = { AnnotatedString(stringResource(certProviderTextRes(it))) },
    )

    DisableProcessTextPreference()
    EnableTaskerPreference()
    HideLauncherIconPreference()
}
