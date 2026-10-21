package com.aether.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View

class AvatarView(context: Context) : View(context) {

    private val circle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var letter = "?"

    init {
        letterPaint.textAlign = Paint.Align.CENTER
        letterPaint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        letterPaint.color = 0xFF0B0D12.toInt()
    }

    fun bind(color: Int, initial: String) {
        circle.color = color
        letter = initial
        invalidate()
    }

    fun bind(profile: Profile) {
        bind(ProfileStore.avatarColor(profile), ProfileStore.initial(profile))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        letterPaint.textSize = minOf(w, h) * 0.5f
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawCircle(w / 2f, h / 2f, minOf(w, h) / 2f, circle)
        val y = h / 2f - (letterPaint.descent() + letterPaint.ascent()) / 2f
        canvas.drawText(letter, w / 2f, y, letterPaint)
    }
}
