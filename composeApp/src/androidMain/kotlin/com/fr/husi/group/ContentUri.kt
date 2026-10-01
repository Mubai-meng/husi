package com.fr.husi.group

import androidx.core.net.toUri
import com.fr.husi.repository.resolveAndroidRepository

actual fun readContentUri(uri: String): String? {
    return resolveAndroidRepository().context.contentResolver.openInputStream(uri.toUri())
        ?.bufferedReader()
        ?.readText()
}
