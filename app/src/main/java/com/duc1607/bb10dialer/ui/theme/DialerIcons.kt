package com.duc1607.bb10dialer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The phone app's own glyphs, drawn in code on the kit's 24 grid (no BlackBerry assets). Unlike
 * the kit's line icons most of these are solid, because that is how the phone app draws them.
 */
object DialerIcons {
    private const val HANDSET =
        "M6 3C6.6 2.6 7.4 2.7 7.8 3.3L10 6.8C10.3 7.3 10.2 8 9.8 8.4L8.4 9.8C9.4 11.9 12.1 14.6 14.2 15.6" +
            "L15.6 14.2C16 13.8 16.7 13.7 17.2 14L20.7 16.2C21.3 16.6 21.4 17.4 21 18L19.6 20" +
            "C19 20.9 17.9 21.3 16.9 21C10.4 19.3 4.7 13.6 3 7.1C2.7 6.1 3.1 5 4 4.4Z"

    /** Head and shoulders, with the collar left open. */
    internal const val FIGURE =
        "M12 2C9.4 2 7.6 4.1 7.6 7.2C7.6 10.8 9.6 14 12 14C14.4 14 16.4 10.8 16.4 7.2C16.4 4.1 14.6 2 12 2Z" +
            "M8.8 14.9L4.6 17C3.4 17.6 3 18.3 2.8 19.2L2 22H22L21.2 19.2C21 18.3 20.6 17.6 19.4 17L15.2 14.9" +
            "L12 18.2Z"

    val Phone = solid("phone", HANDSET)
    val Person = solid("person", FIGURE)
    val Star = solid("star", "M12 2.5L14.9 8.9L21.9 9.6L16.7 14.3L18.2 21.2L12 17.6L5.8 21.2L7.3 14.3L2.1 9.6L9.1 8.9Z")

    val Outgoing = callIcon("outgoing", "M14 9.5L21 2.5M15.5 2.5H21V8")
    val Incoming = callIcon("incoming", "M21 2.5L14.5 9M14.5 3.5V9H20")
    val Missed = callIcon("missed", "M15 3L21 9M21 3L15 9")

    /** The big figure behind a call without a photo: a head with ears, over open shoulders. */
    val CallFigure = ImageVector.Builder("callFigure", 24.dp, 24.dp, 24f, 24f).apply {
        group(scaleX = 0.78f, scaleY = 0.78f, pivotX = 12f) {
            addPath(
                addPathNodes(
                    "M12 2.5C8.8 2.5 7.2 4.8 7.2 8V9.4C6.6 9.4 6.4 10 6.5 10.9C6.7 12 7.1 12.8 7.7 12.9" +
                        "C8.3 15.2 10.2 17.2 12 17.2C13.8 17.2 15.7 15.2 16.3 12.9C16.9 12.8 17.3 12 17.5 10.9" +
                        "C17.6 10 17.4 9.4 16.8 9.4V8C16.8 4.8 15.2 2.5 12 2.5Z" +
                        "M9.6 17.4L2.5 20.6V31H21.5V20.6L14.4 17.4L12 20Z",
                ),
                fill = SolidColor(Color.Black),
            )
        }
    }.build()

    val GitHub = solid("github", "M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12Z")
    val Play = solid("play", "M7 4L20 12L7 20Z")
    val Pause = solid("pause", "M6.5 5H10.5V19H6.5ZM13.5 5H17.5V19H13.5Z")
    val Block = stroke("block", 2f, "M12 3A9 9 0 1 1 12 21A9 9 0 1 1 12 3ZM5.6 5.6L18.4 18.4")

