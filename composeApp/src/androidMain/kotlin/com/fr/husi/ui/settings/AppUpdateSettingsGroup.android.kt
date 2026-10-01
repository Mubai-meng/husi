package com.fr.husi.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fr.husi.bg.AppUpdateChecker
import com.fr.husi.bg.AppUpdateInfo
import com.fr.husi.bg.AppUpdateInstaller
import com.fr.husi.bg.GITHUB_NEW_TOKEN_URL
import com.fr.husi.bg.ShizukuAvailability
import com.fr.husi.bg.isObtainiumInstalled
import com.fr.husi.bg.obtainiumAddAppLink
import com.fr.husi.bg.todayEpochDay
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.IconMaskShapes
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.PasswordPreference
import com.fr.husi.compose.Preference
import com.fr.husi.compose.SwitchPreference
import com.fr.husi.compose.TextButton
import com.fr.husi.compose.collectAsStateWithLifecycle
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.database.DataStore
import com.fr.husi.ktx.Logs
import com.fr.husi.ktx.readableMessage
import com.fr.husi.permission.AppPermission
import com.fr.husi.permission.LocalPermissionPlatform
import com.fr.husi.resources.Res
import com.fr.husi.resources.android
import com.fr.husi.resources.app_update_auto_check
import com.fr.husi.resources.app_update_auto_check_sum
import com.fr.husi.resources.app_update_check_now
import com.fr.husi.resources.app_update_checking
import com.fr.husi.resources.app_update_install_permission
import com.fr.husi.resources.app_update_install_permission_sum
import com.fr.husi.resources.app_update_last_check
import com.fr.husi.resources.app_update_never_checked
import com.fr.husi.resources.app_update_obtainium
import com.fr.husi.resources.app_update_obtainium_sum
import com.fr.husi.resources.app_update_only_when_connected
import com.fr.husi.resources.app_update_only_when_connected_sum
import com.fr.husi.resources.app_update_pre_release
import com.fr.husi.resources.app_update_pre_release_sum
import com.fr.husi.resources.app_update_shizuku_denied
import com.fr.husi.resources.app_update_shizuku_not_installed
import com.fr.husi.resources.app_update_shizuku_not_running
import com.fr.husi.resources.app_update_shizuku_unsupported
import com.fr.husi.resources.app_update_token
import com.fr.husi.resources.app_update_token_create
import com.fr.husi.resources.app_update_up_to_date
import com.fr.husi.resources.app_update_use_shizuku
import com.fr.husi.resources.app_update_use_shizuku_sum
import com.fr.husi.resources.apps
import com.fr.husi.resources.cached
import com.fr.husi.resources.fiber_smart_record
import com.fr.husi.resources.password
import com.fr.husi.resources.public_icon
import com.fr.husi.resources.security
import com.fr.husi.resources.update
import com.fr.husi.ui.AppUpdateDialog
import com.fr.husi.ui.LocalSnackbarEmitter
import com.fr.husi.ui.StringOrRes
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

internal fun LazyListScope.appUpdateSettings() {
    preferenceGroup {
        AppUpdateSettingsGroup()
    }
    if (!isObtainiumInstalled()) return
    preferenceGroup {
        ObtainiumPreference()
    }
}

