plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/*
 * The player: the now-playing screen, the mini player, the lyric sheets and the
 * panes they open. It owns its model (PlayerViewModel), its rhythm (PlayerSpacing)
 * and its own tests, so it can be edited without the screens around it.
 *
 * What the app sees is deliberately small: the screen, the mini bar, the view
 * model and its state. Everything else stays here.
 */
android {
    namespace = "com.grayguava.legato.player"
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
    val composeBom = platform("androidx.compose:compose-bom:2026.03.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.runtime:runtime")
    implementation("com.materialkolor:material-kolor:4.0.0")
    implementation("dev.chrisbanes.haze:haze:1.7.1")
    implementation("io.github.kyant0:backdrop:2.0.0-alpha03")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")

    // The shared look, and the models the player draws.
    api(project(":legato:uicore"))
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
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
