import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// EXPERIMENTAL — Phase 0 input feasibility harness.
//
// This is a measurement instrument, not product code (PROJECT_STRUCTURE.md §16 and §27). It ships
// as its own APK with its own applicationId so it can be installed alongside the product and
// uninstalled without trace. Nothing here is a dependency of :app, and nothing here should be
// promoted to production without moving it behind platform/input/ first.
//
// It deliberately does not depend on :core. The harness must report what Android actually does,
// not what the domain model expects.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.zxaidman.kestrel.phase0"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.zxaidman.kestrel.phase0"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 3
        versionName = "phase0-0.0.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    // Shizuku — experimental, harness only.
    //
    // Why: Phase 0 must determine whether a shell-privileged process can reach the kernel
    // virtual-input facility. Shizuku is the only way to obtain that privilege on an unrooted
    // phone without attaching a computer, which is what makes the remaining tiers runnable by
    // someone who is not a developer.
    // Why not platform APIs: no public API grants shell privilege to an ordinary application.
    // Licence: Apache-2.0. Android compatibility: within the API 29 baseline.
    // Risk: an external component that must be installed and running; every call here is guarded
    // so the harness degrades to observation-only when it is absent.
    //
    // This dependency must never appear in :app or :core — see ADR-003 and PROJECT_STRUCTURE.md §21.
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)
}
