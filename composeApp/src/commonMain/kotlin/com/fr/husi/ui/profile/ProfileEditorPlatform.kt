package com.fr.husi.ui.profile

import androidx.compose.runtime.Composable
import com.fr.husi.database.ProxyEntity

@Composable
internal expect fun platformSupportShortcut(): Boolean

@Composable
internal expect fun ShortcutMenuItem(entity: ProxyEntity, postClick: () -> Unit)