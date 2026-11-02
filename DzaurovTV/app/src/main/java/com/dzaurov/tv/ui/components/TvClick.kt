package com.dzaurov.tv.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput

const val LONG_PRESS_MS = 550L

fun Key.isSelect(): Boolean =
    this == Key.DirectionCenter || this == Key.Enter || this == Key.NumPadEnter || this == Key.ButtonA

private class PressState {
    var down = false
    var downAt = 0L
    var longFired = false
}

/**
 * Remote-friendly click: OK clicks on release, holding OK fires [onLongClick] once.
 * A key-up that belongs to a press started elsewhere (on another screen) is ignored.
 * Taps and long taps from an air mouse work too.
 */
fun Modifier.tvClickable(
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val st = remember { PressState() }
    val click = rememberUpdatedState(onClick)
    val long = rememberUpdatedState(onLongClick)
    val on = rememberUpdatedState(enabled)
    this
        .onPreviewKeyEvent { ev ->
            if (!on.value || !ev.key.isSelect()) return@onPreviewKeyEvent false
            val n = ev.nativeKeyEvent
            when (ev.type) {
                KeyEventType.KeyDown -> {
                    if (n.repeatCount == 0) {
                        st.down = true
                        st.downAt = n.eventTime
                        st.longFired = false
                    } else {
                        val l = long.value
                        if (l != null && st.down && !st.longFired && (n.isLongPress || n.eventTime - st.downAt >= LONG_PRESS_MS)) {
                            st.longFired = true
                            l()
                        }
                    }
                    true
                }
                KeyEventType.KeyUp -> {
                    val wasDown = st.down
                    st.down = false
                    if (wasDown && !st.longFired) click.value()
                    true
                }
                else -> false
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { if (on.value) click.value() },
                onLongPress = { if (on.value) long.value?.invoke() },
            )
        }
        .focusable(enabled)
}

/** requestFocus that never crashes when the target is not on screen yet. */
fun FocusRequester.safeRequest(): Boolean = runCatching { requestFocus() }.isSuccess
