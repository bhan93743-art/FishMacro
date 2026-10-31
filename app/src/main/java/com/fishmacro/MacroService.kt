package com.fishmacro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class MacroService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var wm: WindowManager? = null
    private var panel: LinearLayout? = null

    // ===== 설정값 =====
    private val holdMs = 1200L
    private val biteTimeoutMs = 8000L
    private val resultWaitMs = 3000L
    private val tapGapMs = 60L
    private val tapXRatio = 0.5f
    private val tapYRatio = 0.55f
    // ==================

    private var state = 0
    private var stateStart = 0L
    private var tapCount = 0
    private var missCount = 0

    private fun now() = System.currentTimeMillis()

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.HORIZONTAL
        layout.setBackgroundColor(Color.argb(150, 0, 0, 0))
        val start = Button(this)
        start.text = "시작"
        start.setOnClickListener { startMacro() }
        val stop = Button(this)
        stop.text = "끝내기"
        stop.setOnClickListener { stopMacro() }
        layout.addView(start)
        layout.addView(stop)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 250
        lp.y = 10
        wm?.addView(layout, lp)
        panel = layout
    }

    private fun startMacro() {
        if (running) return
        running = true
        state = 0
        tapCount = 0
        missCount = 0
        Toast.makeText(this, "낚시 매크로 시작", Toast.LENGTH_SHORT).show()
        handler.post { tick() }
    }

    private fun stopMacro() {
        running = false
        handler.removeCallbacksAndMessages(null)
        Toast.makeText(this, "낚시 매크로 정지", Toast.LENGTH_SHORT).show()
    }

    private fun tick() {
        if (!running) return
        when (state) {
            0 -> {
                gesture(holdMs)
                state = 1
                stateStart = now()
                handler.postDelayed({ tick() }, holdMs + 400)
            }
            1 -> checkMinigame { found ->
                if (running) {
                    if (found == true) {
                        state = 2
                        tapCount = 0
                        missCount = 0
                        handler.post { tick() }
                    } else if (now() - stateStart > biteTimeoutMs) {
                        state = 0
                        handler.postDelayed({ tick() }, 500)
                    } else {
                        handler.postDelayed({ tick() }, 250)
                    }
                }
            }
            2 -> {
                gesture(20L)
                tapCount++
                if (tapCount % 6 == 0) {
                    checkMinigame { found ->
                        if (running) {
                            if (found == true) missCount = 0
                            else if (found == false) missCount++
                            if (missCount >= 2) {
                                state = 3
                                handler.postDelayed({ state = 0; tick() }, resultWaitMs)
                            } else {
                                handler.postDelayed({ tick() }, tapGapMs)
                            }
                        }
                    }
                } else {
                    handler.postDelayed({ tick() }, tapGapMs)
                }
            }
        }
    }

    private fun gesture(duration: Long) {
        val b = wm!!.currentWindowMetrics.bounds
        val p = Path()
        p.moveTo(b.width() * tapXRatio, b.height() * tapYRatio)
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, duration))
            .build()
        dispatchGesture(g, null, null)
    }

    private fun checkMinigame(cb: (Boolean?) -> Unit) {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    var found = false
                    val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    if (hw != null) {
                        val bmp = hw.copy(Bitmap.Config.ARGB_8888, false)
                        hw.recycle()
                        if (bmp != null) {
                            found = detectRed(bmp)
                            bmp.recycle()
                        }
                    }
                    result.hardwareBuffer.close()
                    cb(found)
                }

                override fun onFailure(errorCode: Int) {
                    cb(null)
                }
            }
        )
    }

    private fun detectRed(b: Bitmap): Boolean {
        val w = b.width
        val h = b.height
        val x0 = (w * 0.700f).toInt()
        val x1 = (w * 0.745f).toInt()
        val y0 = (h * 0.88f).toInt()
        val y1 = (h * 0.92f).toInt()
        var cnt = 0
        var total = 0
        var y = y0
        while (y < y1) {
            var x = x0
            while (x < x1) {
                val c = b.getPixel(x, y)
                total++
                if (Color.red(c) > 190 && Color.green(c) < 110 && Color.blue(c) < 110) cnt++
                x += 4
            }
            y += 4
        }
        return total > 0 && cnt * 100 / total >= 20
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        try {
            panel?.let { wm?.removeView(it) }
        } catch (e: Exception) {
        }
        super.onDestroy()
    }
}
