// core/ is platform-independent domain logic (PROJECT_STRUCTURE.md §5).
//
// This is a Kotlin/JVM module, not an Android library module, and that is deliberate: it makes the
// dependency rule in PROJECT_STRUCTURE.md §21 unenforceable to break by accident. Adding Compose,
// Android UI, or Shizuku here fails to resolve rather than merely failing review.
//
// If a future domain type genuinely needs an Android API, that is a signal the type belongs in
// platform/ instead — not a reason to convert this module.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Bytecode target is pinned; the JDK used to run the build only has to be at least this version.
// A toolchain is deliberately not declared, so the build does not require provisioning an exact
// JDK before it can run.
java {
    val javaVersion = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
    }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
