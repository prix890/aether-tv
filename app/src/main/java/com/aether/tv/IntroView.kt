package com.aether.tv

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils

class IntroView(
    context: Context,
    private val accent: Int,
    private val ownerName: String
) : View(context) {

    private val durationMs = 6000L
    private val title = "AETHER TV"

    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var onFinished: (() -> Unit)? = null
    private var finished = false

    private val bgPaint = Paint()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val aPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val ring = RectF()
    private val aPath = Path()
    private val shineMatrix = Matrix()
    private var shineShader: LinearGradient? = null

    private var cx = 0f
    private var logoCy = 0f
    private var r = 0f
    private var titleY = 0f
    private var subY = 0f
    private var nameY = 0f
    private var lineY = 0f
    private var nameW = 0f
    private var shineStartX = 0f
    private var shineEndX = 0f
    private var charDrop = 0f
    private var charX = FloatArray(0)

    init {
        ringPaint.style = Paint.Style.STROKE
        ringPaint.strokeCap = Paint.Cap.ROUND
        ringPaint.color = accent

        aPaint.style = Paint.Style.STROKE
        aPaint.strokeCap = Paint.Cap.ROUND
        aPaint.strokeJoin = Paint.Join.ROUND
        aPaint.color = 0xFFF2F4FA.toInt()

        titlePaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        titlePaint.color = 0xFFF2F4FA.toInt()

        subPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        subPaint.color = 0xFF9AA3B8.toInt()
        subPaint.textAlign = Paint.Align.CENTER

        namePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        namePaint.color = accent
        namePaint.textAlign = Paint.Align.CENTER

        shinePaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        shinePaint.textAlign = Paint.Align.CENTER

        linePaint.color = accent
        linePaint.strokeCap = Paint.Cap.ROUND
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        val wf = w.toFloat()
        val hf = h.toFloat()

        cx = wf / 2f
        logoCy = hf * 0.28f
        r = minOf(wf, hf) * 0.11f

        bgPaint.shader = LinearGradient(
            0f, 0f, wf, hf,
            ColorUtils.blendARGB(accent, 0xFF0B0D12.toInt(), 0.88f),
            0xFF05060A.toInt(),
            Shader.TileMode.CLAMP
        )
        glowPaint.shader = RadialGradient(
            cx, logoCy, r * 3.4f,
            ColorUtils.setAlphaComponent(accent, 80),
            ColorUtils.setAlphaComponent(accent, 0),
            Shader.TileMode.CLAMP
        )

        ring.set(cx - r, logoCy - r, cx + r, logoCy + r)
        ringPaint.strokeWidth = r * 0.21f
        aPaint.strokeWidth = r * 0.21f

        val k = r / 24f
        aPath.reset()
        aPath.moveTo(cx - 11f * k, logoCy + 14f * k)
        aPath.lineTo(cx, logoCy - 14f * k)
        aPath.lineTo(cx + 11f * k, logoCy + 14f * k)
        aPath.moveTo(cx - 6.5f * k, logoCy + 5f * k)
        aPath.lineTo(cx + 6.5f * k, logoCy + 5f * k)

        // Title, laid out letter by letter.
        titlePaint.textSize = hf * 0.085f
        val gap = titlePaint.textSize * 0.28f
        val n = title.length
        val widths = FloatArray(n)
        var total = 0f
        for (i in 0 until n) {
            widths[i] = titlePaint.measureText(title, i, i + 1)
            total += widths[i]
        }
        total += gap * (n - 1)
        charX = FloatArray(n)
        var x = cx - total / 2f
        for (i in 0 until n) {
            charX[i] = x
            x += widths[i] + gap
        }
        titleY = hf * 0.58f
        charDrop = hf * 0.03f

        subPaint.textSize = hf * 0.04f
        subY = hf * 0.71f

        namePaint.textSize = hf * 0.11f
        shinePaint.textSize = hf * 0.11f
        namePaint.setShadowLayer(hf * 0.03f, 0f, 0f, accent)
        nameY = hf * 0.85f
        nameW = namePaint.measureText(ownerName)

        val band = nameW * 0.35f
        val shader = LinearGradient(
            0f, 0f, band, 0f,
            intArrayOf(0x00FFFFFF, 0xCCFFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        shineShader = shader
        shinePaint.shader = shader
        shineStartX = cx - nameW / 2f - band
        shineEndX = cx + nameW / 2f

        lineY = nameY + hf * 0.035f
        linePaint.strokeWidth = hf * 0.007f
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        val t = progress

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        val glow = ease(seg(t, 0.00f, 0.15f))
        if (glow > 0f) {
            glowPaint.alpha = (255f * glow).toInt()
            canvas.drawCircle(cx, logoCy, r * 3.4f, glowPaint)
        }

        val ringP = ease(seg(t, 0.02f, 0.20f))
        if (ringP > 0f) {
            canvas.drawArc(ring, -90f, 360f * ringP, false, ringPaint)
        }

        val aP = ease(seg(t, 0.12f, 0.26f))
        if (aP > 0f) {
            canvas.save()
            val s = 0.7f + 0.3f * aP
            canvas.scale(s, s, cx, logoCy)
            aPaint.alpha = (255f * aP).toInt()
            canvas.drawPath(aPath, aPaint)
            canvas.restore()
        }

        for (i in 0 until title.length) {
            val start = 0.22f + i * 0.022f
            val cp = ease(seg(t, start, start + 0.07f))
            if (cp <= 0f) continue
            titlePaint.alpha = (255f * cp).toInt()
            canvas.drawText(title, i, i + 1, charX[i], titleY + (1f - cp) * charDrop, titlePaint)
        }

        val subP = ease(seg(t, 0.50f, 0.58f))
        if (subP > 0f) {
            subPaint.alpha = (255f * subP).toInt()
            canvas.drawText("Crafted by", cx, subY, subPaint)
        }

        val nameP = ease(seg(t, 0.56f, 0.68f))
        if (nameP > 0f) {
            canvas.save()
            val s = 0.85f + 0.15f * nameP
            canvas.scale(s, s, cx, nameY)
            namePaint.alpha = (255f * nameP).toInt()
            canvas.drawText(ownerName, cx, nameY, namePaint)
            canvas.restore()
        }

        val lineP = ease(seg(t, 0.66f, 0.78f))
        if (lineP > 0f) {
            val half = nameW / 2f * lineP
            canvas.drawLine(cx - half, lineY, cx + half, lineY, linePaint)
        }

        val sh = seg(t, 0.70f, 0.90f)
        if (sh > 0f && sh < 1f) {
            shineMatrix.setTranslate(shineStartX + (shineEndX - shineStartX) * sh, 0f)
            shineShader?.setLocalMatrix(shineMatrix)
            canvas.drawText(ownerName, cx, nameY, shinePaint)
        }
    }

    private fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

    private fun ease(x: Float): Float = x * x * (3f - 2f * x)

    /** Starts the intro. onFinished is called once, when it ends or is skipped. */
    fun start(onFinished: () -> Unit) {
        this.onFinished = onFinished
        val a = ValueAnimator.ofFloat(0f, 1f)
        a.duration = durationMs
        a.interpolator = LinearInterpolator()
        a.addUpdateListener { v ->
            progress = v.animatedValue as Float
            alpha = if (progress > 0.94f) ((1f - progress) / 0.06f).coerceIn(0f, 1f) else 1f
            invalidate()
        }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                finish()
            }
        })
        animator = a
        a.start()
    }

    /** Ends the intro early. onFinished still runs once. */
    fun skip() {
        animator?.cancel()
        finish()
    }

    /** Stops without calling onFinished. Use when the screen is being destroyed. */
    fun release() {
        onFinished = null
        animator?.removeAllListeners()
        animator?.cancel()
        animator = null
    }

    private fun finish() {
        if (finished) return
        finished = true
        val cb = onFinished
        onFinished = null
        cb?.invoke()
    }
}
