package com.fr.husi.ui.tools

import androidx.compose.runtime.Composable
import com.fr.husi.ui.NavRoutes

@Composable
internal expect fun PlatformNetworkTools(onOpenTool: (NavRoutes.ToolsPage) -> Unit)
