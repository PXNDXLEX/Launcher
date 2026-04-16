plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Opcional, pero recomendado para Google Maps
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin") version "2.0.1" apply false
}

android {
    namespace = "com.tuusuario.carlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tuusuario.carlauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
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
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Jetpack Compose
    implementation("androidx.compose.ui:ui:1.6.3")
    implementation("androidx.compose.ui:ui-graphics:1.6.3")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.3")
    
    // GPS para el velocímetro
    implementation("com.google.android.gms:play-services-location:21.1.0")

    // LIBRERÍAS DE GOOGLE MAPS PARA COMPOSE
    implementation("com.google.maps.android:maps-compose:4.3.3")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
}