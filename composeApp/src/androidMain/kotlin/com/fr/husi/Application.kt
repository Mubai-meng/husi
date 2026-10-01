package com.fr.husi

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.StrictMode
import com.fr.husi.bg.AppChangeReceiver
import com.fr.husi.bg.DefaultNetworkMonitor
import com.fr.husi.bg.RouteAssetUpdater
import com.fr.husi.bg.SubscriptionUpdater
import com.fr.husi.bg.migrateCustomRouteAssets
import com.fr.husi.compose.clearClipboardImageCache
import com.fr.husi.database.DataStore
import com.fr.husi.database.SagerDatabase
import com.fr.husi.di.initHusiKoin
import com.fr.husi.ktx.invariantDirectoryPathString
import com.fr.husi.ktx.Logs
import com.fr.husi.ktx.runOnDefaultDispatcher
import com.fr.husi.ktx.runOnIoDispatcher
import com.fr.husi.libcore.Libcore
import com.fr.husi.libcore.loadCA
import com.fr.husi.repository.AndroidRepository
import com.fr.husi.repository.SagerRepository
import com.fr.husi.utils.CrashHandler
import com.fr.husi.utils.PackageCache
import com.fr.husi.utils.copyBundledRuleSetAssetsIfNeeded
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

        Seq.setContext(this)
        runOnDefaultDispatcher {
            repository.updateNotificationChannels()
        }

        // init core
        externalAssets.mkdirs()
        val rulesProvider = DataStore.rulesProvider.getBlocking()
        val isExpert = DataStore.isExpert.getBlocking()
        if (isBgProcess) {
            // :bg 进程必须在启动服务前完成规则资源准备，保持主线程同步。
            runBlocking {
                if (rulesProvider == RuleProvider.OFFICIAL) {
                    copyBundledRuleSetAssetsIfNeeded()
                }
                migrateCustomRouteAssets(
                    externalAssets,
                    SagerDatabase.assetDao.getAll().first().map { it.name },
                )
            }
        } else {
            // 主进程：打开 Room 库 + 资源迁移与首帧无关，同步跑在主线程
            // 会显著推迟首帧（冷启动白屏），改为后台执行。一次性迁移，
            // 完成前极短窗口内路由资源可能未就位，可接受。
            runOnDefaultDispatcher {
                runCatching {
                    migrateCustomRouteAssets(
                        externalAssets,
                        SagerDatabase.assetDao.getAll().first().map { it.name },
                    )
                }.onFailure { Logs.w(it) }
            }
        }
        Libcore.initCore(
            isBgProcess,
            !isBgProcess,
            cacheDir.invariantDirectoryPathString(),
            filesDir.invariantDirectoryPathString(),
            externalAssets.invariantDirectoryPathString(),
            DataStore.logMaxLine.getBlocking(),
            DataStore.logLevel.getBlocking(),
            rulesProvider == 0,
            isExpert,
        )
        if (isBgProcess) {
            loadCA(DataStore.certProvider.getBlocking())
        } else {
            // 主进程：证书加载（解析 100+ 系统证书）与首帧无关，后台执行。
            // TLS 由 :bg 进程承担；主进程仅在用户手动触发订阅更新/测速时
            // 需要，届时早已完成。
            runOnDefaultDispatcher {
                runCatching { loadCA(DataStore.certProvider.getBlocking()) }
                    .onFailure { Logs.w(it) }
            }
        }

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

        if (isExpert) StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build(),
        )
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
