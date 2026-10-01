package com.fr.husi.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun AppListScreen(
    initialPackages: Set<String>,
    resultKey: String,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    error("AppListScreen is not supported on this platform")
}
