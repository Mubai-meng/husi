package com.fr.husi.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import com.fr.husi.LauncherIcon
import com.fr.husi.compose.HostTextField
import com.fr.husi.compose.collectAsStateWithLifecycle
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.IconMaskShapes
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.SwitchPreference
import com.fr.husi.compose.TextButton
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.TwoTargetSwitchPreference
import com.fr.husi.compose.ValidatedTextField
import com.fr.husi.compose.material3.Text
import com.fr.husi.database.DataStore
import com.fr.husi.ktx.findActivity
import com.fr.husi.ktx.getColour
import com.fr.husi.resources.Res
import com.fr.husi.resources.acquire_wake_lock
import com.fr.husi.resources.acquire_wake_lock_summary
import com.fr.husi.resources.allow_apps_bypass_vpn
import com.fr.husi.resources.app_registration
import com.fr.husi.resources.append_http_proxy
import com.fr.husi.resources.append_http_proxy_sum
import com.fr.husi.resources.apps
import com.fr.husi.resources.auto_connect
import com.fr.husi.resources.auto_connect_summary
import com.fr.husi.resources.bolt
import com.fr.husi.resources.cancel
import com.fr.husi.resources.data_usage
import com.fr.husi.resources.developer_board
import com.fr.husi.resources.disable_process_text
import com.fr.husi.resources.domain
import com.fr.husi.resources.enable_tasker
import com.fr.husi.resources.enable_tasker_summary
import com.fr.husi.resources.format_align_left
import com.fr.husi.resources.hide_launcher_icon
import com.fr.husi.resources.hide_launcher_icon_confirm
import com.fr.husi.resources.hide_launcher_icon_summary
import com.fr.husi.resources.http_proxy_bypass
import com.fr.husi.resources.keyboard_tab
import com.fr.husi.resources.label
import com.fr.husi.resources.legend_toggle
import com.fr.husi.resources.metered
import com.fr.husi.resources.metered_summary
import com.fr.husi.resources.ok
import com.fr.husi.resources.phonelink_ring
import com.fr.husi.resources.privacy
import com.fr.husi.resources.privacy_mode
import com.fr.husi.resources.privacy_mode_summary
import com.fr.husi.resources.proxied_apps
import com.fr.husi.resources.proxied_apps_summary
import com.fr.husi.resources.route_opt_bypass_lan
import com.fr.husi.resources.show_group_in_notification
import com.fr.husi.resources.transform
import com.fr.husi.resources.update_proxy_apps_when_install
import com.fr.husi.resources.visibility_off
import com.fr.husi.resources.vpn_session_name
import com.fr.husi.resources.vpn_session_name_summary
import com.fr.husi.tasker.TaskerActivity
import com.fr.husi.tasker.TaskerReceiver
import com.fr.husi.ui.settings.appUpdateSettings
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun AutoConnectPreference(showMessage: (String) -> Unit) {
    val value by DataStore.persistAcrossReboot.collectAsStateWithLifecycle()
    SwitchPreference(
        value = value,
        onValueChange = { DataStore.persistAcrossReboot.setBlocking(it) },
        title = { Text(stringResource(Res.string.auto_connect)) },
        icon = {
            MaskedIcon(
                Res.drawable.phonelink_ring,
                color = IconMaskColors.IconLightPink,
            )
        },
        summary = { Text(stringResource(Res.string.auto_connect_summary)) },
    )
}

@Composable
internal actual fun PlatformDaemonSettingsGroup(showMessage: (String) -> Unit) {
}

internal actual fun LazyListScope.platformAppUpdateSettings() {
    appUpdateSettings()
}

@Composable
internal actual fun rememberApplyNightMode(): (Int) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { selection ->
            AppCompatDelegate.setDefaultNightMode(
                when (selection) {
                    0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    1 -> AppCompatDelegate.MODE_NIGHT_YES
                    2 -> AppCompatDelegate.MODE_NIGHT_NO
                    else -> AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
                },
            )
            context.findActivity<Activity>()!!.recreate()
        }
    }
}

