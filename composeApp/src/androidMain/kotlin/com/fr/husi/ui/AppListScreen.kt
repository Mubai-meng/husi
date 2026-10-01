package com.fr.husi.ui

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.fr.husi.compose.material3.Icon
import com.fr.husi.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fr.husi.compose.BackHandler
import com.fr.husi.resources.Res
import com.fr.husi.resources.cleaning_services
import com.fr.husi.resources.clear_selections
import com.fr.husi.resources.fiber_smart_record
import com.fr.husi.resources.invert_selections
import com.fr.husi.resources.select_apps
import com.fr.husi.results.LocalResultEventBus
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.random.Random

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal actual fun AppListScreen(
    initialPackages: Set<String>,
    resultKey: String,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val resultBus = LocalResultEventBus.current
    val context = LocalContext.current
    val viewModel: AppListViewModel = viewModel {
        AppListViewModel(
            pm = context.packageManager,
            appPackageName = context.packageName,
            packages = initialPackages,
        )
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    fun saveAndExit() {
        resultBus.sendResult<Set<String>>(resultKey, viewModel.allPackages().toSet())
        onBack()
    }
    BackHandler(enabled = true) {
        saveAndExit()
    }

    AppListScaffold(
        viewModel = viewModel,
        title = { Text(stringResource(Res.string.select_apps)) },
        isLoading = uiState.isLoading,
        apps = uiState.apps,
        filteredApps = uiState.filteredApps,
        snackbarMessage = uiState.snackbarMessage,
        onNavigationClick = ::saveAndExit,
        modifier = modifier,
        dropdownMenuItems = { onDismiss ->
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.invert_selections)) },
                onClick = {
                    viewModel.invertSections()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.fiber_smart_record),
                        contentDescription = null,
                    )
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.clear_selections)) },
                onClick = {
                    viewModel.clearSections()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.cleaning_services),
                        contentDescription = null,
                    )
                },
            )
        },
    )
}

@Preview
@Composable
private fun PreviewAppListScreen() {
    PreviewContainer {
        AppListScreen(
            initialPackages = emptySet(),
            resultKey = "app-list-${Random.nextLong()}",
            onBack = {},
        )
    }
}
