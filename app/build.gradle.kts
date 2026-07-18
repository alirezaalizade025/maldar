plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Semantic versioning driven by conventional commit prefixes, computed from the
// commits since the LAST release tag (so a MINOR bump resets PATCH to 0):
//   - commits starting with "feat" since last tag bump MINOR (0.X.0)
//   - commits starting with "fix"  since last tag bump PATCH (0.0.X)
// The base version is taken from the most recent "vX.Y[.Z]" tag; the new
// version is that base plus the feat/fix deltas. versionCode is monotonic:
// the base tag's code (parsed from a ".cN" suffix when present) plus the
// number of commits since the tag.
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

// Last release tag by creation date, restricted to conventional "vX.Y[.Z]"
// tags (ignores legacy timestamp/whole-number tags). Falls back to empty when
// none exist.
val lastTag: String = git("for-each-ref", "--sort=-creatordate",
        "--format=%(refname:short)", "refs/tags")
    .lineSequence()
    .firstOrNull { it.matches(Regex("""v\d+\.\d+(\.\d+)?""")) }
    .orEmpty()

// Subjects of commits since the last tag (whole history when untagged).
val sinceRange = if (lastTag.isNotBlank()) "$lastTag..HEAD" else "HEAD"
val gitLog = git("log", sinceRange, "--pretty=format:%s")

fun gitCommitPrefixCount(log: String, prefix: String): Int {
    return log.lineSequence().count { it.trim().lowercase().startsWith(prefix) }
}

val featDelta = gitCommitPrefixCount(gitLog, "feat")
val fixDelta = gitCommitPrefixCount(gitLog, "fix")

// Base version components parsed from the last tag (default 0.0.0).
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

// Monotonic versionCode: base code (from ".cN" suffix) + commits since tag.
val baseCode = lastTag.substringAfterLast(".c", "")
    .toIntOrNull() ?: (baseMajor * 10000 + baseMinor * 100 + basePatch)
val commitsSinceTag = git("rev-list", "--count", sinceRange).trim().toIntOrNull() ?: 0
val versionCodeValue = (baseCode + commitsSinceTag).coerceAtLeast(1)

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
            // Sign the release build with the auto-generated debug keystore so
            // the resulting APK is installable on Android. (An unsigned APK
            // cannot be installed.) Replace with a real release keystore before
            // publishing to the Play Store.
            signingConfig = signingConfigs.getByName("debug")
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
