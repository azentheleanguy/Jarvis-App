package com.jarvis.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

enum class IrisMode { SLEEP, LISTENING, THINKING, SPEAKING, DENIED }

class IrisView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var mode: IrisMode = IrisMode.SLEEP
    var amplitude: Float = 0f

    private var t = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val animator = ValueAnimator.ofFloat(0f, 1000f).apply {
        duration = 1000000
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            t += 0.016f
            invalidate()
        }
    }

    init { animator.start() }

    private fun colorFor(mode: IrisMode, alpha: Int): Int = when (mode) {
        IrisMode.SLEEP -> Color.argb(alpha, 95, 122, 114)
        IrisMode.LISTENING, IrisMode.SPEAKING -> Color.argb(alpha, 74, 232, 164)
        IrisMode.THINKING -> Color.argb(alpha, 242, 166, 90)
        IrisMode.DENIED -> Color.argb(alpha, 239, 91, 91)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseR = width * 0.27f

        var pulse = 0f
        when (mode) {
            IrisMode.SLEEP -> pulse = sin(t * 0.9f) * 6f
            IrisMode.LISTENING -> pulse = amplitude * 60f + sin(t * 4f) * 4f
            IrisMode.THINKING -> pulse = sin(t * 6f) * 10f
            IrisMode.SPEAKING -> pulse = sin(t * 10f) * 14f + amplitude * 30f
            IrisMode.DENIED -> pulse = sin(t * 14f) * 10f
        }

        for (i in 0..2) {
            paint.color = colorFor(mode, (60 - i * 15))
            paint.strokeWidth = 2f
            canvas.drawCircle(cx, cy, baseR + i * 26 + pulse * 0.4f, paint)
        }

        canvas.save()
        canvas.translate(cx, cy)
        val rot = t * (if (mode == IrisMode.THINKING) 1.4f else 0.15f)
        for (i in 0 until 48) {
            val a = (i / 48f) * (2 * Math.PI).toFloat() + rot
            val r1 = baseR + 34
            val r2 = baseR + (if (i % 6 == 0) 46 else 40)
            paint.color = colorFor(mode, if (i % 6 == 0) 130 else 45)
            paint.strokeWidth = if (i % 6 == 0) 4f else 2.5f
            canvas.drawLine(
                (cos(a) * r1), (sin(a) * r1),
                (cos(a) * r2), (sin(a) * r2), paint
            )
        }
        canvas.restore()

        val coreR = baseR * 0.55f + pulse * 0.5f
        fillPaint.color = colorFor(mode, 140)
        canvas.drawCircle(cx, cy, coreR, fillPaint)
        fillPaint.color = colorFor(mode, 230)
        canvas.drawCircle(cx, cy, baseR * 0.18f + Math.abs(pulse) * 0.15f, fillPaint)
    }
}
