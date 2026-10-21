package com.aether.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class SwatchView(
    context: Context,
    private val color: Int,
    private val accent: Int
) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        fill.color = color
        ring.style = Paint.Style.STROKE
        ring.strokeWidth = Ui.dp(context, 3f).toFloat()
        isFocusable = true
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = Ui.dp(context, 46f)
        setMeasuredDimension(s, s)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val base = width / 2f - Ui.dp(context, 8f)
        canvas.drawCircle(cx, cy, base, fill)
        if (isSelected) {
            ring.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(cx, cy, base + Ui.dp(context, 4f), ring)
        }
        if (isFocused) {
            ring.color = accent
            canvas.drawCircle(cx, cy, base + Ui.dp(context, 6f), ring)
        }
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }
}

object ProfileEditor {

    /**
     * Builds the editor card. Pass existing = null to create a new profile.
     * Pass onDelete = null when the profile cannot be deleted (or is new).
     */
    fun build(
        ctx: Context,
        p: Palette,
        existing: Profile?,
        onSave: (String, Int) -> Unit,
        onDelete: (() -> Unit)?,
        onCancel: () -> Unit
    ): View {
        val card = LinearLayout(ctx)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_HORIZONTAL
        val pad = Ui.dp(ctx, 28f)
        card.setPadding(pad, pad, pad, pad)
        card.background = Ui.rounded(p.surface, Ui.dp(ctx, 24f).toFloat())

        val title = TextView(ctx)
        title.text = if (existing == null) "New profile" else "Edit profile"
        title.setTextColor(p.text)
        title.textSize = 26f
        card.addView(title)

        var selected = existing?.avatar ?: (ProfileStore.all().size % ProfileStore.AVATAR_COLORS.size)

        val avatar = AvatarView(ctx)
        val avatarSize = Ui.dp(ctx, 88f)
        val avatarLp = LinearLayout.LayoutParams(avatarSize, avatarSize)
        avatarLp.topMargin = Ui.dp(ctx, 14f)
        card.addView(avatar, avatarLp)

        val nameEdit = EditText(ctx)
        nameEdit.setText(existing?.name ?: "")
        nameEdit.hint = "Profile name"
        nameEdit.setTextColor(p.text)
        nameEdit.setHintTextColor(p.textDim)
        nameEdit.textSize = 20f
        nameEdit.setSingleLine(true)
        nameEdit.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(16))
        nameEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        nameEdit.imeOptions = EditorInfo.IME_ACTION_DONE
        nameEdit.gravity = Gravity.CENTER
        nameEdit.setPadding(Ui.dp(ctx, 20f), Ui.dp(ctx, 12f), Ui.dp(ctx, 20f), Ui.dp(ctx, 12f))
        nameEdit.background = W.focusBg(ctx, p, 12f, p.surfaceHigh)
        nameEdit.minWidth = Ui.dp(ctx, 320f)
        val nameLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        nameLp.topMargin = Ui.dp(ctx, 12f)
        card.addView(nameEdit, nameLp)

        val hint = TextView(ctx)
        hint.text = "Press OK on the name box to type"
        hint.setTextColor(p.textDim)
        hint.textSize = 13f
        hint.gravity = Gravity.CENTER
        card.addView(hint)

        fun refreshAvatar() {
            val t = nameEdit.text.toString().trim()
            val initial = if (t.isEmpty()) "?" else t.substring(0, 1).uppercase()
            avatar.bind(ProfileStore.AVATAR_COLORS[selected], initial)
        }
        refreshAvatar()

        nameEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                refreshAvatar()
            }
        })

        val swatchRow = LinearLayout(ctx)
        swatchRow.orientation = LinearLayout.HORIZONTAL
        swatchRow.gravity = Gravity.CENTER
        val swatches = ArrayList<SwatchView>()
        for (i in ProfileStore.AVATAR_COLORS.indices) {
            val sw = SwatchView(ctx, ProfileStore.AVATAR_COLORS[i], p.accent)
            sw.isSelected = (i == selected)
            sw.setOnClickListener {
                selected = i
                for (j in swatches.indices) {
                    swatches[j].isSelected = (j == selected)
                    swatches[j].invalidate()
                }
                refreshAvatar()
            }
            swatches.add(sw)
            swatchRow.addView(sw)
        }
        val swatchLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        swatchLp.topMargin = Ui.dp(ctx, 14f)
        card.addView(swatchRow, swatchLp)

        val buttons = LinearLayout(ctx)
        buttons.orientation = LinearLayout.HORIZONTAL
        buttons.gravity = Gravity.CENTER
        val margin = Ui.dp(ctx, 8f)

        val save = W.button(ctx, p, "Save") { onSave(nameEdit.text.toString(), selected) }
        val cancel = W.button(ctx, p, "Cancel") { onCancel() }
        val saveLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        saveLp.setMargins(margin, 0, margin, 0)
        buttons.addView(save, saveLp)
        val cancelLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cancelLp.setMargins(margin, 0, margin, 0)
        buttons.addView(cancel, cancelLp)

        if (onDelete != null) {
            val delete = W.button(ctx, p, "Delete") { onDelete() }
            delete.setTextColor(0xFFFF7A7A.toInt())
            val deleteLp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            deleteLp.setMargins(margin, 0, margin, 0)
            buttons.addView(delete, deleteLp)
        }

        val buttonsLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        buttonsLp.topMargin = Ui.dp(ctx, 18f)
        card.addView(buttons, buttonsLp)

        return card
    }
}
