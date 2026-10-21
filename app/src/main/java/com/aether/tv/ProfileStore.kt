package com.aether.tv

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class Profile(
    val id: String,
    var name: String,
    var avatar: Int,
    val isGuest: Boolean,
    var pinHash: String,
    var pinSalt: String
) {
    val hasPin: Boolean
        get() = pinHash.isNotEmpty()
}

object ProfileStore {

    const val USER_ID = "user"
    const val GUEST_ID = "guest"
    const val MAX_PROFILES = 8
    const val PIN_MIN = 4
    const val PIN_MAX = 8

    const val RESULT_OK = 0
    const val RESULT_WRONG = 1
    const val RESULT_LOCKED = 2

    private const val MAX_FAILS = 5
    private const val LOCK_MS = 30000L
    private const val NAME_MAX = 16

    val AVATAR_COLORS = intArrayOf(
        0xFF7C9CFF.toInt(),
        0xFFB388FF.toInt(),
        0xFF4DE1B0.toInt(),
        0xFFFF7A7A.toInt(),
        0xFFFFC857.toInt(),
        0xFFFF6FB5.toInt(),
        0xFF38D6F0.toInt(),
        0xFFA6E22E.toInt(),
        0xFFFF9F5A.toInt(),
        0xFF9AA3B8.toInt()
    )

    val AVATAR_NAMES = arrayOf(
        "Sky", "Violet", "Mint", "Coral", "Amber", "Rose", "Cyan", "Lime", "Orange", "Silver"
    )

    private const val GUEST_AVATAR = 9

    private lateinit var sp: SharedPreferences
    private val profiles = ArrayList<Profile>()

    /** The unlocked profile. Kept only in memory, so it is empty after every cold start. */
    @Volatile
    var activeId: String? = null

    @Synchronized
    fun ensure(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("aether_profiles", Context.MODE_PRIVATE)
        load()
    }

    // ---------- Global settings (used before any profile is chosen) ----------
    var introEnabled: Boolean
        get() = sp.getBoolean("introEnabled", true)
        set(v) {
            sp.edit().putBoolean("introEnabled", v).apply()
        }

    /** The name highlighted in the intro. */
    var ownerName: String
        get() = sp.getString("ownerName", "Priyesh") ?: "Priyesh"
        set(v) {
            sp.edit().putString("ownerName", cleanName(v)).apply()
        }

    // ---------- Profiles ----------
    fun all(): List<Profile> = ArrayList(profiles)

    fun get(id: String): Profile? = profiles.firstOrNull { it.id == id }

    fun active(): Profile? {
        val id = activeId ?: return null
        return get(id)
    }

    fun cleanName(raw: String): String {
        val t = raw.trim().take(NAME_MAX)
        return if (t.isEmpty()) "Profile" else t
    }

    fun canCreateMore(): Boolean = profiles.size < MAX_PROFILES

    fun create(name: String, avatar: Int): Profile? {
        if (!canCreateMore()) return null
        val p = Profile(
            "p" + System.currentTimeMillis(),
            cleanName(name),
            avatar.coerceIn(0, AVATAR_COLORS.size - 1),
            false,
            "",
            ""
        )
        profiles.add(p)
        save()
        return p
    }

    fun rename(id: String, name: String): Boolean {
        val p = get(id) ?: return false
        if (p.isGuest) return false
        p.name = cleanName(name)
        save()
        return true
    }

    fun setAvatar(id: String, avatar: Int): Boolean {
        val p = get(id) ?: return false
        if (p.isGuest) return false
        p.avatar = avatar.coerceIn(0, AVATAR_COLORS.size - 1)
        save()
        return true
    }

    /** User and Guest cannot be deleted. */
    fun delete(context: Context, id: String): Boolean {
        val p = get(id) ?: return false
        if (p.isGuest || p.id == USER_ID) return false
        profiles.remove(p)
        if (activeId == id) activeId = null
        save()
        Prefs.forget(id)
        context.applicationContext.deleteSharedPreferences("aether_prefs_$id")
        sp.edit().remove("fails_$id").remove("lock_$id").apply()
        return true
    }