    val Forward = callIcon("forward", "M13.5 6H21.5M18 2.5L21.5 6L18 9.5")
    val CallWaiting = callIcon("callWaiting", "M15.5 2.5V9.5M20 2.5V9.5")
    val ReplyCall = callIcon("replyCall", "M13.5 2.5H21.5V8H16.5L14.5 10V8H13.5Z")
    val LockedCall = callIcon("lockedCall", "M14.5 5.5H21V10.5H14.5ZM16 5.5V3.5A1.75 1.75 0 0 1 19.5 3.5V5.5")
    val Bell = solid("bell", "M12 2.5A6.5 6.5 0 0 0 5.5 9V14L3.5 17.5H20.5L18.5 14V9A6.5 6.5 0 0 0 12 2.5ZM9.5 19A2.5 2.5 0 0 0 14.5 19Z")
    val Bluetooth = stroke("bluetooth", 2f, "M6.5 7L17 16.5L12 21.5V2.5L17 7.5L6.5 17")
    val Blocking = solid("blocking", "M12 2A10 10 0 1 1 12 22A10 10 0 1 1 12 2ZM6 10.5V13.5H18V10.5Z")
    val Hash = stroke("hash", 2.6f, "M9.5 3L7.5 21M16.5 3L14.5 21M4 8.5H20.5M3 15.5H19.5")
    val Clipboard = stroke("clipboard", 2f, "M5 4.5H19V21.5H5ZM9 2.5H15V6H9ZM8.5 10.5H15.5M8.5 15H15.5")
    val Ear = stroke("ear", 2f, "M7 10A5.5 5.5 0 0 1 18 10C18 13 15 14 14.5 16.5C14 19 12.5 20.5 10.5 20.5C9 20.5 8 19.5 7.5 18.5M10 10A2.5 2.5 0 0 1 15 10")
    val Tty = solid("tty", "M3 8C3 5 8 4 12 4C16 4 21 5 21 8V10.5H16V8.5H8V10.5H3ZM3 12.5H7V15H3ZM8.5 12.5H12V15H8.5ZM13.5 12.5H17V15H13.5ZM18.5 12.5H21V15H18.5ZM3 16.5H9V19H3ZM10.5 16.5H21V19H10.5Z")

    val AddCall = callIcon("addCall", "M18 2.5V9.5M14.5 6H21.5")

    /** The handset lying down, with its ends pointing at the floor. */
    val HangUp = ImageVector.Builder("hangUp", 24.dp, 24.dp, 24f, 24f).apply {
        group(rotate = 135f, pivotX = 12f, pivotY = 12f) {
            addPath(addPathNodes(HANDSET), fill = SolidColor(Color.Black))
        }
    }.build()

    val Mute = stroke(
        "mute", 1.8f,
        "M12 3A3 3 0 0 0 9 6V12A3 3 0 0 0 15 12V6A3 3 0 0 0 12 3Z" +
            "M5.5 11.5A6.5 6.5 0 0 0 18.5 11.5M12 18V21M8.5 21H15.5M4 3L20 21",
    )

    /** A note, struck out. */
    val RingerOff = stroke(
        "ringerOff", 1.8f,
        "M9 18V5.5L19 3.5V16M9 18A2.5 2.5 0 1 1 4 18A2.5 2.5 0 1 1 9 18Z" +
            "M19 16A2.5 2.5 0 1 1 14 16A2.5 2.5 0 1 1 19 16ZM3 3L21 21",
    )

    /** A dot in a ring. */
    val Record = solid(
        "record",
        "M12 2A10 10 0 1 1 12 22A10 10 0 1 1 12 2Z" +
            "M12 4A8 8 0 1 1 12 20A8 8 0 1 1 12 4Z" +
            "M12 7.5A4.5 4.5 0 1 1 12 16.5A4.5 4.5 0 1 1 12 7.5Z",
    )

    val CallsTab = stroke("callsTab", 2.6f, "M4.5 13L12 5.5M6.5 4.5H13V11M19.5 11L12 18.5M17.5 19.5H11V13")

    val DialPad = ImageVector.Builder("dialPad", 24.dp, 24.dp, 24f, 24f).apply {
        val cells = buildString {
            for (row in 0..2) for (col in 0..2) {
                val x = 2f + col * 7f
                val y = 2.5f + row * 5f
                append("M$x ${y}H${x + 6}V${y + 4}H${x}Z")
            }
        }
        addPath(addPathNodes(cells), fill = SolidColor(Color.Black))
        val last = (0..2).joinToString("") { col ->
            val x = 2.5f + col * 7f
            "M$x 18H${x + 5}V21H${x}Z"
        }
        addPath(addPathNodes(last), stroke = SolidColor(Color.Black), strokeLineWidth = 1f)
    }.build()

    /** Twice as wide as the other icons, on a 36 x 16 grid: give it a wide box. */
    val Voicemail = ImageVector.Builder("voicemail", 36.dp, 16.dp, 36f, 16f).apply {
        addPath(
            addPathNodes(
                "M14.6 8A6.8 6.8 0 1 1 1 8A6.8 6.8 0 1 1 14.6 8Z" +
                    "M35 8A6.8 6.8 0 1 1 21.4 8A6.8 6.8 0 1 1 35 8ZM7.8 14.8H28.2",
            ),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.4f,
        )
    }.build()

    /** Filled bubble with two lines cut out. */
    val Message = solid(
        "message",
        "M3 4H21V17H11L6.5 21.5V17H3Z" +
            "M6 7.6V9H18V7.6ZM6 11.4V12.8H18V11.4Z",
    )

