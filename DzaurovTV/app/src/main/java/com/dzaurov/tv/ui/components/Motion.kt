package com.dzaurov.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * The last arrow pressed on the remote. Set by the activity for every D-pad press, read
 * when a tile gains focus so it can "swing in" from that side (like tvOS icons).
 */
object NavDir {
    const val NONE = 0
    const val LEFT = 1
    const val RIGHT = 2
    const val UP = 3
    const val DOWN = 4

    @Volatile
    var last: Int = NONE
}

/** iOS-style wiggle for edit mode: a quick back-and-forth, each tile slightly out of step. */
@Composable
fun rememberJiggle(seed: String): State<Float> {
    val t = rememberInfiniteTransition(label = "jiggle")
    val duration = 120 + (seed.hashCode() and 31)
    return t.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(duration, easing = LinearEasing), RepeatMode.Reverse),
        label = "jiggleAngle",
    )
}
