package com.fr.husi.ui.profile

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.resources.Res
import com.fr.husi.resources.emoji_symbols
import com.fr.husi.resources.profile_config
import com.fr.husi.resources.profile_name
import com.fr.husi.ui.NavRoutes
import org.jetbrains.compose.resources.stringResource

@Composable
fun DirectSettingsScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: DirectSettingsViewModel =
        profileEditorViewModel(profileId = profileId, isSubscription = isSubscription) {
            DirectSettingsViewModel()
        }

    ProfileSettingsScreenScaffold(
        title = Res.string.profile_config,
        viewModel = viewModel,
        onResult = onResult,
        onOpenConfigEditor = onOpenConfigEditor,
    ) { uiState, _ ->
        directSettings(uiState as DirectUiState, viewModel)
    }
}

private fun LazyListScope.directSettings(
    uiState: DirectUiState,
    viewModel: DirectSettingsViewModel,
) {
    preferenceGroup {
        TextFieldPreference(
            value = uiState.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.profile_name)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.emoji_symbols,
                    color = IconMaskColors.IconCyan,
                )
            },
            summary = { Text(contentOrUnset(uiState.name)) },
            valueToText = { it },
        )
    }
}