    val AddContact = ImageVector.Builder("addContact", 24.dp, 24.dp, 24f, 24f).apply {
        group(scaleX = 0.82f, scaleY = 0.82f) {
            addPath(addPathNodes(FIGURE), fill = SolidColor(Color.Black))
        }
        addPath(
            addPathNodes("M19 15V23M15 19H23"),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
        )
    }.build()

    /** A contact card with its corner curling back. */
    val ContactCard = ImageVector.Builder("contactCard", 24.dp, 24.dp, 24f, 24f).apply {
        // The figure is a hole: icons are tinted in one colour.
        addPath(
            addPathNodes(
                "M6 2H22V18H6Z" +
                    "M14 4.2C12.5 4.2 11.4 5.5 11.4 7.4C11.4 9.6 12.6 11.6 14 11.6C15.4 11.6 16.6 9.6 16.6 7.4" +
                    "C16.6 5.5 15.5 4.2 14 4.2Z" +
                    "M8.5 16.5L9.2 14.4L12.2 12.6L14 14.2L15.8 12.6L18.8 14.4L19.5 16.5Z",
            ),
            pathFillType = PathFillType.EvenOdd,
            fill = SolidColor(Color.Black),
        )
        addPath(
            addPathNodes("M3.5 16V20.5H8"),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
        )
    }.build()

    val Device = stroke(
        "device", 1.8f,
        "M6 2.5H18V21.5H6ZM8.8 6H15.2V11H8.8ZM8.8 14H15.2M8.8 17H15.2",
    )

    val ToTop = stroke("toTop", 3f, "M3 3.5H21M4 18L12 9L20 18")
    val ToBottom = stroke("toBottom", 3f, "M3 20.5H21M4 6L12 15L20 6")
    val ClearAll = stroke("clearAll", 2f, "M3 3H21V21H3ZM7.5 7.5L16.5 16.5M16.5 7.5L7.5 16.5")
    val ChangeLine = stroke(
        "changeLine", 2f,
        "M8 3.5L5.5 20.5M14 3.5L11.5 20.5M3 8.5H16M2 15H15M15.5 12H23M20 9L23 12L20 15",
    )

    val Settings = run {
        // A gear: eight teeth around a ring with a round hole.
        val outer = 10.5f
        val inner = 7.8f
        val path = buildString {
            val teeth = 8
            for (tooth in 0 until teeth) {
                val step = 2 * PI / teeth
                val a = tooth * step
                fun point(radius: Float, angle: Double) =
                    "${12 + radius * cos(angle).toFloat()} ${12 + radius * sin(angle).toFloat()}"
                append(if (tooth == 0) "M" else "L").append(point(inner, a - step * 0.30))
                append("L").append(point(outer, a - step * 0.17))
                append("L").append(point(outer, a + step * 0.17))
                append("L").append(point(inner, a + step * 0.30))
            }
            append("Z")
            append("M15.4 12A3.4 3.4 0 1 0 8.6 12A3.4 3.4 0 1 0 15.4 12Z")
        }
        solid("settings", path)
    }

    /** A key with a cross cut out of it. */
    val Backspace = run {
        val cx = 15f
        val cy = 12f
        val arm = 4f
        val half = 0.9f
        // The cross, as one outline: its twelve corners rotated by 45 degrees.
        val corners = listOf(
            -half to -arm, half to -arm, half to -half, arm to -half, arm to half, half to half,
            half to arm, -half to arm, -half to half, -arm to half, -arm to -half, -half to -half,
        )
        val root = 0.7071f
        val cross = corners.mapIndexed { i, (x, y) ->
            val px = cx + (x - y) * root
            val py = cy + (x + y) * root
            "${if (i == 0) "M" else "L"}$px $py"
        }.joinToString("") + "Z"
        solid("backspace", "M8 5H23V19H8L1.5 12Z$cross")
    }

    private fun solid(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                addPathNodes(path),
                pathFillType = PathFillType.EvenOdd,
                fill = SolidColor(Color.Black),
            )
        }.build()

    private fun stroke(name: String, width: Float, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
            )
        }.build()

    /** The handset, a little smaller, with a sign for the direction in the top right corner. */
    private fun callIcon(name: String, sign: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            group(scaleX = 0.88f, scaleY = 0.88f, translationY = 2.9f) {
                addPath(addPathNodes(HANDSET), fill = SolidColor(Color.Black))
            }
            addPath(
                addPathNodes(sign),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineJoin = StrokeJoin.Miter,
            )
        }.build()
}
