package com.aether.tv

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import java.util.Calendar

class Palette(prefs: Prefs) {
    val amoled: Boolean = prefs.theme == Prefs.THEME_AMOLED
    val accent: Int = prefs.accent
    val bg: Int = if (amoled) 0xFF000000.toInt() else 0xFF0B0D12.toInt()
    val surface: Int = if (amoled) 0xFF0E0F13.toInt() else 0xFF151925.toInt()
    val surfaceHigh: Int = if (amoled) 0xFF1A1B22.toInt() else 0xFF1E2434.toInt()
    val text: Int = 0xFFF2F4FA.toInt()
    val textDim: Int = 0xFF9AA3B8.toInt()
}

object Ui {

    val WALLPAPER_NAMES = arrayOf("Aurora", "Midnight", "Dusk", "Ocean", "Plain")

    fun dp(ctx: Context, v: Float): Int =
        (v * ctx.resources.displayMetrics.density + 0.5f).toInt()

    fun rounded(color: Int, radiusPx: Float): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = radiusPx
        g.setColor(color)
        return g
    }

    fun rounded(color: Int, radiusPx: Float, strokePx: Int, strokeColor: Int): GradientDrawable {
        val g = rounded(color, radiusPx)
        g.setStroke(strokePx, strokeColor)
        return g
    }

    /** A cheap two-colour gradient used as the wallpaper. Drawn once, never animated. */
    fun backgroundGradient(p: Palette, preset: Int): GradientDrawable {
        val tint = when (preset) {
            0 -> p.accent
            1 -> 0xFF2A3560.toInt()
            2 -> 0xFF4B2A7A.toInt()
            3 -> 0xFF0F5A73.toInt()
            else -> p.bg
        }
        val strength = if (p.amoled) 0.90f else 0.78f
        val top = ColorUtils.blendARGB(tint, p.bg, strength)
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(top, p.bg)
        )
    }

    fun greeting(prefs: Prefs): String {
        val custom = prefs.greetingText.trim()
        if (custom.isNotEmpty()) return custom
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val part = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        val name = prefs.userName.trim()
        return if (name.isEmpty()) part else "$part, $name"
    }

    fun cardWidth(ctx: Context, prefs: Prefs): Int = dp(
        ctx,
        when (prefs.cardSize) {
            0 -> 150f
            2 -> 230f
            else -> 190f
        }
    )

    fun gap(ctx: Context, prefs: Prefs): Int = dp(
        ctx,
        when (prefs.spacing) {
            0 -> 8f
            2 -> 20f
            else -> 14f
        }
    )

    /** Scales an animation length by the Off / Fast / Normal setting. */
    fun animMs(prefs: Prefs, base: Long): Long = when (prefs.animSpeed) {
        Prefs.ANIM_OFF -> 0L
        Prefs.ANIM_FAST -> base / 2
        else -> base
    }
}
