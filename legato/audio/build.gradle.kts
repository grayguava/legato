plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/*
 * The sound itself: the player service and the queue it plays from, the DSP
 * chain, the USB and car outputs, the cache, and what the app knows about
 * the audio it is playing.
 */
android {
    namespace = "com.grayguava.legato.audio"
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
    api(project(":legato:base"))
    api(project(":legato:settings"))
    api(project(":legato:source"))
    api(project(":legato:library"))

    // The player's own types (MediaItem, Player) are part of this module's
    // API, so Media3 is passed on.
    api("androidx.media3:media3-exoplayer:1.11.0")
    api("androidx.media3:media3-session:1.11.0")
    api("androidx.media3:media3-common:1.11.0")
    api("androidx.media3:media3-datasource-okhttp:1.11.0")
    api("androidx.media3:media3-database:1.11.0")
    // Internet radio stations that stream HLS rather than Icecast, and Apple's
    // animated artist headers, which are m3u8 clips. Nothing plays DASH.
    api("androidx.media3:media3-exoplayer-hls:1.11.0")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.annotation:annotation:1.9.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
}
