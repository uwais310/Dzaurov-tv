package com.dzaurov.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.dzaurov.tv.ui.MenuAction
import com.dzaurov.tv.ui.MenuSource
import com.dzaurov.tv.ui.MenuState
import com.dzaurov.tv.ui.Prompt
import com.dzaurov.tv.ui.theme.Dz
import com.dzaurov.tv.ui.theme.dzStyle

private fun MenuAction.label(): String = when (this) {
    MenuAction.Open -> "Open"
    MenuAction.Move -> "Move"
    MenuAction.AddHome -> "Add to Home"
    MenuAction.RemoveHome -> "Remove from Home"
    MenuAction.RemoveRecent -> "Remove from Recent"
    MenuAction.Hide -> "Hide app"
    MenuAction.Unhide -> "Show app again"
    MenuAction.Info -> "App info"
}

private fun MenuAction.icon(): ImageVector = when (this) {
    MenuAction.Open -> DzIcons.Play
    MenuAction.Move -> DzIcons.Move
    MenuAction.AddHome -> DzIcons.Star
    MenuAction.RemoveHome -> DzIcons.Trash
    MenuAction.RemoveRecent -> DzIcons.Trash
    MenuAction.Hide -> DzIcons.EyeOff
    MenuAction.Unhide -> DzIcons.Eye
    MenuAction.Info -> DzIcons.Info
}

/** Long-press OK menu. A real dialog window, so focus can't escape and Back closes it. */
@Composable
fun AppMenu(state: MenuState, isFavorite: Boolean, onDismiss: () -> Unit, onAction: (MenuAction) -> Unit) {
    val actions = buildList {
        add(MenuAction.Open)
        when (state.source) {
            MenuSource.Favorites -> {
                add(MenuAction.Move)
                add(MenuAction.RemoveHome)
            }
            MenuSource.Recent -> {
                add(if (isFavorite) MenuAction.RemoveHome else MenuAction.AddHome)
                add(MenuAction.RemoveRecent)
            }
            MenuSource.Grid -> {
                add(if (isFavorite) MenuAction.RemoveHome else MenuAction.AddHome)
                add(MenuAction.Hide)
            }
            MenuSource.Hidden -> add(MenuAction.Unhide)
        }
        add(MenuAction.Info)
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val first = remember { FocusRequester() }
        // Springs open like an iOS context menu.
        val pop = remember { androidx.compose.animation.core.Animatable(0.82f) }
        LaunchedEffect(Unit) {
            pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 520f))
        }
        Column(
            Modifier
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                    alpha = ((pop.value - 0.82f) / 0.18f).coerceIn(0f, 1f)
                }
                .width(300.dp)
                .background(Dz.Panel, RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val visual by rememberAppVisual(state.app)
            Row(
                Modifier.padding(start = 6.dp, bottom = 8.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                visual?.let { v ->
                    Image(
                        bitmap = v.icon,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.Medium,
                        modifier = Modifier.size(30.dp),
                    )
                }
                Text(state.app.label, style = dzStyle(15.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            actions.forEachIndexed { i, action ->
                MenuRow(
                    icon = action.icon(),
                    text = action.label(),
                    onClick = { onAction(action) },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            first.safeRequest()
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val a = animateFloatAsState(if (focused) 1f else 0f, tween(140), label = "row")
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .drawBehind {
                drawRoundRect(Color.White.copy(alpha = 0.95f * a.value), cornerRadius = CornerRadius(12.dp.toPx()))
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val fg = if (focused) Dz.Dark else Dz.Ink
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(text, style = dzStyle(14.sp, FontWeight.Medium, fg))
    }
}

/** Confirmation and help dialogs. */
@Composable
fun PromptDialog(prompt: Prompt, onDismiss: () -> Unit, onConfirmReset: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val first = remember { FocusRequester() }
        Column(
            Modifier
                .width(440.dp)
                .background(Dz.Panel, RoundedCornerShape(22.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(22.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (prompt) {
                Prompt.Reset -> {
                    Text("Reset layout?", style = dzStyle(20.sp, FontWeight.SemiBold))
                    Text(
                        "Favorites, their order, Recent and hidden apps go back to the start. Theme and other settings stay.",
                        style = dzStyle(13.sp, color = Dz.Ink2),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MenuButton("Cancel", onDismiss, Modifier.focusRequester(first))
                        MenuButton("Reset layout", onConfirmReset)
                    }
                }
                is Prompt.Info -> {
                    Text(prompt.title, style = dzStyle(20.sp, FontWeight.SemiBold))
                    Text(prompt.body, style = dzStyle(13.sp, color = Dz.Ink2))
                    MenuButton("OK", onDismiss, Modifier.focusRequester(first))
                }
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            first.safeRequest()
        }
    }
}

@Composable
private fun MenuButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(38.dp)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .background(if (focused) Color.White else Color.White.copy(alpha = 0.10f), RoundedCornerShape(19.dp))
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = dzStyle(13.sp, FontWeight.SemiBold, if (focused) Dz.Dark else Dz.Ink))
    }
}
