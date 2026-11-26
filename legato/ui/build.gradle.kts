plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/*
 * The screens: everything the listener sees and touches, in Compose.
 *
 * The app module sits on top of this and owns the Application, the activity
 * and the manifest; nothing here knows about those.
 *
 * Above :legato:uicore (theme, icons, shared components) and :legato:player
 * (the now-playing screen, its model and its rhythm); the shared pieces live
 * where several screens read them, the player where one screen lives.
 */
android {
    namespace = "com.grayguava.legato.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "VERSION_NAME", "\"${rootProject.extra["legato.versionName"]}\"")
        buildConfigField("int", "VERSION_CODE", rootProject.extra["legato.versionCode"].toString())
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // The recorded service answers the tests read live with the code they came from.
    sourceSets["test"].resources.srcDir(rootProject.file("legato/source/src/test/resources"))
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    explicitApi()
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
    // Full Material 3 color schemes from one seed color: accent picker and
    // artwork-tinted themes. Also quantizes artwork into a seed.
    implementation("com.materialkolor:material-kolor:4.0.0")
    // Frosted glass behind the floating nav bar and mini player.
    implementation("dev.chrisbanes.haze:haze:1.7.1")
    // Apple-style Liquid Glass (lens refraction, highlights) on Android 13+.
    implementation("io.github.kyant0:backdrop:2.0.0-alpha03")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    // Apple's SF Symbols, as Compose vectors, for the settings and the menus.
    // Without its own dependencies: it asks for a newer Compose than this
    // module has, and the plain vectors need nothing it doesn't already have.
    implementation("com.github.cosmictaserdev-creator.Jetpack_SF_Symbols:sfsymbols-android:1.1.1") {
        isTransitive = false
    }

    // ---- Images ----
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    // Artist signatures that come as SVG are drawn from the vector, crisp at any size.
    implementation("io.coil-kt.coil3:coil-svg:3.0.4")

    // The modules the screens are made of.
    api(project(":legato:uicore"))
    api(project(":legato:player"))
    api(project(":legato:base"))
    api(project(":legato:settings"))
    api(project(":legato:source"))
    api(project(":legato:lyrics"))
    api(project(":legato:library"))
    api(project(":legato:audio"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("io.coil-kt.coil3:coil-test:3.0.4")
    // Rendering screens to images in unit tests, to check layouts.
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
