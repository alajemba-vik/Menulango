package com.menulango.android

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.menulango.MenuLangoApp

/** The single activity. Everything visible is the shared Compose UI. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Android has no "reduce motion" switch; turning animations off in accessibility settings is the equivalent.
        val reduceMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        setContent { MenuLangoApp(reduceMotion = reduceMotion) }
    }
}
