package com.menulango.platform

import android.content.Context
import android.os.Build

private var applicationContext: Context? = null

internal fun installAndroidFeedbackContext(context: Context) {
    applicationContext = context.applicationContext
}

internal actual fun feedbackMailUri(issue: String?): String {
    val context = applicationContext
    val version =
        context
            ?.packageManager
            ?.getPackageInfo(context.packageName, 0)
            ?.let { "${it.versionName} (${it.longVersionCode})" }
            ?: "unknown"
    return feedbackMailUri(version, "Android", "Android ${Build.VERSION.RELEASE}", issue)
}
