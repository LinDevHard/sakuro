package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext

/** Root settings screen: the list of sections that drill down into their own screens. */
class SettingsMenuComponent(
    componentContext: ComponentContext,
    val version: String,
    val onBack: () -> Unit,
    val onOpenPlayback: () -> Unit,
    val onOpenUpscale: () -> Unit,
    val onOpenControls: () -> Unit,
    val onOpenAdvanced: () -> Unit,
    val onOpenAbout: () -> Unit,
) : ComponentContext by componentContext
