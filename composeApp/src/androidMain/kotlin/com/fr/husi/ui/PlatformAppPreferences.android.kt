package com.fr.husi.ui

import androidx.compose.runtime.Composable
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.Preference
import com.fr.husi.compose.material3.Text
import com.fr.husi.resources.Res
import com.fr.husi.resources.apps
import com.fr.husi.resources.apps_message
import com.fr.husi.resources.legend_toggle
import com.fr.husi.resources.not_set
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal actual fun AppSelectPreference(
    packages: Set<String>,
    onSelectApps: (Set<String>) -> Unit,
) {
    Preference(
        title = { Text(stringResource(Res.string.apps)) },
        icon = {
            MaskedIcon(
                resource = Res.drawable.legend_toggle,
                color = IconMaskColors.IconLavender,
            )
        },
        summary = {
            val text = when (val size = packages.size) {
                0 -> stringResource(Res.string.not_set)
                in 1..5 -> packages.joinToString("\n")
                else -> pluralStringResource(Res.plurals.apps_message, size, size)
            }
            Text(text)
        },
        onClick = {
            onSelectApps(packages)
        },
    )
}
