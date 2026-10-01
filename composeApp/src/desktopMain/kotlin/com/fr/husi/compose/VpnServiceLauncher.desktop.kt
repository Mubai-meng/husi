package com.fr.husi.compose

import androidx.compose.runtime.Composable
import com.fr.husi.repository.resolveRepository

@Composable
actual fun rememberVpnServiceLauncher(onFailed: () -> Unit): () -> Unit {
    return { resolveRepository().startService() }
}
