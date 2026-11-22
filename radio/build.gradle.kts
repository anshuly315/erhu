import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "org.simpmusic.radio"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.common)
    implementation(projects.domain)
    implementation(libs.core.ktx)

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.material3.lib)
    implementation(libs.compose.ui)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coroutines.android)
    implementation(libs.activity.compose)
    // The same liquid-glass backdrop the SimpMusic pages use.
    implementation(libs.liquid.glass)
    // Reads and writes tags in one chosen local file (Edit tags in the player menu).
    implementation("net.jthink:jaudiotagger:3.0.1")
    // VYBE: on-device text embedding for describing what to listen to.
    implementation("com.google.mediapipe:tasks-text:1.1.0")
}