@Composable
private fun AppUpdateSettingsGroup() {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarEmitter.current
    val uriHandler = LocalUriHandler.current

    val autoCheck by DataStore.appUpdateAutoCheck.collectAsStateWithLifecycle()
    val preRelease by DataStore.appUpdatePreRelease.collectAsStateWithLifecycle()
    val onlyWhenConnected by DataStore.appUpdateOnlyWhenConnected.collectAsStateWithLifecycle()
    val token by DataStore.appUpdateToken.collectAsStateWithLifecycle()
    val useShizuku by DataStore.appUpdateUseShizuku.collectAsStateWithLifecycle()
    val lastCheckEpochDay by DataStore.appUpdateLastCheckEpochDay.collectAsStateWithLifecycle()

    val permission = LocalPermissionPlatform.current
    var installPermissionGranted by remember {
        mutableStateOf(permission.hasPermission(AppPermission.InstallPackages))
    }

    val shizuku by AppUpdateInstaller.shizukuAvailability.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        AppUpdateInstaller.refreshShizukuAvailability()
        installPermissionGranted = permission.hasPermission(AppPermission.InstallPackages)
        onPauseOrDispose {}
    }

    var checking by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<AppUpdateInfo?>(null) }

    fun checkNow() {
        checking = true
        scope.launch {
            try {
                DataStore.appUpdateLastCheckEpochDay.set(todayEpochDay())
                val update = AppUpdateChecker().check()
                if (update == null) {
                    snackbar.show(StringOrRes.Res(Res.string.app_update_up_to_date))
                } else {
                    found = update
                }
            } catch (e: Exception) {
                Logs.e("check app update", e)
                snackbar.show(StringOrRes.Direct(e.readableMessage))
            } finally {
                checking = false
            }
        }
    }

    SwitchPreference(
        value = autoCheck,
        onValueChange = { DataStore.appUpdateAutoCheck.setBlocking(it) },
        title = { Text(stringResource(Res.string.app_update_auto_check)) },
        icon = { MaskedIcon(Res.drawable.update, color = IconMaskColors.IconLightGreen) },
        summary = { Text(stringResource(Res.string.app_update_auto_check_sum)) },
    )

    AnimatedVisibility(visible = autoCheck) {
        SwitchPreference(
            value = onlyWhenConnected,
            onValueChange = { DataStore.appUpdateOnlyWhenConnected.setBlocking(it) },
            title = { Text(stringResource(Res.string.app_update_only_when_connected)) },
            icon = { MaskedIcon(Res.drawable.public_icon, color = IconMaskColors.IconLightBlue) },
            summary = { Text(stringResource(Res.string.app_update_only_when_connected_sum)) },
        )
    }

    SwitchPreference(
        value = preRelease,
        onValueChange = { DataStore.appUpdatePreRelease.setBlocking(it) },
        title = { Text(stringResource(Res.string.app_update_pre_release)) },
        icon = {
            MaskedIcon(
                Res.drawable.fiber_smart_record,
                color = IconMaskColors.IconLightYellow,
                shape = IconMaskShapes.risk(),
            )
        },
        summary = { Text(stringResource(Res.string.app_update_pre_release_sum)) },
    )

    PasswordPreference(
        value = token,
        onValueChange = { DataStore.appUpdateToken.setBlocking(it) },
        title = { Text(stringResource(Res.string.app_update_token)) },
        icon = {
            MaskedIcon(
                Res.drawable.password,
                color = IconMaskColors.IconCoral,
                shape = IconMaskShapes.credential(),
            )
        },
        dialogFooter = {
            TextButton(
                text = stringResource(Res.string.app_update_token_create),
                onClick = { uriHandler.openUri(GITHUB_NEW_TOKEN_URL) },
            )
        },
    )

    if (permission.canRequestPermission(AppPermission.InstallPackages)) {
        Preference(
            title = { Text(stringResource(Res.string.app_update_install_permission)) },
            icon = {
                MaskedIcon(
                    Res.drawable.android,
                    color = IconMaskColors.IconLightGreen,
                    shape = IconMaskShapes.risk(),
                )
            },
            summary = if (installPermissionGranted) {
                null
            } else {
                { Text(stringResource(Res.string.app_update_install_permission_sum)) }
            },
            onClick = {
                permission.requestPermission(AppPermission.InstallPackages) { granted ->
                    installPermissionGranted = granted
                }
            },
        )
    }

    SwitchPreference(
        value = useShizuku,
        onValueChange = { enable ->
            if (!enable) {
                DataStore.appUpdateUseShizuku.setBlocking(false)
                return@SwitchPreference
            }
            scope.launch {
                val granted =
                    AppUpdateInstaller.requestShizukuPermission() == ShizukuAvailability.Granted
                DataStore.appUpdateUseShizuku.setBlocking(granted)
            }
        },
        title = { Text(stringResource(Res.string.app_update_use_shizuku)) },
        enabled = shizuku == ShizukuAvailability.Granted ||
            shizuku == ShizukuAvailability.NotGranted,
        icon = {
            MaskedIcon(
                Res.drawable.security,
                color = IconMaskColors.IconLavender,
                shape = IconMaskShapes.risk(),
            )
        },
        summary = { Text(stringResource(shizukuSummary(shizuku))) },
    )

    Preference(
        title = { Text(stringResource(Res.string.app_update_check_now)) },
        enabled = !checking,
        icon = { MaskedIcon(Res.drawable.cached, color = IconMaskColors.IconCyan) },
        summary = {
            Text(
                if (checking) {
                    stringResource(Res.string.app_update_checking)
                } else {
                    lastCheckSummary(lastCheckEpochDay)
                },
            )
        },
        onClick = { checkNow() },
    )

    found?.let { info ->
        AppUpdateDialog(
            info = info,
            onDismissRequest = { found = null },
            onSkipVersion = {
                DataStore.appUpdateSkippedVersion.setBlocking(info.version)
                found = null
            },
        )
    }
}

@Composable
private fun ObtainiumPreference() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val preRelease by DataStore.appUpdatePreRelease.collectAsStateWithLifecycle()

    Preference(
        title = { Text(stringResource(Res.string.app_update_obtainium)) },
        icon = { MaskedIcon(Res.drawable.apps, color = IconMaskColors.IconLightOrange) },
        summary = { Text(stringResource(Res.string.app_update_obtainium_sum)) },
        onClick = { uriHandler.openUri(obtainiumAddAppLink(context.packageName, preRelease)) },
    )
}

@Composable
private fun lastCheckSummary(lastCheckEpochDay: Long): String {
    if (lastCheckEpochDay <= 0L) return stringResource(Res.string.app_update_never_checked)
    val date = remember(lastCheckEpochDay) { LocalDate.fromEpochDays(lastCheckEpochDay) }
    return stringResource(Res.string.app_update_last_check, date.toString())
}

private fun shizukuSummary(availability: ShizukuAvailability) = when (availability) {
    ShizukuAvailability.NotInstalled -> Res.string.app_update_shizuku_not_installed
    ShizukuAvailability.NotRunning -> Res.string.app_update_shizuku_not_running
    ShizukuAvailability.Unsupported -> Res.string.app_update_shizuku_unsupported
    ShizukuAvailability.NotGranted -> Res.string.app_update_shizuku_denied
    ShizukuAvailability.Granted -> Res.string.app_update_use_shizuku_sum
}
