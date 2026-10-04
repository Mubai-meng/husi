package com.fr.husi.ui.configuration

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ernestoyaquello.dragdropswipelazycolumn.OrderedItem
import com.fr.husi.GroupOrder
import com.fr.husi.database.DataStore
import com.fr.husi.database.ProfileManager
import com.fr.husi.database.ProxyEntity
import com.fr.husi.database.ProxyGroup
import com.fr.husi.database.SagerDatabase
import com.fr.husi.database.displayType
import com.fr.husi.fmt.ValidateResult
import com.fr.husi.ktx.runOnDefaultDispatcher
import com.fr.husi.libcore.Libcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@Immutable
data class GroupProfilesHolderUiState(
    val profiles: List<ProfileItem> = emptyList(),
    val hiddenProfiles: Int = 0,
    val scrollIndex: Int? = null,
    /**
     * Whether [scrollIndex] should scroll with an animation. Position restore
     * (cold start / screen entry) uses an instant jump: animating through the
     * whole list composes every passed card (bean deserialization + icon
     * loading) and visibly drops frames on long lists.
     */
    val scrollAnimated: Boolean = false,
    /**
     * Whether the displayed positions are the persisted user order, which is what drag-and-drop
     * reordering writes back. A search query or a group order other than [GroupOrder.ORIGIN]
     * makes them unrelated to it, so dragging has to stay disabled there.
     */
    val canReorder: Boolean = true,
)

@Immutable
data class ProfileItem(
    val profile: ProxyEntity,
    val isSelected: Boolean,
    val started: Boolean,
    /**
     * 安全校验结果（securityAdvisory 关闭时为 null）。组合期做校验会让
     * 冷启动初期滚动明显掉帧，因此在加载列表时于后台线程预计算。
     */
    val insecureResult: ValidateResult? = null,
    /** 预格式化的流量文本（formatBytes 走 JNI，同样不能在组合期逐卡执行）。 */
    val trafficFormatted: Pair<String, String>? = null,
)

private data class PendingScrollToProxy(
    val proxyId: Long,
    val fallbackToTop: Boolean,
    val animated: Boolean,
)

/**
 * 主界面分组节点列表的显示顺序比较器（单一事实来源）。
 *
 * 除列表页外，批量测速（SpeedTestGroupLoader / 多选对话框）也用它排序，
 * 保证"从第一个按顺序测"的顺序 = 用户在主界面实际看到的顺序。
 */
internal fun proxyDisplayComparator(order: Int): Comparator<ProxyEntity> = when (order) {
    GroupOrder.BY_NAME -> compareBy { it.displayName() }
    GroupOrder.BY_DELAY -> compareBy<ProxyEntity> {
        when {
            it.status == ProxyEntity.STATUS_AVAILABLE -> 0
            !it.error.isNullOrBlank() -> 1
            else -> 2
        }
    }.thenBy {
        if (it.status == ProxyEntity.STATUS_AVAILABLE) {
            it.ping
        } else {
            0
        }
    }

    else -> compareBy<ProxyEntity> { it.userOrder }.thenBy { it.id }
}

