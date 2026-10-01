package com.menulango.platform

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

internal actual fun shareText(text: String) {
    val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
    // On iPad the sheet is a popover and needs an anchor; the centre of the screen will do.
    sheet.popoverPresentationController?.sourceView = topPresenter()?.view
    topPresenter()?.presentViewController(sheet, animated = true, completion = null)
}

private fun topPresenter(): UIViewController? {
    val window =
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
            .firstOrNull { it.isKeyWindow() }
    var top = window?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}
