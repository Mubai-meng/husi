package com.fr.husi.core

import com.fr.husi.bg.ServiceAlert
import com.fr.husi.bg.ServiceState
import com.fr.husi.bg.SpeedStats

sealed interface ServiceEvent {
    data class State(val state: ServiceState, val profileName: String?) : ServiceEvent
    data class Speed(val stats: SpeedStats) : ServiceEvent
    data class Alert(val alert: ServiceAlert) : ServiceEvent
}
