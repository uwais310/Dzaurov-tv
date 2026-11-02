package com.dzaurov.tv.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.SystemClock
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.dzaurov.tv.data.AppEntry
import com.dzaurov.tv.ui.ADD_KEY
import com.dzaurov.tv.ui.HomeFocus
import com.dzaurov.tv.ui.MainViewModel
import com.dzaurov.tv.ui.MenuSource
import com.dzaurov.tv.ui.components.AddTile
import com.dzaurov.tv.ui.components.AppTile
import com.dzaurov.tv.ui.components.WatchNextCard
import com.dzaurov.tv.ui.components.remainingText
import com.dzaurov.tv.ui.components.Chip
import com.dzaurov.tv.ui.components.DzIcons
import com.dzaurov.tv.ui.components.LocalBackdrop
import com.dzaurov.tv.ui.components.RowLabel
import com.dzaurov.tv.ui.components.categoryName
import com.dzaurov.tv.ui.components.glass
import com.dzaurov.tv.ui.components.greeting
import com.dzaurov.tv.ui.components.isSelect
import com.dzaurov.tv.ui.components.relativeTime
import com.dzaurov.tv.ui.components.rememberAppVisual
import com.dzaurov.tv.ui.components.safeRequest
import com.dzaurov.tv.ui.recentKey
import com.dzaurov.tv.ui.watchKey
import com.dzaurov.tv.ui.theme.Dz
import com.dzaurov.tv.ui.theme.dzStyle
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.max

private val FAV_W = 136.dp
private val FAV_H = 84.dp
private val REC_W = 136.dp
private val REC_H = 84.dp
private val CW_W = 192.dp
private val CW_H = 108.dp

/** Rows on Home, top to bottom. Focus in a lower row scrolls the hero away (Apple TV style). */
private const val ROW_FAV = 0
private const val ROW_CW = 1
private const val ROW_REC = 2

