package com.grandma.launcher.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens an installed app, or the Play Store page for that package when it is missing.
 * Implicit intents are started inside try/catch. Callers declare the packages in queries.
 * Ask does not use this class. The in-app screen asks the backend for a short-lived token.
 */
object InstalledApp {
    const val YOUTUBE = "com.google.android.youtube"
    const val YOUTUBE_VOICE_SEARCH = "com.google.android.youtube.action.VOICE_SEARCH"
    const val GEMINI = "com.google.android.apps.bard"
    /** Voice-first assistant entry. The public SDK exposes ACTION_ASSIST, not this action. */
    const val ACTION_VOICE_ASSIST = "android.intent.action.VOICE_ASSIST"
    const val WHATSAPP = "com.whatsapp"
    const val PHOTOS = "com.google.android.apps.photos"
    const val MIUI_GALLERY = "com.miui.gallery"

    enum class Handoff { OPENED, NEEDS_INSTALL }

    fun playStoreMarketUri(packageName: String): Uri = Uri.parse("market://details?id=$packageName")

    fun playStoreWebUri(packageName: String): Uri =
        Uri.parse("https://play.google.com/store/apps/details?id=$packageName")

    /** Voice entry first, then the assistant entry. Both name the Gemini package. */
    fun geminiVoiceIntents(): List<Intent> = listOf(
        Intent(ACTION_VOICE_ASSIST).setPackage(GEMINI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        Intent(Intent.ACTION_ASSIST).setPackage(GEMINI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    fun youtubeVoiceIntent(): Intent =
        Intent(YOUTUBE_VOICE_SEARCH).setPackage(YOUTUBE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun geminiInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(GEMINI) != null

    /**
     * Returns true when one intent starts. A missing activity falls through.
     * [start] may throw [ActivityNotFoundException] or [SecurityException].
     */
    fun startFirst(intents: List<Intent>, start: (Intent) -> Unit): Boolean {
        for (intent in intents) {
            try {
                start(intent)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        return false
    }

    fun openOrStore(context: Context, packageName: String, launch: Intent) {
        try {
            context.startActivity(launch)
        } catch (_: ActivityNotFoundException) {
            openStore(context, packageName)
        } catch (_: SecurityException) {
            openStore(context, packageName)
        }
    }

    fun launchPackageOrStore(context: Context, packageName: String) {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launch == null) {
            openStore(context, packageName)
            return
        }
        openOrStore(context, packageName, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openStore(context: Context, packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, playStoreMarketUri(packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(market)
        } catch (_: ActivityNotFoundException) {
            val web = Intent(Intent.ACTION_VIEW, playStoreWebUri(packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(web)
            } catch (_: ActivityNotFoundException) {
            }
        }
    }

    /**
     * Opens the gallery app so she can look at pictures.
     * MIUI Gallery and Google Photos are full libraries. A photos-only intent falls
     * through to a video browse, then the Play Store page for Google Photos.
     */
    fun openGallery(context: Context) {
        for (packageName in listOf(MIUI_GALLERY, PHOTOS)) {
            val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: continue
            try {
                context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        val gallery = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_GALLERY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(gallery)
            return
        } catch (_: ActivityNotFoundException) {
        }
        val videos = Intent(Intent.ACTION_VIEW)
            .setType("video/*")
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(videos)
        } catch (_: ActivityNotFoundException) {
            openStore(context, PHOTOS)
        }
    }

    /** Voice search when YouTube exports it. Otherwise the YouTube app, or its Play Store page. */
    fun openYouTube(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(YOUTUBE)
        if (launch == null) {
            openStore(context, YOUTUBE)
            return
        }
        if (startFirst(listOf(youtubeVoiceIntent())) { context.startActivity(it) }) return
        openOrStore(context, YOUTUBE, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Opens the Gemini app on this phone. Ask does not call this. The hold-to-talk
     * screen uses a short-lived token and never embeds an API key.
     */
    fun openGemini(context: Context): Handoff {
        val launch = context.packageManager.getLaunchIntentForPackage(GEMINI)
        if (launch == null) {
            openStore(context, GEMINI)
            return Handoff.NEEDS_INSTALL
        }
        if (startFirst(geminiVoiceIntents()) { context.startActivity(it) }) return Handoff.OPENED
        return if (startFirst(listOf(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) { context.startActivity(it) }) {
            Handoff.OPENED
        } else {
            openStore(context, GEMINI)
            Handoff.NEEDS_INSTALL
        }
    }
}
