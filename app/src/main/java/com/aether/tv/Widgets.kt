package com.aether.tv

import android.content.Context
import android.graphics.Outline
import android.graphics.drawable.StateListDrawable
import android.util.StateSet
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.TextView

object W {

    /** Rounded background that shows an accent ring while the view has focus. */
    fun focusBg(ctx: Context, p: Palette, radiusDp: Float, fill: Int): StateListDrawable {
        val r = Ui.dp(ctx, radiusDp).toFloat()
        val stroke = Ui.dp(ctx, 3f)
        val focused = Ui.rounded(p.surfaceHigh, r, stroke, p.accent)
        val normal = Ui.rounded(fill, r)
        val list = StateListDrawable()
        list.addState(intArrayOf(android.R.attr.state_focused), focused)
        list.addState(intArrayOf(android.R.attr.state_pressed), focused)
        list.addState(StateSet.WILD_CARD, normal)
        return list
    }

    /** A focusable text button. */
    fun button(ctx: Context, p: Palette, label: String, onClick: () -> Unit): TextView {
        val t = TextView(ctx)
        t.text = label
        t.setTextColor(p.text)
        t.textSize = 18f
        t.gravity = Gravity.CENTER
        t.isFocusable = true
        t.isClickable = true
        t.setPadding(Ui.dp(ctx, 24f), Ui.dp(ctx, 12f), Ui.dp(ctx, 24f), Ui.dp(ctx, 12f))
        t.background = focusBg(ctx, p, 14f, p.surface)
        t.setOnClickListener { onClick() }
        return t
    }

    /** Clips a view's content to rounded corners. */
    fun roundClip(v: View, radiusPx: Float) {
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
            }
        }
        v.clipToOutline = true
    }
}
