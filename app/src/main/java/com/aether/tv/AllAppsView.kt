package com.aether.tv

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AllAppsView(
    context: Context,
    private val p: Palette,
    private val prefs: Prefs,
    private val host: HomeHost
) : LinearLayout(context) {

    private val margin = Ui.dp(context, 56f)
    private val cols = prefs.columns.coerceIn(3, 10)
    private val gap = Ui.gap(context, prefs)
    private val imageW: Int = run {
        val screenW = context.resources.displayMetrics.widthPixels
        val cell = (screenW - 2 * margin) / cols
        maxOf(cell - gap - Ui.dp(context, 12f), Ui.dp(context, 80f))
    }

    private val adapter = AppAdapter(
        context, p, imageW, gap, Ui.animMs(prefs, 120L),
        { host.launchApp(it) },
        { host.showAppMenu(it) }
    )

    private val search = EditText(context)
    private val countView = TextView(context)
    private val emptyView = TextView(context)
    private val grid = RecyclerView(context)

    init {
        orientation = VERTICAL
        setPadding(margin, Ui.dp(context, 28f), margin, 0)
        clipChildren = false
        clipToPadding = false

        // Top bar: title, search box, count.
        val bar = LinearLayout(context)
        bar.orientation = HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL

        val title = TextView(context)
        title.text = "All Apps"
        title.setTextColor(p.text)
        title.textSize = 30f
        bar.addView(title)

        search.hint = "Search apps"
        search.setHintTextColor(p.textDim)
        search.setTextColor(p.text)
        search.textSize = 18f
        search.setSingleLine(true)
        search.inputType = InputType.TYPE_CLASS_TEXT
        search.imeOptions = EditorInfo.IME_ACTION_SEARCH
        search.setPadding(
            Ui.dp(context, 18f), Ui.dp(context, 10f),
            Ui.dp(context, 18f), Ui.dp(context, 10f)
        )
        search.background = W.focusBg(context, p, 14f, p.surface)
        val sLp = LinearLayout.LayoutParams(Ui.dp(context, 360f), ViewGroup.LayoutParams.WRAP_CONTENT)
        sLp.leftMargin = Ui.dp(context, 28f)
        bar.addView(search, sLp)

        countView.setTextColor(p.textDim)
        countView.textSize = 15f
        val cLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        cLp.leftMargin = Ui.dp(context, 20f)
        bar.addView(countView, cLp)

        addView(bar)

        val hint = TextView(context)
        hint.text = "Press Back to return home"
        hint.setTextColor(p.textDim)
        hint.textSize = 12f
        val hLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        hLp.topMargin = Ui.dp(context, 6f)
        addView(hint, hLp)

        emptyView.setTextColor(p.textDim)
        emptyView.textSize = 16f
        emptyView.setPadding(0, Ui.dp(context, 20f), 0, 0)
        addView(emptyView)

        grid.layoutManager = GridLayoutManager(context, cols)
        grid.adapter = adapter
        grid.itemAnimator = null
        grid.clipChildren = false
        grid.clipToPadding = false
        grid.overScrollMode = OVER_SCROLL_NEVER
        grid.isFocusable = false
        grid.isFocusableInTouchMode = false
        grid.setPadding(0, Ui.dp(context, 14f), 0, Ui.dp(context, 28f))
        addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                reload()
            }
        })
        search.setOnEditorActionListener { _, _, _ ->
            grid.requestFocus()
            true
        }
    }

    /** Re-runs the search with the current text. Call after the app list changes. */
    fun reload() {
        val q = search.text.toString()
        val list = AppRepository.search(prefs, q)
        adapter.submitIfChanged(list)
        countView.text = "${list.size} apps"
        emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        emptyView.text = when {
            !AppRepository.loaded -> "Loading apps…"
            q.isBlank() -> "No launchable apps were found."
            else -> "No apps match \"$q\""
        }
    }

    /** Focuses the first app (not the search box) once the grid has been laid out. */
    fun focusGrid() {
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (viewTreeObserver.isAlive) viewTreeObserver.removeOnGlobalLayoutListener(this)
                if (!grid.requestFocus()) search.requestFocus()
            }
        })
    }
}
