package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.backhandler.BackCallback
import com.rinwave.sakuro.core.media.FOLDER_OTHER
import com.rinwave.sakuro.core.media.LibraryEvents
import com.rinwave.sakuro.core.media.LibraryPreferencesStore
import com.rinwave.sakuro.core.media.LibrarySort
import com.rinwave.sakuro.core.media.MediaLibrary
import com.rinwave.sakuro.core.media.SortOrder
import com.rinwave.sakuro.core.media.VideoFolder
import com.rinwave.sakuro.core.media.VideoItem
import com.rinwave.sakuro.core.media.VideoSection
import com.rinwave.sakuro.core.media.toFolders
import com.rinwave.sakuro.core.media.toSections
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryComponent(
    componentContext: ComponentContext,
    private val mediaLibrary: MediaLibrary,
    private val preferences: LibraryPreferencesStore,
    private val onOpenVideo: (uri: String, title: String) -> Unit,
    val onOpenSettings: () -> Unit,
) : ComponentContext by componentContext {

    enum class Tab { Videos, Folders }

    data class State(
        val isLoading: Boolean = true,
        val tab: Tab = Tab.Videos,
        val sortOrder: SortOrder = SortOrder(),
        /** The opened folder (drill-down) or null at the top level. */
        val openFolder: String? = null,
        /** Sections of the Videos tab or of the opened folder's content. */
        val sections: List<VideoSection> = emptyList(),
        /** Folders of the Folders tab. */
        val folders: List<VideoFolder> = emptyList(),
    ) {
        val isEmpty: Boolean get() = sections.isEmpty() && folders.isEmpty()
    }

    private val scope = componentScope()

    private var allItems: List<VideoItem> = emptyList()
    private var loadedOnce = false

    private val _state = MutableStateFlow(State(sortOrder = preferences.sortOrder.value))
    val state: StateFlow<State> = _state.asStateFlow()

    /** The system back button exits an opened folder to the top level. */
    private val backCallback = BackCallback(isEnabled = false) { onBack() }

    init {
        backHandler.register(backCallback)
        reload()
        // Live updates: a ContentObserver / re-opening the library triggers a scan.
        scope.launch {
            LibraryEvents.refreshRequests.collect { reload() }
        }
    }

    /** Manual refresh: asks the system to re-index storage, then re-reads the library. */
    fun refresh() {
        mediaLibrary.requestSystemRescan()
        reload()
    }

    /** Re-reads the library from MediaStore in the background (isLoading only on the first load). */
    private fun reload() {
        scope.launch {
            if (!loadedOnce) emit(_state.value.copy(isLoading = true))
            allItems = mediaLibrary.scan()
            loadedOnce = true
            emit(rebuild(_state.value.copy(isLoading = false)))
        }
    }

    fun selectTab(tab: Tab) {
        if (tab == _state.value.tab && _state.value.openFolder == null) return
        emit(rebuild(_state.value.copy(tab = tab, openFolder = null)))
    }

    fun openFolder(name: String) {
        emit(rebuild(_state.value.copy(tab = Tab.Folders, openFolder = name)))
    }

    /** From an opened folder — back to the list. */
    fun onBack() {
        if (_state.value.openFolder == null) return
        emit(rebuild(_state.value.copy(openFolder = null)))
    }

    fun setSortField(field: LibrarySort) =
        applySort(SortOrder(field, field.defaultDescending))

    fun toggleSortDirection() = _state.value.sortOrder.let {
        applySort(it.copy(descending = !it.descending))
    }

    private fun applySort(order: SortOrder) {
        preferences.setSortOrder(order)
        emit(rebuild(_state.value.copy(sortOrder = order)))
    }

    private fun emit(new: State) {
        backCallback.isEnabled = new.openFolder != null
        _state.value = new
    }

    /** Rebuilds the sections/folders view from [allItems] for the current state. */
    private fun rebuild(base: State): State {
        val order = base.sortOrder
        val folderItems = base.openFolder?.let { name ->
            allItems.filter { it.folderName.ifBlank { FOLDER_OTHER } == name }
        }
        return base.copy(
            sections = when {
                folderItems != null -> folderItems.toSections(order)
                base.tab == Tab.Videos -> allItems.toSections(order)
                else -> emptyList()
            },
            folders = if (base.tab == Tab.Folders && base.openFolder == null) {
                allItems.toFolders(order)
            } else {
                emptyList()
            },
        )
    }

    fun onVideoClick(item: VideoItem) = onOpenVideo(item.uri, item.title)

    fun onFilePicked(uri: String, title: String) = onOpenVideo(uri, title)
}
