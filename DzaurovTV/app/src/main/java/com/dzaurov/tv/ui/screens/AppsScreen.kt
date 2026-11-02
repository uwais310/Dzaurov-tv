package com.dzaurov.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.dzaurov.tv.data.AppEntry
import com.dzaurov.tv.ui.MainViewModel
import com.dzaurov.tv.ui.MenuSource
import com.dzaurov.tv.ui.components.AppTile
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.dzaurov.tv.ui.components.FullWidthNote
import com.dzaurov.tv.ui.components.PageTitle
import com.dzaurov.tv.ui.components.safeRequest
import com.dzaurov.tv.ui.theme.Dz
import com.dzaurov.tv.ui.theme.dzStyle

@Composable
fun AppsScreen(vm: MainViewModel, perf: Boolean) {
    val context = LocalContext.current
    val apps by vm.visibleApps.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val favorites = remember(prefs.favorites) { prefs.favorites.toSet() }
    Column(Modifier.fillMaxSize().padding(top = 78.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PageTitle("All apps", "${apps.size} apps · hold OK to add to Home or hide")
        AppGrid(
            vm = vm,
            apps = apps,
            perf = perf,
            starred = favorites,
            emptyText = "Looking for apps…",
            onClick = { vm.open(context, it) },
            onLongClick = { vm.showMenu(it, MenuSource.Grid) },
            launchFrom = { app, rect, color -> vm.launchFrom(context, app, rect, color) },
        )
    }
}

@Composable
fun HiddenAppsScreen(vm: MainViewModel, perf: Boolean) {
    val apps by vm.hiddenApps.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(top = 78.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PageTitle("Hidden apps", "Press OK to show an app again")
        AppGrid(
            vm = vm,
            apps = apps,
            perf = perf,
            starred = emptySet(),
            emptyText = "No hidden apps. To hide one, hold OK on it in All apps.",
            onClick = { vm.unhide(it) },
            onLongClick = { vm.showMenu(it, MenuSource.Hidden) },
        )
    }
}

/** Six glass tiles per row (icon + name inside), with a "NEW" mark for recent installs. */
@Composable
fun AppGrid(
    vm: MainViewModel,
    apps: List<AppEntry>,
    perf: Boolean,
    starred: Set<String>,
    emptyText: String,
    onClick: (AppEntry) -> Unit,
    onLongClick: (AppEntry) -> Unit,
    state: LazyGridState = rememberLazyGridState(),
    firstFocus: FocusRequester = remember { FocusRequester() },
    takeFocusOnStart: Boolean = true,
    launchFrom: ((AppEntry, Rect, Color) -> Unit)? = null,
) {
    val now = remember { System.currentTimeMillis() }
    val shownAt = remember { android.os.SystemClock.uptimeMillis() }
    if (apps.isEmpty()) {
        FullWidthNote(emptyText, Modifier.padding(top = 24.dp))
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        state = state,
        contentPadding = PaddingValues(start = 44.dp, end = 44.dp, top = 14.dp, bottom = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
            AppTile(
                app = app,
                width = 132.dp,
                height = 80.dp,
                perf = perf,
                focusScale = 1.08f,
                starred = app.packageName in starred,
                isNew = app.isNew(now),
                onClick = { onClick(app) },
                onLongClick = { onLongClick(app) },
                appearDelay = if (i < 18 && android.os.SystemClock.uptimeMillis() - shownAt < 600L) (i / 6) * 70L + (i % 6) * 35L else -1L,
                launchFrom = launchFrom?.let { f -> { rect: Rect, color: Color -> f(app, rect, color) } },
                modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
    if (takeFocusOnStart) {
        LaunchedEffect(apps.isNotEmpty()) {
            if (apps.isNotEmpty()) {
                withFrameNanos { }
                firstFocus.safeRequest()
            }
        }
    }
    val latest by rememberUpdatedState(apps)
    if (takeFocusOnStart) LaunchedEffect(Unit) {
        vm.focusRestore.collect {
            if (latest.isNotEmpty()) {
                state.scrollToItem(0)
                withFrameNanos { }
                firstFocus.safeRequest()
            }
        }
    }
}
