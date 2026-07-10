package com.rinwave.sakuro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.rinwave.sakuro.navigation.RootComponent
import com.rinwave.sakuro.ui.screens.LibraryScreen
import com.rinwave.sakuro.ui.screens.PlayerScreen
import com.rinwave.sakuro.ui.screens.settings.SettingsScreen
import com.rinwave.sakuro.ui.theme.SakuroTheme

@Composable
fun App(root: RootComponent) {
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
                }
            }
        }
    }
}
