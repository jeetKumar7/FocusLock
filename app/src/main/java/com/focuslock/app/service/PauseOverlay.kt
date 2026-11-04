package com.focuslock.app.service

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Full-screen "Do you really want this?" screen shown before a Reel/Short opened outside its
 * tab plays. "Not now" leaves; "Watch it" unlocks only after the countdown.
 */
class PauseOverlay(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var root: View? = null
    private var tick: Runnable? = null

    val isShowing get() = root != null

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()

    private fun pill(text: String, fill: Int, textColor: Int) = TextView(context).apply {
        this.text = text
        textSize = 17f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setTextColor(textColor)
        background = GradientDrawable().apply { cornerRadius = dp(16f).toFloat(); setColor(fill) }
    }

    fun show(title: String, seconds: Int, onLeave: () -> Unit, onWatch: () -> Unit) {
        if (root != null) return

        val count = TextView(context).apply {
            textSize = 54f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        val ring = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x1AFFFFFF)
                setStroke(dp(3f), 0xFF5E5CE6.toInt())
            }
            addView(count, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        val leave = pill("Not now", 0xFF0A84FF.toInt(), 0xFFFFFFFF.toInt()).apply {
            setOnClickListener { dismiss(); onLeave() }
        }
        val watch = pill("", 0x26FFFFFF, 0x80FFFFFF.toInt()).apply {
            isEnabled = false
            setOnClickListener { dismiss(); onWatch() }
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFA15142B.toInt(), 0xFA000000.toInt()),
            )
            setPadding(dp(28f), dp(120f), dp(28f), dp(56f))

            addView(ring, LinearLayout.LayoutParams(dp(132f), dp(132f)))
            addView(TextView(context).apply {
                text = "Do you really want this?"
                textSize = 26f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(0xFFFFFFFF.toInt())
                gravity = Gravity.CENTER
                setPadding(0, dp(28f), 0, dp(8f))
            })
            addView(TextView(context).apply {
                text = "$title\nTake a breath. You can still say no."
                textSize = 16f
                setTextColor(0xB3FFFFFF.toInt())
                gravity = Gravity.CENTER
                setLineSpacing(dp(3f).toFloat(), 1f)
            })
            addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
            addView(leave, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56f)))
            addView(watch, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56f)).apply { topMargin = dp(12f) })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Not focusable: the app underneath stays the active window (so detection keeps
            // working) and the system Back gesture still closes the Reel.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { wm.addView(layout, params) }.onSuccess { root = layout }.onFailure { onWatch(); return }
        layout.alpha = 0f
        layout.animate().alpha(1f).setDuration(220).start()

        var left = seconds
        fun render() {
            count.text = if (left > 0) left.toString() else "✓"
            watch.text = if (left > 0) "Watch in ${left}s" else "Watch it"
            watch.isEnabled = left <= 0
            watch.setTextColor(if (left > 0) 0x80FFFFFF.toInt() else 0xFFFFFFFF.toInt())
        }
        render()
        tick = object : Runnable {
            override fun run() {
                if (root == null) return
                left--
                render()
                if (left > 0) handler.postDelayed(this, 1000)
            }
        }.also { handler.postDelayed(it, 1000) }
    }

    fun dismiss() {
        tick?.let { handler.removeCallbacks(it) }
        tick = null
        root?.let { runCatching { wm.removeView(it) } }
        root = null
    }
}