@Composable
internal actual fun PlatformGeneralOptions(needReload: () -> Unit) {
    val value by DataStore.vpnSessionName.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = value,
        onValueChange = {
            DataStore.vpnSessionName.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.vpn_session_name)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.label, color = IconMaskColors.IconLightBlue)
        },
        summary = {
            val text = value.ifBlank { stringResource(Res.string.vpn_session_name_summary) }
            Text(text)
        },
        valueToText = { it },
    ) { value, onValueChange, onOk ->
        ValidatedTextField(
            value = value,
            onValueChange = onValueChange,
            onOk = onOk,
            validator = { text ->
                if (text.lines().size > 1) {
                    "Unexpected new line"
                } else {
                    null
                }
            },
        )
    }

    val bypassValue by DataStore.allowAppsBypassVpn.collectAsStateWithLifecycle()
    SwitchPreference(
        value = bypassValue,
        onValueChange = {
            DataStore.allowAppsBypassVpn.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.allow_apps_bypass_vpn)) },
        icon = {
            MaskedIcon(Res.drawable.transform, color = IconMaskColors.IconCyan)
        },
    )

    val showGroupValue by DataStore.showGroupInNotification.collectAsStateWithLifecycle()
    SwitchPreference(
        value = showGroupValue,
        onValueChange = {
            DataStore.showGroupInNotification.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.show_group_in_notification)) },
        icon = {
            MaskedIcon(Res.drawable.label, color = IconMaskColors.IconLightPink)
        },
    )
}

@Composable
internal actual fun PlatformRouteOptions(needReload: () -> Unit, isVpnMode: Boolean) {
    val value by DataStore.bypassLan.collectAsStateWithLifecycle()
    SwitchPreference(
        value = value,
        onValueChange = {
            DataStore.bypassLan.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.route_opt_bypass_lan)) },
        icon = {
            MaskedIcon(
                Res.drawable.legend_toggle,
                color = IconMaskColors.IconLightGreen,
            )
        },
    )
}

@Composable
internal actual fun ProxyAppsPreferences(openAppManager: () -> Unit) {
    val value by DataStore.proxyApps.collectAsStateWithLifecycle()
    TwoTargetSwitchPreference(
        value = value,
        onValueChange = {
            DataStore.proxyApps.setBlocking(it)
            if (it) {
                openAppManager()
            }
        },
        title = { Text(stringResource(Res.string.proxied_apps)) },
        icon = {
            MaskedIcon(Res.drawable.apps, color = IconMaskColors.IconCyan)
        },
        summary = { Text(stringResource(Res.string.proxied_apps_summary)) },
        onClick = {
            if (!value) {
                DataStore.proxyApps.setBlocking(true)
            }
            openAppManager()
        },
    )
    val updateValue by DataStore.updateProxyAppsWhenInstall.collectAsStateWithLifecycle()
    SwitchPreference(
        value = updateValue,
        onValueChange = { DataStore.updateProxyAppsWhenInstall.setBlocking(it) },
        title = { Text(stringResource(Res.string.update_proxy_apps_when_install)) },
        icon = {
            MaskedIcon(
                Res.drawable.keyboard_tab,
                color = IconMaskColors.IconLavender,
            )
        },
    )
}

@Composable
internal actual fun PlatformSecurityOptions() {
    val value by DataStore.privacyMode.collectAsStateWithLifecycle()
    SwitchPreference(
        value = value,
        onValueChange = { DataStore.privacyMode.setBlocking(it) },
        title = { Text(stringResource(Res.string.privacy_mode)) },
        icon = {
            MaskedIcon(Res.drawable.privacy, color = IconMaskColors.IconCoral)
        },
        summary = { Text(stringResource(Res.string.privacy_mode_summary)) },
    )
}

@Composable
internal actual fun MeteredNetworkPreference(needReload: () -> Unit) {
    val value by DataStore.meteredNetwork.collectAsStateWithLifecycle()
    SwitchPreference(
        value = value,
        onValueChange = {
            DataStore.meteredNetwork.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.metered)) },
        icon = {
            MaskedIcon(
                Res.drawable.data_usage,
                color = IconMaskColors.IconLightBlue,
            )
        },
        summary = { Text(stringResource(Res.string.metered_summary)) },
    )
}

@Composable
internal actual fun PlatformAppendHttpProxyPreferences(needReload: () -> Unit) {
    val appendHttpProxyValue by DataStore.appendHttpProxy.collectAsStateWithLifecycle()
    SwitchPreference(
        value = appendHttpProxyValue,
        onValueChange = {
            DataStore.appendHttpProxy.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.append_http_proxy)) },
        icon = {
            MaskedIcon(
                Res.drawable.app_registration,
                color = IconMaskColors.IconLightGreen,
            )
        },
        summary = {
            Text(stringResource(Res.string.append_http_proxy_sum))
        },
    )

    val value by DataStore.httpProxyBypass.collectAsStateWithLifecycle()
    TextFieldPreference(
        value = value,
        onValueChange = {
            DataStore.httpProxyBypass.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.http_proxy_bypass)) },
        textToValue = { it },
        icon = {
            MaskedIcon(Res.drawable.domain, color = IconMaskColors.IconCyan)
        },
        valueToText = { it },
        enabled = appendHttpProxyValue,
    ) { value, onValueChange, onOk ->
        HostTextField(value, onValueChange, onOk)
    }
}

