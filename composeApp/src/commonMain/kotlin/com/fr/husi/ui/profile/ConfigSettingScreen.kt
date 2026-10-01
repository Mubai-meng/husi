package com.fr.husi.ui.profile

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fr.husi.compose.BackHandler
import com.fr.husi.compose.BoxedVerticalScrollbar
import com.fr.husi.compose.CapsuleTopBar
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.IconMaskShapes
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.Preference
import com.fr.husi.compose.ProvidePreferenceLocals
import com.fr.husi.compose.SimpleIconButton
import com.fr.husi.compose.SwitchPreference
import com.fr.husi.compose.TextButton
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.material3.Icon
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.fmt.config.ConfigBean
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.resources.Res
import com.fr.husi.resources.apply
import com.fr.husi.resources.cancel
import com.fr.husi.resources.close
import com.fr.husi.resources.custom_config
import com.fr.husi.resources.delete
import com.fr.husi.resources.delete_confirm_prompt
import com.fr.husi.resources.done
import com.fr.husi.resources.emoji_symbols
import com.fr.husi.resources.is_outbound_only
import com.fr.husi.resources.layers
import com.fr.husi.resources.lines
import com.fr.husi.resources.no
import com.fr.husi.resources.not_set
import com.fr.husi.resources.ok
import com.fr.husi.resources.outbond
import com.fr.husi.resources.profile_name
import com.fr.husi.resources.question_mark
import com.fr.husi.resources.unsaved_changes_prompt
import com.fr.husi.resources.warning
import com.fr.husi.results.ResultEffect
import com.fr.husi.ui.jsoneditor.ConfigSchema
import com.fr.husi.ui.NavRoutes
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.random.Random

@Composable
fun ConfigSettingScreen(
    profileId: Long,
    isSubscription: Boolean,
    onResult: (updated: Boolean) -> Unit,
    onOpenConfigEditor: (NavRoutes.ConfigEditor) -> Unit,
) {
    val viewModel: ConfigSettingsViewModel =
        profileEditorViewModel(profileId = profileId, isSubscription = isSubscription) {
            ConfigSettingsViewModel()
        }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()

    var showBackAlert by remember { mutableStateOf(false) }
    var showDeleteAlert by remember { mutableStateOf(false) }

    BackHandler(enabled = isDirty) { showBackAlert = true }

    val resultKeyNumber = rememberSaveable {
        viewModel.editingId.takeIf { it >= 0L } ?: Random.nextLong()
    }
    val resultKey = "config-settings-result-$resultKeyNumber"
    ResultEffect<String?>(resultKey = resultKey) { result ->
        if (result == null) return@ResultEffect
        viewModel.setConfigForResult(result)
    }

    val config =
        when (uiState.type) {
            ConfigBean.TYPE_CONFIG -> uiState.customConfig
            ConfigBean.TYPE_OUTBOUND -> uiState.customOutbound
            else -> error("impossible")
        }

    val windowInsets = WindowInsets.safeDrawing
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CapsuleTopBar(
                hazeState = null,
                navigationIcon = {
                    SimpleIconButton(
                        imageVector = vectorResource(Res.drawable.close),
                        contentDescription = stringResource(Res.string.close),
                    ) {
                        if (isDirty) {
                            showBackAlert = true
                        } else {
                            onResult(false)
                        }
                    }
                },
                title = { Text(stringResource(Res.string.custom_config)) },
                actions = {
                    if (!viewModel.isNew) {
                        CapsuleActionButton {
                            SimpleIconButton(
                                imageVector = vectorResource(Res.drawable.delete),
                                contentDescription = stringResource(Res.string.delete),
                                onClick = { showDeleteAlert = true },
                            )
                        }
                    }
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.done),
                            contentDescription = stringResource(Res.string.apply),
                        ) {
                            viewModel.save()
                            onResult(true)
                        }
                    }
                },
                windowInsets =
                    windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
    ) { innerPadding ->
        val listState = rememberLazyListState()
        ProvidePreferenceLocals {
            Row(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(innerPadding),
                ) {
                    preferenceGroup(key = "settings") {
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
                        SwitchPreference(
                            value = uiState.type == ConfigBean.TYPE_OUTBOUND,
                            onValueChange = {
                                viewModel.setType(
                                    if (it) {
                                        ConfigBean.TYPE_OUTBOUND
                                    } else {
                                        ConfigBean.TYPE_CONFIG
                                    },
                                )
                            },
                            title = { Text(stringResource(Res.string.is_outbound_only)) },
                            icon = {
                                MaskedIcon(
                                    resource = Res.drawable.outbond,
                                    color = IconMaskColors.IconLightOrange,
                                    shape = IconMaskShapes.credential(),
                                )
                            },
                        )
                        Preference(
                            title = { Text(stringResource(Res.string.custom_config)) },
                            icon = {
                                MaskedIcon(
                                    resource = Res.drawable.layers,
                                    color = IconMaskColors.IconLightYellow,
                                )
                            },
                            summary = {
                                val text =
                                    if (config.isBlank()) {
                                        stringResource(Res.string.not_set)
                                    } else {
                                        val count = config.count { it == '\n' } + 1
                                        pluralStringResource(Res.plurals.lines, count, count)
                                    }
                                Text(text)
                            },
                            onClick = {
                                onOpenConfigEditor(
                                    NavRoutes.ConfigEditor(
                                        initialText = config,
                                        resultKey = resultKey,
                                        schema = when (uiState.type) {
                                            ConfigBean.TYPE_CONFIG -> ConfigSchema.CONFIG
                                            ConfigBean.TYPE_OUTBOUND -> ConfigSchema.OUTBOUND
                                            else -> error("impossible")
                                        },
                                    ),
                                )
                            },
                        )
                    }
                    item("bottom_padding") {
                        Spacer(
                            modifier =
                                Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars),
                        )
                    }
                }

                BoxedVerticalScrollbar(
                    modifier = Modifier.fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(scrollState = listState),
                    style = defaultMaterialScrollbarStyle().copy(thickness = 12.dp),
                )
            }
        }
    }

    if (showBackAlert) {
        AlertDialog(
            onDismissRequest = { showBackAlert = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    viewModel.save()
                    onResult(true)
                }
            },
            dismissButton = { TextButton(stringResource(Res.string.no)) { onResult(false) } },
            icon = { Icon(vectorResource(Res.drawable.question_mark), null) },
            title = { Text(stringResource(Res.string.unsaved_changes_prompt)) },
        )
    }

    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            confirmButton = {
                TextButton(stringResource(Res.string.ok)) {
                    viewModel.delete()
                    onResult(true)
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.cancel)) { showDeleteAlert = false }
            },
            icon = { Icon(vectorResource(Res.drawable.warning), null) },
            title = { Text(stringResource(Res.string.delete_confirm_prompt)) },
        )
    }
}
