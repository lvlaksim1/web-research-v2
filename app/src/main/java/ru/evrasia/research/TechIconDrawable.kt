package ru.evrasia.research

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Small internal vector icon set used by the browser and debugger chrome.
 *
 * Geometry is drawn in a 24x24 virtual viewport. The default content scale
 * produces a large, optically centered glyph inside a 48dp touch target, matching Android
 * icon-button proportions without depending on font glyphs or external assets.
 */
class TechIconDrawable(
    private val kind: Kind,
    private val color: Int,
    private val contentScale: Float = 0.88f
) : Drawable() {
    enum class Kind {
        MENU,
        NAVIGATE,
        RELOAD,
        STOP,
        NETWORK,
        BACK,
        FILTER,
        SEARCH,
        RECORD,
        DELETE,
        CLOSE,
        BOOKMARK_ADD,
        BOOKMARKS,
        COOKIE,
        APPEARANCE,
        THEME,
        COLOR,
        INFO,
        CHEVRON_RIGHT,
        EXPAND_MORE,
        EXPAND_LESS
    }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.05f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = this@TechIconDrawable.color
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = this@TechIconDrawable.color
    }
    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val visual = minOf(b.width(), b.height()) * contentScale.coerceIn(0.35f, 0.96f)
        val scale = visual / VIEWPORT
        canvas.save()
        canvas.translate(b.exactCenterX() - HALF * scale, b.exactCenterY() - HALF * scale)
        canvas.scale(scale, scale)
        path.reset()

        when (kind) {
            Kind.MENU -> {
                canvas.drawLine(5.5f, 7f, 18.5f, 7f, stroke)
                canvas.drawLine(5.5f, 12f, 18.5f, 12f, stroke)
                canvas.drawLine(5.5f, 17f, 18.5f, 17f, stroke)
            }

            Kind.NAVIGATE -> {
                canvas.drawLine(5.5f, 12f, 18f, 12f, stroke)
                canvas.drawLine(13.5f, 7.5f, 18f, 12f, stroke)
                canvas.drawLine(18f, 12f, 13.5f, 16.5f, stroke)
            }

            Kind.BACK -> {
                canvas.drawLine(18.5f, 12f, 6f, 12f, stroke)
                canvas.drawLine(10.5f, 7.5f, 6f, 12f, stroke)
                canvas.drawLine(6f, 12f, 10.5f, 16.5f, stroke)
            }

            Kind.RELOAD -> {
                canvas.drawArc(RectF(5.2f, 5.2f, 18.8f, 18.8f), 40f, 285f, false, stroke)
                canvas.drawLine(17.6f, 8.2f, 14.2f, 7.0f, stroke)
                canvas.drawLine(17.6f, 8.2f, 16.6f, 11.5f, stroke)
            }

            Kind.STOP -> canvas.drawRoundRect(RectF(6.8f, 6.8f, 17.2f, 17.2f), 2f, 2f, fill)

            Kind.RECORD -> canvas.drawCircle(12f, 12f, 5.2f, fill)

            Kind.NETWORK -> {
                canvas.drawLine(7.1f, 7.6f, 10.5f, 10.7f, stroke)
                canvas.drawLine(16.9f, 7.6f, 13.5f, 10.7f, stroke)
                canvas.drawLine(12f, 15.2f, 12f, 17f, stroke)
                canvas.drawCircle(6.4f, 7f, 1.7f, fill)
                canvas.drawCircle(17.6f, 7f, 1.7f, fill)
                canvas.drawCircle(12f, 12.2f, 2f, fill)
                canvas.drawCircle(12f, 18f, 1.7f, fill)
            }

            Kind.FILTER -> {
                path.moveTo(5.5f, 6.5f)
                path.lineTo(18.5f, 6.5f)
                path.lineTo(13.5f, 12.2f)
                path.lineTo(13.5f, 17.2f)
                path.lineTo(10.5f, 18.5f)
                path.lineTo(10.5f, 12.2f)
                path.close()
                canvas.drawPath(path, stroke)
            }

            Kind.SEARCH -> {
                canvas.drawCircle(10.4f, 10.4f, 4.8f, stroke)
                canvas.drawLine(14f, 14f, 18.5f, 18.5f, stroke)
            }

            Kind.DELETE -> {
                canvas.drawLine(7f, 7.4f, 17f, 7.4f, stroke)
                canvas.drawLine(9.5f, 5.5f, 14.5f, 5.5f, stroke)
                canvas.drawLine(8.4f, 9.2f, 9.2f, 18f, stroke)
                canvas.drawLine(15.6f, 9.2f, 14.8f, 18f, stroke)
                canvas.drawLine(9.2f, 18f, 14.8f, 18f, stroke)
                canvas.drawLine(11f, 10.2f, 11.2f, 15.8f, stroke)
                canvas.drawLine(13f, 10.2f, 12.8f, 15.8f, stroke)
            }

            Kind.CLOSE -> {
                val normalWidth = stroke.strokeWidth
                stroke.strokeWidth = 2.4f
                canvas.drawLine(3.8f, 3.8f, 20.2f, 20.2f, stroke)
                canvas.drawLine(20.2f, 3.8f, 3.8f, 20.2f, stroke)
                stroke.strokeWidth = normalWidth
            }

            Kind.BOOKMARK_ADD -> {
                path.moveTo(7f, 5.5f)
                path.lineTo(15.5f, 5.5f)
                path.lineTo(15.5f, 18.5f)
                path.lineTo(11.25f, 15.6f)
                path.lineTo(7f, 18.5f)
                path.close()
                canvas.drawPath(path, stroke)
                canvas.drawLine(17.8f, 6f, 17.8f, 10f, stroke)
                canvas.drawLine(15.8f, 8f, 19.8f, 8f, stroke)
            }

            Kind.BOOKMARKS -> {
                path.moveTo(8f, 5.5f)
                path.lineTo(16.5f, 5.5f)
                path.lineTo(16.5f, 18.5f)
                path.lineTo(12.25f, 15.6f)
                path.lineTo(8f, 18.5f)
                path.close()
                canvas.drawPath(path, stroke)
                canvas.drawLine(6f, 7f, 6f, 17f, stroke)
            }

            Kind.COOKIE -> {
                canvas.drawArc(RectF(5f, 5f, 19f, 19f), 42f, 285f, false, stroke)
                canvas.drawCircle(9.1f, 10f, 1f, fill)
                canvas.drawCircle(12.2f, 15f, 1f, fill)
                canvas.drawCircle(14.4f, 10.7f, .9f, fill)
                canvas.drawCircle(17.6f, 7.1f, 1.3f, stroke)
            }

            Kind.APPEARANCE -> {
                canvas.drawLine(5.5f, 7.5f, 18.5f, 7.5f, stroke)
                canvas.drawLine(5.5f, 12f, 18.5f, 12f, stroke)
                canvas.drawLine(5.5f, 16.5f, 18.5f, 16.5f, stroke)
                canvas.drawCircle(9f, 7.5f, 1.7f, fill)
                canvas.drawCircle(15f, 12f, 1.7f, fill)
                canvas.drawCircle(11.5f, 16.5f, 1.7f, fill)
            }

            Kind.THEME -> {
                canvas.drawCircle(12f, 12f, 3.3f, stroke)
                for (angle in 0 until 360 step 45) {
                    val rad = Math.toRadians(angle.toDouble())
                    val x1 = 12f + (5.5f * kotlin.math.cos(rad)).toFloat()
                    val y1 = 12f + (5.5f * kotlin.math.sin(rad)).toFloat()
                    val x2 = 12f + (7.2f * kotlin.math.cos(rad)).toFloat()
                    val y2 = 12f + (7.2f * kotlin.math.sin(rad)).toFloat()
                    canvas.drawLine(x1, y1, x2, y2, stroke)
                }
            }

            Kind.COLOR -> {
                canvas.drawCircle(12f, 12f, 7f, stroke)
                canvas.drawCircle(9f, 9f, 1.25f, fill)
                canvas.drawCircle(14.3f, 8.3f, 1.25f, fill)
                canvas.drawCircle(15.7f, 13.3f, 1.25f, fill)
                canvas.drawCircle(10.4f, 15.3f, 1.25f, fill)
            }

            Kind.INFO -> {
                canvas.drawCircle(12f, 12f, 7f, stroke)
                canvas.drawCircle(12f, 8.4f, 1f, fill)
                canvas.drawLine(12f, 11f, 12f, 16f, stroke)
            }

            Kind.CHEVRON_RIGHT -> {
                canvas.drawLine(9.5f, 7.4f, 14.5f, 12f, stroke)
                canvas.drawLine(14.5f, 12f, 9.5f, 16.6f, stroke)
            }

            Kind.EXPAND_MORE -> {
                canvas.drawLine(7.4f, 9.5f, 12f, 14f, stroke)
                canvas.drawLine(12f, 14f, 16.6f, 9.5f, stroke)
            }

            Kind.EXPAND_LESS -> {
                canvas.drawLine(7.4f, 14.5f, 12f, 10f, stroke)
                canvas.drawLine(12f, 10f, 16.6f, 14.5f, stroke)
            }
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        stroke.alpha = alpha
        fill.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        stroke.colorFilter = colorFilter
        fill.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    companion object {
        private const val VIEWPORT = 24f
        private const val HALF = VIEWPORT / 2f
    }
}