@Composable
internal actual fun PlatformMiscOptions(needReload: () -> Unit) {
    val value by DataStore.acquireWakeLock.collectAsStateWithLifecycle()
    SwitchPreference(
        value = value,
        onValueChange = {
            DataStore.acquireWakeLock.setBlocking(it)
            needReload()
        },
        title = { Text(stringResource(Res.string.acquire_wake_lock)) },
        icon = {
            MaskedIcon(
                Res.drawable.developer_board,
                color = IconMaskColors.IconLightGreen,
            )
        },
        summary = { Text(stringResource(Res.string.acquire_wake_lock_summary)) },
    )
}

@Composable
internal actual fun rememberThemeExtraColors(): List<Color> {
    val context = LocalContext.current
    return remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Color(context.getColour(android.R.color.system_accent1_600)))
        } else {
            emptyList()
        }
    }
}

@Composable
internal actual fun rememberAppLanguageController(defaultTag: String): AppLanguageController {
    val initialValue = remember(defaultTag) {
        AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { defaultTag }
    }
    return remember {
        object : AppLanguageController {
            override var value: String = initialValue
                set(value) {
                    field = value
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(value),
                    )
                }
            override val flow = flowOf(initialValue)
        }
    }
}

private const val PROCESS_TEXT_ALIAS = "com.fr.husi.ui.ProcessTextActivityAlias"

private fun Context.setComponentEnabled(component: ComponentName, enabled: Boolean) {
    packageManager.setComponentEnabledSetting(
        component,
        if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        },
        PackageManager.DONT_KILL_APP,
    )
}

@Composable
internal actual fun DisableProcessTextPreference() {
    val value by DataStore.disableProcessText.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SwitchPreference(
        value = value,
        onValueChange = { disabled ->
            DataStore.disableProcessText.setBlocking(disabled)
            context.setComponentEnabled(
                ComponentName(context, PROCESS_TEXT_ALIAS),
                !disabled,
            )
        },
        title = { Text(stringResource(Res.string.disable_process_text)) },
        icon = {
            MaskedIcon(
                Res.drawable.format_align_left,
                color = IconMaskColors.IconWarmGray,
            )
        },
    )
}

@Composable
internal actual fun EnableTaskerPreference() {
    val value by DataStore.enableTasker.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SwitchPreference(
        value = value,
        onValueChange = { enabled ->
            DataStore.enableTasker.setBlocking(enabled)
            context.setComponentEnabled(
                ComponentName(context, TaskerReceiver::class.java),
                enabled,
            )
            context.setComponentEnabled(
                ComponentName(context, TaskerActivity::class.java),
                enabled,
            )
        },
        title = { Text(stringResource(Res.string.enable_tasker)) },
        icon = {
            MaskedIcon(
                Res.drawable.bolt,
                color = IconMaskColors.IconLavender,
                shape = IconMaskShapes.risk(),
            )
        },
        summary = { Text(stringResource(Res.string.enable_tasker_summary)) },
    )
}

@Composable
internal actual fun HideLauncherIconPreference() {
    val value by DataStore.hideLauncherIcon.collectAsStateWithLifecycle()
    var showConfirm by rememberSaveable { mutableStateOf(false) }

    fun setHidden(hidden: Boolean) {
        DataStore.hideLauncherIcon.setBlocking(hidden)
        LauncherIcon.hidden = hidden
    }

    SwitchPreference(
        value = value,
        onValueChange = { hide ->
            if (hide) {
                showConfirm = true
            } else {
                setHidden(false)
            }
        },
        title = { Text(stringResource(Res.string.hide_launcher_icon)) },
        icon = {
            MaskedIcon(
                Res.drawable.visibility_off,
                color = IconMaskColors.IconLavender,
            )
        },
        summary = {
            Text(stringResource(Res.string.hide_launcher_icon_summary, LauncherIcon.DIAL_CODE))
        },
    )

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(Res.string.hide_launcher_icon)) },
            text = {
                Text(stringResource(Res.string.hide_launcher_icon_confirm, LauncherIcon.DIAL_CODE))
            },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    showConfirm = false
                    setHidden(true)
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    showConfirm = false
                }
            },
        )
    }
}
