// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.10.1" apply false
    id("com.android.library") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10" apply false
}

// The root project produces no artefacts, only reports, and those go with the rest
// of the machine-local build state so the root of the repository stays readable.
layout.buildDirectory.set(layout.projectDirectory.dir(".gradle/build"))

// The version lives in the `version` file at the root of the repository, which is
// the one thing a release changes. It is read here and given to every module.
//
// A local build can take a version of its own, so an update can be tried without
// touching the tracked file: LEGATO_VERSION=1.0.5 ./gradlew assembleRelease
val versionFile = rootProject.file("version")
val localVersion = (project.findProperty("legatoVersion") as String?)?.trim()
    ?: providers.environmentVariable("LEGATO_VERSION").orNull?.trim()
val appVersion = localVersion?.takeIf { it.isNotEmpty() }
    ?: versionFile.takeIf { it.exists() }
        ?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    ?: "0.0.0"
val versionParts = appVersion.split(".")
require(versionParts.size == 3 && versionParts.all { it.toIntOrNull() != null }) {
    "version must contain three numeric parts, for example 1.0.5, but was $appVersion"
}
// 1.0.5 -> 10005, so the code always rises as the version does. 0.0.0 is a
// development build, and Android rejects a versionCode of 0, so it starts at 1.
val appVersionCode = (versionParts[0].toInt() * 10_000 + versionParts[1].toInt() * 100 + versionParts[2].toInt())
    .coerceAtLeast(1)

extra["legato.versionName"] = appVersion
extra["legato.versionCode"] = appVersionCode
