package com.waters.blindmusic

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.view.accessibility.AccessibilityEvent

class BlindAccessibilityService : AccessibilityService() {

    companion object {
        var isAccessibilityRunning = false
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val onlyMusic = AppConfig.isOnlyWhenMusic(this@BlindAccessibilityService)
                    if (!onlyMusic || audioManager.isMusicActive) {
                        val lockIntent = Intent(this@BlindAccessibilityService, LockScreenGestureActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(lockIntent)
                    }
                }
                Intent.ACTION_USER_PRESENT -> {
                    LockScreenGestureActivity.currentInstance?.finish()
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isAccessibilityRunning = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不读取任何屏幕内容，零额外耗电
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        isAccessibilityRunning = false
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
    }
}
