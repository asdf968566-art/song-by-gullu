plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dxdepartment.screenmonitor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dxdepartment.screenmonitor"
        minSdk = 26
        targetSdk = 35
        // CI passes its run number so every build installs over the previous one.
        val build = System.getenv("DX_BUILD_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "0.1.$build"
    }

    // A fixed key kept in the repo on purpose: every build (CI or a Windows PC) has the same signature,
    // so a new APK installs over the old one without uninstalling.
    signingConfigs {
        create("dx") {
            storeFile = file("dx-debug.jks")
            storePassword = "dxmonitor"
            keyAlias = "dx"
            keyPassword = "dxmonitor"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dx")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("dx")
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
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // QR code for pairing (pure Java, no Google Play services).
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
}
