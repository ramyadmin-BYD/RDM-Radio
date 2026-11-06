package com.radiocompanion.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Faint lattice of eight-pointed stars (khatam), the motif of mashrabiya screens and
 * Ramadan lanterns. Drawn behind content at very low contrast so text stays crisp.
 */
fun Modifier.ramadanPattern(color: Color, cell: Dp = 84.dp): Modifier = drawWithCache {
    val c = cell.toPx()
    val star = eightPointStar(radius = c * 0.30f)
    val dot = c * 0.035f
    val stroke = Stroke(width = 1.2.dp.toPx())
    onDrawBehind {
        var row = 0
        var y = 0f
        while (y < size.height + c) {
            var x = if (row % 2 == 0) 0f else c / 2
            while (x < size.width + c) {
                translate(x, y) { drawPath(star, color, style = stroke) }
                drawCircle(color, dot, Offset(x + c / 2, y))
                x += c
            }
            y += c * 0.5f
            row++
        }
    }
}

/** Two overlapping squares, the second turned 45°: the Rub el Hizb outline. */
private fun eightPointStar(radius: Float): Path {
    val path = Path()
    for (k in 0..1) {
        val offset = k * PI / 4
        for (i in 0..3) {
            val a = offset + i * PI / 2
            val x = (radius * cos(a)).toFloat()
            val y = (radius * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
    return path
}

/** Crescent moon with a small star, for the Ramadan banner and drawer header. */
@Composable
fun Crescent(color: Color, diameter: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(diameter)) {
        val d = diameter.toPx()
        val outer = Path().apply { addOval(Rect(0f, 0f, d * 0.82f, d * 0.82f).translate(Offset(0f, d * 0.09f))) }
        val inner = Path().apply { addOval(Rect(0f, 0f, d * 0.68f, d * 0.68f).translate(Offset(d * 0.24f, d * 0.02f))) }
        drawPath(Path.combine(PathOperation.Difference, outer, inner), color)
        val star = eightPointStar(d * 0.11f)
        translate(d * 0.80f, d * 0.30f) { drawPath(star, color) }
    }
}
