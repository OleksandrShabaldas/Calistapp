import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Pull secrets out of local.properties (never committed) so they don't live in source.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val geminiApiKey: String = localProps.getProperty("GEMINI_API_KEY", "")
val geminiModel: String = localProps.getProperty("GEMINI_MODEL", "gemini-2.0-flash")
val githubVideoToken: String = localProps.getProperty("GITHUB_VIDEO_TOKEN", "")
val keystoreStorePassword: String = localProps.getProperty("KEYSTORE_STORE_PASSWORD", "")
val keystoreKeyAlias: String = localProps.getProperty("KEYSTORE_KEY_ALIAS", "")
val keystoreKeyPassword: String = localProps.getProperty("KEYSTORE_KEY_PASSWORD", "")

android {
    namespace = "com.calistapp.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.calistapp"
        minSdk = 26
        targetSdk = 35
        // Versioning: 0.0.x = small fixes, 0.x.0 = bigger changes (patch resets to 0). Before any
        // segment would need a second digit, carry into the one on its left instead — the release
        // after 0.9.x is 1.0.0, never 0.10.0. Keeps :app and :wear in lockstep since they always
        // ship together. versionCode = major*100 + minor*10 + patch, so it's derived from
        // versionName rather than tracked separately.
        versionCode = 30
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
        buildConfigField("String", "GEMINI_MODEL", "\"$geminiModel\"")
        buildConfigField("String", "GITHUB_VIDEO_TOKEN", "\"$githubVideoToken\"")
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("calistapp.jks")
            storePassword = keystoreStorePassword
            keyAlias = keystoreKeyAlias
            keyPassword = keystoreKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":updater"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    implementation(libs.play.services.wearable)
    // Lets the phone open the app on the watch when a workout starts.
    implementation(libs.wear.remote.interactions)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
}
