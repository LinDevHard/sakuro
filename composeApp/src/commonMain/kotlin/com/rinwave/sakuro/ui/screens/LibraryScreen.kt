package com.rinwave.sakuro.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowDownUp
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Settings
import com.rinwave.sakuro.core.media.FOLDER_OTHER
import com.rinwave.sakuro.core.media.LibrarySort
import com.rinwave.sakuro.core.media.SortOrder
import com.rinwave.sakuro.core.media.VideoFolder
import com.rinwave.sakuro.core.media.VideoItem
import com.rinwave.sakuro.core.media.VideoSection
import com.rinwave.sakuro.navigation.LibraryComponent
import com.rinwave.sakuro.navigation.LibraryComponent.Tab
import com.rinwave.sakuro.ui.VideoThumbnail
import com.rinwave.sakuro.ui.label
import com.rinwave.sakuro.ui.rememberVideoFilePicker
import com.rinwave.sakuro.ui.theme.SakuroColors
import com.rinwave.sakuro.ui.util.formatSize
import com.rinwave.sakuro.ui.util.formatTime
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.action_back
import sakuro.composeapp.generated.resources.action_open_file
import sakuro.composeapp.generated.resources.action_refresh
import sakuro.composeapp.generated.resources.action_settings
import sakuro.composeapp.generated.resources.folder_other
import sakuro.composeapp.generated.resources.library_empty_subtitle
import sakuro.composeapp.generated.resources.library_empty_title
import sakuro.composeapp.generated.resources.sort
import sakuro.composeapp.generated.resources.sort_ascending
import sakuro.composeapp.generated.resources.sort_by
import sakuro.composeapp.generated.resources.sort_descending
import sakuro.composeapp.generated.resources.tab_folders
import sakuro.composeapp.generated.resources.tab_videos

@Composable
fun LibraryScreen(component: LibraryComponent) {
    val state by component.state.collectAsState()
    val openFilePicker = rememberVideoFilePicker(component::onFilePicked)
    var sortSheetOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = openFilePicker,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Lucide.FolderOpen, contentDescription = null, Modifier.size(20.dp)) },
                text = { Text(stringResource(Res.string.action_open_file)) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            LibraryHeader(onRefresh = component::refresh, onSettings = component.onOpenSettings)
            CatalogControls(
                state = state,
                onSelectTab = component::selectTab,
                onBack = component::onBack,
                onOpenSort = { sortSheetOpen = true },
            )
            LibraryContent(state = state, component = component)
        }
    }

    if (sortSheetOpen) {
        SortSheet(
            current = state.sortOrder,
            onField = component::setSortField,
            onToggleDirection = component::toggleSortDirection,
            onDismiss = { sortSheetOpen = false },
        )
    }
}

@Composable
private fun LibraryContent(state: LibraryComponent.State, component: LibraryComponent) {
    when {
        state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        state.isEmpty -> EmptyLibrary()

        else -> AnimatedContent(
            targetState = state.tab to state.openFolder,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "catalog",
        ) { (tab, folder) ->
            if (tab == Tab.Folders && folder == null) {
                FolderGrid(folders = state.folders, onOpen = component::openFolder)
            } else {
                VideoGrid(sections = state.sections, onClick = component::onVideoClick)
            }
        }
    }
}

@Composable
private fun LibraryHeader(onRefresh: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "SAKURO",
                style = MaterialTheme.typography.titleLarge.copy(
                    letterSpacing = 7.sp,
                    fontWeight = FontWeight.Light,
                ),
                color = SakuroColors.AccentSakura,
            )
            Text(
                text = "BY RINWAVE",
                fontSize = 9.sp,
                letterSpacing = 4.sp,
                color = SakuroColors.TextMuted,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onRefresh) {
            Icon(
                Lucide.RefreshCw,
                stringResource(Res.string.action_refresh),
                tint = SakuroColors.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onSettings) {
            Icon(
                Lucide.Settings,
                stringResource(Res.string.action_settings),
                tint = SakuroColors.TextMuted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun CatalogControls(
    state: LibraryComponent.State,
    onSelectTab: (Tab) -> Unit,
    onBack: () -> Unit,
    onOpenSort: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val folder = state.openFolder
        if (folder != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Lucide.ArrowLeft,
                    stringResource(Res.string.action_back),
                    tint = SakuroColors.TextPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = folderLabel(folder),
                style = MaterialTheme.typography.titleMedium,
                color = SakuroColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            SegmentedTabs(selected = state.tab, onSelect = onSelectTab, modifier = Modifier.weight(1f))
        }
        SortButton(order = state.sortOrder, onClick = onOpenSort)
    }
}

@Composable
private fun SegmentedTabs(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(SakuroColors.Surface, CircleShape)
            .padding(3.dp),
    ) {
        SegmentChip(stringResource(Res.string.tab_videos), selected == Tab.Videos) { onSelect(Tab.Videos) }
        SegmentChip(stringResource(Res.string.tab_folders), selected == Tab.Folders) { onSelect(Tab.Folders) }
    }
}

@Composable
private fun SegmentChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (active) SakuroColors.Twilight else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) SakuroColors.TextPrimary else SakuroColors.TextMuted,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SortButton(order: SortOrder, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .border(1.dp, SakuroColors.Twilight, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Lucide.ArrowDownUp,
            stringResource(Res.string.sort),
            tint = SakuroColors.AccentSakura,
            modifier = Modifier.size(16.dp),
        )
        Text(order.field.label(), style = MaterialTheme.typography.labelMedium, color = SakuroColors.TextPrimary)
    }
}

