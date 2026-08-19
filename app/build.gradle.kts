// app/ is the Android assembly layer only (PROJECT_STRUCTURE.md §4): manifest, startup, wiring,
// navigation host, resources, APK configuration.
//
// Feature and domain logic must not accumulate here — see PROJECT_STRUCTURE.md §23.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.zxaidman.kestrel"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.zxaidman.kestrel"
        // Android 10 / API 29 is fixed by ADR-004. Do not raise without superseding that record.
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 11
        versionName = "0.0.11-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        val javaVersion = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    buildFeatures {
        compose = true
        aidl = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
    }
}

dependencies {
    implementation(project(":core"))

    // Shizuku — the privilege ADR-INPUT-001's backend needs.
    //
    // Why here: the accepted backend requires shell privilege, and no public API grants it to an
    // ordinary application. It is confined to platform/shizuku/ behind one capability boundary
    // (PROJECT_STRUCTURE.md §558), and ADR-003 keeps it optional at runtime — with Shizuku absent
    // the application still runs and reports what is unavailable.
    // Licence: Apache-2.0. Within the API 29 baseline.
    // It must never reach :core or any Composable directly (CLAUDE.md §4).
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
