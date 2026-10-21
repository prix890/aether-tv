package com.aether.tv

import android.app.ActivityManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HomeView(
    context: Context,
    private val p: Palette,
    private val prefs: Prefs,
    private val host: HomeHost
) : ScrollView(context) {

    private val gap = Ui.gap(context, prefs)
    private val cardW = Ui.cardWidth(context, prefs)
    private val animMs = Ui.animMs(prefs, 120L)

    private val content = LinearLayout(context)

    private val favAdapter = makeAdapter()
    private val recentAdapter = makeAdapter()
    private val appsAdapter = makeAdapter()

    private val favRv = makeRow(favAdapter)
    private val recentRv = makeRow(recentAdapter)
    private val appsRv = makeRow(appsAdapter)

    private val favSection = LinearLayout(context)
    private val recentSection = LinearLayout(context)
    private val appsSection = LinearLayout(context)

    private val favEmpty = hintText(
        "No favorites yet. Open an app's menu (hold OK on it) and choose Add to favorites."
    )
    private val appsEmpty = hintText("Loading apps…")

    private var infoView: TextView? = null
    private var allAppsButton: TextView? = null

    init {
        isFillViewport = true
        clipChildren = false
        clipToPadding = false
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER

        content.orientation = LinearLayout.VERTICAL
        content.clipChildren = false
        content.clipToPadding = false
        content.setPadding(
            Ui.dp(context, 56f), Ui.dp(context, 28f),
            Ui.dp(context, 56f), Ui.dp(context, 36f)
        )
        addView(
            content,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        content.addView(buildHeader())

        if (!prefs.isGuest) {
            setupSection(favSection, "Favorites")
            favSection.addView(favEmpty)
            favSection.addView(favRv)
            content.addView(favSection)

            setupSection(recentSection, "Recent")
            recentSection.addView(recentRv)
            content.addView(recentSection)
        }

        setupSection(appsSection, "Apps")
        appsSection.addView(appsEmpty)
        appsSection.addView(appsRv)
        content.addView(appsSection)

        content.addView(sectionTitle("Quick settings"))
        content.addView(buildQuickSettings())
    }

    // ---------- building blocks ----------

    private fun makeAdapter(): AppAdapter = AppAdapter(
        context, p, cardW, gap, animMs,
        { host.launchApp(it) },
        { host.showAppMenu(it) }
    )

    private fun makeRow(adapter: AppAdapter): RecyclerView {
        val rv = RecyclerView(context)
        rv.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = adapter
        rv.itemAnimator = null
        rv.clipChildren = false
        rv.clipToPadding = false
        rv.overScrollMode = OVER_SCROLL_NEVER
        rv.isFocusable = false
        rv.isFocusableInTouchMode = false
        rv.setPadding(0, Ui.dp(context, 10f), 0, Ui.dp(context, 14f))
        rv.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        return rv
    }

    private fun hintText(text: String): TextView {
        val t = TextView(context)
        t.text = text
        t.setTextColor(p.textDim)
        t.textSize = 15f
        t.setPadding(0, Ui.dp(context, 8f), 0, Ui.dp(context, 8f))
        return t
    }

    private fun sectionTitle(text: String): TextView {
        val t = TextView(context)
        t.text = text
        t.setTextColor(p.text)
        t.textSize = 21f
        t.letterSpacing = 0.04f
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = Ui.dp(context, 22f)
        t.layoutParams = lp
        return t
    }

    private fun setupSection(section: LinearLayout, title: String) {
        section.orientation = LinearLayout.VERTICAL
        section.clipChildren = false
        section.clipToPadding = false
        section.addView(sectionTitle(title))
    }

    private fun buildHeader(): View {
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.TOP
        row.clipChildren = false

        val left = LinearLayout(context)
        left.orientation = LinearLayout.VERTICAL

        if (prefs.showClock) {
            val clock = TextClock(context)
            clock.format12Hour = "h:mm a"
            clock.format24Hour = "HH:mm"
            clock.setTextColor(p.text)
            clock.textSize = 56f
            clock.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            left.addView(clock)
        }
        if (prefs.showDate) {
            val date = TextClock(context)
            date.format12Hour = "EEEE, d MMMM"
            date.format24Hour = "EEEE, d MMMM"
            date.setTextColor(p.textDim)
            date.textSize = 18f
            left.addView(date)
        }
        if (prefs.showGreeting) {
            val greeting = TextView(context)
            greeting.text = Ui.greeting(prefs)
            greeting.setTextColor(p.accent)
            greeting.textSize = 22f
            val glp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            glp.topMargin = Ui.dp(context, 6f)
            left.addView(greeting, glp)
        }
        if (prefs.showInfo) {
            val info = TextView(context)
            info.text = deviceInfo()
            info.setTextColor(p.textDim)
            info.textSize = 13f
            val ilp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            ilp.topMargin = Ui.dp(context, 4f)
            left.addView(info, ilp)
            infoView = info
        }
        row.addView(
            left,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        val actions = LinearLayout(context)
        actions.orientation = LinearLayout.HORIZONTAL
        actions.gravity = Gravity.CENTER_VERTICAL
        val m = Ui.dp(context, 5f)

        val chipLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        chipLp.setMargins(m, 0, m, 0)
        actions.addView(profileChip(), chipLp)

        val allBtn = W.button(context, p, "All apps & search") { host.openAllApps() }
        val allLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        allLp.setMargins(m, 0, m, 0)
        actions.addView(allBtn, allLp)
        allAppsButton = allBtn

        if (!prefs.isGuest) {
            val settingsBtn = W.button(context, p, "Settings") { host.openSettings() }
            val sLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            sLp.setMargins(m, 0, m, 0)
            actions.addView(settingsBtn, sLp)
        }

        row.addView(
            actions,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return row
    }

    private fun profileChip(): View {
        val prof = ProfileStore.active()
        val chip = LinearLayout(context)
        chip.orientation = LinearLayout.HORIZONTAL
        chip.gravity = Gravity.CENTER_VERTICAL
        chip.isFocusable = true
        chip.isClickable = true
        chip.setPadding(
            Ui.dp(context, 12f), Ui.dp(context, 8f),
            Ui.dp(context, 18f), Ui.dp(context, 8f)
        )
        chip.background = W.focusBg(context, p, 28f, p.surface)
        chip.setOnClickListener { host.switchProfile() }

        val av = AvatarView(context)
        if (prof != null) av.bind(prof) else av.bind(p.surfaceHigh, "?")
        val size = Ui.dp(context, 36f)
        chip.addView(av, LinearLayout.LayoutParams(size, size))

        val texts = LinearLayout(context)
        texts.orientation = LinearLayout.VERTICAL
        val name = TextView(context)
        name.text = prof?.name ?: "Profile"
        name.setTextColor(p.text)
        name.textSize = 16f
        texts.addView(name)
        val sub = TextView(context)
        sub.text = "Switch profile"
        sub.setTextColor(p.textDim)
        sub.textSize = 11f
        texts.addView(sub)
        val tlp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        tlp.leftMargin = Ui.dp(context, 10f)
        chip.addView(texts, tlp)
        return chip
    }

    private fun buildQuickSettings(): View {
        val scroll = HorizontalScrollView(context)
        scroll.isHorizontalScrollBarEnabled = false
        scroll.overScrollMode = OVER_SCROLL_NEVER
        scroll.clipChildren = false

        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(0, Ui.dp(context, 10f), 0, Ui.dp(context, 6f))

        val items = arrayOf(
            Pair("Wi-Fi & network", Settings.ACTION_WIFI_SETTINGS),
            Pair("Display", Settings.ACTION_DISPLAY_SETTINGS),
            Pair("Sound", Settings.ACTION_SOUND_SETTINGS),
            Pair("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
            Pair("All Android settings", Settings.ACTION_SETTINGS)
        )
        val m = Ui.dp(context, 5f)
        for (item in items) {
            val action = item.second
            val b = W.button(context, p, item.first) { host.openSystemSettings(action) }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 0, m * 2, 0)
            row.addView(b, lp)
        }
        scroll.addView(row)
        return scroll
    }

    private fun deviceInfo(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val base = "${Build.MODEL}  •  Android ${Build.VERSION.RELEASE}"
        if (am == null) return base
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val freeMb = mi.availMem / (1024L * 1024L)
        val totalMb = mi.totalMem / (1024L * 1024L)
        return "$base  •  RAM $freeMb MB free of $totalMb MB"
    }

    // ---------- public API used by MainActivity ----------

    /** Re-reads apps, favorites and recents. Only changed rows are touched. */
    fun refresh() {
        val apps = AppRepository.visible(prefs)
        appsAdapter.submitIfChanged(apps)
        appsEmpty.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        appsEmpty.text =
            if (AppRepository.loaded) "No launchable apps were found." else "Loading apps…"

        if (!prefs.isGuest) {
            val favs = AppRepository.favoriteApps(prefs)
            favAdapter.submitIfChanged(favs)
            favEmpty.visibility = if (favs.isEmpty()) View.VISIBLE else View.GONE
            favRv.visibility = if (favs.isEmpty()) View.GONE else View.VISIBLE

            val recents = AppRepository.recentApps(prefs)
            recentAdapter.submitIfChanged(recents)
            recentSection.visibility = if (recents.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    fun updateInfo() {
        infoView?.text = deviceInfo()
    }

    /** Puts the remote's focus on the first app card once the screen has been laid out. */
    fun focusMain() {
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (viewTreeObserver.isAlive) viewTreeObserver.removeOnGlobalLayoutListener(this)
                val target: View =
                    if (!prefs.isGuest && favAdapter.itemCount > 0) favRv else appsRv
                if (!target.requestFocus()) content.requestFocus()
            }
        })
    }

    fun focusAllAppsButton() {
        allAppsButton?.requestFocus()
    }
}
