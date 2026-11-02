package com.dzaurov.tv.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons drawn from SVG paths (24×24), so no icon library is needed. */
object DzIcons {
    private fun stroke(name: String, vararg paths: String) = build(name, filled = false, paths = paths)
    private fun fill(name: String, vararg paths: String) = build(name, filled = true, paths = paths)

    private fun build(name: String, filled: Boolean, paths: Array<out String>): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                for (d in paths) {
                    addPath(
                        pathData = addPathNodes(d),
                        fill = if (filled) SolidColor(Color.White) else null,
                        stroke = if (filled) null else SolidColor(Color.White),
                        strokeLineWidth = 1.8f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }
            .build()

    val Search = stroke("search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M20 20l-3.5-3.5")
    val Settings = stroke(
        "settings", "M4 7h10M18 7h2M4 17h4M12 17h8",
        "M16 5a2 2 0 1 0 0 4a2 2 0 1 0 0-4z", "M10 15a2 2 0 1 0 0 4a2 2 0 1 0 0-4z",
    )
    val Moon = stroke("moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    val Star = fill("star", "M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.3-4.1 5.9-.9z")
    val Plus = stroke("plus", "M12 5v14M5 12h14")
    val Play = fill("play", "M7 4.5v15a1 1 0 0 0 1.5.86l12.5-7.5a1 1 0 0 0 0-1.72L8.5 3.64A1 1 0 0 0 7 4.5z")
    val ChevronLeft = stroke("left", "M15 6l-6 6 6 6")
    val ChevronRight = stroke("right", "M9 6l6 6-6 6")
    val Info = stroke("info", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M12 11v5M12 8v.01")
    val EyeOff = stroke(
        "eyeoff", "M3 3l18 18",
        "M10.6 6.1A10 10 0 0 1 12 6c5 0 9 6 9 6a17 17 0 0 1-2.6 3.2M6.6 7.6C4.3 9.2 3 12 3 12s4 6 9 6a9 9 0 0 0 4-.9",
    )
    val Eye = stroke("eye", "M3 12s4-6 9-6 9 6 9 6-4 6-9 6-9-6-9-6z", "M12 9.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z")
    val Trash = stroke("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3")
    val Move = stroke("move", "M5 12h14M8 9l-3 3 3 3M16 9l3 3-3 3")
    val Image = stroke("image", "M3 5h18v14H3z", "M3 16l5-5 4 4 3-3 6 6", "M15.5 8.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z")
    val Reset = stroke("reset", "M4 4v6h6", "M20 12a8 8 0 1 1-2.3-5.7L4 10")
    val Home = stroke("home", "M4 11l8-7 8 7v9H4z")
    val Sparkle = stroke("sparkle", "M12 3v4M12 17v4M3 12h4M17 12h4M6.3 6.3l2 2M15.7 15.7l2 2M6.3 17.7l2-2M15.7 8.3l2-2")
    val Bolt = stroke("bolt", "M13 2L4 14h7l-1 8 9-12h-7z")
    val Screen = stroke("screen", "M3 5h18v12H3z", "M8 21h8M12 17v4")

    /** System settings: a cog, so it reads differently from the launcher's own sliders icon. */
    val Cog = stroke(
        "cog", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z",
        "M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1",
    )

    /** Control Center: two toggles. */
    val Toggles = stroke(
        "toggles", "M7 5h10a3.5 3.5 0 0 1 0 7H7a3.5 3.5 0 0 1 0-7z", "M7 12h10a3.5 3.5 0 0 1 0 7H7a3.5 3.5 0 0 1 0-7z",
        "M15.5 6.8a1.7 1.7 0 1 0 0 3.4a1.7 1.7 0 1 0 0-3.4z", "M8.5 13.8a1.7 1.7 0 1 0 0 3.4a1.7 1.7 0 1 0 0-3.4z",
    )
    val Bluetooth = stroke("bluetooth", "M7 7l10 10-5 4V3l5 4L7 17")
    val Sound = stroke("sound", "M4 9.5h3.5L12 6v12l-4.5-3.5H4z", "M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11")
    val Picture = stroke("picture", "M3 5h18v12H3z", "M8 21h8", "M7 13l3-3 2 2 3-3 2 2")
    val Timer = stroke("timer", "M12 6a7.5 7.5 0 1 0 0 15a7.5 7.5 0 1 0 0-15z", "M12 9.5v4l2.5 1.5", "M9.5 2.5h5M12 2.5V6")
    val Sleep = stroke("sleep", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z", "M14 4h4l-4 4h4")
    val Power = stroke("power", "M12 3v8", "M7 6.3a7.5 7.5 0 1 0 10 0")
    val Hand = stroke("hand", "M8 12V5.5a1.5 1.5 0 0 1 3 0V11", "M11 10V4.5a1.5 1.5 0 0 1 3 0V11", "M14 10.5V6a1.5 1.5 0 0 1 3 0v7c0 4-2.5 7-6 7-2.5 0-4-1.2-5.2-3.2L4 14a1.5 1.5 0 0 1 2.6-1.5L8 14")
    val Palette = stroke(
        "palette", "M12 3a9 9 0 0 0 0 18c1.2 0 1.8-.8 1.8-1.7 0-1.2-1-1.6-1-2.6 0-.9.7-1.7 1.8-1.7H17a4 4 0 0 0 4-4c0-4.4-4-8-9-8z",
        "M7.5 11v.01M10 7.5v.01M14.5 7.5v.01",
    )
    val Speaker = stroke("speaker", "M7 3h10v18H7z", "M12 13a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5z", "M12 7v.01")

    private const val WIFI_DOT = "M12 19.5v.01"
    private const val WIFI_1 = "M9.2 16.3a4 4 0 0 1 5.6 0"
    private const val WIFI_2 = "M6.4 13.3a8 8 0 0 1 11.2 0"
    private const val WIFI_3 = "M3.6 10.3a12 12 0 0 1 16.8 0"
    private const val WIFI_4 = "M1 7.3a16 16 0 0 1 22 0"

    /** Wi-Fi with 1..4 bars (index 0 = 1 bar). */
    val WifiBars: List<androidx.compose.ui.graphics.vector.ImageVector> = listOf(
        stroke("wifi1", WIFI_DOT, WIFI_1),
        stroke("wifi2", WIFI_DOT, WIFI_1, WIFI_2),
        stroke("wifi3", WIFI_DOT, WIFI_1, WIFI_2, WIFI_3),
        stroke("wifi4", WIFI_DOT, WIFI_1, WIFI_2, WIFI_3, WIFI_4),
    )
    val WifiOff = stroke("wifioff", WIFI_DOT, WIFI_1, WIFI_2, WIFI_3, "M3 3l18 18")
    val Ethernet = stroke("ethernet", "M4 6h16v9H4z", "M8 15v3h8v-3", "M8 9v2M12 9v2M16 9v2", "M12 18v3")

    fun network(kind: com.dzaurov.tv.data.NetKind, bars: Int): ImageVector = when (kind) {
        com.dzaurov.tv.data.NetKind.Ethernet -> Ethernet
        com.dzaurov.tv.data.NetKind.Offline -> WifiOff
        else -> WifiBars[(bars.coerceIn(1, 4)) - 1]
    }

    val Sun = stroke(
        "sun", "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z",
        "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4",
    )
    val MoonClear = stroke("moonclear", "M19 14.5A7 7 0 1 1 10 5a5.5 5.5 0 0 0 9 9.5z")
    val Cloud = stroke("cloud", "M7 18h10a4 4 0 0 0 .5-7.97A6 6 0 0 0 6.2 9.1 4.5 4.5 0 0 0 7 18z")
    val Rain = stroke("rain", "M7 14h10a4 4 0 0 0 .5-7.97A6 6 0 0 0 6.2 5.1 4.5 4.5 0 0 0 7 14z", "M9 17l-1 3M13 17l-1 3M17 17l-1 3")
    val Snow = stroke("snow", "M7 14h10a4 4 0 0 0 .5-7.97A6 6 0 0 0 6.2 5.1 4.5 4.5 0 0 0 7 14z", "M9 18v.01M13 18v.01M17 18v.01M11 21v.01M15 21v.01")
    val Fog = stroke("fog", "M4 9h16M6 13h12M4 17h16")
    val Storm = stroke("storm", "M7 14h10a4 4 0 0 0 .5-7.97A6 6 0 0 0 6.2 5.1 4.5 4.5 0 0 0 7 14z", "M13 15l-3 4h4l-3 4")

    /** WMO weather code (Open-Meteo) to an icon. */
    fun weather(code: Int, isDay: Boolean): ImageVector = when (code) {
        0, 1 -> if (isDay) Sun else MoonClear
        2, 3 -> Cloud
        45, 48 -> Fog
        in 51..67, in 80..82 -> Rain
        in 71..77, 85, 86 -> Snow
        in 95..99 -> Storm
        else -> Cloud
    }

    fun weatherLabel(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> ""
    }
}
