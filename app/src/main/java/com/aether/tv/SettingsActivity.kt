package com.aether.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class SettingsActivity : Activity() {

    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
    private val reqWallpaper = 41

    private lateinit var pf: Prefs
    private lateinit var pal: Palette
    private lateinit var profile: Profile
    private lateinit var root: FrameLayout
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var overlay: OverlayHost

    private val rows = ArrayList<View>()
    private var presetLabel = "choose"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProfileStore.ensure(this)
        val active = ProfileStore.active()
        if (active == null || active.isGuest) {
            finish()
            return
        }
        profile = active
        pf = Prefs.get(this)
        pal = Palette(pf)

        root = FrameLayout(this)
        scroll = ScrollView(this)
        scroll.isFillViewport = true
        scroll.isVerticalScrollBarEnabled = false
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        scroll.addView(content, FrameLayout.LayoutParams(match, wrap))
        root.addView(
            scroll,
            FrameLayout.LayoutParams(Ui.dp(this, 780f), match, Gravity.CENTER_HORIZONTAL)
        )
        overlay = OverlayHost(this, scroll)
        root.addView(overlay, FrameLayout.LayoutParams(match, match))
        setContentView(root)

        build()
        val first = rows.firstOrNull()
        if (first != null) first.post { first.requestFocus() }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (!overlay.hideTop()) finish()
    }

    // ------------------------------------------------------------ building the screen

    private fun build() {
        pal = Palette(pf)
        root.background = Ui.backgroundGradient(pal, pf.wallpaperPreset)
        content.setPadding(Ui.dp(this, 24f), Ui.dp(this, 28f), Ui.dp(this, 24f), Ui.dp(this, 40f))

        val head = TextView(this)
        head.text = "Settings  ·  ${profile.name}"
        head.setTextColor(pal.text)
        head.textSize = 30f
        content.addView(head)

        section("Appearance")
        choice("Theme", arrayOf("Dark", "AMOLED black"), pf.theme) { pf.theme = it }
        val ai = Prefs.ACCENTS.indexOf(pf.accent)
        row("Accent colour", if (ai >= 0) Prefs.ACCENT_NAMES[ai] else "Custom") {
            pf.accent = Prefs.ACCENTS[(ai + 1) % Prefs.ACCENTS.size]
            rebuild()
        }
        row("Custom accent (OK shifts the hue)", "") {
            val hsv = FloatArray(3)
            Color.colorToHSV(pf.accent, hsv)
            val next = (hsv[0] + 30f) % 360f
            pf.accent = Color.HSVToColor(floatArrayOf(next, 0.55f, 1f))
            rebuild()
        }
        choice("Wallpaper style", Ui.WALLPAPER_NAMES, pf.wallpaperPreset) { pf.wallpaperPreset = it }
        row("Wallpaper image", if (pf.wallpaperUri.isEmpty()) "None" else "Custom") { pickWallpaper() }
        if (pf.wallpaperUri.isNotEmpty()) {
            row("Remove wallpaper image", "") {
                pf.wallpaperUri = ""
                rebuild()
            }
        }

        section("Layout")
        val presetNames = arrayOf("Cinematic", "Compact", "Large print")
        row("Layout preset (OK applies the next)", presetLabel) {
            val next = (presetNames.indexOf(presetLabel) + 1) % presetNames.size
            pf.applyPreset(next)
            presetLabel = presetNames[next]
            rebuild()
        }
        choice("Card size", arrayOf("Small", "Medium", "Large"), pf.cardSize) { pf.cardSize = it }
        choice("Grid columns (All Apps)", arrayOf("4", "5", "6", "7", "8"), pf.columns - 4) {
            pf.columns = it + 4
        }
        choice("Spacing", arrayOf("Compact", "Normal", "Roomy"), pf.spacing) { pf.spacing = it }
        choice("Sort apps", arrayOf("A to Z", "Z to A"), pf.sort) { pf.sort = it }

        section("Home header")
        toggle("Clock", pf.showClock) { pf.showClock = it }
        toggle("Date", pf.showDate) { pf.showDate = it }
        toggle("Greeting", pf.showGreeting) { pf.showGreeting = it }
        toggle("Device info line", pf.showInfo) { pf.showInfo = it }
        row("Greeting text", if (pf.greetingText.isEmpty()) "Automatic" else pf.greetingText) {
            promptText("Greeting text", "Leave empty for automatic", pf.greetingText, 40) {
                pf.greetingText = it.trim()
                rebuild()
            }
        }

        section("Motion")
        choice("Animations", arrayOf("Off", "Fast", "Normal"), pf.animSpeed) { pf.animSpeed = it }
        toggle("Intro when Aether starts (all profiles)", ProfileStore.introEnabled) {
            ProfileStore.introEnabled = it
        }
        row("Name shown in the intro", ProfileStore.ownerName) {
            promptText("Name shown in the intro", "Your name", ProfileStore.ownerName, 16) {
                ProfileStore.ownerName = it
                rebuild()
            }
        }

        section("Apps")
        row("Hidden apps", "${AppRepository.hiddenApps(pf).size}") { showHiddenApps() }
        row("Clear recent apps", "") {
            pf.clearRecents()
            toast("Recent apps cleared")
        }
        note("Reorder favorites: hold OK on a favorite app on Home, then choose Move left or Move right.")

        section("Profile and security")
        row("Edit this profile (name and avatar)", profile.name) { editProfile() }
        if (!profile.hasPin) {
            row("PIN protection", "Off. OK to turn on") { startSetPin() }
        } else {
            row("PIN protection", "On. OK to change PIN") { askCurrentPin { startSetPin() } }
            row("Turn PIN protection off", "") {
                askCurrentPin {
                    ProfileStore.clearPin(profile.id)
                    toast("PIN protection is off")
                    rebuild()
                }
            }
        }
        note("The PIN only protects Aether TV. If you forget it: Android Settings > Apps > Aether TV > Clear storage (this resets all profiles).")

        section("Reset and about")
        row("Reset this profile's settings", "") {
            confirm(
                "Reset settings?",
                "Colours, layout, favorites, hidden apps and recents of this profile go back to default.",
                "Reset"
            ) {
                pf.resetAll()
                rebuild()
            }
        }
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }
        row("Aether TV version", "$version") {}
        row("Privacy", "No ads, no analytics, no internet permission") {}
        row("Close settings", "") { finish() }
    }

    private fun rebuild() {
        val focused = currentFocus
        val idx = if (focused != null) rows.indexOf(focused) else -1
        content.removeAllViews()
        rows.clear()
        build()
        val target = rows.getOrNull(if (idx >= 0) idx else 0)
        if (target != null) target.post { target.requestFocus() }
    }

    // ------------------------------------------------------------ row helpers

    private fun section(text: String) {
        val t = TextView(this)
        t.text = text
        t.setTextColor(pal.accent)
        t.textSize = 18f
        t.letterSpacing = 0.06f
        val lp = LinearLayout.LayoutParams(wrap, wrap)
        lp.topMargin = Ui.dp(this, 26f)
        content.addView(t, lp)
    }

    private fun note(text: String) {
        val t = TextView(this)
        t.text = text
        t.setTextColor(pal.textDim)
        t.textSize = 13f
        val lp = LinearLayout.LayoutParams(match, wrap)
        lp.topMargin = Ui.dp(this, 8f)
        content.addView(t, lp)
    }

    private fun row(title: String, value: String, onClick: () -> Unit): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.isFocusable = true
        r.isClickable = true
        r.setPadding(Ui.dp(this, 20f), Ui.dp(this, 14f), Ui.dp(this, 20f), Ui.dp(this, 14f))
        r.background = W.focusBg(this, pal, 14f, pal.surface)
        r.setOnClickListener { onClick() }

        val t = TextView(this)
        t.text = title
        t.setTextColor(pal.text)
        t.textSize = 18f
        r.addView(t, LinearLayout.LayoutParams(0, wrap, 1f))

        if (value.isNotEmpty()) {
            val v = TextView(this)
            v.text = value
            v.setTextColor(pal.accent)
            v.textSize = 16f
            v.gravity = Gravity.END
            r.addView(v, LinearLayout.LayoutParams(wrap, wrap))
        }

        val lp = LinearLayout.LayoutParams(match, wrap)
        lp.topMargin = Ui.dp(this, 6f)
        content.addView(r, lp)
        rows.add(r)
        return r
    }

    private fun choice(title: String, options: Array<String>, current: Int, onPick: (Int) -> Unit) {
        val cur = current.coerceIn(0, options.size - 1)
        row(title, options[cur]) {
            onPick((cur + 1) % options.size)
            rebuild()
        }
    }

    private fun toggle(title: String, on: Boolean, onSet: (Boolean) -> Unit) {
        row(title, if (on) "On" else "Off") {
            onSet(!on)
            rebuild()
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ------------------------------------------------------------ dialogs

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        val pad = Ui.dp(this, 28f)
        c.setPadding(pad, pad, pad, pad)
        c.background = Ui.rounded(pal.surface, Ui.dp(this, 24f).toFloat())
        return c
    }

    private fun heading(text: String): TextView {
        val t = TextView(this)
        t.text = text
        t.setTextColor(pal.text)
        t.textSize = 24f
        t.gravity = Gravity.CENTER
        return t
    }

    private fun buttonRow(vararg buttons: View): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER
        val m = Ui.dp(this, 8f)
        for (b in buttons) {
            val lp = LinearLayout.LayoutParams(wrap, wrap)
            lp.setMargins(m, 0, m, 0)
            r.addView(b, lp)
        }
        return r
    }

    private fun addWithTop(parent: LinearLayout, child: View, topDp: Float) {
        val lp = LinearLayout.LayoutParams(wrap, wrap)
        lp.topMargin = Ui.dp(this, topDp)
        parent.addView(child, lp)
    }

    private fun confirm(title: String, message: String, yes: String, onYes: () -> Unit) {
        val c = card()
        c.addView(heading(title))
        val msg = TextView(this)
        msg.text = message
        msg.setTextColor(pal.textDim)
        msg.textSize = 16f
        msg.maxWidth = Ui.dp(this, 420f)
        msg.gravity = Gravity.CENTER
        addWithTop(c, msg, 12f)
        val cancel = W.button(this, pal, "Cancel") { overlay.hideTop() }
        val ok = W.button(this, pal, yes) {
            overlay.hideTop()
            onYes()
        }
        addWithTop(c, buttonRow(cancel, ok), 18f)
        overlay.show(c)
    }

    private fun promptText(
        title: String,
        hint: String,
        current: String,
        maxLen: Int,
        onSave: (String) -> Unit
    ) {
        val c = card()
        c.addView(heading(title))
        val edit = EditText(this)
        edit.setText(current)
        edit.hint = hint
        edit.setTextColor(pal.text)
        edit.setHintTextColor(pal.textDim)
        edit.textSize = 20f
        edit.setSingleLine(true)
        edit.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(maxLen))
        edit.inputType = InputType.TYPE_CLASS_TEXT
        edit.imeOptions = EditorInfo.IME_ACTION_DONE
        edit.minWidth = Ui.dp(this, 360f)
        edit.setPadding(Ui.dp(this, 20f), Ui.dp(this, 12f), Ui.dp(this, 20f), Ui.dp(this, 12f))
        edit.background = W.focusBg(this, pal, 12f, pal.surfaceHigh)
        addWithTop(c, edit, 14f)
        val tip = TextView(this)
        tip.text = "Press OK on the box to type"
        tip.setTextColor(pal.textDim)
        tip.textSize = 13f
        c.addView(tip)
        val cancel = W.button(this, pal, "Cancel") { overlay.hideTop() }
        val save = W.button(this, pal, "Save") {
            overlay.hideTop()
            onSave(edit.text.toString())
        }
        addWithTop(c, buttonRow(save, cancel), 16f)
        overlay.show(c)
    }

    private fun showHiddenApps() {
        val list = AppRepository.hiddenApps(pf)
        val c = card()
        c.addView(heading("Hidden apps"))
        if (list.isEmpty()) {
            val t = TextView(this)
            t.text = "No hidden apps. Hide an app from its menu on Home (hold OK)."
            t.setTextColor(pal.textDim)
            t.textSize = 16f
            t.maxWidth = Ui.dp(this, 420f)
            t.gravity = Gravity.CENTER
            addWithTop(c, t, 12f)
        } else {
            val sv = ScrollView(this)
            val inner = LinearLayout(this)
            inner.orientation = LinearLayout.VERTICAL
            for (app in list) {
                val b = W.button(this, pal, "Restore: ${app.label}") {
                    pf.unhide(app.packageName)
                    overlay.hideTop()
                    rebuild()
                }
                val lp = LinearLayout.LayoutParams(match, wrap)
                lp.topMargin = Ui.dp(this, 6f)
                inner.addView(b, lp)
            }
            sv.addView(inner)
            val svLp = LinearLayout.LayoutParams(Ui.dp(this, 420f), Ui.dp(this, 300f))
            svLp.topMargin = Ui.dp(this, 12f)
            c.addView(sv, svLp)
        }
        val close = W.button(this, pal, "Close") { overlay.hideTop() }
        addWithTop(c, close, 14f)
        overlay.show(c)
    }

    // ------------------------------------------------------------ profile and PIN

    private fun editProfile() {
        val onDelete: (() -> Unit)? =
            if (profile.id == ProfileStore.USER_ID) null else ({ confirmDelete() })
        val c = ProfileEditor.build(
            this, pal, profile,
            { name, avatar ->
                ProfileStore.rename(profile.id, name)
                ProfileStore.setAvatar(profile.id, avatar)
                overlay.hideTop()
                rebuild()
            },
            onDelete,
            { overlay.hideTop() }
        )
        overlay.show(c)
    }

    private fun confirmDelete() {
        confirm(
            "Delete ${profile.name}?",
            "This removes the profile and its settings. The apps on your box are not touched.",
            "Delete"
        ) {
            overlay.hideAll()
            ProfileStore.delete(this, profile.id)
            finish()
        }
    }

    private fun startSetPin() {
        val pad = PinPadView(this, pal)
        var first: String? = null
        pad.setTitle("Choose a PIN")
        pad.setMessage("4 to 8 digits", false)
        pad.onSubmit = { pin ->
            val f = first
            if (f == null) {
                first = pin
                pad.clearEntry()
                pad.setTitle("Repeat the PIN")
                pad.setMessage("Type it again to confirm", false)
            } else if (f == pin) {
                ProfileStore.setPin(profile.id, pin)
                overlay.hideTop()
                toast("PIN protection is on")
                rebuild()
            } else {
                first = null
                pad.clearEntry()
                pad.setTitle("Choose a PIN")
                pad.setMessage("The PINs did not match. Start again.", true)
            }
        }
        overlay.show(pad)
        pad.post { pad.focusStart() }
    }

    private fun askCurrentPin(then: () -> Unit) {
        val pad = PinPadView(this, pal)
        pad.setTitle("Enter current PIN")
        pad.setMessage("Needed to change this setting", false)
        pad.onSubmit = { pin ->
            when (ProfileStore.verifyPin(profile.id, pin)) {
                ProfileStore.RESULT_OK -> {
                    overlay.hideTop()
                    then()
                }
                ProfileStore.RESULT_LOCKED -> {
                    pad.clearEntry()
                    val s = (ProfileStore.lockRemainingMs(profile.id) + 999L) / 1000L
                    pad.setMessage("Too many tries. Wait $s s", true)
                }
                else -> {
                    pad.clearEntry()
                    pad.setMessage("Wrong PIN", true)
                }
            }
        }
        overlay.show(pad)
        pad.post { pad.focusStart() }
    }

    // ------------------------------------------------------------ wallpaper image

    private fun pickWallpaper() {
        try {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "image/*"
            i.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
            startActivityForResult(i, reqWallpaper)
        } catch (e: Exception) {
            toast("This box has no file picker for images")
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == reqWallpaper && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // some pickers do not offer persistable access
            }
            pf.wallpaperUri = uri.toString()
            rebuild()
        }
    }
}
