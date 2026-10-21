package com.aether.tv

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

data class AppEntry(val packageName: String, val label: String)

object AppRepository {

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var apps: List<AppEntry> = emptyList()
        private set

    @Volatile
    var loaded: Boolean = false
        private set

    /** Scans installed apps on a background thread, then calls onDone on the main thread. */
    fun refresh(context: Context, onDone: (List<AppEntry>) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            val list = scan(appContext)
            apps = list
            loaded = true
            main.post { onDone(list) }
        }
    }

    private fun scan(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()
        val categories = arrayOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (category in categories) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            val results = try {
                pm.queryIntentActivities(query, 0)
            } catch (e: Exception) {
                emptyList<ResolveInfo>()
            }
            for (ri in results) {
                val info = ri.activityInfo ?: continue
                val pkg = info.packageName
                if (pkg == ctx.packageName) continue
                if (!seen.add(pkg)) continue
                val label = try {
                    ri.loadLabel(pm).toString()
                } catch (e: Exception) {
                    pkg
                }
                out.add(AppEntry(pkg, label))
            }
        }
        return out
    }

    fun find(pkg: String): AppEntry? = apps.firstOrNull { it.packageName == pkg }

    /** Apps that are not hidden, sorted according to the saved sort setting. */
    fun visible(prefs: Prefs): List<AppEntry> {
        val hidden = prefs.hidden().toHashSet()
        val list = apps.filter { !hidden.contains(it.packageName) }
        return if (prefs.sort == Prefs.SORT_ZA) {
            list.sortedByDescending { it.label.lowercase() }
        } else {
            list.sortedBy { it.label.lowercase() }
        }
    }

    /** Apps the user hid, so Settings can restore them. */
    fun hiddenApps(prefs: Prefs): List<AppEntry> {
        val hidden = prefs.hidden().toHashSet()
        return apps.filter { hidden.contains(it.packageName) }
            .sortedBy { it.label.lowercase() }
    }

    fun favoriteApps(prefs: Prefs): List<AppEntry> =
        prefs.favorites().mapNotNull { find(it) }

    fun recentApps(prefs: Prefs): List<AppEntry> =
        prefs.recents().mapNotNull { find(it) }

    fun search(prefs: Prefs, query: String): List<AppEntry> {
        val q = query.trim()
        if (q.isEmpty()) return visible(prefs)
        return visible(prefs).filter { it.label.contains(q, ignoreCase = true) }
    }

    /** Returns false if the app cannot be launched (removed, or no launch intent). */
    fun launch(context: Context, pkg: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
