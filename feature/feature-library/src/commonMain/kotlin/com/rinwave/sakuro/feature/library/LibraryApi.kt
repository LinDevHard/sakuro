package com.rinwave.sakuro.feature.library

import com.arkivanov.decompose.ComponentContext
import com.rinwave.sakuro.core.media.LibraryPreferencesStore
import com.rinwave.sakuro.core.media.MediaLibrary
import com.rinwave.sakuro.core.media.PlaybackHistoryStore
import org.koin.core.module.Module
import org.koin.dsl.module

/** What the library asks its host to do; the host owns navigation. */
public sealed interface LibraryOutput {
    public data class OpenVideo(val uri: String, val title: String) : LibraryOutput

    public data object OpenSettings : LibraryOutput
}

/** Creates [LibraryComponent]s; the only way to build one outside this module. */
public fun interface LibraryComponentFactory {
    public fun create(context: ComponentContext, onOutput: (LibraryOutput) -> Unit): LibraryComponent
}

public fun LibraryComponentFactory(
    mediaLibrary: MediaLibrary,
    preferences: LibraryPreferencesStore,
    history: PlaybackHistoryStore,
): LibraryComponentFactory = LibraryComponentFactory { context, onOutput ->
    LibraryComponent(context, mediaLibrary, preferences, history, onOutput)
}

/** Registers [LibraryComponentFactory]; platform modules provide the library and stores. */
public val libraryFeatureModule: Module = module {
    single { LibraryComponentFactory(get(), get(), get()) }
}
