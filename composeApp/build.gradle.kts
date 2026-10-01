import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    explicitApi()

    android {
        namespace = "com.menulango.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        androidResources { enable = true }
        withHostTest { }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
        // SQLDelight's native driver uses the system SQLite. The app links it in Xcode; test binaries link it here.
        target.binaries.all { linkerOpts("-lsqlite3") }
    }
    // RevenueCat ships Swift. Xcode links the Swift runtime shims for the app; a bare test executable needs them spelled out.
    if (System.getProperty("os.name").startsWith("Mac")) {
        iosSimulatorArm64().binaries.getTest("DEBUG").linkerOpts(
            "-L${swiftToolchainLibs("iphonesimulator")}",
            "-lswiftCompatibility56",
            "-lswiftCompatibilityConcurrency",
        )
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.addAll(
            "kotlin.io.encoding.ExperimentalEncodingApi",
            "kotlin.time.ExperimentalTime",
            "kotlin.uuid.ExperimentalUuidApi",
            "androidx.compose.ui.ExperimentalComposeUiApi",
            "androidx.compose.animation.ExperimentalSharedTransitionApi",
        )
    }

    sourceSets {
        matching { it.name.startsWith("ios") }.configureEach {
            languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
            languageSettings.optIn("kotlinx.cinterop.BetaInteropApi")
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            // QR codes for sharing a menu with the table.
            implementation(libs.qrose)
            implementation(libs.compose.ui.tooling.preview)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
            implementation(libs.navigationevent.compose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.multiplatformSettings)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.purchases.kmp.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.koin.android)
            implementation(libs.kotlinx.coroutines.play.services)
            // Phone-to-phone table sharing (Bluetooth and Wi-Fi, no internet needed).
            implementation(libs.play.services.nearby)
            implementation(libs.kotlinx.mlkit.translate)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.multiplatformSettings.test)
        }
    }
}

dependencies {
    "androidRuntimeClasspath"(libs.compose.ui.tooling)
}

compose.resources {
    publicResClass = false
    packageOfResClass = "com.menulango.resources"
    generateResClass = always
}

sqldelight {
    databases {
        create("MenuLangoDatabase") {
            packageName.set("com.menulango.data.db")
        }
    }
}

/** The Swift static libraries for [platform] in the active Xcode toolchain. */
fun swiftToolchainLibs(platform: String): String {
    val swiftc =
        providers
            .exec { commandLine("xcrun", "--find", "swiftc") }
            .standardOutput.asText
            .get()
            .trim()
    return File(swiftc)
        .parentFile
        .resolve("../lib/swift/$platform")
        .normalize()
        .path
}
