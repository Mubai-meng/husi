package com.fr.husi.keyevent

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import com.fr.husi.platform.PlatformInfo

actual val KeyEvent.isTypeControlPressed: Boolean
    get() = if (PlatformInfo.isMacOs) isMetaPressed else isCtrlPressed
