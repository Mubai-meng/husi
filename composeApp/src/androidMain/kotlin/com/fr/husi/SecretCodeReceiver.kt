package com.fr.husi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fr.husi.database.DataStore
import com.fr.husi.ktx.runOnDefaultDispatcher
import com.fr.husi.ktx.showToast
import com.fr.husi.repository.resolveRepository
import com.fr.husi.resources.Res
import com.fr.husi.resources.launcher_icon_restored

class SecretCodeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.data?.host != LauncherIcon.SECRET_CODE) return
        if (!LauncherIcon.hidden) return

        val pendingResult = goAsync()
        runOnDefaultDispatcher {
            try {
                LauncherIcon.hidden = false
                DataStore.hideLauncherIcon.set(false)
                showToast(resolveRepository().getString(Res.string.launcher_icon_restored), true)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
