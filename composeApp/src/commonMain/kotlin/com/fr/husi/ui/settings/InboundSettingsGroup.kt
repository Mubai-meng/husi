package com.fr.husi.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.collectAsStateWithLifecycle
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.PasswordPreference
import com.fr.husi.compose.PortTextField
import com.fr.husi.compose.SwitchPreference
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.material3.Text
import com.fr.husi.database.DataStore
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.resources.Res
import com.fr.husi.resources.allow_access
import com.fr.husi.resources.allow_access_sum
import com.fr.husi.resources.apps
import com.fr.husi.resources.directions_boat
import com.fr.husi.resources.inbound_password
import com.fr.husi.resources.inbound_username
import com.fr.husi.resources.nat
import com.fr.husi.resources.person
import com.fr.husi.resources.port_local_dns
import com.fr.husi.resources.port_proxy
import com.fr.husi.resources.wifi
import com.fr.husi.ui.PlatformAppendHttpProxyPreferences
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun InboundSettingsGroup(
    needReload: () -> Unit,
) {
    val isExpertState by DataStore.isExpert.collectAsStateWithLifecycle()

    val mixedPort by DataStore.mixedPort.collectAsStateWithLifecycle()
    val mixedPortValue = mixedPort.toString()
    TextFieldPreference(
        value = mixedPortValue,
        onValueChange = {
            DataStore.mixedPort.setBlocking(it.toIntOrNull() ?: 2080)
            needReload()
        },
        title = { Text(stringResource(Res.string.port_proxy)) },
        textToValue = { it },
        icon = {
            MaskedIcon(
                Res.drawable.directions_boat,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(contentOrUnset(mixedPortValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        PortTextField(value, onValueChange, onOk)
    }

    val localDnsPort by DataStore.localDNSPort.collectAsStateWithLifecycle()
    val localDnsPortValue = localDnsPort.toString()
    TextFieldPreference(
        value = localDnsPortValue,
        onValueChange = {
            DataStore.localDNSPort.setBlocking(it.toIntOrNull() ?: 0)
            needReload()
        },
        title = { Text(stringResource(Res.string.port_local_dns)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.apps, color = IconMaskColors.IconWarmGray)
        },
        summary = { Text(contentOrUnset(localDnsPortValue)) },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        PortTextField(value, onValueChange, onOk)
    }

    PlatformAppendHttpProxyPreferences(needReload)

    val allowAccessValue by DataStore.allowAccess.collectAsStateWithLifecycle()
    SwitchPreference(
        value = allowAccessValue,
        onValueChange = {
            DataStore.allowAccess.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.allow_access)) },
        icon = {
            MaskedIcon(Res.drawable.nat, color = IconMaskColors.IconCoral)
        },
        summary = { Text(stringResource(Res.string.allow_access_sum)) },
    )

    val inboundUsernameValue by DataStore.inboundUsername.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = inboundUsernameValue,
        onValueChange = {
            DataStore.inboundUsername.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.inbound_username)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.person, color = IconMaskColors.IconCyan)
        },
        summary = { Text(contentOrUnset(inboundUsernameValue)) },
        valueToText = { it },
    )

    val inboundPasswordValue by DataStore.inboundPassword.collectAsStateWithLifecycle()
    PasswordPreference(
        value = inboundPasswordValue,
        onValueChange = {
            DataStore.inboundPassword.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.inbound_password)) },
    )
    if (isExpertState) {
        val anchorSSIDValue by DataStore.anchorSSID.collectAsStateWithLifecycle()
        TextFieldPreference(
            value = anchorSSIDValue,
            onValueChange = {
                DataStore.anchorSSID.setBlocking(it)
                needReload()
            },
            title = { Text("Anchor SSIDs") },
            textToValue = { it },
            icon = {
                MaskedIcon(Res.drawable.wifi, color = IconMaskColors.IconCoral)
            },
            summary = { Text(contentOrUnset(anchorSSIDValue)) },
            valueToText = { it },
        )
    }
}