@Stable
class GroupProfilesHolderViewModel(
    initialGroup: ProxyGroup,
    val preSelected: Long?,
) : ViewModel() {

    var group: ProxyGroup = initialGroup
        private set

    val uiState: StateFlow<GroupProfilesHolderUiState>
        field = MutableStateFlow(GroupProfilesHolderUiState())

    val alwaysShowAddress = DataStore.alwaysShowAddress.flow()
    val blurredAddress = DataStore.blurredAddress.flow()
    val trafficStatistics = DataStore.profileTrafficStatistics.flow()
    val securityAdvisory = DataStore.securityAdvisory.flow()
    val selectedProxy = DataStore.selectedProxy.flow()

    private var isFirstLoad = true
    private var observeJob: Job? = null
    private var loadJob: Job? = null
    private var deleteTimer: Job? = null
    private var hasLoadedProfiles = false
    private var pendingScrollToProxy: PendingScrollToProxy? = null
    private val hiddenProfileAccess = Mutex()
    private val hiddenProfileIds = mutableSetOf<Long>()

    fun startObserving() {
        if (observeJob != null) return
        observeJob = viewModelScope.launch {
            coroutineScope {
                launch {
                    SagerDatabase.proxyDao.getByGroup(group.id).collect { profiles ->
                        val shouldScroll = isFirstLoad
                        isFirstLoad = false
                        reloadProfiles(profiles, shouldScroll)
                    }
                }

                if (preSelected == null) {
                    launch {
                        selectedProxy.collect {
                            reloadProfiles(null, false)
                        }
                    }
                }

                launch {
                    SagerDatabase.groupDao.getById(group.id).collectLatest { updated ->
                        if (updated != null && updated != group) {
                            group = updated
                            reloadProfiles(null, false)
                        }
                    }
                }

                // 预计算依赖的两个设置变化时重新加载（重新预计算卡片数据）。
                launch {
                    DataStore.securityAdvisory.flow().drop(1).collect {
                        reloadProfiles(null, false)
                    }
                }
                launch {
                    DataStore.profileTrafficStatistics.flow().drop(1).collect {
                        reloadProfiles(null, false)
                    }
                }
            }
        }
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    fun submitReordered(changes: List<OrderedItem<ProfileItem>>) = runOnDefaultDispatcher {
        val state = uiState.value
        if (changes.isEmpty() || !state.canReorder) return@runOnDefaultDispatcher

        val reordered = state.profiles.toMutableList()
        for (change in changes) {
            if (change.newIndex !in reordered.indices) {
                return@runOnDefaultDispatcher
            }
            reordered[change.newIndex] = change.value
        }

        val toChange = reordered.mapIndexedNotNull { index, item ->
            val newOrder = (index + 1).toLong()
            val profile = item.profile
            if (profile.userOrder != newOrder) {
                profile.copy(userOrder = newOrder)
            } else {
                null
            }
        }
        if (toChange.isNotEmpty()) withContext(Dispatchers.IO) {
            ProfileManager.updateProfile(toChange)
        }
    }

    var query: String = ""
        set(value) {
            val lowercase = value.lowercase()
            if (lowercase != field) {
                field = lowercase
                loadJob?.cancel()
                loadJob = viewModelScope.launch {
                    reloadProfiles(null, false)
                }
            }
        }

    private suspend fun reloadProfiles(
        raw: List<ProxyEntity>?,
        shouldScroll: Boolean,
    ) = hiddenProfileAccess.withLock {
        val started = DataStore.serviceState.started
        val current = DataStore.currentProfile.get()
        val selected = preSelected ?: DataStore.selectedProxy.get()

        val comparator = proxyDisplayComparator(group.order)
        var selectedIndex = -1
        val filtered = (raw ?: withContext(Dispatchers.IO) {
            SagerDatabase.proxyDao.getByGroup(group.id).first()
        })
            .filter {
                if (it.id in hiddenProfileIds) return@filter false
                val query = query
                if (query.isBlank()) {
                    true
                } else {
                    it.displayName().lowercase().contains(query)
                            || it.displayType().lowercase().contains(query)
                            || it.displayAddress().lowercase().contains(query)
                }
            }
            .sortedWith(comparator)
        hasLoadedProfiles = true
        // 卡片展示所需的重活（安全校验 + formatBytes JNI）在后台线程
        // 一次性预计算，避免冷启动初期滚动时逐卡在主线程执行。
        val securityAdvisoryOn = DataStore.securityAdvisory.get()
        val trafficStatisticsOn = DataStore.profileTrafficStatistics.get()
        val profiles = withContext(Dispatchers.Default) {
            filtered.mapIndexed { index, entity ->
                val isSelected = entity.id == selected
                if (isSelected) selectedIndex = index
                ProfileItem(
                    profile = entity,
                    isSelected = isSelected,
                    started = isSelected && started && entity.id == current,
                    insecureResult = if (securityAdvisoryOn) {
                        entity.requireBean().isInsecure()
                    } else {
                        null
                    },
                    trafficFormatted = if (trafficStatisticsOn && entity.tx + entity.rx > 0L) {
                        Libcore.formatBytes(entity.tx) to Libcore.formatBytes(entity.rx)
                    } else {
                        null
                    },
                )
            }
        }
        var scrollIndex = selectedIndex.takeIf { shouldScroll && selectedIndex >= 0 }
        var scrollAnimated = false
        pendingScrollToProxy?.let { pending ->
            val pendingIndex = profiles.indexOfFirst { it.profile.id == pending.proxyId }
            scrollIndex = when {
                pendingIndex >= 0 -> pendingIndex
                pending.fallbackToTop && profiles.isNotEmpty() -> 0
                else -> scrollIndex
            }
            scrollAnimated = pending.animated
            pendingScrollToProxy = null
        }

        uiState.update { state ->
            state.copy(
                profiles = profiles,
                hiddenProfiles = hiddenProfileIds.size,
                scrollIndex = scrollIndex,
                scrollAnimated = scrollAnimated,
                canReorder = query.isBlank() && group.order == GroupOrder.ORIGIN,
            )
        }
    }

    fun consumeScrollIndex() {
        uiState.update { it.copy(scrollIndex = null, scrollAnimated = false) }
    }

    fun scrollToProxy(proxyId: Long, fallbackToTop: Boolean, animated: Boolean = true) {
        viewModelScope.launch {
            val profiles = uiState.value.profiles
            val index = profiles.indexOfFirst { it.profile.id == proxyId }
            if (index >= 0) {
                uiState.update { it.copy(scrollIndex = index, scrollAnimated = animated) }
            } else if (fallbackToTop) {
                if (profiles.isNotEmpty()) {
                    uiState.update { it.copy(scrollIndex = 0, scrollAnimated = animated) }
                } else if (!hasLoadedProfiles) {
                    pendingScrollToProxy = PendingScrollToProxy(proxyId, fallbackToTop, animated)
                }
            }
        }
    }

    fun profileToSelect(delta: Int): Long? {
        val profiles = uiState.value.profiles
        if (profiles.isEmpty()) return null

        val currentIndex = profiles.indexOfFirst { it.isSelected }
        val targetIndex = if (currentIndex < 0) {
            0
        } else {
            (currentIndex + delta).coerceIn(0, profiles.lastIndex)
        }
        return profiles.getOrNull(targetIndex)?.profile?.id
            ?.takeIf { targetIndex != currentIndex }
    }

    fun onProfileSelected(profileId: Long) {
        viewModelScope.launch {
            reloadProfiles(null, false)
        }
    }

    fun undoableRemove(id: Long) = viewModelScope.launch {
        hiddenProfileAccess.withLock {
            uiState.update { state ->
                val profiles = state.profiles.toMutableList()
                val index = profiles.indexOfFirst { it.profile.id == id }
                if (index >= 0) {
                    profiles.removeAt(index)
                    hiddenProfileIds.add(id)
                }
                state.copy(
                    profiles = profiles,
                    hiddenProfiles = hiddenProfileIds.size,
                )
            }
        }
        startDeleteTimer()
    }

    private fun startDeleteTimer() {
        deleteTimer?.cancel()
        deleteTimer = viewModelScope.launch {
            delay(5000.milliseconds)
            commit()
        }
    }

    fun undo() = viewModelScope.launch {
        deleteTimer?.cancel()
        deleteTimer = null
        hiddenProfileAccess.withLock {
            hiddenProfileIds.clear()
        }
        val profiles = withContext(Dispatchers.IO) { SagerDatabase.proxyDao.getByGroup(group.id).first() }
        reloadProfiles(profiles, false)
    }

    fun commit() = runOnDefaultDispatcher {
        deleteTimer?.cancel()
        deleteTimer = null
        val toDelete = hiddenProfileAccess.withLock {
            val toDelete = hiddenProfileIds.toList()
            hiddenProfileIds.clear()
            toDelete
        }
        withContext(Dispatchers.IO) {
            ProfileManager.deleteProfiles(group.id, toDelete)
        }
    }
}
