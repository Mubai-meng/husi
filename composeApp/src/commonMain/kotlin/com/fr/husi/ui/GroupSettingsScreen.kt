package com.fr.husi.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.fr.husi.GroupOrder
import com.fr.husi.GroupType
import com.fr.husi.SubscriptionType
import com.fr.husi.compose.BackHandler
import com.fr.husi.compose.BoxedVerticalScrollbar
import com.fr.husi.compose.CapsuleTopBar
import com.fr.husi.compose.IconMaskColors
import com.fr.husi.compose.LinkOrContentTextField
import com.fr.husi.compose.ListPreference
import com.fr.husi.compose.MaskedIcon
import com.fr.husi.compose.PreferenceCategory
import com.fr.husi.compose.PreferenceType
import com.fr.husi.compose.ProvidePreferenceLocals
import com.fr.husi.compose.SimpleIconButton
import com.fr.husi.compose.SwitchPreference
import com.fr.husi.compose.TextButton
import com.fr.husi.compose.TextFieldPreference
import com.fr.husi.compose.UIntegerTextField
import com.fr.husi.compose.ValidatedTextField
import com.fr.husi.compose.fadingEdge
import com.fr.husi.compose.material3.Icon
import com.fr.husi.compose.material3.Text
import com.fr.husi.compose.preferenceGroup
import com.fr.husi.compose.withNavigation
import com.fr.husi.ktx.USER_AGENT
import com.fr.husi.ktx.blankAsNull
import com.fr.husi.ktx.contentOrUnset
import com.fr.husi.ktx.intListN
import com.fr.husi.ktx.readableMessage
import com.fr.husi.libcore.Libcore
import com.fr.husi.resources.Res
import com.fr.husi.resources.age_identity
import com.fr.husi.resources.apply
import com.fr.husi.resources.auto_update
import com.fr.husi.resources.auto_update_delay
import com.fr.husi.resources.auto_update_off
import com.fr.husi.resources.auto_update_on
import com.fr.husi.resources.close
import com.fr.husi.resources.content_copy
import com.fr.husi.resources.deduplication
import com.fr.husi.resources.deduplication_sum
import com.fr.husi.resources.delete
import com.fr.husi.resources.delete_group_prompt
import com.fr.husi.resources.delete_sweep
import com.fr.husi.resources.dns
import com.fr.husi.resources.done
import com.fr.husi.resources.emoji_symbols
import com.fr.husi.resources.filter_regex
import com.fr.husi.resources.flip_camera_android
import com.fr.husi.resources.force_resolve
import com.fr.husi.resources.force_resolve_sum
import com.fr.husi.resources.grid_3x3
import com.fr.husi.resources.group_basic
import com.fr.husi.resources.group_name
import com.fr.husi.resources.group_order
import com.fr.husi.resources.group_order_by_delay
import com.fr.husi.resources.group_order_by_name
import com.fr.husi.resources.group_order_origin
import com.fr.husi.resources.group_settings
import com.fr.husi.resources.group_subscription_link
import com.fr.husi.resources.group_type
import com.fr.husi.resources.layers
import com.fr.husi.resources.link
import com.fr.husi.resources.low_priority
import com.fr.husi.resources.nfc
import com.fr.husi.resources.no
import com.fr.husi.resources.no_thanks
import com.fr.husi.resources.ok
import com.fr.husi.resources.ooc_subscription_token
import com.fr.husi.resources.oocv1
import com.fr.husi.resources.outbound_dns
import com.fr.husi.resources.outbound_dns_sum
import com.fr.husi.resources.question_mark
import com.fr.husi.resources.raw
import com.fr.husi.resources.security
import com.fr.husi.resources.sip008
import com.fr.husi.resources.subscription
import com.fr.husi.resources.subscription_settings
import com.fr.husi.resources.subscription_type
import com.fr.husi.resources.unsaved_changes_prompt
import com.fr.husi.resources.update_settings
import com.fr.husi.resources.update_when_connected_only
import com.fr.husi.resources.update_when_connected_only_sum
import com.fr.husi.resources.user_agent
import com.fr.husi.resources.vpn_key
import io.github.oikvpqya.compose.fastscroller.material3.defaultMaterialScrollbarStyle
import io.github.oikvpqya.compose.fastscroller.rememberScrollbarAdapter
import me.zhanghai.compose.preference.ListPreferenceType
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource

