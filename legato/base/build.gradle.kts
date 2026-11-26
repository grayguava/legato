plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/*
 * The vocabulary every other module speaks: the song and shelf types, the
 * log, the HTTP client, the toast pills and the stats.
 *
 * Nothing here depends on another Legato module, so anything can use it.
 */
android {
    namespace = "com.grayguava.legato.base"
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
    // Coroutines and serialization show up in this module's own API (the
    // state flows, the serializable types), so they are passed on.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // Media3 hands back Guava futures, which are awaited here.
    api("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.11.0")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    // The HTTP client the app and the sources share.
    api("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.squareup.okio:okio:3.17.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
}
