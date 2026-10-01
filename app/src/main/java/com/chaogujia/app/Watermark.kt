/*
 * Watermark
 */
package com.chaogujia.app

import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val WATERMARK_COLOR = 0x26000000
private const val WATERMARK_TEXT_SP = 16f
private const val WATERMARK_ROTATE = -30f

@Composable
fun WatermarkLayer(enabled: Boolean, text: String) {
    if (!enabled || text.isEmpty()) return
    AndroidView(
        modifier = Modifier,
        factory = { ctx ->
            object : View(ctx) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = WATERMARK_COLOR
                    textSize = WATERMARK_TEXT_SP * resources.displayMetrics.scaledDensity
                    isFakeBoldText = true
                }
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val w = width.toFloat()
                    val h = height.toFloat()
                    if (w <= 0f || h <= 0f) return
                    val stepX = paint.measureText(text) + 120f
                    val stepY = stepX * 0.7f
                    canvas.save()
                    canvas.rotate(WATERMARK_ROTATE, w / 2f, h / 2f)
                    var y = -h
                    while (y < h * 2) {
                        var x = -w
                        while (x < w * 2) {
                            canvas.drawText(text, x, y, paint)
                            x += stepX
                        }
                        y += stepY
                    }
                    canvas.restore()
                }
            }
        },
        update = { it.invalidate() }
    )
}