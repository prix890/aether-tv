plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ksPath: String? = System.getenv("AETHER_KEYSTORE")
val useKey: Boolean = ksPath != null && file(ksPath).exists()

android {
    namespace = "com.aether.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aether.tv"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            if (useKey) {
                storeFile = file(ksPath!!)
                storePassword = "aether-ci-pass"
                keyAlias = "aether"
                keyPassword = "aether-ci-pass"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (useKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