@Composable
private fun VideoGrid(sections: List<VideoSection>, onClick: (VideoItem) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 168.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        sections.forEach { section ->
            section.bucket?.let { bucket ->
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(bucket.label()) }
            }
            items(section.items, key = { it.id }) { item ->
                VideoCard(item = item, onClick = { onClick(item) })
            }
        }
    }
}

@Composable
private fun FolderGrid(folders: List<VideoFolder>, onOpen: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 168.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(folders, key = { it.name }) { folder ->
            FolderCard(folder = folder, onClick = { onOpen(folder.name) })
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = SakuroColors.TextPrimary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptyLibrary() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Lucide.Film, contentDescription = null, tint = SakuroColors.Twilight, modifier = Modifier.size(56.dp))
            Text(
                stringResource(Res.string.library_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = SakuroColors.TextPrimary,
            )
            Text(
                stringResource(Res.string.library_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = SakuroColors.TextMuted,
            )
        }
    }
}

@Composable
private fun VideoCard(item: VideoItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Brush.linearGradient(listOf(SakuroColors.SurfaceElevated, SakuroColors.Twilight))),
        ) {
            Icon(
                Lucide.Film,
                contentDescription = null,
                tint = SakuroColors.TextMuted.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp).align(Alignment.Center),
            )
            VideoThumbnail(uri = item.uri, modifier = Modifier.matchParentSize())
            if (item.durationMs > 0) {
                Text(
                    text = formatTime(item.durationMs),
                    fontSize = 11.sp,
                    color = SakuroColors.TextPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(SakuroColors.Background.copy(alpha = 0.75f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = SakuroColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildList {
                if (item.height > 0) add("${item.height}p")
                formatSize(item.sizeBytes).takeIf { it.isNotEmpty() }?.let { add(it) }
            }.joinToString(" • ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = SakuroColors.TextMuted)
            }
        }
    }
}

@Composable
private fun FolderCard(folder: VideoFolder, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Brush.linearGradient(listOf(SakuroColors.SurfaceElevated, SakuroColors.Twilight))),
        ) {
            VideoThumbnail(uri = folder.cover.uri, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(SakuroColors.Background.copy(alpha = 0.28f)))
            Icon(
                Lucide.Folder,
                contentDescription = null,
                tint = SakuroColors.TextPrimary.copy(alpha = 0.9f),
                modifier = Modifier.size(30.dp).align(Alignment.Center),
            )
            Text(
                text = "${folder.count}",
                style = MaterialTheme.typography.labelMedium,
                color = SakuroColors.TextPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(SakuroColors.Background.copy(alpha = 0.7f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Lucide.Folder,
                contentDescription = null,
                tint = SakuroColors.AccentLavender,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = folderLabel(folder.name),
                style = MaterialTheme.typography.bodyMedium,
                color = SakuroColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The catch-all "Other" folder shows a localized label; real folders keep their name. */
@Composable
private fun folderLabel(name: String): String =
    if (name == FOLDER_OTHER) stringResource(Res.string.folder_other) else name

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    current: SortOrder,
    onField: (LibrarySort) -> Unit,
    onToggleDirection: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SakuroColors.Surface,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.sort_by),
                    style = MaterialTheme.typography.titleMedium,
                    color = SakuroColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                DirectionToggle(descending = current.descending, onClick = onToggleDirection)
            }
            LibrarySort.entries.forEach { field ->
                SortRow(field = field, selected = field == current.field, onClick = { onField(field) })
            }
        }
    }
}

@Composable
private fun DirectionToggle(descending: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(SakuroColors.SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Lucide.ArrowDownUp,
            contentDescription = null,
            tint = SakuroColors.AccentSakura,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = if (descending) {
                stringResource(Res.string.sort_descending)
            } else {
                stringResource(Res.string.sort_ascending)
            },
            style = MaterialTheme.typography.labelMedium,
            color = SakuroColors.TextPrimary,
        )
    }
}

@Composable
private fun SortRow(field: LibrarySort, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = field.label(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) SakuroColors.AccentSakura else SakuroColors.TextPrimary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Lucide.Check,
                contentDescription = null,
                tint = SakuroColors.AccentSakura,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
