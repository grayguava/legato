plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/*
 * Everything several screens share: the theme, the icons, the glass and the
 * cards. It exists so the player can be a module of its own without the
 * screens depending on it in return.
 *
 * The Kotlin packages stay under com.grayguava.legato.ui, so moving a file here
 * from :legato:ui changes nothing at its call sites.
 */
android {
    namespace = "com.grayguava.legato.uicore"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    // Not strict: this module is an internal kit, its surface changes with every
    // screen. The player module, which has a designed public API, is the strict one.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // ---- Compose (Material 3) ----
    val composeBom = platform("androidx.compose:compose-bom:2026.03.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("com.materialkolor:material-kolor:4.0.0")
    implementation("dev.chrisbanes.haze:haze:1.7.1")
    implementation("io.github.kyant0:backdrop:2.0.0-alpha03")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.github.cosmictaserdev-creator.Jetpack_SF_Symbols:sfsymbols-android:1.1.1") {
        isTransitive = false
    }

    // ---- Images ----
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("io.coil-kt.coil3:coil-svg:3.0.4")

    // The models the shared pieces draw.
    api(project(":legato:base"))
    api(project(":legato:settings"))
    api(project(":legato:source"))
    api(project(":legato:library"))
    // The menus act on the playback: keep a song cached, or keep it in the shuffle.
    api(project(":legato:audio"))
}
