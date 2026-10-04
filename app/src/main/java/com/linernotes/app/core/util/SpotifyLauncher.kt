package com.linernotes.app.core.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Spotify 应用唤醒与跳转路由工具类
 * 彻底解决 Android 11+ (API 30+) Package Visibility 机制导致的直接唤醒失败与意外降级为网页浏览问题
 */
object SpotifyLauncher {

    const val SPOTIFY_PACKAGE = "com.spotify.music"
    const val SPOTIFY_LITE_PACKAGE = "com.spotify.lite"

    /**
     * 强力直达 Spotify 原生客户端
     * 1. 首选标准版/Lite 版 Launcher Intent
     * 2. 次选 spotify: 原生 URI Scheme 并指派 target package
     * 3. 再次选全局广播/通用 spotify: URI
     * 4. 终选引导前往系统应用商店/Google Play 下载，绝不轻易回退至网页版
     */
    fun launchSpotify(context: Context) {
        // 1. 首选：通过 PackageManager 查询启动 Intent
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(SPOTIFY_PACKAGE)
            ?: pm.getLaunchIntentForPackage(SPOTIFY_LITE_PACKAGE)

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            try {
                context.startActivity(launchIntent)
                return
            } catch (_: Exception) {}
        }

        // 2. 次选：通过指定包名的 spotify: 原生协议 Scheme 强行直达 App
        try {
            val targetedSpotifyUriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:")).apply {
                setPackage(SPOTIFY_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(targetedSpotifyUriIntent)
            return
        } catch (_: Exception) {}

        // 3. 再次选：通用 spotify: 协议唤醒（支持 Lite 版或其他受支持客户端响应）
        try {
            val genericSpotifyUriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(genericSpotifyUriIntent)
            return
        } catch (_: Exception) {}

        // 4. 终选：本机确实未安装，优先引导前往应用商店下载 Spotify，若应用商店未响应才打开网页
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SPOTIFY_PACKAGE")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            try {
                val playStoreWebIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$SPOTIFY_PACKAGE")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(playStoreWebIntent)
            } catch (_: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        }
    }
}
