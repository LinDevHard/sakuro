package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.rinwave.sakuro.core.media.LibraryEvents
import com.rinwave.sakuro.core.media.MediaLibrary
import com.rinwave.sakuro.core.media.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryComponent(
    componentContext: ComponentContext,
    private val mediaLibrary: MediaLibrary,
    private val onOpenVideo: (uri: String, title: String) -> Unit,
    val onOpenSettings: () -> Unit,
) : ComponentContext by componentContext {

    data class State(
        val isLoading: Boolean = true,
        val items: List<VideoItem> = emptyList(),
    )

    private val scope = componentScope()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        refresh()
        scope.launch {
            LibraryEvents.refreshRequests.collect { refresh() }
        }
    }

    fun refresh() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true)
            _state.value = State(isLoading = false, items = mediaLibrary.scan())
        }
    }

    fun onVideoClick(item: VideoItem) = onOpenVideo(item.uri, item.title)

    fun onFilePicked(uri: String, title: String) = onOpenVideo(uri, title)
}
