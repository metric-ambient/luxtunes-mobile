plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing: the build workflow decodes the key from GitHub secrets and
// passes its location and password in these variables. Without them the APK
// is signed with the debug key (fine for trying a build, not for sharing).
val keystorePath: String? = System.getenv("LUX_KEYSTORE")
val keystorePassword: String? = System.getenv("LUX_KEYSTORE_PASSWORD")

android {
    namespace = "io.github.metricambient.luxtunes"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.metricambient.luxtunes"
        minSdk = 26
        targetSdk = 35
        // Every GitHub build gets a higher number, so a new APK installs over the old one
        // (as long as it's signed with the same key).
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        // A release build (tag v0.2.0) takes its version from the tag.
        versionName = System.getenv("GITHUB_REF_NAME")
            ?.takeIf { System.getenv("GITHUB_REF_TYPE") == "tag" }
            ?.removePrefix("v")
            ?: "0.7.0"
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = "luxtunes"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-extractor:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")      // the real org.json for tests (Android's is a stub there)
}
