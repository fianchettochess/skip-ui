// Copyright 2023–2026 Skip
// SPDX-License-Identifier: MPL-2.0
package skip.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil

/// Compose content once and record only the drawing needed for its drop shadow.
@Composable fun Shadowed(context: ComposeContext, color: Color, offsetX: Dp, offsetY: Dp, blurRadius: Dp, content: @Composable (ComposeContext) -> Unit) {
    val density = LocalDensity.current
    val offsetXPx = with(density) { offsetX.toPx() }.toInt()
    val offsetYPx = with(density) { offsetY.toPx() }.toInt()
    val blurPx = with(density) { blurRadius.toPx() }
    val paddingPx = ceil(blurPx.coerceAtLeast(0f)).toInt()
    val colorFilter = remember(color) { ColorFilter.colorMatrix(shadowColorMatrix(color)) }
    val blurEffect = remember(blurPx) {
        if (blurPx > 0f) BlurEffect(renderEffect = null, radiusX = blurPx, radiusY = blurPx, edgeTreatment = TileMode.Decal) else null
    }
    val shadowLayer = rememberGraphicsLayer()
    val contentContext = context.content()

    Layout(modifier = context.modifier.drawWithContent {
        // Padding belongs to the recorded shadow, not the measured content.
        // It preserves the unbounded blur without adding an accessibility,
        // input or effect-producing copy of the composable subtree.
        shadowLayer.record(size = IntSize(size.width.toInt() + paddingPx * 2, size.height.toInt() + paddingPx * 2)) {
            translate(paddingPx.toFloat(), paddingPx.toFloat()) {
                this@drawWithContent.drawContent()
            }
        }
        shadowLayer.colorFilter = colorFilter
        shadowLayer.renderEffect = blurEffect
        translate((offsetXPx - paddingPx).toFloat(), (offsetYPx - paddingPx).toFloat()) {
            drawLayer(shadowLayer)
        }
        drawContent()
    }, content = {
        content(contentContext)
    }) { measurables, constraints ->
        val contentPlaceable = measurables.single().measure(constraints)
        layout(width = contentPlaceable.width, height = contentPlaceable.height) {
            contentPlaceable.placeRelative(x = 0, y = 0)
        }
    }
}

/// Return a color matrix with which to paint our content as a shadow of the given color.
private fun shadowColorMatrix(color: Color): ColorMatrix {
    return ColorMatrix().apply {
        set(0, 0, 0f) // Do not preserve original R
        set(1, 1, 0f) // Do not preserve original G
        set(2, 2, 0f) // Do not preserve original B

        set(0, 4, color.red * 255) // Use given color's R
        set(1, 4, color.green * 255) // Use given color's G
        set(2, 4, color.blue * 255) // Use given color's B
        set(3, 3, color.alpha) // Multiply original alpha by shadow color alpha
    }
}
