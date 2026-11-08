package com.citeraloka.phone

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.sin

object UiIcons {

    enum class Type {
        FILTER,
        SETTINGS,
        SEARCH,
        CLOSE,
        BACK,
        PLAY,
        INFO,
        UPDATE,
        TRASH,
        ASPECT,
        EPISODES,
        SERVER
    }

    fun drawable(
        context: Context,
        type: Type,
        sizeDp: Int = 16,
        colorInt: Int = Color.parseColor("#F0FDFA")
    ): VectorIconDrawable {
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            sizeDp.toFloat(),
            context.resources.displayMetrics
        ).toInt().coerceAtLeast(12)
        val strokePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            1.8f,
            context.resources.displayMetrics
        )
        return VectorIconDrawable(type, px, strokePx, colorInt)
    }

    fun attachIcon(
        view: TextView,
        type: Type,
        sizeDp: Int = 15,
        paddingDp: Int = 6,
        colorInt: Int = view.currentTextColor
    ) {
        val d = drawable(view.context, type, sizeDp, colorInt)
        view.setCompoundDrawablesWithIntrinsicBounds(d, null, null, null)
        val padPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            if (view.text.isNullOrEmpty()) 0f else paddingDp.toFloat(),
            view.context.resources.displayMetrics
        ).toInt()
        view.compoundDrawablePadding = padPx
    }

    fun syncIconTint(view: TextView, colorInt: Int = view.currentTextColor) {
        view.compoundDrawables.forEach { d ->
            if (d is VectorIconDrawable) {
                d.setTintColor(colorInt)
            }
        }
    }

    class VectorIconDrawable(
        private val type: Type,
        private val sizePx: Int,
        private val strokeWidthPx: Float,
        initialColor: Int
    ) : Drawable() {

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = initialColor
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = initialColor
        }

        fun setTintColor(colorInt: Int) {
            strokePaint.color = colorInt
            fillPaint.color = colorInt
            invalidateSelf()
        }

        override fun getIntrinsicWidth(): Int = sizePx
        override fun getIntrinsicHeight(): Int = sizePx

        override fun draw(canvas: Canvas) {
            val b = bounds
            val w = (if (b.width() > 0) b.width() else sizePx).toFloat()
            val h = (if (b.height() > 0) b.height() else sizePx).toFloat()
            val l = b.left.toFloat()
            val t = b.top.toFloat()
            val cx = l + w * 0.5f
            val cy = t + h * 0.5f

            when (type) {
                Type.FILTER -> {
                    val x1 = l + w * 0.14f
                    val x2 = l + w * 0.86f
                    val yTop = t + h * 0.25f
                    val yMid = t + h * 0.50f
                    val yBot = t + h * 0.75f
                    val r = w * 0.09f

                    canvas.drawLine(x1, yTop, x2, yTop, strokePaint)
                    canvas.drawCircle(l + w * 0.36f, yTop, r, fillPaint)

                    canvas.drawLine(x1, yMid, x2, yMid, strokePaint)
                    canvas.drawCircle(l + w * 0.66f, yMid, r, fillPaint)

                    canvas.drawLine(x1, yBot, x2, yBot, strokePaint)
                    canvas.drawCircle(l + w * 0.44f, yBot, r, fillPaint)
                }

                Type.SETTINGS -> {
                    val outerR = w * 0.30f
                    val innerR = w * 0.13f
                    val toothInner = w * 0.28f
                    val toothOuter = w * 0.42f
                    for (i in 0 until 8) {
                        val angle = Math.toRadians((i * 45).toDouble())
                        val sx = cx + (toothInner * cos(angle)).toFloat()
                        val sy = cy + (toothInner * sin(angle)).toFloat()
                        val ex = cx + (toothOuter * cos(angle)).toFloat()
                        val ey = cy + (toothOuter * sin(angle)).toFloat()
                        canvas.drawLine(sx, sy, ex, ey, strokePaint)
                    }
                    canvas.drawCircle(cx, cy, outerR, strokePaint)
                    canvas.drawCircle(cx, cy, innerR, fillPaint)
                }

                Type.SEARCH -> {
                    val lensCx = l + w * 0.43f
                    val lensCy = t + h * 0.43f
                    val lensR = w * 0.26f
                    canvas.drawCircle(lensCx, lensCy, lensR, strokePaint)
                    val startX = lensCx + lensR * 0.72f
                    val startY = lensCy + lensR * 0.72f
                    canvas.drawLine(startX, startY, l + w * 0.85f, t + h * 0.85f, strokePaint)
                }

                Type.CLOSE -> {
                    val m = w * 0.22f
                    canvas.drawLine(l + m, t + m, l + w - m, t + h - m, strokePaint)
                    canvas.drawLine(l + w - m, t + m, l + m, t + h - m, strokePaint)
                }

                Type.BACK -> {
                    val leftX = l + w * 0.18f
                    val rightX = l + w * 0.82f
                    val wingX = l + w * 0.46f
                    canvas.drawLine(rightX, cy, leftX, cy, strokePaint)
                    canvas.drawLine(wingX, t + h * 0.22f, leftX, cy, strokePaint)
                    canvas.drawLine(wingX, t + h * 0.78f, leftX, cy, strokePaint)
                }

                Type.PLAY -> {
                    val path = Path().apply {
                        moveTo(l + w * 0.26f, t + h * 0.18f)
                        lineTo(l + w * 0.82f, cy)
                        lineTo(l + w * 0.26f, t + h * 0.82f)
                        close()
                    }
                    canvas.drawPath(path, fillPaint)
                }

                Type.INFO -> {
                    canvas.drawCircle(cx, cy, w * 0.38f, strokePaint)
                    canvas.drawCircle(cx, t + h * 0.33f, strokeWidthPx * 0.75f, fillPaint)
                    canvas.drawLine(cx, t + h * 0.47f, cx, t + h * 0.70f, strokePaint)
                }

                Type.UPDATE -> {
                    val rect = RectF(l + w * 0.18f, t + h * 0.18f, l + w * 0.82f, t + h * 0.82f)
                    canvas.drawArc(rect, -35f, 285f, false, strokePaint)
                    val ax = l + w * 0.78f
                    val ay = t + h * 0.34f
                    canvas.drawLine(ax, ay, l + w * 0.60f, ay, strokePaint)
                    canvas.drawLine(ax, ay, ax, t + h * 0.16f, strokePaint)
                }

                Type.TRASH -> {
                    canvas.drawLine(l + w * 0.20f, t + h * 0.26f, l + w * 0.80f, t + h * 0.26f, strokePaint)
                    canvas.drawLine(l + w * 0.38f, t + h * 0.16f, l + w * 0.62f, t + h * 0.16f, strokePaint)
                    val rect = RectF(l + w * 0.26f, t + h * 0.26f, l + w * 0.74f, t + h * 0.84f)
                    canvas.drawRoundRect(rect, w * 0.06f, w * 0.06f, strokePaint)
                }

                Type.ASPECT -> {
                    val m = w * 0.16f
                    val c = w * 0.20f
                    canvas.drawLine(l + m, t + m + c, l + m, t + m, strokePaint)
                    canvas.drawLine(l + m, t + m, l + m + c, t + m, strokePaint)
                    canvas.drawLine(l + w - m - c, t + m, l + w - m, t + m, strokePaint)
                    canvas.drawLine(l + w - m, t + m, l + w - m, t + m + c, strokePaint)
                    canvas.drawLine(l + m, t + h - m - c, l + m, t + h - m, strokePaint)
                    canvas.drawLine(l + m, t + h - m, l + m + c, t + h - m, strokePaint)
                    canvas.drawLine(l + w - m - c, t + h - m, l + w - m, t + h - m, strokePaint)
                    canvas.drawLine(l + w - m, t + h - m - c, l + w - m, t + h - m, strokePaint)
                }

                Type.EPISODES -> {
                    val x1 = l + w * 0.18f
                    val x2 = l + w * 0.82f
                    canvas.drawLine(x1, t + h * 0.26f, x2, t + h * 0.26f, strokePaint)
                    canvas.drawLine(x1, t + h * 0.50f, x2, t + h * 0.50f, strokePaint)
                    canvas.drawLine(x1, t + h * 0.74f, x2, t + h * 0.74f, strokePaint)
                }

                Type.SERVER -> {
                    val r1 = RectF(l + w * 0.16f, t + h * 0.20f, l + w * 0.84f, t + h * 0.45f)
                    val r2 = RectF(l + w * 0.16f, t + h * 0.55f, l + w * 0.84f, t + h * 0.80f)
                    canvas.drawRoundRect(r1, w * 0.08f, w * 0.08f, strokePaint)
                    canvas.drawRoundRect(r2, w * 0.08f, w * 0.08f, strokePaint)
                    canvas.drawCircle(l + w * 0.30f, t + h * 0.325f, strokeWidthPx * 0.7f, fillPaint)
                    canvas.drawCircle(l + w * 0.30f, t + h * 0.675f, strokeWidthPx * 0.7f, fillPaint)
                }
            }
        }

        override fun setAlpha(alpha: Int) {
            strokePaint.alpha = alpha
            fillPaint.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            strokePaint.colorFilter = colorFilter
            fillPaint.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
