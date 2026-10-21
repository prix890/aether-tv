package com.aether.tv

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity(), HomeHost {

    private enum class Screen { NONE, INTRO, PICKER, HOME, ALL }

    private val match = ViewGroup.LayoutParams.MATCH_PARENT

    private lateinit var root: FrameLayout
    private lateinit var wallpaper: ImageView
    private lateinit var layer: FrameLayout
    private lateinit var overlay: OverlayHost

    private var screen = Screen.NONE
    private var intro: IntroView? = null
    private var home: HomeView? = null
    private var allApps: AllAppsView? = null
    private var prefs: Prefs? = null
    private var palette: Palette? = null

    private var settingsOpened = false
    private var wallpaperToken = 0
    private var pinCountdown: Runnable? = null
    private var pkgReceiver: BroadcastReceiver? = null

    private val handler = Handler(Looper.getMainLooper())
    private val rescan = Runnable { AppRepository.refresh(this) { onAppsChanged() } }

    companion object {
        /** The intro plays once per app start, not every time you press Home. */
        private var introPlayed = false
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProfileStore.ensure(this)

        root = FrameLayout(this)
        wallpaper = ImageView(this)
        wallpaper.scaleType = ImageView.ScaleType.CENTER_CROP
        wallpaper.visibility = View.GONE
        root.addView(wallpaper, FrameLayout.LayoutParams(match, match))

        layer = FrameLayout(this)
        layer.clipChildren = false
        root.addView(layer, FrameLayout.LayoutParams(match, match))

        overlay = OverlayHost(this, layer)
        root.addView(overlay, FrameLayout.LayoutParams(match, match))

        setContentView(root)
        applyTheme()
        registerPackageReceiver()
        AppRepository.refresh(this) { onAppsChanged() }
        startFlow()
    }

    override fun onResume() {
        super.onResume()
        if (settingsOpened) {
            settingsOpened = false
            if (ProfileStore.activeId != null) showHome() else showPicker()
            return
        }
        if (screen == Screen.HOME) {
            home?.refresh()
            home?.updateInfo()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) goHome()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        IconCache.trim(level)
    }

    override fun onDestroy() {
        super.onDestroy()
        intro?.release()
        handler.removeCallbacksAndMessages(null)
        val r = pkgReceiver
        if (r != null) {
            try {
                unregisterReceiver(r)
            } catch (e: Exception) {
                // already unregistered
            }
            pkgReceiver = null
        }
    }

    // ---------------------------------------------------------------- flow

    private fun startFlow() {
        val activeId = ProfileStore.activeId
        if (activeId != null && ProfileStore.get(activeId) != null) {
            showHome()
            return
        }
        if (!introPlayed && ProfileStore.introEnabled) showIntro() else showPicker()
    }

    private fun showIntro() {
        introPlayed = true
        screen = Screen.INTRO
        val view = IntroView(this, Prefs.ACCENTS[0], ProfileStore.ownerName)
        intro = view
        layer.removeAllViews()
        layer.addView(view, FrameLayout.LayoutParams(match, match))
        view.start {
            intro = null
            layer.removeView(view)
            showPicker()
        }
    }

    private fun showPicker() {
        screen = Screen.PICKER
        stopCountdown()
        overlay.hideAll()
        ProfileStore.activeId = null
        home = null
        allApps = null
        applyTheme()
        layer.removeAllViews()
        val view = ProfilePickerView(
            this, pal(), ProfileStore.all(), ProfileStore.canCreateMore(),
            { onProfilePicked(it) },
            { onAddProfile() }
        )
        layer.addView(view, FrameLayout.LayoutParams(match, match))
        view.post { view.focusFirst() }
    }

    private fun onProfilePicked(profile: Profile) {
        if (!profile.hasPin) {
            enterProfile(profile)
        } else {
            showPinPad(profile)
        }
    }

    private fun enterProfile(profile: Profile) {
        stopCountdown()
        ProfileStore.activeId = profile.id
        overlay.hideAll()
        showHome()
    }

    private fun showPinPad(profile: Profile) {
        val pad = PinPadView(this, pal())
        pad.setTitle("Enter PIN for ${profile.name}")
        pad.setMessage("Use the on-screen keys or your remote's number keys", false)
        pad.onSubmit = { pin ->
            when (ProfileStore.verifyPin(profile.id, pin)) {
                ProfileStore.RESULT_OK -> enterProfile(profile)
                ProfileStore.RESULT_LOCKED -> {
                    pad.clearEntry()
                    startCountdown(profile.id, pad)
                }
                else -> {
                    pad.clearEntry()
                    if (ProfileStore.lockRemainingMs(profile.id) > 0L) {
                        startCountdown(profile.id, pad)
                    } else {
                        pad.setMessage("Wrong PIN. Try again.", true)
                    }
                }
            }
        }
        overlay.show(pad)
        pad.post { pad.focusStart() }
        if (ProfileStore.lockRemainingMs(profile.id) > 0L) startCountdown(profile.id, pad)
    }

    private fun startCountdown(id: String, pad: PinPadView) {
        stopCountdown()
        val r = object : Runnable {
            override fun run() {
                val left = ProfileStore.lockRemainingMs(id)
                if (left > 0L) {
                    pad.setMessage("Too many wrong tries. Wait ${(left + 999L) / 1000L} s", true)
                    handler.postDelayed(this, 1000L)
                } else {
                    pad.setMessage("You can try again", false)
                    pinCountdown = null
                }
            }
        }
        pinCountdown = r
        handler.post(r)
    }

    private fun stopCountdown() {
        val r = pinCountdown
        if (r != null) handler.removeCallbacks(r)
        pinCountdown = null
    }

    private fun onAddProfile() {
        val card = ProfileEditor.build(
            this, pal(), null,
            { name, avatar ->
                ProfileStore.create(name, avatar)
                overlay.hideAll()
                showPicker()
            },
            null,
            { overlay.hideTop() }
        )
        overlay.show(card)
    }

    private fun showHome() {
        applyTheme()
        screen = Screen.HOME
        allApps = null
        layer.removeAllViews()
        val pf = prefs ?: Prefs.get(this)
        val h = HomeView(this, pal(), pf, this)
        home = h
        layer.addView(h, FrameLayout.LayoutParams(match, match))
        h.refresh()
        h.focusMain()
    }

    private fun goHome() {
        if (screen == Screen.HOME || screen == Screen.ALL) {
            stopCountdown()
            overlay.hideAll()
            if (screen == Screen.ALL) closeAllApps()
        }
    }

    // ---------------------------------------------------------------- theme and wallpaper

    private fun pal(): Palette = palette ?: Palette(Prefs.get(this))

    private fun applyTheme() {
        val pf = Prefs.get(this)
        val pl = Palette(pf)
        prefs = pf
        palette = pl
        root.background = Ui.backgroundGradient(pl, pf.wallpaperPreset)
        loadWallpaper(pf)
    }

    private fun loadWallpaper(pf: Prefs) {
        val uriText = pf.wallpaperUri
        wallpaperToken++
        val token = wallpaperToken
        if (uriText.isEmpty()) {
            wallpaper.setImageDrawable(null)
            wallpaper.visibility = View.GONE
            return
        }
        val dm = resources.displayMetrics
        val targetW = minOf(dm.widthPixels, 1280)
        val targetH = minOf(dm.heightPixels, 720)
        Thread {
            val bmp = decodeScaled(Uri.parse(uriText), targetW, targetH)
            runOnUiThread {
                if (token == wallpaperToken && !isDestroyed) {
                    if (bmp != null) {
                        wallpaper.setImageBitmap(bmp)
                        wallpaper.setColorFilter(0x88000000.toInt(), PorterDuff.Mode.SRC_OVER)
                        wallpaper.visibility = View.VISIBLE
                    } else {
                        wallpaper.visibility = View.GONE
                    }
                }
            }
        }.start()
    }

    private fun decodeScaled(uri: Uri, w: Int, h: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) {
                sample *= 2
            }
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Throwable) {
            null
        }
    }

    // ---------------------------------------------------------------- apps changing

    private fun registerPackageReceiver() {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_PACKAGE_ADDED)
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED)
        filter.addDataScheme("package")
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                handler.removeCallbacks(rescan)
                handler.postDelayed(rescan, 800L)
            }
        }
        pkgReceiver = r
        registerReceiver(r, filter)
    }

    private fun onAppsChanged() {
        if (isDestroyed) return
        home?.refresh()
        allApps?.reload()
    }

    // ---------------------------------------------------------------- HomeHost

    override fun launchApp(app: AppEntry) {
        if (AppRepository.launch(this, app.packageName)) {
            prefs?.pushRecent(app.packageName)
        } else {
            Toast.makeText(
                this,
                "${app.label} can't be opened (it may have been removed)",
                Toast.LENGTH_LONG
            ).show()
            AppRepository.refresh(this) { onAppsChanged() }
        }
    }

    override fun openAllApps() {
        val pf = prefs ?: return
        screen = Screen.ALL
        val v = AllAppsView(this, pal(), pf, this)
        allApps = v
        home?.visibility = View.GONE
        layer.addView(v, FrameLayout.LayoutParams(match, match))
        v.reload()
        v.focusGrid()
    }

    private fun closeAllApps() {
        val v = allApps ?: return
        layer.removeView(v)
        allApps = null
        screen = Screen.HOME
        home?.visibility = View.VISIBLE
        home?.focusAllAppsButton()
    }

    override fun openSettings() {
        if (prefs?.isGuest == true) return
        settingsOpened = true
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun switchProfile() {
        showPicker()
    }

    override fun openSystemSettings(action: String) {
        try {
            val i = Intent(action)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "That setting isn't available on this device", Toast.LENGTH_LONG).show()
        }
    }

    override fun showAppMenu(app: AppEntry) {
        val pf = prefs ?: return
        if (pf.isGuest) return
        val pkg = app.packageName

        val items = ArrayList<MenuItem>()
        items.add(MenuItem("Open") {
            overlay.hideTop()
            launchApp(app)
        })
        if (pf.isFavorite(pkg)) {
            items.add(MenuItem("Move left in favorites") {
                pf.moveFavorite(pkg, -1)
                afterMenuChange()
            })
            items.add(MenuItem("Move right in favorites") {
                pf.moveFavorite(pkg, 1)
                afterMenuChange()
            })
            items.add(MenuItem("Remove from favorites") {
                pf.removeFavorite(pkg)
                afterMenuChange()
            })
        } else {
            items.add(MenuItem("Add to favorites") {
                pf.addFavorite(pkg)
                afterMenuChange()
            })
        }
        items.add(MenuItem("Hide from launcher") {
            pf.hide(pkg)
            afterMenuChange()
        })
        items.add(MenuItem("Cancel") { overlay.hideTop() })

        val pl = pal()
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = android.view.Gravity.CENTER_HORIZONTAL
        val pad = Ui.dp(this, 24f)
        card.setPadding(pad, pad, pad, pad)
        card.background = Ui.rounded(pl.surface, Ui.dp(this, 24f).toFloat())

        val title = TextView(this)
        title.text = app.label
        title.setTextColor(pl.text)
        title.textSize = 22f
        title.setSingleLine(true)
        card.addView(title)

        for (item in items) {
            val b = W.button(this, pl, item.label) { item.action() }
            val lp = LinearLayout.LayoutParams(Ui.dp(this, 320f), ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = Ui.dp(this, 8f)
            card.addView(b, lp)
        }
        overlay.show(card)
    }

    private fun afterMenuChange() {
        overlay.hideTop()
        home?.refresh()
        allApps?.reload()
    }

    // ---------------------------------------------------------------- remote keys

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (screen == Screen.INTRO) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE,
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        intro?.skip()
                        return true
                    }
                }
            }
            return super.dispatchKeyEvent(event)
        }

        if (event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0 &&
            event.keyCode == KeyEvent.KEYCODE_MENU &&
            (screen == Screen.HOME || screen == Screen.ALL) &&
            !overlay.isShowing
        ) {
            val app = currentFocus?.tag as? AppEntry
            if (app != null) {
                showAppMenu(app)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (overlay.isShowing) {
            stopCountdown()
            overlay.hideTop()
            return
        }
        when (screen) {
            Screen.ALL -> closeAllApps()
            Screen.INTRO -> intro?.skip()
            else -> {
                // A launcher never exits on Back.
            }
        }
    }
}
