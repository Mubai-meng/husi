package com.fr.husi.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.fr.husi.compose.BackHandler
import com.fr.husi.compose.BoxedVerticalScrollbar
import com.fr.husi.compose.CapsuleTopBar
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.LinkOrContentTextField
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.ProvidePreferenceLocals
import com.fr.husi.compose.SimpleIconButton
import com.fr.husi.compose.TextButton
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.UIntegerTextField
import com.fr.husi.compose.ValidatedTextField
import com.fr.husi.compose.fadingEdge
import com.fr.husi.compose.material3.Icon
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.compose.withNavigation
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.resources.Res
import com.fr.husi.resources.apply
import com.fr.husi.resources.assets_settings
import com.fr.husi.resources.auto_update_off
import com.fr.husi.resources.auto_update_on
import com.fr.husi.resources.cancel
import com.fr.husi.resources.close
import com.fr.husi.resources.delete
import com.fr.husi.resources.delete_confirm_prompt
import com.fr.husi.resources.done
import com.fr.husi.resources.emoji_symbols
import com.fr.husi.resources.error_title
import com.fr.husi.resources.link
import com.fr.husi.resources.no
import com.fr.husi.resources.ok
import com.fr.husi.resources.question_mark
import com.fr.husi.resources.route_asset_auto_update_delay
import com.fr.husi.resources.route_asset_name
import com.fr.husi.resources.timer
import com.fr.husi.resources.unsaved_changes_prompt
import com.fr.husi.resources.url
import com.fr.husi.resources.warning
import com.fr.husi.resources.warning_amber
import com.fr.husi.results.LocalResultEventBus
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Serializable
sealed interface AssetEditResult {

    @Serializable
    data object Saved : AssetEditResult

    @Serializable
    data class Created(
        val assetName: String,
    ) : AssetEditResult

    @Serializable
    data class ShouldUpdate(
        val assetName: String,
    ) : AssetEditResult

    @Serializable
    data class Deleted(
        val assetName: String,
    ) : AssetEditResult

    @Serializable
    data object Canceled : AssetEditResult

}