@Composable
internal fun GroupSettingsScreen(
    groupId: Long,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupSettingsViewModel = viewModel { GroupSettingsViewModel(groupId) },
) {
    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    var showBackAlert by remember { mutableStateOf(false) }
    BackHandler(enabled = isDirty) {
        showBackAlert = true
    }
    var showDeleteAlert by remember { mutableStateOf(false) }

    val windowInsets = WindowInsets.safeDrawing
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val hazeState = rememberHazeState()
    val uiState by viewModel.uiState.collectAsState()

    fun saveAndExit() {
        viewModel.save()
        onBackPress()
    }

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
                        onBackPress()
                    }
                },
                title = { Text(stringResource(Res.string.group_settings)) },
                actions = {
                    CapsuleActionButton {
                        SimpleIconButton(
                            imageVector = vectorResource(Res.drawable.delete),
                            contentDescription = stringResource(Res.string.delete),
                        ) {
                            if (viewModel.isNew) {
                                onBackPress()
                            } else {
                                showDeleteAlert = true
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
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .fadingEdge(
                            scrollableState = listState,
                            fadeStart = true,
                            fadeEnd = true,
                        ),
                    contentPadding = contentPadding,
                ) {
                    groupSettings(
                        uiState = uiState,
                        viewModel = viewModel,
                    )
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
                    saveAndExit()
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.no)) {
                    onBackPress()
                }
            },
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
                    onBackPress()
                    showDeleteAlert = false
                }
            },
            dismissButton = {
                TextButton(stringResource(Res.string.no_thanks)) {
                    showDeleteAlert = false
                }
            },
            icon = { Icon(vectorResource(Res.drawable.question_mark), null) },
            title = { Text(stringResource(Res.string.delete_group_prompt)) },
        )
    }
}

