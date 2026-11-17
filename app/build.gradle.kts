plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Numéro de version = numéro de compilation GitHub (sert à la mise à jour intégrée)
// Mot de passe de la clé de signature : secret GitHub SAMOTH_KEYSTORE_PASSWORD, jamais dans le dépôt
val keystorePassword: String? = System.getenv("SAMOTH_KEYSTORE_PASSWORD")

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
            storePassword = keystorePassword
            keyAlias = "samothdash"
            keyPassword = keystorePassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sans le secret (tests sur émulateur), l'APK de publication n'est pas signé
            if (!keystorePassword.isNullOrEmpty()) signingConfig = signingConfigs.getByName("samoth")
        }
        // Les tests sur émulateur utilisent la clé de débogage standard
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
