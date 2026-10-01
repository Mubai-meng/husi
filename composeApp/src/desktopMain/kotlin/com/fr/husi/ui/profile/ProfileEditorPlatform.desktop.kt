package com.fr.husi.ui.profile

import androidx.compose.runtime.Composable
import com.fr.husi.database.ProxyEntity

@Composable
internal actual fun platformSupportShortcut(): Boolean = false

@Composable
internal actual fun ShortcutMenuItem(entity: ProxyEntity, postClick: () -> Unit) {
}
