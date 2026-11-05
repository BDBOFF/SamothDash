plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Numéro de version = numéro de compilation GitHub (sert à la mise à jour intégrée)
val buildNumber: Int = (project.findProperty("BUILD_NUMBER") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.samoth.dash"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.samoth.dash"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.$buildNumber"
    }

    signingConfigs {
        create("samoth") {
            storeFile = file("samoth-dash.jks")
            storePassword = "samothdash2026"
            keyAlias = "samothdash"
            keyPassword = "samothdash2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("samoth")
        }
        debug {
            signingConfig = signingConfigs.getByName("samoth")
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
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