private fun LazyListScope.groupSettings(
    uiState: GroupSettingsUiState,
    viewModel: GroupSettingsViewModel,
) {
    fun groupType(type: Int) = when (type) {
        GroupType.BASIC -> Res.string.group_basic
        GroupType.SUBSCRIPTION -> Res.string.subscription
        else -> error("impossible")
    }

    fun groupOrder(order: Int) = when (order) {
        GroupOrder.ORIGIN -> Res.string.group_order_origin
        GroupOrder.BY_NAME -> Res.string.group_order_by_name
        GroupOrder.BY_DELAY -> Res.string.group_order_by_delay
        else -> error("impossible")
    }

    item("category_basic", PreferenceType.CATEGORY) {
        PreferenceCategory(text = { Text(stringResource(Res.string.group_settings)) })
    }
    preferenceGroup {
        TextFieldPreference(
            value = uiState.name,
            onValueChange = { viewModel.setName(it) },
            title = { Text(stringResource(Res.string.group_name)) },
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
        ListPreference(
            value = uiState.type,
            onValueChange = { viewModel.setType(it) },
            values = intListN(2),
            title = { Text(stringResource(Res.string.group_type)) },
            icon = {
                MaskedIcon(
                    Res.drawable.layers,
                    color = IconMaskColors.IconLavender,
                )
            },
            summary = { Text(stringResource(groupType(uiState.type))) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(stringResource(groupType(it))) },
        )
        ListPreference(
            value = uiState.order,
            onValueChange = { viewModel.setOrder(it) },
            values = intListN(3),
            title = { Text(stringResource(Res.string.group_order)) },
            icon = {
                MaskedIcon(
                    Res.drawable.low_priority,
                    color = IconMaskColors.IconLightOrange,
                )
            },
            summary = { Text(stringResource(groupOrder(uiState.order))) },
            type = ListPreferenceType.DROPDOWN_MENU,
            valueToText = { AnnotatedString(stringResource(groupOrder(it))) },
        )
        TextFieldPreference(
            value = uiState.outboundDns,
            onValueChange = { viewModel.setOutboundDns(it) },
            title = { Text(stringResource(Res.string.outbound_dns)) },
            textToValue = { it },
            icon = {
                MaskedIcon(
                    Res.drawable.dns,
                    color = IconMaskColors.IconLightGreen,
                )
            },
            summary = {
                Text(
                    uiState.outboundDns.blankAsNull()
                        ?: stringResource(Res.string.outbound_dns_sum),
                )
            },
            valueToText = { it },
        )
    }

    if (uiState.type == GroupType.SUBSCRIPTION) {
        item("category_subscription", PreferenceType.CATEGORY) {
            PreferenceCategory(text = { Text(stringResource(Res.string.subscription_settings)) })
        }
        fun subType(type: Int) = when (type) {
            SubscriptionType.RAW -> Res.string.raw
            SubscriptionType.OOCv1 -> Res.string.oocv1
            SubscriptionType.SIP008 -> Res.string.sip008
            else -> error("impossible")
        }
        preferenceGroup {
            ListPreference(
                value = uiState.subscriptionType,
                onValueChange = { viewModel.setSubscriptionType(it) },
                values = intListN(3),
                title = { Text(stringResource(Res.string.subscription_type)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.nfc,
                        color = IconMaskColors.IconLightYellow,
                    )
                },
                summary = { Text(stringResource(subType(uiState.subscriptionType))) },
                type = ListPreferenceType.DROPDOWN_MENU,
                valueToText = { AnnotatedString(stringResource(subType(it))) },
            )
            TextFieldPreference(
                value = uiState.subscriptionLink,
                onValueChange = { viewModel.setSubscriptionLink(it) },
                title = { Text(stringResource(Res.string.group_subscription_link)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.link,
                        color = IconMaskColors.IconLightBlue,
                    )
                },
                summary = { Text(contentOrUnset(uiState.subscriptionLink)) },
                valueToText = { it },
                textField = { value, onValueChange, onOk ->
                    LinkOrContentTextField(value, onValueChange, onOk)
                },
            )
            TextFieldPreference(
                value = uiState.subscriptionFilterNotRegex,
                onValueChange = { viewModel.setSubscriptionFilterNotRegex(it) },
                title = { Text(stringResource(Res.string.filter_regex)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.delete_sweep,
                        color = IconMaskColors.IconLightGreen,
                    )
                },
                summary = { Text(contentOrUnset(uiState.subscriptionFilterNotRegex)) },
                valueToText = { it },
            )
            SwitchPreference(
                value = uiState.subscriptionForceResolve,
                onValueChange = { viewModel.setSubscriptionForceResolve(it) },
                title = { Text(stringResource(Res.string.force_resolve)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.dns,
                        color = IconMaskColors.IconLightBlue,
                    )
                },
                summary = { Text(stringResource(Res.string.force_resolve_sum)) },
            )
            SwitchPreference(
                value = uiState.subscriptionDeduplication,
                onValueChange = { viewModel.setSubscriptionDeduplication(it) },
                title = { Text(stringResource(Res.string.deduplication)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.content_copy,
                        color = IconMaskColors.IconLightOrange,
                    )
                },
                summary = { Text(stringResource(Res.string.deduplication_sum)) },
            )
        }
        val supportsAge = uiState.subscriptionType == SubscriptionType.RAW
        if (supportsAge) {
            preferenceGroup {
                TextFieldPreference(
                    value = uiState.subscriptionAgeIdentity,
                    onValueChange = { viewModel.setSubscriptionAgeIdentity(it) },
                    title = { Text(stringResource(Res.string.age_identity)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.security,
                            color = IconMaskColors.IconCoral,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.subscriptionAgeIdentity)) },
                    valueToText = { it },
                    textField = { value, onValueChange, onOk ->
                        ValidatedAgeIdentityTextField(value, onValueChange, onOk)
                    },
                )
            }
        }
        val isOOCv1 = uiState.subscriptionType == SubscriptionType.OOCv1
        if (isOOCv1) {
            preferenceGroup {
                TextFieldPreference(
                    value = uiState.subscriptionToken,
                    onValueChange = { viewModel.setSubscriptionToken(it) },
                    title = { Text(stringResource(Res.string.ooc_subscription_token)) },
                    textToValue = { it },
                    icon = {
                        MaskedIcon(
                            Res.drawable.vpn_key,
                            color = IconMaskColors.IconLavender,
                        )
                    },
                    summary = { Text(contentOrUnset(uiState.subscriptionToken)) },
                    valueToText = { it },
                )
            }
        }
        item("category_update", PreferenceType.CATEGORY) {
            PreferenceCategory(text = { Text(stringResource(Res.string.update_settings)) })
        }
        preferenceGroup {
            SwitchPreference(
                value = uiState.subscriptionUpdateWhenConnectedOnly,
                onValueChange = { viewModel.setSubscriptionUpdateWhenConnectedOnly(it) },
                title = { Text(stringResource(Res.string.update_when_connected_only)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.security,
                        color = IconMaskColors.IconLightYellow,
                    )
                },
                summary = { Text(stringResource(Res.string.update_when_connected_only_sum)) },
            )
            TextFieldPreference(
                value = uiState.subscriptionUserAgent,
                onValueChange = { viewModel.setSubscriptionUserAgent(it) },
                title = { Text(stringResource(Res.string.user_agent)) },
                textToValue = { it },
                icon = {
                    MaskedIcon(
                        Res.drawable.grid_3x3,
                        color = IconMaskColors.IconCyan,
                    )
                },
                summary = {
                    val text = uiState.subscriptionUserAgent.blankAsNull() ?: USER_AGENT
                    Text(text)
                },
                valueToText = { it },
            )
            SwitchPreference(
                value = uiState.subscriptionAutoUpdate,
                onValueChange = { viewModel.setSubscriptionAutoUpdate(it) },
                title = { Text(stringResource(Res.string.auto_update)) },
                icon = {
                    MaskedIcon(
                        Res.drawable.flip_camera_android,
                        color = IconMaskColors.IconLavender,
                    )
                },
            )
            TextFieldPreference(
                value = uiState.subscriptionUpdateDelay,
                onValueChange = { viewModel.setSubscriptionUpdateDelay(it) },
                title = { Text(stringResource(Res.string.auto_update_delay)) },
                textToValue = { it.toIntOrNull() ?: 1440 },
                enabled = uiState.subscriptionAutoUpdate,
                icon = {
                    MaskedIcon(
                        Res.drawable.grid_3x3,
                        color = IconMaskColors.IconLightOrange,
                    )
                },
                summary = {
                    Text(
                        if (uiState.subscriptionUpdateDelay > 0) {
                            stringResource(Res.string.auto_update_on, uiState.subscriptionUpdateDelay)
                        } else {
                            stringResource(Res.string.auto_update_off)
                        },
                    )
                },
                textField = { value, onValueChange, onOk ->
                    UIntegerTextField(value, onValueChange, onOk)
                },
            )
        }
    }
}

@Composable
fun ValidatedAgeIdentityTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onOk: () -> Unit,
) {
    ValidatedTextField(
        value = value,
        onValueChange = onValueChange,
        onOk = onOk,
        singleLine = true,
    ) { text ->
        if (text.isBlank()) {
            null
        } else try {
            Libcore.validateAgeIdentities(text)
            null
        } catch (e: Exception) {
            e.readableMessage
        }
    }
}
