plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/*
 * The app itself: the Application, the activity, the manifest and the
 * home-screen widget. Everything the listener sees is in :legato:ui.
 */
android {
    namespace = "com.grayguava.legato"
    // InnerTubeX's AAR is compiled against 37; targetSdk (runtime behaviour) stays 36.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.grayguava.legato"
        minSdk = 26
        targetSdk = 36
        versionCode = rootProject.extra["legato.versionCode"].toString().toInt()
        versionName = rootProject.extra["legato.versionName"].toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A release key from the environment when there is one; otherwise release
    // builds are signed with the debug key so they still install. The key
    // itself lives outside the repository; see .github/workflows/release.yml.
    val releaseKeystore = System.getenv("LEGATO_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("LEGATO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LEGATO_KEY_ALIAS")
                keyPassword = System.getenv("LEGATO_KEY_PASSWORD")
            }
        }
    }

    val unsignedRelease = providers.gradleProperty("unsignedRelease").map { it.toBoolean() }.getOrElse(false)

    buildTypes {
        debug {
            // Its own application id, so the debug build and the release build
            // can sit on the same phone: two builds signed with different keys
            // cannot upgrade one another.
            applicationIdSuffix = ".debug"
        }
        release {
            // Shrinking only: nothing renamed or optimized (see proguard-rules.pro),
            // because stream resolution reaches NewPipe, Rhino, Ktor and others by
            // reflection. Unused library code and resources go.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (unsignedRelease) null else (signingConfigs.findByName("release") ?: signingConfigs.getByName("debug"))
        }
    }

    // One APK per processor type, so each is a third of the size, plus one
    // that runs anywhere.
    splits {
        abi {
            isEnable = !providers.gradleProperty("universalOnly").map { it.toBoolean() }.getOrElse(false)
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
    // No encrypted dependency report in the APK: only Google can read it, and
    // F-Droid-style repositories (IzzyOnDroid) flag it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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
        // Lets JVM tests run code that logs through android.util.Log.
        unitTests.isReturnDefaultValues = true
        // Robolectric tests (the media library) read the merged manifest and resources.
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// A finished apk goes to a bin folder at the root of the repository, so it is
// where you expect it: bin/app-debug.apk, bin/app-release.apk.
val bin = rootProject.layout.projectDirectory.dir("bin")

listOf("debug" to "Debug", "release" to "Release").forEach { (type, name) ->
    val publish = tasks.register<Copy>("publish${name}Apk") {
        from(layout.buildDirectory.dir("outputs/apk/$type"))
        into(bin)
        include("*.apk")
    }
    tasks.matching { it.name == "assemble$name" }.configureEach { finalizedBy(publish) }
}

dependencies {
    // The activity draws the screens, so it needs Compose too.
    val composeBom = platform("androidx.compose:compose-bom:2026.03.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.annotation:annotation:1.9.1")

    // The widget shows the playing song's cover.
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    // Artist signatures that come as SVG are drawn from the vector.
    implementation("io.coil-kt.coil3:coil-svg:3.0.4")

    // The player's own types, for the widget's media button receiver.
    implementation("androidx.media3:media3-common:1.11.0")
    implementation("androidx.media3:media3-session:1.11.0")

    // The modules the app is made of.
    implementation(project(":legato:base"))
    implementation(project(":legato:settings"))
    implementation(project(":legato:source"))
    implementation(project(":legato:library"))
    implementation(project(":legato:audio"))
    implementation(project(":legato:ui"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
