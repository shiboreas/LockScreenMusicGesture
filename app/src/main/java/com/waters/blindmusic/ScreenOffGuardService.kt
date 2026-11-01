package com.waters.blindmusic

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ScreenOffGuardService : Service() {

    companion object {
        var isServiceRunning = false
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (BlindAccessibilityService.isAccessibilityRunning) return

            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val onlyMusic = AppConfig.isOnlyWhenMusic(this@ScreenOffGuardService)
                    if (!onlyMusic || audioManager.isMusicActive) {
                        val lockIntent = Intent(this@ScreenOffGuardService, LockScreenGestureActivity::class.java).apply {
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

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        startForegroundNotification()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun startForegroundNotification() {
        val channelId = "lock_music_guard_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "锁屏切歌守护服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("锁屏切歌 运行中")
            .setContentText("黑屏画 >、<、|| 切歌 | 双击点亮锁屏 | 指纹直接解锁")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        unregisterReceiver(screenReceiver)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
