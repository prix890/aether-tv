package com.aether.tv

import android.content.Context
import android.content.SharedPreferences

class Prefs private constructor(context: Context) {

    private val sp: SharedPreferences =
        context.getSharedPreferences("aether_prefs", Context.MODE_PRIVATE)

    private fun int(key: String, def: Int): Int = sp.getInt(key, def)
    private fun bool(key: String, def: Boolean): Boolean = sp.getBoolean(key, def)
    private fun str(key: String, def: String): String = sp.getString(key, def) ?: def
    private fun put(key: String, v: Int) { sp.edit().putInt(key, v).apply() }
    private fun put(key: String, v: Boolean) { sp.edit().putBoolean(key, v).apply() }
    private fun put(key: String, v: String) { sp.edit().putString(key, v).apply() }

    private fun getList(key: String): MutableList<String> {
        val s = sp.getString(key, "") ?: ""
        return if (s.isEmpty()) mutableListOf() else s.split("\n").toMutableList()
    }

    private fun putList(key: String, list: List<String>) {
        sp.edit().putString(key, list.joinToString("\n")).apply()
    }

    // ---------- Appearance ----------
    var theme: Int
        get() = int("theme", THEME_DARK)
        set(v) = put("theme", v)

    var accent: Int
        get() = int("accent", ACCENTS[0])
        set(v) = put("accent", v)

    var wallpaperPreset: Int
        get() = int("wallpaperPreset", 0)
        set(v) = put("wallpaperPreset", v)

    /** Empty string means "no custom image". */
    var wallpaperUri: String
        get() = str("wallpaperUri", "")
        set(v) = put("wallpaperUri", v)

    // ---------- Layout ----------
    var cardSize: Int
        get() = int("cardSize", 1)
        set(v) = put("cardSize", v)

    var spacing: Int
        get() = int("spacing", 1)
        set(v) = put("spacing", v)

    var columns: Int
        get() = int("columns", 6)
        set(v) = put("columns", v)

    var sort: Int
        get() = int("sort", SORT_AZ)
        set(v) = put("sort", v)

    // ---------- Motion ----------
    var animSpeed: Int
        get() = int("animSpeed", ANIM_NORMAL)
        set(v) = put("animSpeed", v)

    var introEnabled: Boolean
        get() = bool("introEnabled", true)
        set(v) = put("introEnabled", v)

    // ---------- Header ----------
    var userName: String
        get() = str("userName", "Priyesh")
        set(v) = put("userName", v)

    /** Empty string means "automatic greeting". */
    var greetingText: String
        get() = str("greetingText", "")
        set(v) = put("greetingText", v)

    var showClock: Boolean
        get() = bool("showClock", true)
        set(v) = put("showClock", v)

    var showDate: Boolean
        get() = bool("showDate", true)
        set(v) = put("showDate", v)

    var showGreeting: Boolean
        get() = bool("showGreeting", true)
        set(v) = put("showGreeting", v)

    var showInfo: Boolean
        get() = bool("showInfo", true)
        set(v) = put("showInfo", v)

    // ---------- Favorites / hidden / recents ----------
    fun favorites(): MutableList<String> = getList("favorites")

    fun isFavorite(pkg: String): Boolean = favorites().contains(pkg)

    fun addFavorite(pkg: String) {
        val l = favorites()
        if (!l.contains(pkg)) {
            l.add(pkg)
            putList("favorites", l)
        }
    }

    fun removeFavorite(pkg: String) {
        val l = favorites()
        if (l.remove(pkg)) putList("favorites", l)
    }

    fun moveFavorite(pkg: String, delta: Int) {
        val l = favorites()
        val i = l.indexOf(pkg)
        if (i < 0) return
        val j = i + delta
        if (j < 0 || j >= l.size) return
        l.removeAt(i)
        l.add(j, pkg)
        putList("favorites", l)
    }

    fun hidden(): MutableList<String> = getList("hidden")

    fun isHidden(pkg: String): Boolean = hidden().contains(pkg)

    fun hide(pkg: String) {
        val h = hidden()
        if (!h.contains(pkg)) {
            h.add(pkg)
            putList("hidden", h)
        }
        removeFavorite(pkg)
        val r = recents()
        if (r.remove(pkg)) putList("recents", r)
    }

    fun unhide(pkg: String) {
        val h = hidden()
        if (h.remove(pkg)) putList("hidden", h)
    }

    fun recents(): MutableList<String> = getList("recents")

    fun pushRecent(pkg: String) {
        val l = recents()
        l.remove(pkg)
        l.add(0, pkg)
        while (l.size > MAX_RECENTS) l.removeAt(l.size - 1)
        putList("recents", l)
    }

    fun clearRecents() {
        putList("recents", emptyList())
    }

    // ---------- Presets and reset ----------
    fun applyPreset(preset: Int) {
        val e = sp.edit()
        when (preset) {
            PRESET_COMPACT -> {
                e.putInt("cardSize", 0)
                e.putInt("spacing", 0)
                e.putInt("columns", 8)
            }
            PRESET_LARGE -> {
                e.putInt("cardSize", 2)
                e.putInt("spacing", 2)
                e.putInt("columns", 5)
            }
            else -> {
                e.putInt("cardSize", 1)
                e.putInt("spacing", 1)
                e.putInt("columns", 6)
            }
        }
        e.apply()
    }

    fun resetAll() {
        sp.edit().clear().apply()
    }

    companion object {
        const val THEME_DARK = 0
        const val THEME_AMOLED = 1

        const val ANIM_OFF = 0
        const val ANIM_FAST = 1
        const val ANIM_NORMAL = 2

        const val SORT_AZ = 0
        const val SORT_ZA = 1

        const val PRESET_CINEMATIC = 0
        const val PRESET_COMPACT = 1
        const val PRESET_LARGE = 2

        const val MAX_RECENTS = 12

        val ACCENTS = intArrayOf(
            0xFF7C9CFF.toInt(), // Sky
            0xFFB388FF.toInt(), // Violet
            0xFF4DE1B0.toInt(), // Mint
            0xFFFF7A7A.toInt(), // Coral
            0xFFFFC857.toInt(), // Amber
            0xFFFF6FB5.toInt(), // Rose
            0xFF38D6F0.toInt(), // Cyan
            0xFFA6E22E.toInt()  // Lime
        )

        val ACCENT_NAMES = arrayOf(
            "Sky", "Violet", "Mint", "Coral", "Amber", "Rose", "Cyan", "Lime"
        )

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs {
            return instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
        }
    }
}
