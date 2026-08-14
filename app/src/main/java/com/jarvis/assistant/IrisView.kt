package com.jarvis.assistant

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sin

enum class IrisMode { SLEEP, LISTENING, THINKING, SPEAKING, DENIED }

/**
 * Animated HUD "arc reactor" core — the visual face of Jarvis.
 * Breathes gently at idle, pulses with mic amplitude while listening,
 * spins faster while thinking, flashes red when denied, and can be
 * dragged anywhere on screen with a finger.
 */
class IrisView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var mode: IrisMode = IrisMode.SLEEP
        set(value) {
            if (field == value) return
            field = value
            animateColorTransition(value)
        }

    var amplitude: Float = 0f // 0..1, feed from mic level

    private var t = 0f
    private var displayedColorBase = colorFor(IrisMode.SLEEP)
    private var colorAnimator: ValueAnimator? = null
    private val argbEvaluator = ArgbEvaluator()

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val animator = ValueAnimator.ofFloat(0f, 1000f).apply {
        duration = 1000000
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            t += 0.016f
            invalidate()
        }
    }

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    init { animator.start() }

    private fun colorFor(m: IrisMode): Int = when (m) {
        IrisMode.SLEEP -> Color.rgb(95, 122, 114)
        IrisMode.LISTENING, IrisMode.SPEAKING -> Color.rgb(74, 232, 164)
        IrisMode.THINKING -> Color.rgb(242, 166, 90)
        IrisMode.DENIED -> Color.rgb(239, 91, 91)
    }

    private fun animateColorTransition(target: IrisMode) {
        colorAnimator?.cancel()
        val fromColor = displayedColorBase
        val toColor = colorFor(target)
        colorAnimator = ValueAnimator.ofObject(argbEvaluator, fromColor, toColor).apply {
            duration = 400
            addUpdateListener { anim ->
                displayedColorBase = anim.animatedValue as Int
                invalidate()
            }
            start()
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseR = width * 0.24f

        // idle breathing, always present, layered under mode-specific pulse
        val breath = sin(t * 0.7f) * 5f

        var pulse = breath
        when (mode) {
            IrisMode.SLEEP -> pulse += sin(t * 0.9f) * 3f
            IrisMode.LISTENING -> pulse += amplitude * 55f + sin(t * 4f) * 4f
            IrisMode.THINKING -> pulse += sin(t * 6f) * 9f
            IrisMode.SPEAKING -> pulse += sin(t * 10f) * 12f + amplitude * 26f
            IrisMode.DENIED -> pulse += sin(t * 14f) * 9f
        }

        // outer soft glow halo (layered translucent circles fake a blur)
        for (i in 6 downTo 1) {
            glowPaint.color = withAlpha(displayedColorBase, 10 - i)
            canvas.drawCircle(cx, cy, baseR + i * 16f + pulse * 0.3f, glowPaint)
        }

        // counter-rotating rings, different speeds
        val ringSpeeds = floatArrayOf(0.18f, -0.27f, 0.11f)
        for ((i, speed) in ringSpeeds.withIndex()) {
            ringPaint.color = withAlpha(displayedColorBase, 70 - i * 15)
            ringPaint.strokeWidth = 2f
            val r = baseR + 22 + i * 20 + pulse * 0.4f
            canvas.save()
            canvas.translate(cx, cy)
            canvas.rotate((t * speed * (if (mode == IrisMode.THINKING) 2.2f else 1f)) * 57.3f)
            canvas.drawCircle(0f, 0f, r, ringPaint)
            canvas.restore()
        }

        // segmented reactor arc — chunky turbine-style segments with gaps
        canvas.save()
        canvas.translate(cx, cy)
        val segCount = 16
        val gapDeg = 6f
        val segDeg = (360f / segCount) - gapDeg
        val spin = t * (if (mode == IrisMode.THINKING) 70f else 12f)
        val arcR = baseR + 42 + pulse * 0.35f
        arcPaint.strokeWidth = 5f
        for (i in 0 until segCount) {
            val start = i * (360f / segCount) + spin
            arcPaint.color = withAlpha(displayedColorBase, if (i % 4 == 0) 200 else 90)
            canvas.drawArc(-arcR, -arcR, arcR, arcR, start, segDeg, false, arcPaint)
        }
        canvas.restore()

        // core glow
        val coreR = baseR * 0.55f + pulse * 0.5f
        corePaint.color = withAlpha(displayedColorBase, 150)
        canvas.drawCircle(cx, cy, coreR, corePaint)
        corePaint.color = withAlpha(displayedColorBase, 235)
        canvas.drawCircle(cx, cy, baseR * 0.2f + Math.abs(pulse) * 0.15f, corePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastTouchX
                val dy = event.rawY - lastTouchY
                translationX += dx
                translationY += dy
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
