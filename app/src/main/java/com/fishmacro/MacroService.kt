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
    private val holdMs = 700L
    private val biteTimeoutMs = 8000L
    private val minBiteMs = 1500L
    private val resultWaitMs = 1500L
    private val tapGapMs = 60L
    private val endGapMs = 1800L
    private val maxMinigameMs = 25000L
    private val shotIntervalMs = 1100L
    private val tapXRatio = 0.3f
    private val tapYRatio = 0.5f
    // ==================

    private var state = 0
    private var stateStart = 0L
    private var lastSeen = 0L
    private var lastShot = 0L
    private var shotBusy = false

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
        lastSeen = 0L
        lastShot = 0L
        shotBusy = false
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
        val t = now()
        var delay = 300L
        when (state) {
            0 -> {
                gesture(holdMs)
                state = 1
                stateStart = t + holdMs
                delay = holdMs + 300
            }
            1 -> {
                requestShot()
                if (t - stateStart > minBiteMs && lastSeen > stateStart) {
                    state = 2
                    stateStart = t
                    lastSeen = t
                    delay = tapGapMs
                } else if (t - stateStart > biteTimeoutMs) {
                    state = 0
                }
            }
            2 -> {
                gesture(20L)
                requestShot()
                if (t - lastSeen > endGapMs || t - stateStart > maxMinigameMs) {
                    state = 3
                    stateStart = t
                }
                delay = tapGapMs
            }
            else -> {
                if (t - stateStart > resultWaitMs) {
                    state = 0
                }
            }
        }
        handler.postDelayed({ tick() }, delay)
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

    private fun requestShot() {
        val t = now()
        if (shotBusy && t - lastShot > 3000) shotBusy = false
        if (shotBusy || t - lastShot < shotIntervalMs) return
        shotBusy = true
        lastShot = t
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    var found = false
                    try {
                        val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        if (hw != null) {
                            val bmp = hw.copy(Bitmap.Config.ARGB_8888, false)
                            hw.recycle()
                            if (bmp != null) {
                                found = detectMinigame(bmp)
                                bmp.recycle()
                            }
                        }
                    } catch (e: Exception) {
                    }
                    result.hardwareBuffer.close()
                    if (found) lastSeen = now()
                    shotBusy = false
                }

                override fun onFailure(errorCode: Int) {
                    shotBusy = false
                }
            }
        )
    }

    private fun detectMinigame(bmp: Bitmap): Boolean {
        val w = bmp.width
        val h = bmp.height

        // 1) 빨간 정지 버튼
        var red = 0
        var redTotal = 0
        var y = (h * 0.88f).toInt()
        val yEnd = (h * 0.92f).toInt()
        val xStart = (w * 0.700f).toInt()
        val xEnd = (w * 0.745f).toInt()
        while (y < yEnd) {
            var x = xStart
            while (x < xEnd) {
                val c = bmp.getPixel(x, y)
                redTotal++
                if (Color.red(c) > 190 && Color.green(c) < 110 && Color.blue(c) < 110) red++
                x += 4
            }
            y += 4
        }
        if (redTotal == 0 || red * 100 / redTotal < 20) return false

        // 2) 버튼 왼쪽의 어두운 회색 게이지 막대
        var dark = 0
        var darkTotal = 0
        y = (h * 0.89f).toInt()
        val yEnd2 = (h * 0.93f).toInt()
        val xStart2 = (w * 0.60f).toInt()
        val xEnd2 = (w * 0.685f).toInt()
        while (y < yEnd2) {
            var x = xStart2
            while (x < xEnd2) {
                val c = bmp.getPixel(x, y)
                val r = Color.red(c)
                val g = Color.green(c)
                val bl = Color.blue(c)
                val mx = maxOf(r, maxOf(g, bl))
                val mn = minOf(r, minOf(g, bl))
                darkTotal++
                if (mx < 95 && mx - mn < 30) dark++
                x += 4
            }
            y += 4
        }
        return darkTotal > 0 && dark * 100 / darkTotal >= 30
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
