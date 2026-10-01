package com.fr.husi.ui.configuration

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.fr.husi.compose.DropdownMenuAction
import com.fr.husi.resources.Res
import com.fr.husi.resources.add_profile_methods_scan_qr_code
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun scannerMenuAction(onDismissMenu: () -> Unit): DropdownMenuAction? {
    val context = LocalContext.current
    return DropdownMenuAction(stringResource(Res.string.add_profile_methods_scan_qr_code)) {
        onDismissMenu()
        context.startActivity(Intent(context, ScannerActivity::class.java))
    }
}
