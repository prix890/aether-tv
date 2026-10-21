package com.aether.tv

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

class ProfilePickerView(
    context: Context,
    private val p: Palette,
    profiles: List<Profile>,
    canAdd: Boolean,
    private val onSelect: (Profile) -> Unit,
    private val onAdd: () -> Unit
) : LinearLayout(context) {

    private val tiles = ArrayList<View>()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        clipChildren = false
        clipToPadding = false

        val title = TextView(context)
        title.text = "Who's watching?"
        title.setTextColor(p.text)
        title.textSize = 34f
        title.gravity = Gravity.CENTER
        addView(title)

        val row = LinearLayout(context)
        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER
        row.clipChildren = false
        row.clipToPadding = false
        val rowLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        rowLp.topMargin = Ui.dp(context, 36f)
        addView(row, rowLp)

        val tileMargin = Ui.dp(context, 8f)
        for (profile in profiles) {
            val sub = when {
                profile.isGuest -> "Open access"
                profile.hasPin -> "PIN protected"
                else -> "No PIN"
            }
            val tile = makeTile(profile.name, sub, { it.bind(profile) }, { onSelect(profile) })
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(tileMargin, 0, tileMargin, 0)
            row.addView(tile, lp)
            tiles.add(tile)
        }
        if (canAdd) {
            val tile = makeTile(
                "Add profile", " ",
                { it.bind(p.surfaceHigh, "+") },
                { onAdd() }
            )
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(tileMargin, 0, tileMargin, 0)
            row.addView(tile, lp)
            tiles.add(tile)
        }
    }

    private fun makeTile(
        label: String,
        sub: String,
        bindAvatar: (AvatarView) -> Unit,
        onClick: () -> Unit
    ): View {
        val tile = LinearLayout(context)
        tile.orientation = VERTICAL
        tile.gravity = Gravity.CENTER_HORIZONTAL
        val pad = Ui.dp(context, 14f)
        tile.setPadding(pad, pad, pad, pad)
        tile.isFocusable = true
        tile.isClickable = true
        tile.background = W.focusBg(context, p, 20f, 0)
        tile.setOnClickListener { onClick() }
        tile.setOnFocusChangeListener { v, hasFocus ->
            val s = if (hasFocus) 1.08f else 1f
            v.animate().scaleX(s).scaleY(s).setDuration(120).start()
        }

        val avatar = AvatarView(context)
        bindAvatar(avatar)
        val size = Ui.dp(context, 112f)
        tile.addView(avatar, LinearLayout.LayoutParams(size, size))

        val name = TextView(context)
        name.text = label
        name.setTextColor(p.text)
        name.textSize = 19f
        name.gravity = Gravity.CENTER
        val nameLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        nameLp.topMargin = Ui.dp(context, 10f)
        tile.addView(name, nameLp)

        val subView = TextView(context)
        subView.text = sub
        subView.setTextColor(p.textDim)
        subView.textSize = 13f
        subView.gravity = Gravity.CENTER
        tile.addView(subView)

        return tile
    }

    fun focusFirst() {
        tiles.firstOrNull()?.requestFocus()
    }
}
