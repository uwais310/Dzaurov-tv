package com.dzaurov.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Real frames the box drew, from Android's own frame metrics (fed in MainActivity).
 * Only frames that were actually drawn count, so when nothing moves it shows a low number.
 */
object FrameStats {
    private const val SIZE = 360
    private val lock = Any()
    private val at = LongArray(SIZE)
    private val took = LongArray(SIZE)
    private var count = 0
    private var head = 0

    fun add(vsyncNs: Long, totalNs: Long) = synchronized(lock) {
        at[head] = vsyncNs
        took[head] = totalNs
        head = (head + 1) % SIZE
        if (count < SIZE) count++
    }

    class Snap(val fps: Int, val slowPct: Int, val worstMs: Int)

    /** Last two seconds: frames per second, share of frames slower than 25 ms, slowest frame. */
    fun snapshot(): Snap = synchronized(lock) {
        val window = 2_000_000_000L
        val now = System.nanoTime()
        var n = 0
        var slow = 0
        var worst = 0L
        for (i in 0 until count) {
            if (now - at[i] > window) continue
            n++
            if (took[i] > 25_000_000L) slow++
            if (took[i] > worst) worst = took[i]
        }
        Snap(n / 2, if (n == 0) 0 else slow * 100 / n, (worst / 1_000_000L).toInt())
    }
}

@Composable
fun FpsMeter(modifier: Modifier = Modifier) {
    val dm = LocalContext.current.resources.displayMetrics
    val hz = LocalView.current.display?.refreshRate ?: 60f
    val line by produceState("…") {
        while (true) {
            val s = FrameStats.snapshot()
            value = "${s.fps} fps · slow ${s.slowPct}% · worst ${s.worstMs} ms"
            delay(500)
        }
    }
    Box(
        modifier
            .padding(18.dp)
            .background(Color(0xD0000000), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = "$line\n${dm.widthPixels}×${dm.heightPixels} px · ${hz.roundToInt()} Hz",
            color = Color.White,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}
