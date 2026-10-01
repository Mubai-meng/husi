package com.fr.husi.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.getValue
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.collectAsStateWithLifecycle
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.compose.material3.Text
import com.fr.husi.database.DataStore
import com.fr.husi.resources.Res
import com.fr.husi.resources.copyright
import com.fr.husi.resources.custom_plugin_prefix
import com.fr.husi.resources.custom_plugin_prefix_summary
import org.jetbrains.compose.resources.stringResource

internal actual fun LazyListScope.platformPluginPreferences(
    isExpert: Boolean,
    needRestart: () -> Unit,
) {
    if (!isExpert) return
    preferenceGroup {
        val value by DataStore.customPluginPrefix.collectAsStateWithLifecycle()
        TextFieldPreference(
            value = value,
            onValueChange = {
                DataStore.customPluginPrefix.setBlocking(it)
                needRestart()
            },
            title = { Text(stringResource(Res.string.custom_plugin_prefix)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.copyright,
                    color = IconMaskColors.IconCoral,
                )
            },
            summary = { Text(stringResource(Res.string.custom_plugin_prefix_summary)) },
            valueToText = { it },
        )
    }
}
