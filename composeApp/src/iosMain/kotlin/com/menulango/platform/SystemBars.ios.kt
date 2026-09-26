package com.menulango.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow

/** The window's interface style drives the status bar's default style, so they always agree. */
@Composable
internal actual fun SystemBarsFollow(darkTheme: Boolean) {
    SideEffect {
        val style =
            if (darkTheme) {
                UIUserInterfaceStyle.UIUserInterfaceStyleDark
            } else {
                UIUserInterfaceStyle.UIUserInterfaceStyleLight
            }
        @Suppress("DEPRECATION")
        UIApplication.sharedApplication.windows.forEach { (it as? UIWindow)?.overrideUserInterfaceStyle = style }
    }
}
