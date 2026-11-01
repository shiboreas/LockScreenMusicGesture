package com.waters.blindmusic

import android.content.Context
import android.os.Build

object AppConfig {
    private const val PREF_NAME = "lock_music_prefs"
    private const val KEY_EFFECT_STYLE = "effect_style"
    private const val KEY_ONLY_MUSIC = "only_when_music"
    private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
    private const val KEY_FP_ZONE_MODE = "fp_zone_mode"

    // 5种可选轨迹特效
    const val EFFECT_CYBER_NEON = 0      // 赛博霓虹
    const val EFFECT_BLAZING_FLAME = 1   // 烈焰流火
    const val EFFECT_GALAXY_STAR = 2     // 星河粒子
    const val EFFECT_JADE_SWORD = 3      // 翡翠剑气
    const val EFFECT_STEALTH_NONE = 4    // 极客无痕

    // 跨品牌指纹区域位置预设（兼容超声波居中、光学靠下、侧边电源键指纹）
    const val FP_ZONE_ULTRASONIC_HIGH = 0 // 超声波/高位指纹（屏幕纵向 76% 处，适配小米15/三星S系列/vivo X系列等）
    const val FP_ZONE_OPTICAL_LOW = 1     // 短焦光学/低位指纹（屏幕纵向 87% 处，适配部分红米/一加/真我等机型）
    const val FP_ZONE_SIDE_OR_NONE = 2    // 侧边指纹/面容解锁（关闭屏下指纹环穿透，仅用双击亮屏或电源键）

    fun getEffectStyle(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_EFFECT_STYLE, EFFECT_CYBER_NEON)
    }

    fun setEffectStyle(context: Context, style: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_EFFECT_STYLE, style).apply()
    }

    fun isOnlyWhenMusic(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONLY_MUSIC, false)
    }

    fun setOnlyWhenMusic(context: Context, onlyMusic: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ONLY_MUSIC, onlyMusic).apply()
    }

    fun isVibrationEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_VIBRATION_ENABLED, true)
    }

    fun setVibrationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply()
    }

    fun getFpZoneMode(context: Context): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_FP_ZONE_MODE, FP_ZONE_ULTRASONIC_HIGH)
    }

    fun setFpZoneMode(context: Context, mode: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_FP_ZONE_MODE, mode).apply()
    }

    fun getFpVerticalRatio(context: Context): Float {
        return when (getFpZoneMode(context)) {
            FP_ZONE_ULTRASONIC_HIGH -> 0.76f
            FP_ZONE_OPTICAL_LOW -> 0.87f
            else -> -1f
        }
    }

    // 自动识别当前手机品牌，给出对应后台与锁屏权限引导文案
    fun getBrandPermissionGuide(): String {
        val brand = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            brand.contains("xiaomi") || brand.contains("redmi") ->
                "【当前识别机型：小米 / Redmi (澎湃OS / MIUI)】\n" +
                "1. 应用信息 -> 权限管理 -> 其他权限 -> 开启「锁屏显示」与「后台弹出界面」\n" +
                "2. 省电策略设为「无限制」"
            brand.contains("huawei") || brand.contains("honor") ->
                "【当前识别机型：华为 / 荣耀 (HarmonyOS / MagicOS)】\n" +
                "1. 设置 -> 应用和服务 -> 应用启动管理 -> 关闭本应用自动管理，手动勾选「允许自启动 / 后台活动」\n" +
                "2. 权限管理中开启「悬浮窗」与「锁屏显示」"
            brand.contains("oppo") || brand.contains("oneplus") || brand.contains("realme") ->
                "【当前识别机型：OPPO / 一加 / 真我 (ColorOS)】\n" +
                "1. 应用详情 -> 耗电管理 -> 开启「允许完全后台行为」与「允许自启动」\n" +
                "2. 开启「悬浮窗」权限"
            brand.contains("vivo") || brand.contains("iqoo") ->
                "【当前识别机型：vivo / iQOO (OriginOS)】\n" +
                "1. 应用详情 -> 权限 -> 所有权限 -> 开启「后台弹出界面」与「锁屏显示」\n" +
                "2. 电池 -> 后台耗电管理 -> 设为「允许后台高耗电」"
            brand.contains("samsung") ->
                "【当前识别机型：三星 (One UI)】\n" +
                "1. 应用程序信息 -> 电池 -> 设为「不受限制」\n" +
                "2. 开启「出现在顶部」权限"
            else ->
                "【当前识别机型：通用 Android (${Build.MANUFACTURER})】\n" +
                "1. 请在系统设置中为本软件开启「无障碍服务」与「忽略电池优化（无限制）」\n" +
                "2. 允许「显示在其他应用上层（悬浮窗）」"
        }
    }
}
