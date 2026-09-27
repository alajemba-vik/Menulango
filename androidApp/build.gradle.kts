import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/**
 * Secrets are read from `secrets.properties` (git-ignored) or the environment, so a stranger can
 * build with nothing but `PROXY_URL` and a RevenueCat key. Missing values build a debug app that
 * runs on canned data; they never fail the build.
 */
val secrets =
    Properties().apply {
        val file = rootProject.file("secrets.properties")
        if (file.exists()) file.inputStream().use(::load)
    }

fun secret(name: String): String = (secrets.getProperty(name) ?: System.getenv(name) ?: "").trim()

android {
    namespace = "com.menulango.android"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.menulango.app"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 3
        versionName = "1.0.0"
        buildConfigField("String", "PROXY_URL", "\"${secret("PROXY_URL")}\"")
        buildConfigField("String", "REVENUECAT_ANDROID_KEY", "\"${secret("REVENUECAT_ANDROID_KEY")}\"")
        // Test builds for friends only: sample menu + Plus toggle. Never true for a store release.
        buildConfigField(
            "boolean",
            "BETA_TOOLS",
            secret("MENULANGO_BETA_TOOLS").equals("true", ignoreCase = true).toString(),
        )
    }

    signingConfigs {
        create("release") {
            val storePath = secret("MENULANGO_KEYSTORE")
            if (storePath.isNotEmpty()) {
                storeFile = file(storePath)
                storePassword = secret("MENULANGO_KEYSTORE_PASSWORD")
                keyAlias = secret("MENULANGO_KEY_ALIAS")
                keyPassword = secret("MENULANGO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // Google Play billing only works in copies installed from Play, so debug builds from
        // Android Studio buy from RevenueCat's Test Store instead when a `test_` key is set.
        // Release builds, including Play testing tracks, always use the real Google key.
        debug {
            val testKey = secret("REVENUECAT_TEST_KEY")
            if (testKey.isNotEmpty()) buildConfigField("String", "REVENUECAT_ANDROID_KEY", "\"$testKey\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (secret("MENULANGO_KEYSTORE").isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.koin.android)
}
