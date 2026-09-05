import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Same local.properties the :app module reads (never committed) — see there for the Gemini key.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val keystoreStorePassword: String = localProps.getProperty("KEYSTORE_STORE_PASSWORD", "")
val keystoreKeyAlias: String = localProps.getProperty("KEYSTORE_KEY_ALIAS", "")
val keystoreKeyPassword: String = localProps.getProperty("KEYSTORE_KEY_PASSWORD", "")

android {
    namespace = "com.calistapp.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.calistapp"
        minSdk = 30 // Wear OS 3+
        targetSdk = 34
        // Kept in lockstep with :app's versionName/versionCode — see the comment there for the
        // versioning scheme. The two always ship together as one release.
        versionCode = 100
        versionName = "1.0.0"
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

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.wear.compose.material)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.play.services.wearable)
    implementation(libs.health.services.client)
    // Health Services' *Async APIs return Guava ListenableFuture, so it must be on the classpath.
    implementation(libs.guava)
}
