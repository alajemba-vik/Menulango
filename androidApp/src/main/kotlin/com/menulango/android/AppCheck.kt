package com.menulango.android

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.menulango.data.AppAttestation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Starts Firebase App Check so each scan carries a Play Integrity token the proxy can verify.
 * Configured from BuildConfig rather than google-services.json, so builds without the Firebase
 * values still run: they just send no token.
 */
internal fun startAppCheck(context: Context): AppAttestation {
    if (BuildConfig.FIREBASE_APP_ID.isEmpty()) return AppAttestation.None
    val options =
        FirebaseOptions
            .Builder()
            .setApplicationId(BuildConfig.FIREBASE_APP_ID)
            .setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
            .build()
    val appCheck = FirebaseAppCheck.getInstance(FirebaseApp.initializeApp(context, options))
    appCheck.installAppCheckProviderFactory(appCheckProviderFactory())
    return AppAttestation {
        try {
            appCheck.getAppCheckToken(false).await().token
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
