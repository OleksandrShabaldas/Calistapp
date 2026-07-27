plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Self-update for sideloaded builds, shared by `:app` and `:wear`.
 *
 * It lives in its own module rather than being written twice because the risky part — verifying a
 * downloaded APK before handing it to the package installer — is exactly the code you don't want
 * two slightly-different copies of.
 */
android {
    namespace = "com.calistapp.updater"
    compileSdk = 35

    defaultConfig {
        // Matches :app; :wear is higher (30) and so is covered too.
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    api(libs.okhttp)
}
