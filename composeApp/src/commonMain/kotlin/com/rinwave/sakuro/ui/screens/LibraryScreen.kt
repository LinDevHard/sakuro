package com.rinwave.sakuro.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Settings
import com.rinwave.sakuro.core.media.VideoItem
import com.rinwave.sakuro.navigation.LibraryComponent
import com.rinwave.sakuro.ui.rememberVideoFilePicker
import com.rinwave.sakuro.ui.theme.SakuroColors
import com.rinwave.sakuro.ui.util.formatSize
import com.rinwave.sakuro.ui.util.formatTime

@Composable
fun LibraryScreen(component: LibraryComponent) {
    val state by component.state.collectAsState()
    val openFilePicker = rememberVideoFilePicker(component::onFilePicked)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = openFilePicker,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Lucide.FolderOpen, contentDescription = null, Modifier.size(20.dp)) },
                text = { Text("Открыть файл") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            LibraryHeader(
                onRefresh = component::refresh,
                onSettings = component.onOpenSettings,
            )
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                state.items.isEmpty() -> EmptyLibrary()

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 168.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        VideoCard(item = item, onClick = { component.onVideoClick(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(onRefresh: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
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
            Icon(Lucide.RefreshCw, contentDescription = "Обновить", tint = SakuroColors.TextMuted, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onSettings) {
            Icon(Lucide.Settings, contentDescription = "Настройки", tint = SakuroColors.TextMuted, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun EmptyLibrary() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Lucide.Film, contentDescription = null, tint = SakuroColors.Twilight, modifier = Modifier.size(56.dp))
            Text(
                "Видео не найдены",
                style = MaterialTheme.typography.titleMedium,
                color = SakuroColors.TextPrimary,
            )
            Text(
                "Разрешите доступ к медиатеке или откройте файл вручную",
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
                .background(
                    Brush.linearGradient(
                        colors = listOf(SakuroColors.SurfaceElevated, SakuroColors.Twilight),
                    ),
                ),
        ) {
            Icon(
                Lucide.Film,
                contentDescription = null,
                tint = SakuroColors.TextMuted.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp).align(Alignment.Center),
            )
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
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = SakuroColors.TextMuted,
                )
            }
        }
    }
}
