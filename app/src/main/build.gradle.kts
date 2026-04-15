// Archivo app/build.gradle.kts (NIVEL APP)
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tuusuario.carlauncher" // Debe coincidir con el package de tu AndroidManifest
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tuusuario.carlauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    // GPS para el velocímetro
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // MAPBOX (Nota: Solo descomenta esta línea si ya pusiste el MAPBOX_DOWNLOADS_TOKEN en GitHub Secrets)
    implementation("com.mapbox.maps:android:11.2.0")
}