package com.aether.tv

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

class IntroView(
    context: Context,
    private val accent: Int,
    @Suppress("UNUSED_PARAMETER") legacyName: String = ""
) : View(context) {

    private val durationMs = 6000L
    private val title = "AETHER TV"
    private val tagline = "YOUR ENTERTAINMENT UNIVERSE"
    private val creditLead = "Crafted by"
    private val creditName = "Priyesh"
    private val violet = 0xFFB388FF.toInt()

    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var onFinished: (() -> Unit)? = null
    private var finished = false

    private val bgIcon: Drawable? =
        ContextCompat.getDrawable(context, R.drawable.ic_launcher_background)?.mutate()
    private val fgIcon: Drawable? =
        ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)?.mutate()

    private val bgPaint = Paint()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titleAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val taglinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val taglineShinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val leadPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nameShinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val iconClip = Path()
    private val iconRect = RectF()
    private val pillRect = RectF()
    private val starPath = Path()
    private val tagShineMatrix = Matrix()
    private val nameShineMatrix = Matrix()
    private var tagShineShader: LinearGradient? = null
    private var nameShineShader: LinearGradient? = null
    private val blurFilters = arrayOfNulls<BlurMaskFilter>(6)

    private var cx = 0f
    private var iconCy = 0f
    private var iconSize = 0f
    private var iconRadius = 0f
    private var titleY = 0f
    private var charDrop = 0f
    private var charX = FloatArray(0)
    private var taglineY = 0f
    private var tagShineStart = 0f
    private var tagShineEnd = 0f
    private var pillLeft = 0f
    private var pillTop = 0f
    private var pillW = 0f
    private var pillH = 0f
    private var leadX = 0f
    private var nameX = 0f
    private var baseY = 0f
    private var nameShineStart = 0f
    private var nameShineEnd = 0f

    init {
        borderPaint.style = Paint.Style.STROKE
        borderPaint.color = 0xFFFFFFFF.toInt()

        titlePaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        titlePaint.color = 0xFFF2F4FA.toInt()
        titleAccentPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        taglinePaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        taglinePaint.textAlign = Paint.Align.CENTER
        taglinePaint.letterSpacing = 0.28f
        taglinePaint.color = 0xFFFFFFFF.toInt()
        taglineShinePaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        taglineShinePaint.textAlign = Paint.Align.CENTER
        taglineShinePaint.letterSpacing = 0.28f

        pillFill.style = Paint.Style.FILL
        pillFill.color = 0xFFFFFFFF.toInt()
        pillStroke.style = Paint.Style.STROKE
        starPaint.style = Paint.Style.FILL
        starPaint.color = accent

        leadPaint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        leadPaint.color = 0xFFC4CCE0.toInt()
        namePaint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        nameShinePaint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        val wf = w.toFloat()
        val hf = h.toFloat()
        cx = wf / 2f

        bgPaint.shader = LinearGradient(
            0f, 0f, wf, hf,
            ColorUtils.blendARGB(accent, 0xFF0B0D12.toInt(), 0.90f),
            0xFF05060A.toInt(),
            Shader.TileMode.CLAMP
        )

        // ----- icon -----
        iconSize = hf * 0.25f
        iconCy = hf * 0.27f
        val half = iconSize / 2f
        iconRadius = iconSize * 0.22f
        iconRect.set(-half, -half, half, half)
        iconClip.reset()
        iconClip.addRoundRect(iconRect, iconRadius, iconRadius, Path.Direction.CW)
        val big = (half * 1.5f).toInt()
        bgIcon?.setBounds(-big, -big, big, big)
        fgIcon?.setBounds(-big, -big, big, big)
        borderPaint.strokeWidth = maxOf(1f, hf * 0.003f)
        glowPaint.shader = RadialGradient(
            0f, 0f, iconSize * 1.3f,
            ColorUtils.setAlphaComponent(accent, 90),
            ColorUtils.setAlphaComponent(accent, 0),
            Shader.TileMode.CLAMP
        )

        // ----- title, letter by letter -----
        titlePaint.textSize = hf * 0.082f
        titleAccentPaint.textSize = hf * 0.082f
        val gap = titlePaint.textSize * 0.26f
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
        titleY = hf * 0.57f
        charDrop = hf * 0.03f
        titleAccentPaint.shader = LinearGradient(
            charX[7], 0f, charX[8] + widths[8], 0f,
            accent, violet, Shader.TileMode.CLAMP
        )

        // ----- tagline (blurs in) -----
        taglinePaint.textSize = hf * 0.032f
        taglineShinePaint.textSize = hf * 0.032f
        val tw = taglinePaint.measureText(tagline)
        taglineY = hf * 0.67f
        taglinePaint.shader = LinearGradient(
            cx - tw / 2f, 0f, cx + tw / 2f, 0f,
            0xFFA9C0FF.toInt(), 0xFFD9B8FF.toInt(), Shader.TileMode.CLAMP
        )
        val radii = floatArrayOf(1.1f, 0.75f, 0.5f, 0.3f, 0.12f)
        for (i in 0 until 5) {
            blurFilters[i] = BlurMaskFilter(taglinePaint.textSize * radii[i], BlurMaskFilter.Blur.NORMAL)
        }
        blurFilters[5] = null
        val tagBand = tw * 0.22f
        val tagShader = LinearGradient(
            0f, 0f, tagBand, 0f,
            intArrayOf(0x00FFFFFF, 0xCCFFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        tagShineShader = tagShader
        taglineShinePaint.shader = tagShader
        tagShineStart = cx - tw / 2f - tagBand
        tagShineEnd = cx + tw / 2f

        // ----- credit pill, bottom-left -----
        val cs = hf * 0.028f
        leadPaint.textSize = cs
        namePaint.textSize = cs * 1.12f
        nameShinePaint.textSize = cs * 1.12f
        val leadW = leadPaint.measureText(creditLead)
        val nameW = namePaint.measureText(creditName)
        val padX = cs * 0.9f
        val star = cs * 0.55f
        val g1 = cs * 0.55f
        val g2 = cs * 0.35f
        pillH = cs * 2.5f
        pillW = padX + star * 2f + g1 + leadW + g2 + nameW + padX
        pillLeft = wf * 0.05f
        pillTop = hf * 0.925f - pillH
        pillRect.set(0f, 0f, pillW, pillH)
        leadX = padX + star * 2f + g1
        nameX = leadX + leadW + g2
        baseY = pillH / 2f - (leadPaint.ascent() + leadPaint.descent()) / 2f

        pillStroke.strokeWidth = maxOf(1f, hf * 0.0025f)
        pillStroke.shader = LinearGradient(
            0f, 0f, pillW, 0f,
            ColorUtils.setAlphaComponent(accent, 230),
            ColorUtils.setAlphaComponent(violet, 80),
            Shader.TileMode.CLAMP
        )
        namePaint.shader = LinearGradient(
            nameX, 0f, nameX + nameW, 0f,
            accent, violet, Shader.TileMode.CLAMP
        )
        val nameBand = nameW * 0.5f
        val nameShader = LinearGradient(
            0f, 0f, nameBand, 0f,
            intArrayOf(0x00FFFFFF, 0xCCFFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        nameShineShader = nameShader
        nameShinePaint.shader = nameShader
        nameShineStart = nameX - nameBand
        nameShineEnd = nameX + nameW

        val sx = padX + star
        val sy = pillH / 2f
        starPath.reset()
        starPath.moveTo(sx, sy - star)
        starPath.quadTo(sx, sy, sx + star, sy)
        starPath.quadTo(sx, sy, sx, sy + star)
        starPath.quadTo(sx, sy, sx - star, sy)
        starPath.quadTo(sx, sy, sx, sy - star)
        starPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        val t = progress

        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // 1. App icon fades in
        val ip = ease(seg(t, 0.03f, 0.18f))
        if (ip > 0f) {
            canvas.save()
            canvas.translate(cx, iconCy)
            glowPaint.alpha = (255f * ip).toInt()
            canvas.drawCircle(0f, 0f, iconSize * 1.3f, glowPaint)
            val s = 0.82f + 0.18f * ip
            canvas.scale(s, s)
            canvas.save()
            canvas.clipPath(iconClip)
            val a = (255f * ip).toInt()
            bgIcon?.alpha = a
            bgIcon?.draw(canvas)
            fgIcon?.alpha = a
            fgIcon?.draw(canvas)
            canvas.restore()
            borderPaint.alpha = (70f * ip).toInt()
            canvas.drawRoundRect(iconRect, iconRadius, iconRadius, borderPaint)
            canvas.restore()
        }

        // 2. AETHER TV, letter by letter
        for (i in 0 until title.length) {
            val start = 0.14f + i * 0.02f
            val cp = ease(seg(t, start, start + 0.07f))
            if (cp <= 0f) continue
            val paint = if (i >= 7) titleAccentPaint else titlePaint
            paint.alpha = (255f * cp).toInt()
            canvas.drawText(title, i, i + 1, charX[i], titleY + (1f - cp) * charDrop, paint)
        }

        // 3. Tagline blurs in, then shimmers
        val tp = ease(seg(t, 0.30f, 0.52f))
        if (tp > 0f) {
            val level = (tp * 5.999f).toInt()
            taglinePaint.maskFilter = blurFilters[level]
            taglinePaint.alpha = (255f * minOf(1f, tp * 1.6f)).toInt()
            canvas.drawText(tagline, cx, taglineY, taglinePaint)
        }
        val tagSweep = seg(t, 0.52f, 0.66f)
        if (tagSweep > 0f && tagSweep < 1f) {
            tagShineMatrix.setTranslate(tagShineStart + (tagShineEnd - tagShineStart) * tagSweep, 0f)
            tagShineShader?.setLocalMatrix(tagShineMatrix)
            canvas.drawText(tagline, cx, taglineY, taglineShinePaint)
        }

        // 4. Credit pill, bottom-left
        val cp = ease(seg(t, 0.56f, 0.68f))
        if (cp > 0f) {
            canvas.save()
            canvas.translate(pillLeft - (1f - cp) * pillW * 0.3f, pillTop)
            val a = (255f * cp).toInt()
            pillFill.alpha = (36f * cp).toInt()
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillFill)
            pillStroke.alpha = a
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, pillStroke)
            starPaint.alpha = a
            canvas.drawPath(starPath, starPaint)
            leadPaint.alpha = a
            canvas.drawText(creditLead, leadX, baseY, leadPaint)
            namePaint.alpha = a
            canvas.drawText(creditName, nameX, baseY, namePaint)
            val nameSweep = seg(t, 0.70f, 0.84f)
            if (nameSweep > 0f && nameSweep < 1f) {
                nameShineMatrix.setTranslate(
                    nameShineStart + (nameShineEnd - nameShineStart) * nameSweep, 0f
                )
                nameShineShader?.setLocalMatrix(nameShineMatrix)
                canvas.drawText(creditName, nameX, baseY, nameShinePaint)
            }
            canvas.restore()
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
