package com.fr.husi.ui.configuration

import androidx.compose.runtime.Composable
import com.fr.husi.compose.DropdownMenuAction

@Composable
internal expect fun scannerMenuAction(onDismissMenu: () -> Unit): DropdownMenuAction?
