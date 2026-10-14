package ir.cheghadr.feature.assetdetails

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.cheghadr.core.designsystem.changeDownColor
import ir.cheghadr.core.designsystem.changeUpColor
import ir.cheghadr.core.model.HistoryPoint
import java.math.BigDecimal

/**
 * Interactive native line chart drawn on Compose Canvas:
 *  - touch/drag crosshair with value inspection,
 *  - time + price labels,
 *  - subtle gradient fill under the line.
 * Points come from real provider or locally stored observations — never
 * fabricated samples.
 */
@Composable
fun LineChart(
    points: List<HistoryPoint>,
    priceFormatter: (BigDecimal) -> String,
    timeFormatter: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return
    var selectedIndex by remember(points) { mutableStateOf<Int?>(null) }
    val upColor = changeUpColor()
    val downColor = changeDownColor()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)

    val lineColor = if (points.last().price >= points.first().price) upColor else downColor

    val minPrice = points.minOf { it.price }
    val maxPrice = points.maxOf { it.price }
    val priceRange = (maxPrice - minPrice).takeIf { it.signum() > 0 } ?: BigDecimal.valueOf(1)
    val minTs = points.first().timestamp
    val maxTs = points.last().timestamp
    val tsRange = (maxTs - minTs).coerceAtLeast(1L)

    fun xFor(index: Int, width: Float): Float =
        ((points[index].timestamp - minTs).toFloat() / tsRange) * (width - 2 * H_PAD) + H_PAD

    fun yFor(price: BigDecimal, height: Float): Float =
        (height - V_PAD - (price - minPrice).toFloat() / priceRange.toFloat() * (height - 2 * V_PAD))

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(points) {
                detectTapGestures { offset ->
                    selectedIndex = nearestIndex(offset.x, size.width.toFloat(), points) { i, w ->
                        xFor(i, w)
                    }
                }
            }
            .pointerInput(points) {
                detectHorizontalDragGestures { change, _ ->
                    selectedIndex = nearestIndex(change.position.x, size.width.toFloat(), points) { i, w ->
                        xFor(i, w)
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height

        // Horizontal gridlines with price labels.
        val steps = 3
        for (step in 0..steps) {
            val fraction = step.toFloat() / steps
            val y = V_PAD + fraction * (h - 2 * V_PAD)
            drawLine(gridColor, Offset(H_PAD, y), Offset(w - H_PAD, y), strokeWidth = 1f)
            val priceValue = maxPrice - priceRange.multiply(BigDecimal.valueOf(fraction.toDouble()))
            val label = priceFormatter(priceValue)
            val measured = textMeasurer.measure(label, labelStyle)
            // Draw on the native canvas via drawText extension.
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(w - H_PAD - measured.size.width, (y - measured.size.height / 2f).coerceIn(0f, h)),
            )
        }

        // The price line.
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = xFor(i, w)
            val y = yFor(p.price, h)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, lineColor, style = Stroke(width = with(density) { 2.dp.toPx() }))

        // Gradient fill under the line.
        val fill = Path().apply {
            addPath(path)
            lineTo(xFor(points.size - 1, w), h - V_PAD)
            lineTo(H_PAD, h - V_PAD)
            close()
        }
        drawPath(
            fill,
            Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.18f), Color.Transparent),
                startY = V_PAD,
                endY = h - V_PAD,
            ),
        )

        // Crosshair for the selected point.
        selectedIndex?.let { i ->
            val p = points.getOrNull(i) ?: return@let
            val x = xFor(i, w)
            val y = yFor(p.price, h)
            drawLine(gridColor, Offset(x, V_PAD), Offset(x, h - V_PAD), strokeWidth = 1.5f)
            drawCircle(lineColor, radius = with(density) { 4.dp.toPx() }, center = Offset(x, y))
            drawCircle(Color.White, radius = with(density) { 1.5.dp.toPx() }, center = Offset(x, y))

            // Value + time labels near the crosshair.
            val valueText = priceFormatter(p.price)
            val timeText = timeFormatter(p.timestamp)
            val valueLayout = textMeasurer.measure(valueText, TextStyle(fontSize = 11.sp, color = lineColor))
            val timeLayout = textMeasurer.measure(timeText, labelStyle)
            val valueX = (x - valueLayout.size.width / 2f).coerceIn(0f, w - valueLayout.size.width)
            drawText(valueLayout, topLeft = Offset(valueX, (y - valueLayout.size.height - 12f).coerceAtLeast(0f)))
            val timeX = (x - timeLayout.size.width / 2f).coerceIn(0f, w - timeLayout.size.width)
            drawText(timeLayout, topLeft = Offset(timeX, h - timeLayout.size.height))
        } ?: run {
            // First/last time labels when nothing is selected.
            val firstLabel = textMeasurer.measure(timeFormatter(points.first().timestamp), labelStyle)
            val lastLabel = textMeasurer.measure(timeFormatter(points.last().timestamp), labelStyle)
            drawText(firstLabel, topLeft = Offset(H_PAD, h - firstLabel.size.height))
            drawText(
                lastLabel,
                topLeft = Offset(w - H_PAD - lastLabel.size.width, h - lastLabel.size.height),
            )
        }
    }
}

private fun nearestIndex(
    x: Float,
    width: Float,
    points: List<HistoryPoint>,
    xFor: (Int, Float) -> Float,
): Int? {
    if (points.isEmpty() || width <= 0f) return null
    var best = 0
    var bestDistance = Float.MAX_VALUE
    for (i in points.indices) {
        val d = kotlin.math.abs(xFor(i, width) - x)
        if (d < bestDistance) {
            bestDistance = d
            best = i
        }
    }
    return best
}

private const val H_PAD = 8f
private const val V_PAD = 24f
