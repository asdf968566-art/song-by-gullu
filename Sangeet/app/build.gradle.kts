plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sangeet.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sangeet.player"
        minSdk = 26
        targetSdk = 35
        // GitHub Actions ka run number = build number, taaki app naya version pehchaan sake.
        val build = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "UPDATE_REPO", "\"asdf968566-art/song-by-gullu\"")
        buildConfigField(
            "String",
            "YOUTUBE_API_KEY",
            // Key repo mein nahi rakhi: GitHub Secret YOUTUBE_API_KEY se aati hai (ya local.properties / -P se).
            "\"${System.getenv("YOUTUBE_API_KEY") ?: project.findProperty("sangeet.youtubeApiKey") ?: ""}\"",
        )
    }

    // Har build ek hi key se sign ho, taaki naya APK purane ke upar install ho jaye (data safe rahe).
    signingConfigs {
        getByName("debug") {
            storeFile = file("sangeet-debug.jks")
            storePassword = "sangeet123"
            keyAlias = "sangeet"
            keyPassword = "sangeet123"
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
            // Debug key se sign hota hai taaki release APK seedha install ho sake.
            // Play Store ke liye apni keystore lagayein.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        // NewPipeExtractor ko naye Java APIs chahiye.
        isCoreLibraryDesugaringEnabled = true
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
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "/META-INF/{INDEX.LIST,DEPENDENCIES,versions/9/previous-compilation-data.bin}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.coil.compose)
    // Installs the baseline profile (src/main/baseline-prof.txt) even when the APK is sideloaded.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.newpipe.extractor)
    implementation(libs.anthropic.java)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
