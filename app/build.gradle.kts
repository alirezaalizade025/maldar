plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Semantic versioning driven by conventional commit prefixes:
//   - commits starting with "feat" bump MINOR (0.1.0)
//   - commits starting with "fix"  bump PATCH (0.0.1)
// versionCode = minor * 10 + patch (monotonic; matches the ".cN" suffix the
// UpdateChecker compares against, so releases like "vX.Y.c<code>" resolve
// correctly). versionName = "0.<minor>.<patch>".
fun gitCommitPrefixCount(prefix: String): Int {
    return try {
        val out = ByteArrayOutputStream()
        exec {
            commandLine("git", "log", "--pretty=format:%s")
            standardOutput = out
        }
        out.toString().lineSequence()
            .count { it.trim().lowercase().startsWith(prefix) }
    } catch (_: Exception) {
        0
    }
}

val featCount = gitCommitPrefixCount("feat")
val fixCount = gitCommitPrefixCount("fix")
// Start at 1 so a brand-new repo still produces a valid versionCode >= 1.
val versionCodeValue = (featCount * 10 + fixCount).coerceAtLeast(1)
val versionNameValue = "0.$featCount.$fixCount"

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
            isMinifyEnabled = false
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
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

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
