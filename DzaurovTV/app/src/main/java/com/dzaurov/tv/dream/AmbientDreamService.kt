package com.dzaurov.tv.dream

import android.service.dreams.DreamService
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dzaurov.tv.data.UiPrefs
import com.dzaurov.tv.dz
import com.dzaurov.tv.ui.components.BackdropLayer
import com.dzaurov.tv.ui.components.BackdropState
import com.dzaurov.tv.ui.components.LocalBackdrop
import com.dzaurov.tv.ui.components.rememberNow
import com.dzaurov.tv.ui.screens.AmbientContent
import com.dzaurov.tv.ui.theme.DzaurovTheme

/** The Ambient clock as an Android screensaver ("Dzaurov Ambient"). */
class AmbientDreamService : DreamService() {

    private val owner = DreamOwner()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        owner.create()
        val view = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setContent { DzaurovTheme { DreamContent() } }
        }
        window?.decorView?.let {
            it.setViewTreeLifecycleOwner(owner)
            it.setViewTreeSavedStateRegistryOwner(owner)
            it.setViewTreeViewModelStoreOwner(owner)
        }
        setContentView(view)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        owner.resume()
    }

    override fun onDreamingStopped() {
        owner.pause()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        owner.destroy()
        super.onDetachedFromWindow()
    }
}

@Composable
private fun DreamContent() {
    val app = LocalContext.current.dz
    val prefs by app.prefs.flow.collectAsStateWithLifecycle(initialValue = UiPrefs())
    val weather by app.weather.weather.collectAsStateWithLifecycle()
    val now by rememberNow()
    val backdrop = remember { BackdropState() }
    val theme = app.backgrounds.resolve(prefs.theme, now.hour)
    LaunchedEffect(prefs.loaded, theme, prefs.customVersion) {
        if (prefs.loaded) backdrop.backdrop = app.backgrounds.load(theme, prefs.customVersion)
    }
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(
            Modifier.fillMaxSize().onSizeChanged {
                backdrop.screenW = it.width.toFloat()
                backdrop.screenH = it.height.toFloat()
            }
        ) {
            BackdropLayer(
                state = backdrop,
                wash = null,
                dim = 0f,
                atmosphere = !prefs.performance,
                particles = prefs.particles,
                kenBurns = !prefs.performance,
            )
            AmbientContent(now, weather, showWakeHint = false)
        }
    }
}

/** Minimal lifecycle so Compose can run inside a DreamService (which has none of its own). */
private class DreamOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun create() {
        if (registry.currentState != Lifecycle.State.INITIALIZED) return
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun resume() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun pause() {
        if (registry.currentState.isAtLeast(Lifecycle.State.RESUMED)) registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        if (registry.currentState.isAtLeast(Lifecycle.State.STARTED)) registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    fun destroy() {
        pause()
        if (registry.currentState.isAtLeast(Lifecycle.State.CREATED)) registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