@Composable
internal fun AssetEditScreen(
    assetName: String,
    resultKey: String,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AssetEditViewModel = viewModel { AssetEditViewModel(assetName) },
) {
    val resultBus = LocalResultEventBus.current

    val isDirty by viewModel.isDirty.collectAsState()
    var showBackAlert by remember { mutableStateOf(false) }
    BackHandler(enabled = true) {
        if (isDirty) {
            showBackAlert = true
        } else {
            resultBus.sendResult<AssetEditResult>(resultKey, AssetEditResult.Canceled)
            onBack()
        }
    }

    val uiState by viewModel.uiState.collectAsState()

    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var illegalNameMessage by remember { mutableStateOf<StringOrRes?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun saveAndExit() {
        viewModel.save()
        val currentName = viewModel.uiState.value.name
        val result = when {
            viewModel.isNew -> AssetEditResult.Created(currentName)
            viewModel.shouldUpdateFromInternet -> AssetEditResult.ShouldUpdate(currentName)
            else -> AssetEditResult.Saved
        }
        resultBus.sendResult(resultKey, result)
        onBack()
    }

    val hazeState = rememberHazeState()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CapsuleTopBar(
                hazeState = hazeState,
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.close),
                    ) {
                        if (isDirty) {
                            showBackAlert = true
                        } else {
                            resultBus.sendResult<AssetEditResult>(
                                resultKey,
                                AssetEditResult.Canceled,
                            )
                            onBack()
                        }
                    }
                },
                title = { Text(stringResource(Res.string.assets_settings)) },
                actions = {
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.delete),
                            contentDescription = stringResource(Res.string.delete),
                        ) {
                            val editingAssetName = viewModel.editingName
                            if (editingAssetName.isEmpty()) {
                                resultBus.sendResult<AssetEditResult>(
                                    resultKey,
                                    AssetEditResult.Canceled,
                                )
                                onBack()
                            } else {
                                showDeleteConfirm = true
                            }
                        }
                    }
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.done),
                            contentDescription = stringResource(Res.string.apply),
                        ) {
                            saveAndExit()
                        }
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val listState = rememberLazyListState()
        ProvidePreferenceLocals {
            val contentPadding = innerPadding.withNavigation()
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .fadingEdge(
                            scrollableState = listState,
                            fadeStart = true,
                            fadeEnd = true,
                        ),
                    contentPadding = contentPadding,
                ) {
                    preferenceGroup(key = "settings") {
                        TextFieldPreference(
                            value = uiState.name,
                            onValueChange = { viewModel.setName(it) },
                            title = { Text(stringResource(Res.string.route_asset_name)) },
                            textToValue = { it },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.emoji_symbols,
                                    color = IconMaskColors.IconCyan,
                                )
                            },
                            summary = { Text(contentOrUnset(uiState.name)) },
                            valueToText = { it },
                            textField = { value, onValueChange, onOk ->
                                ValidatedTextField(
                                    value = value,
                                    onValueChange = onValueChange,
                                    onOk = onOk,
                                    validator = { name ->
                                        viewModel.validate(name)?.let { getStringOrRes(it) }
                                    },
                                )
                            },
                        )
                        TextFieldPreference(
                            value = uiState.link,
                            onValueChange = { viewModel.setLink(it) },
                            title = { Text(stringResource(Res.string.url)) },
                            textToValue = { it },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.link,
                                    color = IconMaskColors.IconLightBlue,
                                )
                            },
                            summary = { Text(contentOrUnset(uiState.link)) },
                            valueToText = { it },
                            textField = { value, onValueChange, onOk ->
                                LinkOrContentTextField(value, onValueChange, onOk)
                            },
                        )
                        TextFieldPreference(
                            value = uiState.autoUpdateDelay,
                            onValueChange = { viewModel.setAutoUpdateDelay(it) },
                            title = {
                                Text(stringResource(Res.string.route_asset_auto_update_delay))
                            },
                            textToValue = { it.toIntOrNull() ?: 0 },
                            icon = {
                                MaskedIcon(
                                    Res.drawable.timer,
                                    color = IconMaskColors.IconLightOrange,
                                )
                            },
                            summary = {
                                Text(
                                    if (uiState.autoUpdateDelay > 0) {
                                        stringResource(Res.string.auto_update_on, uiState.autoUpdateDelay)
                                    } else {
                                        stringResource(Res.string.auto_update_off)
                                    },
                                )
                            },
                            valueToText = { it.toString() },
                            textField = { value, onValueChange, onOk ->
                                UIntegerTextField(value, onValueChange, onOk)
                            },
                        )
                    }

                    item("bottom_padding") {
                        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }

                BoxedVerticalScrollbar(
                    modifier = Modifier
                        .padding(contentPadding)
                        .fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    style = defaultMaterialScrollbarStyle().copy(
                        thickness = 12.dp,
                    ),
                )
            }
        }
    }

    if (showBackAlert) {
        AlertDialog(
            onDismissRequest = { showBackAlert = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    coroutineScope.launch {
                        viewModel.validate(viewModel.uiState.value.name)?.let {
                            illegalNameMessage = it
                            showBackAlert = false
                            return@launch
                        }
                        saveAndExit()
                    }
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.no)) {
                    resultBus.sendResult<AssetEditResult>(resultKey, AssetEditResult.Canceled)
                    onBack()
                }
            },
            icon = { Icon(vectorResource(Res.drawable.question_mark), null) },
            title = { Text(stringResource(Res.string.unsaved_changes_prompt)) },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    resultBus.sendResult<AssetEditResult>(
                        resultKey,
                        AssetEditResult.Deleted(viewModel.editingName),
                    )
                    onBack()
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) {
                    resultBus.sendResult<AssetEditResult>(resultKey, AssetEditResult.Canceled)
                    onBack()
                }
            },
            icon = { Icon(vectorResource(Res.drawable.warning), null) },
            title = { Text(stringResource(Res.string.delete_confirm_prompt)) },
        )
    }

    illegalNameMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { illegalNameMessage = null },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    illegalNameMessage = null
                }
            },
            icon = { Icon(vectorResource(Res.drawable.warning_amber), null) },
            title = { Text(stringResource(Res.string.error_title)) },
            text = { Text(stringOrRes(message)) },
        )
    }
}
