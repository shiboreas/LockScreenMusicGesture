package com.waters.blindmusic

import android.app.KeyguardManager
import android.content.Context
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.os.*
import android.util.TypedValue
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

class LockScreenGestureActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        var currentInstance: LockScreenGestureActivity? = null
    }

    private lateinit var sensorManager: SensorManager
    private var proximitySensor: Sensor? = null
    private var isPocketBlocked = false
    private lateinit var gestureView: LockGestureView
    private lateinit var keyguardManager: KeyguardManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val params = window.attributes
        params.screenBrightness = 0.01f
        window.attributes = params

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        gestureView = LockGestureView(this)
        setContentView(gestureView)
        hideSystemBars()
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            resources.displayMetrics
        )
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }
    }

    private fun wakeUpLockScreen() {
        val params = window.attributes
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = params
        vibrate(false)
        finish()
        overridePendingTransition(0, 0)
    }

    private fun triggerDirectFingerprintUnlock() {
        val params = window.attributes
        params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = params

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    finish()
                    overridePendingTransition(0, 0)
                }
                override fun onDismissCancelled() {
                    finish()
                    overridePendingTransition(0, 0)
                }
                override fun onDismissError() {
                    finish()
                    overridePendingTransition(0, 0)
                }
            })
        }
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onResume() {
        super.onResume()
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (currentInstance == this) {
            currentInstance = null
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5f
            isPocketBlocked = distance < maxRange && distance < 3f
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun sendMediaKey(keyCode: Int, label: String) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        vibrate(true)
        gestureView.showTraceFeedback(label)
    }

    // 兼容全品牌马达（Android 12+ VibratorManager 及旧版 Vibrator），并受用户“震动开关”控制
    private fun vibrate(strong: Boolean) {
        if (!AppConfig.isVibrationEnabled(this)) return

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(
                VibrationEffect.createPredefined(
                    if (strong) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(if (strong) 45L else 20L)
        }
    }

    data class SparkParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var radius: Float,
        var alpha: Int,
        var color: Int
    )

    inner class LockGestureView(context: Context) : View(context) {
        private val tracePoints0 = mutableListOf<PointF>()
        private val tracePoints1 = mutableListOf<PointF>()
        private val particles = mutableListOf<SparkParticle>()

        private var isTwoFingerGesture = false
        private var maxPointerCount = 0
        private var startX0 = 0f
        private var startY0 = 0f
        private var startX1 = 0f
        private var startY1 = 0f
        private var endX0 = 0f
        private var endY0 = 0f
        private var endX1 = 0f
        private var endY1 = 0f

        private var lastTapTime = 0L
        private var lastTapX = 0f
        private var lastTapY = 0f
        private var feedbackText = ""
        private val handler = Handler(Looper.getMainLooper())
        private val fpLongPressHandler = Handler(Looper.getMainLooper())
        private var isPressingFpZone = false
        private var effectStyle = AppConfig.EFFECT_CYBER_NEON

        private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CCFFFFFF")
            textSize = dp(18f)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        private val fpRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#22FFFFFF")
            style = Paint.Style.STROKE
            strokeWidth = dp(1.2f)
        }

        init {
            setBackgroundColor(Color.BLACK)
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            effectStyle = AppConfig.getEffectStyle(context)
        }

        private fun isInFingerprintSensorZone(x: Float, y: Float): Boolean {
            val ratio = AppConfig.getFpVerticalRatio(context)
            if (ratio < 0f || width == 0 || height == 0) return false
            val centerX = width / 2f
            val centerY = height * ratio
            val radius = width * 0.16f
            return hypot(x - centerX, y - centerY) <= radius
        }

        private fun spawnParticles(x: Float, y: Float) {
            if (effectStyle == AppConfig.EFFECT_STEALTH_NONE) return
            val palette = when (effectStyle) {
                AppConfig.EFFECT_CYBER_NEON -> intArrayOf(Color.CYAN, Color.WHITE, Color.parseColor("#00E5FF"))
                AppConfig.EFFECT_BLAZING_FLAME -> intArrayOf(Color.RED, Color.parseColor("#FF6D00"), Color.YELLOW)
                AppConfig.EFFECT_GALAXY_STAR -> intArrayOf(Color.parseColor("#E040FB"), Color.parseColor("#7C4DFF"), Color.WHITE)
                else -> intArrayOf(Color.parseColor("#00E676"), Color.parseColor("#B9F6CA"))
            }
            val count = if (effectStyle == AppConfig.EFFECT_GALAXY_STAR) 4 else 2
            for (i in 0 until count) {
                particles.add(
                    SparkParticle(
                        x = x + Random.nextFloat() * dp(8f) - dp(4f),
                        y = y + Random.nextFloat() * dp(8f) - dp(4f),
                        vx = Random.nextFloat() * dp(3f) - dp(1.5f),
                        vy = Random.nextFloat() * dp(3f) - dp(1.5f),
                        radius = Random.nextFloat() * dp(2.2f) + dp(1.2f),
                        alpha = 255,
                        color = palette[Random.nextInt(palette.size)]
                    )
                )
            }
        }

        fun showTraceFeedback(label: String) {
            feedbackText = label
            invalidate()
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                feedbackText = ""
                tracePoints0.clear()
                tracePoints1.clear()
                particles.clear()
                invalidate()
            }, 520)
        }

        private fun drawStyledPath(canvas: Canvas, points: List<PointF>) {
            if (points.size < 2 || effectStyle == AppConfig.EFFECT_STEALTH_NONE) return

            val path = Path()
            path.moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }

            when (effectStyle) {
                AppConfig.EFFECT_CYBER_NEON -> {
                    outerGlowPaint.shader = null
                    outerGlowPaint.color = Color.parseColor("#00E5FF")
                    outerGlowPaint.strokeWidth = dp(10f)
                    outerGlowPaint.maskFilter = BlurMaskFilter(dp(8f), BlurMaskFilter.Blur.NORMAL)
                    canvas.drawPath(path, outerGlowPaint)

                    corePaint.shader = null
                    corePaint.color = Color.WHITE
                    corePaint.strokeWidth = dp(3f)
                    canvas.drawPath(path, corePaint)
                }

                AppConfig.EFFECT_BLAZING_FLAME -> {
                    val first = points.first()
                    val last = points.last()
                    val gradient = LinearGradient(
                        first.x, first.y, last.x, last.y,
                        intArrayOf(Color.parseColor("#FF1744"), Color.parseColor("#FF9100"), Color.parseColor("#FFEE58")),
                        null, Shader.TileMode.CLAMP
                    )
                    outerGlowPaint.shader = gradient
                    outerGlowPaint.strokeWidth = dp(12f)
                    outerGlowPaint.maskFilter = BlurMaskFilter(dp(9f), BlurMaskFilter.Blur.NORMAL)
                    canvas.drawPath(path, outerGlowPaint)

                    corePaint.shader = gradient
                    corePaint.strokeWidth = dp(4.5f)
                    canvas.drawPath(path, corePaint)
                }

                AppConfig.EFFECT_GALAXY_STAR -> {
                    val first = points.first()
                    val last = points.last()
                    val gradient = LinearGradient(
                        first.x, first.y, last.x, last.y,
                        intArrayOf(Color.parseColor("#7C4DFF"), Color.parseColor("#E040FB"), Color.parseColor("#40C4FF")),
                        null, Shader.TileMode.CLAMP
                    )
                    outerGlowPaint.shader = gradient
                    outerGlowPaint.strokeWidth = dp(9f)
                    outerGlowPaint.maskFilter = BlurMaskFilter(dp(7f), BlurMaskFilter.Blur.NORMAL)
                    canvas.drawPath(path, outerGlowPaint)

                    corePaint.shader = null
                    corePaint.color = Color.parseColor("#F3E5F5")
                    corePaint.strokeWidth = dp(2.5f)
                    canvas.drawPath(path, corePaint)
                }

                AppConfig.EFFECT_JADE_SWORD -> {
                    outerGlowPaint.shader = null
                    outerGlowPaint.color = Color.parseColor("#00E676")
                    outerGlowPaint.strokeWidth = dp(8.5f)
                    outerGlowPaint.maskFilter = BlurMaskFilter(dp(6f), BlurMaskFilter.Blur.NORMAL)
                    canvas.drawPath(path, outerGlowPaint)

                    corePaint.shader = null
                    corePaint.color = Color.parseColor("#E8F5E9")
                    corePaint.strokeWidth = dp(2.8f)
                    canvas.drawPath(path, corePaint)
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val fpRatio = AppConfig.getFpVerticalRatio(context)
            if (fpRatio > 0f && width > 0 && height > 0) {
                canvas.drawCircle(width / 2f, height * fpRatio, dp(26f), fpRingPaint)
            }

            if (effectStyle != AppConfig.EFFECT_STEALTH_NONE) {
                drawStyledPath(canvas, tracePoints0)
                drawStyledPath(canvas, tracePoints1)

                val iterator = particles.iterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    particlePaint.color = p.color
                    particlePaint.alpha = p.alpha
                    canvas.drawCircle(p.x, p.y, p.radius, particlePaint)
                    p.x += p.vx
                    p.y += p.vy
                    p.alpha -= 16
                    if (p.alpha <= 0) iterator.remove()
                }
            }

            if (feedbackText.isNotEmpty() && effectStyle != AppConfig.EFFECT_STEALTH_NONE) {
                canvas.drawText(feedbackText, width / 2f, height / 2f, textPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (isPocketBlocked) return true

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    effectStyle = AppConfig.getEffectStyle(context)
                    tracePoints0.clear()
                    tracePoints1.clear()
                    particles.clear()
                    tracePoints0.add(PointF(event.x, event.y))
                    spawnParticles(event.x, event.y)
                    maxPointerCount = 1
                    isTwoFingerGesture = false
                    startX0 = event.x
                    startY0 = event.y

                    if (isInFingerprintSensorZone(event.x, event.y)) {
                        isPressingFpZone = true
                        fpLongPressHandler.removeCallbacksAndMessages(null)
                        fpLongPressHandler.postDelayed({
                            if (isPressingFpZone && maxPointerCount == 1) {
                                triggerDirectFingerprintUnlock()
                            }
                        }, 110)
                    } else {
                        isPressingFpZone = false
                    }
                    invalidate()
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    isPressingFpZone = false
                    fpLongPressHandler.removeCallbacksAndMessages(null)
                    if (event.pointerCount > maxPointerCount) maxPointerCount = event.pointerCount
                    if (event.pointerCount == 2) {
                        isTwoFingerGesture = true
                        startX0 = event.getX(0)
                        startY0 = event.getY(0)
                        startX1 = event.getX(1)
                        startY1 = event.getY(1)
                        tracePoints1.clear()
                        tracePoints1.add(PointF(startX1, startY1))
                    }
                    invalidate()
                }

                MotionEvent.ACTION_MOVE -> {
                    if (hypot(event.x - startX0, event.y - startY0) > dp(16f)) {
                        isPressingFpZone = false
                        fpLongPressHandler.removeCallbacksAndMessages(null)
                    }
                    if (event.pointerCount == 1 && !isTwoFingerGesture) {
                        tracePoints0.add(PointF(event.x, event.y))
                        spawnParticles(event.x, event.y)
                    } else if (event.pointerCount == 2 && isTwoFingerGesture) {
                        endX0 = event.getX(0)
                        endY0 = event.getY(0)
                        endX1 = event.getX(1)
                        endY1 = event.getY(1)
                        tracePoints0.add(PointF(endX0, endY0))
                        tracePoints1.add(PointF(endX1, endY1))
                        spawnParticles(endX0, endY0)
                        spawnParticles(endX1, endY1)
                    }
                    invalidate()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isPressingFpZone = false
                    fpLongPressHandler.removeCallbacksAndMessages(null)
                    if (maxPointerCount == 2 && isTwoFingerGesture) {
                        val dy0 = endY0 - startY0
                        val dy1 = endY1 - startY1
                        val dx0 = abs(endX0 - startX0)
                        val dx1 = abs(endX1 - startX1)
                        // 使用与屏幕分辨率无关的 dp 单位判定，兼容未来更换任何 1.5K/2K/折叠屏手机
                        if (dy0 > dp(80f) && dy1 > dp(80f) && dx0 < dp(55f) && dx1 < dp(55f)) {
                            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "||  播放 / 暂停")
                        } else {
                            clearTracesSoon()
                        }
                    } else if (maxPointerCount == 1) {
                        handleSingleFinger(event)
                    }
                }
            }
            return true
        }

        private fun clearTracesSoon() {
            handler.postDelayed({
                tracePoints0.clear()
                tracePoints1.clear()
                particles.clear()
                invalidate()
            }, 220)
        }

        private fun handleSingleFinger(upEvent: MotionEvent) {
            val moveDist = hypot(upEvent.x - startX0, upEvent.y - startY0)
            if (tracePoints0.size < 6 || moveDist < dp(16f)) {
                tracePoints0.clear()
                particles.clear()
                invalidate()
                val now = SystemClock.uptimeMillis()
                val tapDiffDist = hypot(upEvent.x - lastTapX, upEvent.y - lastTapY)
                if (now - lastTapTime in 40..320 && tapDiffDist < dp(65f)) {
                    lastTapTime = 0L
                    wakeUpLockScreen()
                } else {
                    lastTapTime = now
                    lastTapX = upEvent.x
                    lastTapY = upEvent.y
                }
                return
            }

            val startPt = tracePoints0.first()
            val endPt = tracePoints0.last()
            if (endPt.y - startPt.y < dp(60f)) {
                clearTracesSoon()
                return
            }

            var maxXPt = tracePoints0[0]
            var minXPt = tracePoints0[0]
            for (pt in tracePoints0) {
                if (pt.x > maxXPt.x) maxXPt = pt
                if (pt.x < minXPt.x) minXPt = pt
            }

            val rightProtrusion = maxXPt.x - maxOf(startPt.x, endPt.x)
            val leftProtrusion = minOf(startPt.x, endPt.x) - minXPt.x

            if (rightProtrusion > dp(42f) && maxXPt.y > startPt.y + dp(18f) && endPt.y > maxXPt.y + dp(18f)) {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, ">  下一曲")
            } else if (leftProtrusion > dp(42f) && minXPt.y > startPt.y + dp(18f) && endPt.y > minXPt.y + dp(18f)) {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "<  上一曲")
            } else {
                clearTracesSoon()
            }
        }
    }
}
