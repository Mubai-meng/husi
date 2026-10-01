package com.fr.husi.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.fr.husi.database.SagerDatabase
import com.fr.husi.fmt.PluginEntry
import com.fr.husi.ktx.Logs
import com.fr.husi.ktx.blankAsNull
import com.fr.husi.ktx.openFilePath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.enums.enumEntries

internal actual fun platformPluginsFlow(): Flow<List<PluginDisplay>> {
    val entries = enumEntries<PluginEntry>()
    return SagerDatabase.pluginDao.getAll().map { plugins ->
        val pluginMap = plugins.associateBy { it.pluginId }
        entries.mapNotNull { entry ->
            val record = pluginMap[entry.pluginId] ?: return@mapNotNull null
            val path = record.path.trim()
            if (path.isBlank()) return@mapNotNull null
            val version = runCatching { entry.getVersion(path) }
                .getOrElse {
                    Logs.w(it)
                    "unknown"
                }
            PluginDisplay(
                id = entry.pluginId,
                packageName = "",
                version = version,
                versionCode = 0L,
                provider = "Original",
                entry = entry,
                path = path,
            )
        }
    }
}

@Composable
internal actual fun rememberOpenPluginCard(): (PluginDisplay) -> Unit {
    return remember {
        { plugin ->
            plugin.path?.trim().blankAsNull()?.let(::openFilePath)
        }
    }
}
