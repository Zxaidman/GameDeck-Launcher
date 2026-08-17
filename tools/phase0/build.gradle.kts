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
        versionCode = 1
        versionName = "phase0-0.0.1"
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
}
