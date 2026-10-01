package com.fr.husi.bg.proto

import com.fr.husi.bg.BaseService
import com.fr.husi.bg.ServiceEventPublisher
import com.fr.husi.core.CoreClient
import com.fr.husi.database.DataStore
import com.fr.husi.database.ProxyEntity
import com.fr.husi.fmt.ConfigBuildResult
import com.fr.husi.ktx.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext

class ProxyInstance(profile: ProxyEntity, var service: BaseService.Interface? = null) :
    BoxInstance(profile) {

    var displayProfileName = profile.displayNameForService()

    var trafficLooper: TrafficLooper? = null

    /** Owns the traffic looper, cancelled in [close] so nothing outlives this instance. */
    private val looperScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override suspend fun buildConfig(): ConfigBuildResult = super.buildConfig().also {
        Logs.d(it.configJson)
        if (DataStore.isExpert.get()) {
            Logs.d("trafficProfiles: " + it.metadata.trafficProfiles.toString())
        }
    }

    override suspend fun init(isVPN: Boolean) {
        super.init(isVPN)
        pluginConfigs.forEach { (_, plugin) ->
            val (_, content) = plugin
            Logs.d(content)
        }
    }

    override fun launch() {
        super.launch() // start box
        looperScope.launch {
            val data = service?.data ?: return@launch
            trafficLooper = TrafficLooper(
                coreClient = GlobalContext.get().get<CoreClient>(),
                metadata = metadata,
                scope = looperScope,
                onSpeedUpdate = { stats ->
                    ServiceEventPublisher.publishSpeed(stats)
                    data.notification.apply {
                        if (canPostSpeed()) onSpeed(stats)
                    }
                },
            )
            trafficLooper?.start()
        }
    }

    override fun close() {
        super.close()
        runBlocking {
            trafficLooper?.stop()
            trafficLooper = null
        }
        looperScope.cancel()
    }
}
