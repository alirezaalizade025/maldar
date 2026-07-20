plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Versioning strategy (robust against stray/lower tags):
//   - versionName: highest semantic tag (by VERSION, not creation date) plus
//     feat/fix deltas since that tag.
//   - versionCode: STRICTLY MONOTONIC and tag-independent. It is derived from the
//     total commit count on the history leading to HEAD, so every new build has
//     a higher versionCode than every previous build — this is what makes
//     in-place updates ("App not installed") impossible due to a version
//     regression. A fixed BASE_OFFSET keeps it above any legacy hand-set code.
fun git(vararg args: String): String {
    return try {
        val process = Runtime.getRuntime().exec(arrayOf("git", *args))
        val out = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor()
        out
    } catch (_: Exception) {
        ""
    }
}

// All conventional "vX.Y[.Z]" tags, sorted by their numeric version (highest
// first), NOT by creation date. This ignores stray low tags created after a
// higher release line (e.g. a v0.46.0 made after v1.1).
val semanticTags: List<String> = git("tag", "--list", "v*.*.*")
    .lineSequence()
    .filter { it.matches(Regex("""v\d+\.\d+\.\d+""")) }
    .sortedByDescending { tag ->
        val p = tag.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        (p.getOrElse(0) { 0 } shl 16) + (p.getOrElse(1) { 0 } shl 8) + p.getOrElse(2) { 0 }
    }
    .toList()

val lastTag: String = semanticTags.firstOrNull().orEmpty()

// Subjects of commits since the last tag (whole history when untagged).
val sinceRange = if (lastTag.isNotBlank()) "$lastTag..HEAD" else "HEAD"
val gitLog = git("log", sinceRange, "--pretty=format:%s")

fun gitCommitPrefixCount(log: String, prefix: String): Int {
    return log.lineSequence().count { it.trim().lowercase().startsWith(prefix) }
}

val featDelta = gitCommitPrefixCount(gitLog, "feat")
val fixDelta = gitCommitPrefixCount(gitLog, "fix")

// Base version components parsed from the highest semantic tag (default 0.0.0).
val baseParts = lastTag.removePrefix("v")
    .split(".")
    .mapNotNull { it.toIntOrNull() }
val baseMajor = baseParts.getOrElse(0) { 0 }
val baseMinor = baseParts.getOrElse(1) { 0 }
val basePatch = baseParts.getOrElse(2) { 0 }

// SemVer: a feat bumps MINOR and resets PATCH; a fix only bumps PATCH.
val newMinor = baseMinor + featDelta
val newPatch = if (featDelta > 0) 0 else basePatch + fixDelta
val versionNameValue = "$baseMajor.$newMinor.$newPatch"

// Monotonic versionCode: total commits on the path to HEAD + a base offset that
// exceeds any legacy hand-assigned code (legacy releases topped out below 11000).
// Because the commit count only grows, versionCode is always strictly increasing
// across builds, so updates never fail with a lower versionCode.
val BASE_OFFSET = 20000
val totalCommits = git("rev-list", "--count", "HEAD").trim().toIntOrNull() ?: 0
val versionCodeValue = (BASE_OFFSET + totalCommits).coerceAtLeast(1)

android {
    namespace = "com.personalfinance.tracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.personalfinance.tracker"
        minSdk = 26
        targetSdk = 34
        versionCode = versionCodeValue
        versionName = versionNameValue
    }

    buildTypes {
        release {
            // Strip unused code (R8) and unused resources from the release APK.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign with a STABLE release keystore so updates install over a
            // previously installed build (Android requires the same signing
            // cert for in-place updates). CI provides the key via RELEASE_*
            // env vars (decoded from a GitHub secret); local dev falls back to
            // the debug keystore so builds still work without the secret.
            signingConfig = if (System.getenv("RELEASE_STORE_FILE") != null)
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
    }

    // Release signing config populated from environment (set in CI). The keystore
    // is created from the RELEASE_KEYSTORE_BASE64 secret so it is identical on
    // every run — this is what makes updates installable.
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("RELEASE_STORE_FILE") ?: "app/keystore/maldar-release.jks")
            storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "maldar123"
            keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "maldar"
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "maldar123"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Prints the resolved versionName / versionCode so CI can tag the release
// without parsing the (dynamically computed) values out of this file.
tasks.register("printVersionName") {
    doLast {
        println(android.defaultConfig.versionName)
    }
}

tasks.register("printVersionCode") {
    doLast {
        println(android.defaultConfig.versionCode)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended")

    // Material Components (View-based) - provides the Theme.Material3.* window/app
    // themes used by the Activity/splash window. Compose material3 does not ship
    // these XML themes.
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
