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
        versionCode = 32
        versionName = "0.0.32-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    /**
     * One key for every build this repository produces.
     *
     * Android treats a signature as an application's identity, so two builds signed by different
     * keys are two different applications and the second cannot install over the first. Gradle's
     * default debug config generates a keystore on the machine that builds, and a CI runner is a
     * fresh machine every time — so every build was signed by a different key, and every install
     * was a reinstall that took the user's permissions and settings with it.
     *
     * This key is committed and its password is public. That is fine for builds people are testing
     * deliberately and unacceptable for builds people are trusting, so a release must use a key
     * that is not in this repository. `signing/README.md` carries the full reasoning and the steps.
     */
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("signing/kestrel-testing.p12")
            storePassword = "kestrel-testing"
            keyAlias = "kestrel-testing"
            keyPassword = "kestrel-testing"
        }
    }

    buildTypes {
        release {
            // Also the testing key for now: there is no release key, and an unsigned artifact
            // nobody can install is worse than a testing-signed one that says what it is.
            signingConfig = signingConfigs.getByName("debug")
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

    // Reading and writing inside the folder the user chose.
    //
    // Why here: everything Kestrel keeps lives in a folder on shared storage that the user picks,
    // so it survives an uninstall and can be copied in a file manager. Reaching that folder means
    // the Storage Access Framework, whose framework API is DocumentsContract — a dozen calls of
    // URI arithmetic per operation, none of which can be exercised on a laptop. This library is the
    // thin wrapper AndroidX ships for exactly that, and it removes a class of fault this project
    // could only find on a device.
    //
    // Why not the alternative: MANAGE_EXTERNAL_STORAGE would need no library and would grant access
    // to every file on the phone, be a restricted permission, and — on the evidence of ADR-006 —
    // risk another Play Protect block for every user. A folder the user chose is both smaller and
    // more honest.
    //
    // Licence: Apache-2.0. Minimum SDK 19, within the API 29 baseline. No transitive dependencies
    // beyond androidx.core, which is already present.
    implementation(libs.androidx.documentfile)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
