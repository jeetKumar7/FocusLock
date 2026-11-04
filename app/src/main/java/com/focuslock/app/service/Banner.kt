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
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView

/**
 * iOS-style notification banner that drops in from the top when something is blocked:
 * a dark rounded card with a coloured badge, a bold title and up to two lines below.
 */
class Banner(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()

    fun show(title: String, line: String?, footer: String?) {
        if (view != null) return
        val badge = TextView(context).apply {
            text = "✋"
            textSize = 18f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(10f).toFloat()
                colors = intArrayOf(0xFF5E5CE6.toInt(), 0xFF0A84FF.toInt())
                orientation = GradientDrawable.Orientation.TL_BR
            }
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(0xFFFFFFFF.toInt())
            })
            line?.let {
                addView(TextView(context).apply {
                    text = it
                    textSize = 14f
                    setTextColor(0xE6FFFFFF.toInt())
                })
            }
            footer?.let {
                addView(TextView(context).apply {
                    text = it
                    textSize = 12f
                    setTextColor(0x99FFFFFF.toInt())
                    setPadding(0, dp(2f), 0, 0)
                })
            }
        }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14f), dp(12f), dp(18f), dp(12f))
            background = GradientDrawable().apply {
                cornerRadius = dp(22f).toFloat()
                setColor(0xF21C1C1E.toInt())
                setStroke(dp(0.5f).coerceAtLeast(1), 0x33FFFFFF)
            }
            elevation = dp(12f).toFloat()
            addView(badge, LinearLayout.LayoutParams(dp(38f), dp(38f)).apply { marginEnd = dp(12f) })
            addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val screenW = context.resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            screenW - dp(24f),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(44f)
        }
        runCatching { wm.addView(card, params) }.onSuccess {
            view = card
            card.alpha = 0f
            card.translationY = -dp(40f).toFloat()
            card.animate().alpha(1f).translationY(0f).setDuration(380).setInterpolator(OvershootInterpolator(1.1f)).start()
            handler.postDelayed({
                card.animate().alpha(0f).translationY(-dp(30f).toFloat()).setDuration(260)
                    .setInterpolator(DecelerateInterpolator())
                    .withEndAction {
                        runCatching { wm.removeView(card) }
                        view = null
                    }.start()
            }, 2400)
        }
    }

    fun dismiss() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }
}