    fun avatarColor(p: Profile): Int =
        if (p.isGuest) AVATAR_COLORS[GUEST_AVATAR]
        else AVATAR_COLORS[p.avatar.coerceIn(0, AVATAR_COLORS.size - 1)]

    fun initial(p: Profile): String {
        val n = p.name.trim()
        return if (n.isEmpty()) "?" else n.substring(0, 1).uppercase()
    }

    // ---------- PIN ----------
    fun pinFormatOk(pin: String): Boolean =
        pin.length in PIN_MIN..PIN_MAX && pin.all { it in '0'..'9' }

    fun setPin(id: String, pin: String): Boolean {
        val p = get(id) ?: return false
        if (p.isGuest || !pinFormatOk(pin)) return false
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        p.pinSalt = Base64.encodeToString(salt, Base64.NO_WRAP)
        p.pinHash = hash(pin, salt)
        save()
        clearFails(id)
        return true
    }

    fun clearPin(id: String) {
        val p = get(id) ?: return
        p.pinHash = ""
        p.pinSalt = ""
        save()
        clearFails(id)
    }

    fun lockRemainingMs(id: String): Long {
        val left = sp.getLong("lock_$id", 0L) - System.currentTimeMillis()
        return if (left > 0L) left else 0L
    }

    /** Returns RESULT_OK, RESULT_WRONG or RESULT_LOCKED. */
    fun verifyPin(id: String, pin: String): Int {
        val p = get(id) ?: return RESULT_WRONG
        if (!p.hasPin) return RESULT_OK
        if (lockRemainingMs(id) > 0L) return RESULT_LOCKED

        val salt = try {
            Base64.decode(p.pinSalt, Base64.NO_WRAP)
        } catch (e: Exception) {
            return RESULT_WRONG
        }
        val attempt = hash(pin, salt)
        val same = MessageDigest.isEqual(attempt.toByteArray(), p.pinHash.toByteArray())
        if (same) {
            clearFails(id)
            return RESULT_OK
        }

        val fails = sp.getInt("fails_$id", 0) + 1
        if (fails >= MAX_FAILS) {
            sp.edit()
                .putInt("fails_$id", 0)
                .putLong("lock_$id", System.currentTimeMillis() + LOCK_MS)
                .apply()
        } else {
            sp.edit().putInt("fails_$id", fails).apply()
        }
        return RESULT_WRONG
    }

    private fun clearFails(id: String) {
        sp.edit().remove("fails_$id").remove("lock_$id").apply()
    }

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 10000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }

    // ---------- Saving and loading ----------
    private fun save() {
        val arr = JSONArray()
        for (p in profiles) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("name", p.name)
            o.put("avatar", p.avatar)
            o.put("pinHash", p.pinHash)
            o.put("pinSalt", p.pinSalt)
            arr.put(o)
        }
        sp.edit().putString("profiles", arr.toString()).apply()
    }

    private fun load() {
        profiles.clear()
        val raw = sp.getString("profiles", null)
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.getString("id")
                    val guest = id == GUEST_ID
                    profiles.add(
                        Profile(
                            id,
                            if (guest) "Guest" else o.optString("name", "Profile"),
                            if (guest) GUEST_AVATAR else o.optInt("avatar", 0),
                            guest,
                            if (guest) "" else o.optString("pinHash", ""),
                            if (guest) "" else o.optString("pinSalt", "")
                        )
                    )
                }
            } catch (e: Exception) {
                profiles.clear()
            }
        }
        var changed = false
        if (profiles.none { it.id == USER_ID }) {
            profiles.add(0, Profile(USER_ID, "User", 0, false, "", ""))
            changed = true
        }
        if (profiles.none { it.id == GUEST_ID }) {
            profiles.add(minOf(1, profiles.size), Profile(GUEST_ID, "Guest", GUEST_AVATAR, true, "", ""))
            changed = true
        }
        if (changed) save()
    }
}
