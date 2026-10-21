package com.aether.tv

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

class OverlayHost(context: Context, private val blocked: ViewGroup) : FrameLayout(context) {

    private class Entry(val view: View, val restoreTo: View?)

    private val stack = ArrayList<Entry>()

    val isShowing: Boolean
        get() = stack.isNotEmpty()

    init {
        visibility = GONE
        setBackgroundColor(0xCC000000.toInt())
        isClickable = true
    }

    fun show(content: View) {
        val restore = rootView.findFocus()
        if (stack.isNotEmpty()) stack[stack.size - 1].view.visibility = View.GONE
        stack.add(Entry(content, restore))
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        addView(content, lp)
        visibility = View.VISIBLE
        blocked.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        content.post { content.requestFocus() }
    }

    /** Closes the top-most overlay. Returns false if nothing was open. */
    fun hideTop(): Boolean {
        if (stack.isEmpty()) return false
        val e = stack.removeAt(stack.size - 1)
        removeView(e.view)
        if (stack.isEmpty()) {
            visibility = View.GONE
            blocked.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            val r = e.restoreTo
            if (r != null && r.isAttachedToWindow) r.requestFocus()
        } else {
            val top = stack[stack.size - 1].view
            top.visibility = View.VISIBLE
            top.post { top.requestFocus() }
        }
        return true
    }

    fun hideAll() {
        while (hideTop()) {
            // keep closing
        }
    }
}
