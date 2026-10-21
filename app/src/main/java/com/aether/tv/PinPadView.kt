package com.aether.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

private class DotsView(context: Context, p: Palette) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)

    var count: Int = 0
        set(v) {
            field = v
            invalidate()
        }

    init {
        fill.color = p.accent
        ring.color = p.textDim
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = Ui.dp(context, 2f).toFloat()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(Ui.dp(context, 224f), Ui.dp(context, 28f))
    }

    override fun onDraw(canvas: Canvas) {
        val slot = Ui.dp(context, 28f).toFloat()
        val n = maxOf(ProfileStore.PIN_MIN, count)
        val startX = (width - n * slot) / 2f
        val r = slot * 0.28f
        val cy = height / 2f
        for (i in 0 until n) {
            val cx = startX + slot * i + slot / 2f
            if (i < count) canvas.drawCircle(cx, cy, r, fill) else canvas.drawCircle(cx, cy, r, ring)
        }
    }
}

class PinPadView(context: Context, private val p: Palette) : LinearLayout(context) {

    /** Called when the user presses OK on the pad with a long-enough PIN. */
    var onSubmit: ((String) -> Unit)? = null

    private val titleView = TextView(context)
    private val msgView = TextView(context)
    private val dots = DotsView(context, p)
    private val entry = StringBuilder()
    private val keys = ArrayList<TextView>()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val pad = Ui.dp(context, 28f)
        setPadding(pad, pad, pad, pad)
        background = Ui.rounded(p.surface, Ui.dp(context, 24f).toFloat())

        titleView.setTextColor(p.text)
        titleView.textSize = 26f
        titleView.gravity = Gravity.CENTER
        addView(titleView)

        val dotsLp = LinearLayout.LayoutParams(Ui.dp(context, 224f), Ui.dp(context, 28f))
        dotsLp.topMargin = Ui.dp(context, 14f)
        addView(dots, dotsLp)

        msgView.setTextColor(p.textDim)
        msgView.textSize = 15f
        msgView.gravity = Gravity.CENTER
        msgView.minHeight = Ui.dp(context, 28f)
        addView(msgView)

        val labels = arrayOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "Del", "0", "OK")
        val margin = Ui.dp(context, 4f)
        for (row in 0 until 4) {
            val rowLayout = LinearLayout(context)
            rowLayout.orientation = HORIZONTAL
            for (col in 0 until 3) {
                val label = labels[row * 3 + col]
                val key = W.button(context, p, label) { onKey(label) }
                key.textSize = 22f
                key.setPadding(0, 0, 0, 0)
                val lp = LinearLayout.LayoutParams(Ui.dp(context, 84f), Ui.dp(context, 56f))
                lp.setMargins(margin, margin, margin, margin)
                rowLayout.addView(key, lp)
                keys.add(key)
            }
            addView(rowLayout)
        }
    }

    fun setTitle(t: String) {
        titleView.text = t
    }

    fun setMessage(m: String, error: Boolean) {
        msgView.text = m
        msgView.setTextColor(if (error) 0xFFFF7A7A.toInt() else p.textDim)
    }

    fun clearEntry() {
        entry.setLength(0)
        dots.count = 0
    }

    /** Puts the remote's focus on the middle "5" key. */
    fun focusStart() {
        keys[4].requestFocus()
    }

    private fun onKey(label: String) {
        when (label) {
            "Del" -> backspace()
            "OK" -> submit()
            else -> addDigit(label[0] - '0')
        }
    }

    private fun addDigit(d: Int) {
        if (entry.length >= ProfileStore.PIN_MAX) return
        entry.append(d)
        dots.count = entry.length
    }

    private fun backspace() {
        if (entry.isNotEmpty()) {
            entry.deleteCharAt(entry.length - 1)
            dots.count = entry.length
        }
    }

    private fun submit() {
        if (entry.length < ProfileStore.PIN_MIN) {
            setMessage("Enter at least ${ProfileStore.PIN_MIN} digits", true)
            return
        }
        onSubmit?.invoke(entry.toString())
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val k = event.keyCode
            val digit = when (k) {
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> k - KeyEvent.KEYCODE_0
                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> k - KeyEvent.KEYCODE_NUMPAD_0
                else -> -1
            }
            if (digit >= 0) {
                addDigit(digit)
                return true
            }
            if (k == KeyEvent.KEYCODE_DEL || k == KeyEvent.KEYCODE_FORWARD_DEL) {
                backspace()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
