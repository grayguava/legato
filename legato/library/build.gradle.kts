plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/*
 * What the listener owns: the library, the playlists, the listening history,
 * the account, and what the app offers them to hear.
 */
android {
    namespace = "com.grayguava.legato.library"
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

    implementation("androidx.core:core-ktx:1.15.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
}
