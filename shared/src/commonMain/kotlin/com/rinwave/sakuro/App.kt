package com.rinwave.sakuro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.rinwave.sakuro.core.designsystem.SakuroTheme
import com.rinwave.sakuro.core.designsystem.SettingsDetailScaffold
import com.rinwave.sakuro.core.ui.LocalVideoSurfaceRenderers
import com.rinwave.sakuro.core.ui.VideoSurfaceRenderer
import com.rinwave.sakuro.feature.library.LibraryScreen
import com.rinwave.sakuro.feature.settings.SettingsScreen
import com.rinwave.sakuro.navigation.RootComponent
import com.rinwave.sakuro.ui.screens.PlayerScreen
import com.rinwave.sakuro.ui.screens.settings.BenchScreen
import org.jetbrains.compose.resources.stringResource
import sakuro.composeapp.generated.resources.Res
import sakuro.composeapp.generated.resources.bench_title

@Composable
fun App(root: RootComponent, videoSurfaceRenderers: List<VideoSurfaceRenderer> = emptyList()) {
    CompositionLocalProvider(LocalVideoSurfaceRenderers provides videoSurfaceRenderers) {
        AppContent(root)
    }
}

@Composable
private fun AppContent(root: RootComponent) {
    SakuroTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Children(
                stack = root.stack,
                animation = stackAnimation(fade()),
            ) { child ->
                when (val instance = child.instance) {
                    is RootComponent.Child.Library -> LibraryScreen(instance.component)
                    is RootComponent.Child.Player -> PlayerScreen(instance.component)
                    is RootComponent.Child.Settings -> SettingsScreen(instance.component)
                    is RootComponent.Child.Bench ->
                        SettingsDetailScaffold(stringResource(Res.string.bench_title), instance.component.onBack) {
                            BenchScreen(instance.component)
                        }
                }
            }
        }
    }
}
