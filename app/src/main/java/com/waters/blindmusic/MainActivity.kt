package com.waters.blindmusic

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var accessibilityBtn: Button
    private lateinit var serviceBtn: Button
    private lateinit var statusLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(52, 60, 52, 64)
        }

        val title = TextView(this).apply {
            text = "锁屏切歌 (全品牌通用版 v5.0)"
            textSize = 21f
            setTextColor(Color.parseColor("#FF6700"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 14)
        }

        statusLabel = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#4CAF50"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }

        // 一、震动与播放激活开关
        val switchCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(32, 20, 32, 20)
        }

        val vibrateSwitch = CheckBox(this).apply {
            text = "开启手势震动反馈（切歌、暂停、双击亮屏时触发马达震动）"
            setTextColor(Color.WHITE)
            textSize = 14f
            isChecked = AppConfig.isVibrationEnabled(this@MainActivity)
            setPadding(12, 16, 12, 16)
            setOnCheckedChangeListener { _, isChecked ->
                AppConfig.setVibrationEnabled(this@MainActivity, isChecked)
            }
        }

        val musicOnlySwitch = CheckBox(this).apply {
            text = "仅在后台播放音乐时，按电源键才激活黑屏手势层（平时不听歌保持真息屏）"
            setTextColor(Color.parseColor("#FFD54F"))
            textSize = 14f
            isChecked = AppConfig.isOnlyWhenMusic(this@MainActivity)
            setPadding(12, 16, 12, 16)
            setOnCheckedChangeListener { _, isChecked ->
                AppConfig.setOnlyWhenMusic(this@MainActivity, isChecked)
            }
        }

        switchCard.addView(vibrateSwitch)
        switchCard.addView(musicOnlySwitch)

        // 二、选择黑屏手势轨迹特效
        val effectTitle = TextView(this).apply {
            text = "一、 选择黑屏手势轨迹特效："
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 28, 0, 12)
        }

        val effectGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(32, 16, 32, 16)
        }

        val effects = listOf(
            AppConfig.EFFECT_CYBER_NEON to "⚡ 赛博霓虹（电光青外发光 + 纯白高亮核心）",
            AppConfig.EFFECT_BLAZING_FLAME to "🔥 烈焰流火（红橙黄三色渐变 + 火花粒子）",
            AppConfig.EFFECT_GALAXY_STAR to "🌌 星河粒子（紫蓝流光 + 动态闪烁星尘粒子）",
            AppConfig.EFFECT_JADE_SWORD to "🗡️ 翡翠剑气（翠绿锋芒光晕 + 极速残影）",
            AppConfig.EFFECT_STEALTH_NONE to "🌑 极客无痕（100%全黑零光效，极致省电）"
        )

        val currentEffect = AppConfig.getEffectStyle(this)
        for ((code, name) in effects) {
            val rb = RadioButton(this).apply {
                id = code + 100
                text = name
                textSize = 13.5f
                setTextColor(Color.parseColor("#E0E0E0"))
                setPadding(12, 16, 12, 16)
                isChecked = (code == currentEffect)
            }
            effectGroup.addView(rb)
        }
        effectGroup.setOnCheckedChangeListener { _, checkedId ->
            AppConfig.setEffectStyle(this, checkedId - 100)
        }

        // 三、跨品牌手机指纹区域适配
        val fpTitle = TextView(this).apply {
            text = "二、 跨品牌手机指纹解锁区域适配："
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 28, 0, 12)
        }

        val fpGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(32, 16, 32, 16)
        }

        val fpModes = listOf(
            AppConfig.FP_ZONE_ULTRASONIC_HIGH to "超声波/高位屏下指纹（适配小米15/三星/vivo旗舰等）",
            AppConfig.FP_ZONE_OPTICAL_LOW to "光学/靠下屏下指纹（适配一加/红米/华为等偏下指纹机型）",
            AppConfig.FP_ZONE_SIDE_OR_NONE to "侧边电源键指纹/纯面容机型（隐藏屏下指纹环）"
        )

        val currentFpMode = AppConfig.getFpZoneMode(this)
        for ((code, name) in fpModes) {
            val rb = RadioButton(this).apply {
                id = code + 200
                text = name
                textSize = 13.5f
                setTextColor(Color.parseColor("#E0E0E0"))
                setPadding(12, 16, 12, 16)
                isChecked = (code == currentFpMode)
            }
            fpGroup.addView(rb)
        }
        fpGroup.setOnCheckedChangeListener { _, checkedId ->
            AppConfig.setFpZoneMode(this, checkedId - 200)
        }

        // 四、当前手机品牌专属权限说明
        val brandGuideText = TextView(this).apply {
            text = AppConfig.getBrandPermissionGuide()
            textSize = 13f
            setTextColor(Color.parseColor("#BDBDBD"))
            setLineSpacing(8f, 1.1f)
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.parseColor("#1A232E"))
        }

        val spacer0 = Space(this).apply { minimumHeight = 24 }

        accessibilityBtn = Button(this).apply {
            text = "1. 开启「无障碍服务」常驻保活（全品牌最稳/零耗电）"
            textSize = 14.5f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#00897B"))
            setPadding(36, 30, 36, 30)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        val spacer1 = Space(this).apply { minimumHeight = 20 }

        val batteryBtn = Button(this).apply {
            text = "2. 一键申请「忽略电池优化白名单」（防杀后台）"
            textSize = 14.5f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#5E35B1"))
            setPadding(36, 30, 36, 30)
            setOnClickListener {
                requestIgnoreBatteryOptimizations()
            }
        }

        val spacer2 = Space(this).apply { minimumHeight = 20 }

        serviceBtn = Button(this).apply {
            textSize = 14.5f
            setTextColor(Color.WHITE)
            setPadding(36, 30, 36, 30)
            setOnClickListener {
                toggleGuardService()
            }
        }

        val spacer3 = Space(this).apply { minimumHeight = 20 }

        val testNowBtn = Button(this).apply {
            text = "立即全屏预览「锁屏切歌」效果"
            textSize = 14.5f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#3949AB"))
            setPadding(36, 30, 36, 30)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, LockScreenGestureActivity::class.java))
            }
        }

        root.addView(title)
        root.addView(statusLabel)
        root.addView(switchCard)
        root.addView(effectTitle)
        root.addView(effectGroup)
        root.addView(fpTitle)
        root.addView(fpGroup)
        root.addView(spacer0)
        root.addView(brandGuideText)
        root.addView(Space(this).apply { minimumHeight = 24 })
        root.addView(accessibilityBtn)
        root.addView(spacer1)
        root.addView(batteryBtn)
        root.addView(spacer2)
        root.addView(serviceBtn)
        root.addView(spacer3)
        root.addView(testNowBtn)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "已处于系统电池优化白名单中", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun toggleGuardService() {
        val intent = Intent(this, ScreenOffGuardService::class.java)
        if (ScreenOffGuardService.isServiceRunning) {
            stopService(intent)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        serviceBtn.postDelayed({ updateUI() }, 200)
    }

    private fun updateUI() {
        val accOn = BlindAccessibilityService.isAccessibilityRunning
        val fgsOn = ScreenOffGuardService.isServiceRunning

        if (accOn) {
            statusLabel.text = "● 当前状态：无障碍级最高优先级守护中（最稳、零耗电）"
            statusLabel.setTextColor(Color.parseColor("#00E676"))
            accessibilityBtn.text = "已开启「无障碍服务」保活（点击可前往设置管理）"
        } else if (fgsOn) {
            statusLabel.text = "● 当前状态：普通前台通知栏守护运行中"
            statusLabel.setTextColor(Color.parseColor("#4CAF50"))
            accessibilityBtn.text = "1. 开启「无障碍服务」常驻保活（全品牌最稳/零耗电）"
        } else {
            statusLabel.text = "○ 当前状态：未启动（请开启无障碍保活或下方前台服务）"
            statusLabel.setTextColor(Color.parseColor("#FFB300"))
        }

        if (fgsOn) {
            serviceBtn.text = "停止备用前台守护服务"
            serviceBtn.setBackgroundColor(Color.parseColor("#D32F2F"))
        } else {
            serviceBtn.text = "3. 或者：开启备用前台通知栏守护服务"
            serviceBtn.setBackgroundColor(Color.parseColor("#FF6700"))
        }
    }
}
