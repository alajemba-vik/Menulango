package com.menulango.platform

import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

internal actual fun feedbackMailUri(): String {
    val info = NSBundle.mainBundle.infoDictionary
    val version = info?.get("CFBundleShortVersionString") as? String ?: "unknown"
    val build = info?.get("CFBundleVersion") as? String
    return feedbackMailUri(
        "$version${build?.let { " ($it)" }.orEmpty()}",
        "iOS",
        "iOS ${UIDevice.currentDevice.systemVersion}",
    )
}
