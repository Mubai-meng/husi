package com.fr.husi

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.StrictMode
import com.fr.husi.Key
import com.fr.husi.bg.AppChangeReceiver
import com.fr.husi.bg.DefaultNetworkMonitor
import com.fr.husi.bg.RouteAssetUpdater
import com.fr.husi.bg.SubscriptionUpdater
import com.fr.husi.bg.migrateCustomRouteAssets
import com.fr.husi.compose.clearClipboardImageCache
import com.fr.husi.database.DataStore
import com.fr.husi.database.SagerDatabase
import com.fr.husi.di.initHusiKoin
import com.fr.husi.ktx.Logs
import com.fr.husi.ktx.invariantDirectoryPathString
import com.fr.husi.ktx.runOnDefaultDispatcher
import com.fr.husi.ktx.runOnIoDispatcher
import com.fr.husi.libcore.Libcore
import com.fr.husi.libcore.loadCA
import com.fr.husi.repository.AndroidRepository
import com.fr.husi.repository.SagerRepository
import com.fr.husi.tailscale.TaildropSendService
import com.fr.husi.utils.CrashHandler
import com.fr.husi.utils.PackageCache
import com.fr.husi.utils.installBundledRuleSets
import go.Seq
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import androidx.work.Configuration as WorkConfiguration

class Application : Application(),
    WorkConfiguration.Provider {

    private lateinit var repository: AndroidRepository

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        repository = SagerRepository(this, isMainProcess, isBgProcess)
    }

    val externalAssets: File by lazy { getExternalFilesDir(null) ?: filesDir }
    private val appId by lazy { packageName }
    private val process by lazy { tryGetProcessName() }
    val isMainProcess get() = process == appId
    val isBgProcess get() = process.endsWith(":bg")

    override fun onCreate() {
        super.onCreate()
        initHusiKoin(repository)

        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)
        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)

        if (isMainProcess) runOnIoDispatcher {
            clearClipboardImageCache(cacheDir)
        }

        if (isMainProcess) TaildropSendService.observe(this)

        if (isMainProcess) runOnDefaultDispatcher {
            // The component state may drift from the preference, e.g. after a backup restore.
            val hidden = DataStore.hideLauncherIcon.get()
            if (LauncherIcon.hidden != hidden) LauncherIcon.hidden = hidden
        }

        if (isMainProcess || isBgProcess) {
            runOnDefaultDispatcher {
                PackageCache.register(this@Application)
            }
        }

        runOnDefaultDispatcher {
            repository.updateNotificationChannels()
        }

        externalAssets.mkdirs()
        val isDebug = runBlocking { DataStore.advancedFeature(Key.ADVANCED_DEBUG) } != null
        if (!isDebug) {
            Logs.openFile(externalAssets.resolve(LOG_FILE_NAME), DataStore.logLevel.getBlocking())
        }
        runBlocking {
            if (isBgProcess && DataStore.rulesProvider.get() == RuleProvider.OFFICIAL) {
                installBundledRuleSets()
            }
            migrateCustomRouteAssets(
                externalAssets,
                SagerDatabase.assetDao.getAll().first().map { it.name },
            )
        }
        if (isBgProcess) initCore(isDebug)

        if (isMainProcess) runOnDefaultDispatcher {
            runCatching {
                SubscriptionUpdater.reconfigureUpdater()
                RouteAssetUpdater.reconfigureUpdater()
            }
            registerReceiver(
                AppChangeReceiver(),
                IntentFilter().apply {
                    addAction(Intent.ACTION_PACKAGE_ADDED)
                    addDataScheme("package")
                },
            )
        }

        if (isBgProcess) {
            runBlocking {
                DefaultNetworkMonitor.start()
            }
            repository.boxService?.start()
        }

        if (isDebug) StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build(),
        )
    }

    private fun initCore(isDebug: Boolean) {
        Seq.setContext(this)
        Libcore.initCore(
            cacheDir.invariantDirectoryPathString(),
            filesDir.invariantDirectoryPathString(),
            externalAssets.invariantDirectoryPathString(),
            DataStore.logMaxLine.getBlocking(),
            DataStore.logLevel.getBlocking(),
            isDebug,
        )
        Logs.attachCore()
        loadCA(DataStore.certProvider.getBlocking())
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        runOnDefaultDispatcher {
            repository.updateNotificationChannels()
        }
    }

    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setDefaultProcessName(appId)
            .build()

    @SuppressLint("PrivateApi")
    private fun tryGetProcessName(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return getProcessName()

        // Using the same technique as Application.getProcessName() for older devices
        // Using reflection since ActivityThread is an internal API
        try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val methodName = "currentProcessName"
            val getProcessName = activityThread.getDeclaredMethod(methodName)
            return getProcessName.invoke(null) as String
        } catch (_: Exception) {
            return appId
        }
    }

}