@Composable
fun HomeScreen(vm: MainViewModel, perf: Boolean, now: LocalDateTime) {
    val context = LocalContext.current
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val watchNext by vm.watchNext.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()
    val focus by vm.homeFocus.collectAsStateWithLifecycle()
    val pending by vm.pendingHomeFocus.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()

    val backdrop = LocalBackdrop.current
    val scope = rememberCoroutineScope()
    val favState = rememberLazyListState()
    val recState = rememberLazyListState()
    val cwState = rememberLazyListState()
    val scroll = rememberScrollState()
    val requesters = remember { HashMap<String, FocusRequester>() }
    fun req(key: String) = requesters.getOrPut(key) { FocusRequester() }
    val maxShift = with(LocalDensity.current) { 22.dp.toPx() }

    // Staggered entrance: tiles composed in the first moments after Home appears cascade
    // in row by row; anything composed later (scrolling) just shows up.
    val shownAt = remember { SystemClock.uptimeMillis() }
    fun entrance(row: Int, index: Int): Long =
        if (SystemClock.uptimeMillis() - shownAt < 600L && index < 9) row * 90L + index * 45L else -1L

    fun focusChanged(f: HomeFocus, index: Int, count: Int, row: Int = ROW_FAV) {
        vm.onHomeFocus(f)
        val frac = if (count <= 1) 0.5f else index / (count - 1f)
        scope.launch {
            backdrop.parallax.animateTo((frac * 2f - 1f) * maxShift, spring(dampingRatio = 1f, stiffness = 40f))
        }
        scope.launch {
            val target = if (row == ROW_REC) scroll.maxValue else 0
            if (scroll.value != target) scroll.animateScrollTo(target, spring(dampingRatio = 0.85f, stiffness = 220f))
        }
    }

    // First focus once apps and favorites are known, then every time something asks for it.
    LaunchedEffect(ready) {
        if (ready && vm.pendingHomeFocus.value == null) vm.focusHomeDefault()
    }
    LaunchedEffect(pending, favorites, recents, watchNext, ready) {
        val key = pending ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        val (state, index) = when {
            key == ADD_KEY -> favState to favorites.size
            key.startsWith("r:") -> recState to recents.indexOfFirst { recentKey(it.first.packageName) == key }
            key.startsWith("w:") -> cwState to watchNext.indexOfFirst { watchKey(it.first.id) == key }
            else -> favState to favorites.indexOfFirst { it.packageName == key }
        }
        if (index >= 0) {
            ensureVisible(state, index)
            withFrameNanos { }
            if (!req(key).safeRequest()) req(ADD_KEY).safeRequest()
        } else {
            req(ADD_KEY).safeRequest()
        }
        vm.consumePendingHomeFocus()
    }
    LaunchedEffect(Unit) {
        vm.focusRestore.collect { vm.focusHomeDefault() }
    }
    // Keep the tile being moved on screen.
    LaunchedEffect(favorites, editing) {
        val key = editing ?: return@LaunchedEffect
        val i = favorites.indexOfFirst { it.packageName == key }
        if (i >= 0) ensureVisible(favState, i, animate = true)
    }

    // 0 at the top, 1 when scrolled down to Recent. Read only while drawing, so scrolling
    // never recomposes the screen.
    fun scrolledFraction(): Float = if (scroll.maxValue > 0) scroll.value / scroll.maxValue.toFloat() else 0f

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        BrandMark(
            app = focus?.app,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 92.dp, end = 64.dp)
                .size(288.dp, 162.dp)
                .graphicsLayer {
                    val f = scrolledFraction()
                    alpha = 1f - f
                    translationY = -scroll.value.toFloat()
                },
        )

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .heightIn(min = viewport),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Hero(
                focus = focus,
                now = now,
                modifier = Modifier
                    .padding(start = 44.dp, top = 96.dp)
                    .width(600.dp)
                    .height(160.dp)
                    .graphicsLayer { alpha = 1f - scrolledFraction() },
            )

            Column(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                RowLabel("Favorites", favorites.size)
                LazyRow(
                    state = favState,
                    contentPadding = PaddingValues(horizontal = 44.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().height(FAV_H + 24.dp),
                ) {
                    val count = favorites.size + 1
                    itemsIndexed(favorites, key = { _, a -> a.packageName }) { i, app ->
                        val moving = editing == app.packageName
                        AppTile(
                            app = app,
                            width = FAV_W,
                            height = FAV_H,
                            perf = perf,
                            onClick = { vm.open(context, app) },
                            onLongClick = { vm.showMenu(app, MenuSource.Favorites) },
                            onFocused = { focusChanged(HomeFocus(app.packageName, app, null), i, count, ROW_FAV) },
                            moving = moving,
                            jiggle = editing != null,
                            appearDelay = entrance(ROW_FAV, i),
                            launchFrom = { rect, color -> vm.launchFrom(context, app, rect, color) },
                            modifier = Modifier
                                .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = spring(dampingRatio = 0.8f, stiffness = 500f))
                                .focusRequester(req(app.packageName))
                                .then(if (moving) Modifier.editKeys(vm) else Modifier),
                        )
                    }
                    item(key = ADD_KEY) {
                        AddTile(
                            width = FAV_W,
                            height = FAV_H,
                            onClick = { vm.go(com.dzaurov.tv.ui.Screen.Apps) },
                            onFocused = { focusChanged(HomeFocus(ADD_KEY, null, null), count - 1, count, ROW_FAV) },
                            appearDelay = entrance(ROW_FAV, favorites.size),
                            modifier = Modifier.focusRequester(req(ADD_KEY)),
                        )
                    }
                }

                if (watchNext.isNotEmpty()) {
                    RowLabel("Continue watching", watchNext.size, Modifier.padding(top = 2.dp))
                    LazyRow(
                        state = cwState,
                        contentPadding = PaddingValues(horizontal = 44.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth().height(CW_H + 24.dp),
                    ) {
                        itemsIndexed(watchNext, key = { _, w -> watchKey(w.first.id) }) { i, (item, app) ->
                            val key = watchKey(item.id)
                            WatchNextCard(
                                item = item,
                                app = app,
                                width = CW_W,
                                height = CW_H,
                                perf = perf,
                                onClick = { vm.openWatchNext(context, item, app) },
                                onFocused = { focusChanged(HomeFocus(key, app, null, item), i, watchNext.size, ROW_CW) },
                                modifier = Modifier
                                    .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = spring(dampingRatio = 0.8f, stiffness = 500f))
                                    .focusRequester(req(key)),
                            )
                        }
                    }
                }

                if (recents.isNotEmpty()) {
                    RowLabel("Recent", null, Modifier.padding(top = 2.dp))
                    LazyRow(
                        state = recState,
                        contentPadding = PaddingValues(horizontal = 44.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().height(REC_H + 24.dp),
                    ) {
                        itemsIndexed(recents, key = { _, r -> recentKey(r.first.packageName) }) { i, (app, at) ->
                            val key = recentKey(app.packageName)
                            AppTile(
                                app = app,
                                width = REC_W,
                                height = REC_H,
                                perf = perf,
                                onClick = { vm.open(context, app) },
                                onLongClick = { vm.showMenu(app, MenuSource.Recent) },
                                onFocused = { focusChanged(HomeFocus(key, app, at), i, recents.size, ROW_REC) },
                                appearDelay = entrance(ROW_REC, i),
                                launchFrom = { rect, color -> vm.launchFrom(context, app, rect, color) },
                                modifier = Modifier
                                    .animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = spring(dampingRatio = 0.8f, stiffness = 500f))
                                    .focusRequester(req(key)),
                            )
                        }
                    }
                }
            }
        }
    }
}

