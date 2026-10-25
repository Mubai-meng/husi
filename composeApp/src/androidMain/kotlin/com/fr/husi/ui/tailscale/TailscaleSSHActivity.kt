package com.fr.husi.ui.tailscale

import android.os.Bundle
import androidx.activity.compose.setContent
import com.fr.husi.bg.SagerConnection
import com.fr.husi.compose.theme.AppTheme
import com.fr.husi.tailscale.TailscaleSSHSessionManager
import com.fr.husi.ui.ComposeActivity
import org.koin.core.context.GlobalContext

class TailscaleSSHActivity : ComposeActivity() {

    private val serviceConnection = SagerConnection()
    private val manager: TailscaleSSHSessionManager = GlobalContext.get().get()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        serviceConnection.connect(applicationContext)
        setContent {
            AppTheme {
                TailscaleSSHTerminalScreen(onClose = ::finish)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceConnection.disconnect(applicationContext)
        if (isFinishing) manager.closeAll()
    }
}
