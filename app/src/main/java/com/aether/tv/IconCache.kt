package com.aether.tv

import android.content.ComponentCallbacks2
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors

class IconResult(val bitmap: Bitmap, val isBanner: Boolean)

object IconCache {

    private const val BANNER_W = 256
    private const val BANNER_H = 144
    private const val ICON_SIZE = 128

    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    private val cache = object : LruCache<String, IconResult>(budgetBytes()) {
        override fun sizeOf(key: String, value: IconResult): Int = value.bitmap.byteCount
    }

    private fun budgetBytes(): Int {
        val share = (Runtime.getRuntime().maxMemory() / 16L).toInt()
        return share.coerceIn(4 * 1024 * 1024, 20 * 1024 * 1024)
    }

    fun peek(pkg: String): IconResult? = cache.get(pkg)

    /** Calls onLoaded on the main thread. The result is null if no image could be made. */
    fun load(context: Context, pkg: String, onLoaded: (IconResult?) -> Unit) {
        val cached = cache.get(pkg)
        if (cached != null) {
            onLoaded(cached)
            return
        }
        val appContext = context.applicationContext
        executor.execute {
            val result = try {
                render(appContext, pkg)
            } catch (e: Throwable) {
                null
            }
            if (result != null) cache.put(pkg, result)
            main.post { onLoaded(result) }
        }
    }

    fun clear() {
        cache.evictAll()
    }

    /** Called from onTrimMemory. Ignores the routine "UI hidden" signal. */
    fun trim(level: Int) {
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) cache.evictAll()
    }

    private fun render(ctx: Context, pkg: String): IconResult? {
        val pm = ctx.packageManager

        var banner: Drawable? = null
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
        if (intent != null) {
            banner = try {
                pm.getActivityBanner(intent)
            } catch (e: Exception) {
                null
            }
        }
        if (banner == null) {
            banner = try {
                pm.getApplicationBanner(pkg)
            } catch (e: Exception) {
                null
            }
        }
        if (banner != null) {
            return IconResult(toBitmap(banner, BANNER_W, BANNER_H), true)
        }

        val icon: Drawable? = try {
            pm.getApplicationIcon(pkg)
        } catch (e: Exception) {
            null
        }
        if (icon == null) return null
        return IconResult(toBitmap(icon, ICON_SIZE, ICON_SIZE), false)
    }

    private fun toBitmap(d: Drawable, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        d.setBounds(0, 0, w, h)
        d.draw(canvas)
        return bmp
    }
}
