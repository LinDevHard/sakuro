package com.rinwave.sakuro.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** CoroutineScope, привязанный к lifecycle компонента Decompose. */
fun ComponentContext.componentScope(): CoroutineScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lifecycle.doOnDestroy { scope.cancel() }
    return scope
}