private suspend fun ensureVisible(state: LazyListState, index: Int, animate: Boolean = false) {
    val info = state.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    val fully = item != null && item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
    if (!fully) {
        val target = max(0, index - 1)
        if (animate) state.animateScrollToItem(target) else state.scrollToItem(target)
    }
}

/** Reorder mode: ◀ ▶ move the tile, OK drops it, up/down are blocked so focus stays put. */
private fun Modifier.editKeys(vm: MainViewModel): Modifier = composed {
    val sawDown = remember { booleanArrayOf(false) }
    onPreviewKeyEvent { ev ->
        when {
            ev.key == Key.DirectionLeft -> {
                if (ev.type == KeyEventType.KeyDown) vm.moveEditing(-1)
                true
            }
            ev.key == Key.DirectionRight -> {
                if (ev.type == KeyEventType.KeyDown) vm.moveEditing(1)
                true
            }
            ev.key == Key.DirectionUp || ev.key == Key.DirectionDown -> true
            ev.key.isSelect() && ev.type == KeyEventType.KeyDown -> {
                sawDown[0] = true
                true
            }
            ev.key.isSelect() && ev.type == KeyEventType.KeyUp -> {
                // The release of the long-press that started editing is ignored.
                if (sawDown[0]) {
                    sawDown[0] = false
                    vm.endEdit()
                }
                true
            }
            else -> false
        }
    }
}

@Composable
private fun Hero(focus: HomeFocus?, now: LocalDateTime, modifier: Modifier) {
    AnimatedContent(
        targetState = focus,
        contentKey = { it?.key },
        transitionSpec = {
            (fadeIn(tween(320)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 300f)) { it / 5 }) togetherWith
                (fadeOut(tween(150)) + slideOutVertically(tween(200)) { -it / 8 }) using null
        },
        label = "hero",
        modifier = modifier,
    ) { f ->
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val app = f?.app
            val program = f?.program
            if (program != null) {
                val visual = if (app != null) rememberAppVisual(app).value else null
                Chip(app?.label ?: "Continue watching", visual?.color ?: Dz.Blue)
                Text(
                    program.title,
                    style = dzStyle(48.sp, FontWeight.SemiBold, spacing = (-1.5).sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val line = listOfNotNull(program.subtitle, remainingText(program.remainingMs), "Press OK to continue")
                Text(line.joinToString(" · "), style = dzStyle(14.sp, color = Dz.Ink2), maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (app == null) {
                Chip(if (f?.key == ADD_KEY) "Favorites" else "Home", Dz.Blue)
                Text(
                    if (f?.key == ADD_KEY) "Add apps" else "${greeting(now)}, Dzaurov",
                    style = dzStyle(44.sp, FontWeight.SemiBold, spacing = (-1.5).sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (f?.key == ADD_KEY) "Press OK to pick apps for Home." else "Pick something to watch.",
                    style = dzStyle(14.sp, color = Dz.Ink2),
                )
            } else {
                val visual by rememberAppVisual(app)
                Chip(categoryName(app.category), visual?.color ?: Dz.Blue)
                Text(
                    app.label,
                    style = dzStyle(54.sp, FontWeight.SemiBold, spacing = (-2).sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val openedAt = f?.recentAt
                Text(
                    if (openedAt != null) "Opened ${relativeTime(openedAt)} · hold OK for options"
                    else "Press OK to open · hold OK to move or remove",
                    style = dzStyle(14.sp, color = Dz.Ink2),
                )
            }
        }
    }
}

/** The app's own banner (its real logo), large and tilted behind the hero. */
@Composable
private fun BrandMark(app: AppEntry?, modifier: Modifier) {
    AnimatedContent(
        targetState = app,
        contentKey = { it?.packageName },
        transitionSpec = {
            (fadeIn(tween(420)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = 260f), initialScale = 0.86f)) togetherWith
                fadeOut(tween(200)) using null
        },
        label = "brandMark",
        modifier = modifier,
    ) { a ->
        if (a != null) {
            val visual by rememberAppVisual(a)
            val v = visual
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = -4f }
                    .drawWithCache {
                        val glow = Brush.radialGradient(
                            listOf((v?.color ?: Dz.Blue).copy(alpha = 0.45f), Color.Transparent),
                            radius = size.maxDimension * 0.8f,
                        )
                        onDrawBehind { drawCircle(glow, radius = size.maxDimension * 0.8f) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (v != null && v.isBanner) {
                    Image(
                        bitmap = v.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.5.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(22.dp)),
                    )
                } else if (v != null) {
                    Box(Modifier.size(162.dp).clip(RoundedCornerShape(40.dp)).glass(40.dp), contentAlignment = Alignment.Center) {
                        Image(
                            bitmap = v.image,
                            contentDescription = null,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.size(110.dp),
                        )
                    }
                } else {
                    Icon(DzIcons.Play, contentDescription = null, tint = Dz.Ink3, modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}
