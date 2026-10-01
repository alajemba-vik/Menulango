package com.menulango.android

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Emulators and sideloaded builds cannot pass Play Integrity. The debug provider prints a token to
 * Logcat ("DebugAppCheckProvider"); add it under App Check → Manage debug tokens in Firebase.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
