plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/*
 * Where the music comes from: YouTube Music, InnerTubeX, PoTokens, Subsonic
 * servers, internet radio, the phone's own files, and the catalogue metadata
 * and cover art that go with them.
 */
android {
    namespace = "com.grayguava.legato.source"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        buildConfigField("String", "VERSION_NAME", "\"${rootProject.extra["legato.versionName"]}\"")
        buildConfigField("int", "VERSION_CODE", rootProject.extra["legato.versionCode"].toString())
        consumerProguardFiles("consumer-rules.pro")
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

/*
 * NewPipeExtractor ships its own org.schabi.newpipe.extractor.utils.Utils, and
 * src/main/java carries a patched copy at the same package path (see that
 * file for why). A release build merges every input into one dex set, where
 * D8 rejects the duplicate type outright, so the library's copy is stripped
 * from its jar before it reaches dexing, leaving exactly one definition.
 *
 * The artifact is resolved on its own and non-transitive purely to re-jar it;
 * its transitive dependencies are declared by hand below, since dropping the
 * module drops them too.
 */
val newPipeExtractorRaw: Configuration by configurations.creating {
    isTransitive = false
    isCanBeConsumed = false
}
dependencies {
    newPipeExtractorRaw("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
}
val newPipeExtractorStripped = tasks.register<org.gradle.api.tasks.bundling.Jar>(
    "stripNewPipeExtractorUtils"
) {
    archiveFileName.set("NewPipeExtractor-v0.26.3-noutils.jar")
    destinationDirectory.set(layout.buildDirectory.dir("stripped-libs"))
    // `elements` keeps resolution lazy: the jar is only opened when this task runs,
    // not while Gradle configures the build.
    from(newPipeExtractorRaw.elements.map { jars -> jars.map { zipTree(it.asFile) } }) {
        exclude("org/schabi/newpipe/extractor/utils/Utils.class")
        exclude("org/schabi/newpipe/extractor/utils/Utils\$*.class")
    }
}

dependencies {
    api(project(":legato:base"))
    api(project(":legato:settings"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.annotation:annotation:1.9.1")

    // ---- Innertube (YouTube Music) client: Ktor + kotlinx.serialization ----
    api("io.ktor:ktor-client-core:3.5.2")
    api("io.ktor:ktor-client-okhttp:3.5.2")
    api("io.ktor:ktor-client-content-negotiation:3.5.2")
    api("io.ktor:ktor-serialization-kotlinx-json:3.5.2")

    // ---- InnerTubeX: a second stream extractor (client catalog, cipher tiers, PoTokens) ----
    // Ktor and serialization above are held at its versions.
    implementation("com.github.MetrolistGroup.innertubex:innertubex-android:v0.7.4")

    // ---- Stream resolution: NewPipe solves YouTube's signature + `n` throttling ----
    // Pinned to v0.26.3: the newer v0.26.4's player-JS parser fails to parse
    // the current player build's deobfuscation function.
    implementation(files(newPipeExtractorStripped))
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.google.protobuf:protobuf-javalite:4.35.0")
    implementation("org.mozilla:rhino:1.8.1")
    implementation("org.mozilla:rhino-engine:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
}
