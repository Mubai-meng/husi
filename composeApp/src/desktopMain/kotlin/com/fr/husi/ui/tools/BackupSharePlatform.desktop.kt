package com.fr.husi.ui.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.io.File

@Composable
internal actual fun rememberShareBackupFile(): (File) -> Unit = remember { {} }
