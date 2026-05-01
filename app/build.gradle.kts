import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.21-1.0.16"
}

android {
    namespace = "com.zyra.app"
    compileSdk = 35
    buildToolsVersion = "34.0.0"

    defaultConfig {
        applicationId = "com.zyra.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Load API keys from local.properties
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(localPropertiesFile.inputStream())
        }
        
        buildConfigField("String", "GOOGLE_MAPS_API_KEY", "\"${properties.getProperty("GOOGLE_MAPS_API_KEY", "")}\"")
        buildConfigField("String", "IBM_QUANTUM_API_KEY", "\"${properties.getProperty("IBM_QUANTUM_API_KEY", "snU2VhbBd7gt5hel4PNve1iQiySBzI8XqciIvpTB_6uo")}\"")
        buildConfigField("String", "CISCO_QUANTUM_API_KEY", "\"${properties.getProperty("CISCO_QUANTUM_API_KEY", "gBNe17S1s!-,o,(UWQn;IJ:2@5{9+0p1n3W(yE@mY6J67!0d{qfxr90Dh@H{hQ2G")}\"")
        buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"${properties.getProperty("PICOVOICE_ACCESS_KEY", "")}\"")
        buildConfigField("String", "OPENWEATHER_API_KEY", "\"${properties.getProperty("OPENWEATHER_API_KEY", "")}\"")
        buildConfigField("String", "NEWS_API_KEY", "\"${properties.getProperty("NEWS_API_KEY", "")}\"")
        buildConfigField("String", "ALPHA_VANTAGE_API_KEY", "\"${properties.getProperty("ALPHA_VANTAGE_API_KEY", "")}\"")
        buildConfigField("String", "GOOGLE_TRANSLATE_API_KEY", "\"${properties.getProperty("GOOGLE_TRANSLATE_API_KEY", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.7"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2023.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    
    // Google Gemini (Generative AI)
    implementation("com.google.ai.client.generativeai:generativeai:0.7.0")

    // ElevenLabs Conversational AI
    implementation("io.elevenlabs:elevenlabs-android:0.7.2")

    // LiveKit Real-time Voice
    implementation("io.livekit:livekit-android:2.2.0")

    // Location & Maps
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Network (Retrofit)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Icons
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Wake Word Detection (Vosk)
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.38@aar")
    
    // Media Session for Music Control
    implementation("androidx.media:media:1.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    
    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
