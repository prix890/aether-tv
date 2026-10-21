package com.aether.tv

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** What the home and All Apps screens ask the activity to do. */
interface HomeHost {
    fun launchApp(app: AppEntry)
    fun showAppMenu(app: AppEntry)
    fun openAllApps()
    fun openSettings()
    fun switchProfile()
    fun openSystemSettings(action: String)
}

class MenuItem(val label: String, val action: () -> Unit)

class AppCardView(
    context: Context,
    private val p: Palette,
    imageW: Int,
    private val animMs: Long
) : LinearLayout(context) {

    var boundPkg: String? = null
        private set

    private val holder = FrameLayout(context)
    private val image = ImageView(context)
    private val fallback = TextView(context)
    private val nameView = TextView(context)

    init {
        orientation = VERTICAL
        isFocusable = true
        isClickable = true
        val pad = Ui.dp(context, 6f)
        setPadding(pad, pad, pad, pad)
        background = W.focusBg(context, p, 16f, p.surface)

        holder.setBackgroundColor(p.surfaceHigh)
        W.roundClip(holder, Ui.dp(context, 10f).toFloat())
        addView(holder, LinearLayout.LayoutParams(imageW, imageW * 9 / 16))

        image.scaleType = ImageView.ScaleType.FIT_CENTER
        holder.addView(
            image,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        fallback.setTextColor(p.textDim)
        fallback.textSize = 30f
        fallback.gravity = Gravity.CENTER
        holder.addView(
            fallback,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        nameView.setTextColor(p.text)
        nameView.textSize = 14f
        nameView.setSingleLine(true)
        nameView.ellipsize = TextUtils.TruncateAt.END
        nameView.gravity = Gravity.CENTER_HORIZONTAL
        val nameLp = LinearLayout.LayoutParams(imageW, LinearLayout.LayoutParams.WRAP_CONTENT)
        nameLp.topMargin = Ui.dp(context, 6f)
        addView(nameView, nameLp)

        setOnFocusChangeListener { v, hasFocus ->
            val s = if (hasFocus) 1.06f else 1f
            if (animMs > 0L) {
                v.animate().scaleX(s).scaleY(s).setDuration(animMs).start()
            } else {
                v.scaleX = s
                v.scaleY = s
            }
        }
    }

    fun bind(e: AppEntry) {
        val pkg = e.packageName
        boundPkg = pkg
        tag = e
        nameView.text = e.label
        fallback.text = e.label.trim().take(1).uppercase()
        val cached = IconCache.peek(pkg)
        if (cached != null) {
            show(cached)
        } else {
            image.setImageDrawable(null)
            fallback.visibility = VISIBLE
            IconCache.load(context, pkg) { r ->
                if (boundPkg == pkg) show(r)
            }
        }
    }

    fun unbind() {
        boundPkg = null
        tag = null
        image.setImageDrawable(null)
    }

    private fun show(r: IconResult?) {
        if (r == null) {
            image.setImageDrawable(null)
            fallback.visibility = VISIBLE
            return
        }
        fallback.visibility = GONE
        image.setImageBitmap(r.bitmap)
        if (r.isBanner) {
            image.scaleType = ImageView.ScaleType.CENTER_CROP
            image.setPadding(0, 0, 0, 0)
        } else {
            image.scaleType = ImageView.ScaleType.FIT_CENTER
            val ip = Ui.dp(context, 16f)
            image.setPadding(ip, ip, ip, ip)
        }
    }
}

class AppAdapter(
    private val context: Context,
    private val p: Palette,
    private val imageW: Int,
    private val gapPx: Int,
    private val animMs: Long,
    private val onClick: (AppEntry) -> Unit,
    private val onMenu: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    class VH(val card: AppCardView) : RecyclerView.ViewHolder(card)

    private var items: List<AppEntry> = emptyList()

    /** Only touches the list (and resets focus) when something actually changed. */
    fun submitIfChanged(list: List<AppEntry>) {
        if (list == items) return
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val card = AppCardView(context, p, imageW, animMs)
        val lp = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val half = gapPx / 2
        lp.setMargins(half, half, half, half)
        card.layoutParams = lp
        return VH(card)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        val card = holder.card
        card.bind(e)
        card.setOnClickListener { onClick(e) }
        card.setOnLongClickListener {
            onMenu(e)
            true
        }
    }

    override fun onViewRecycled(holder: VH) {
        holder.card.unbind()
    }
}
