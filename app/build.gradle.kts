plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tuusuario.carlauncher"
    compileSdk = 35

    signingConfigs {
        create("shared") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.tuusuario.carlauncher"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.currentTimeMillis() / 1000).toInt()
        versionName = "2.0.${(System.currentTimeMillis() / 1000).toInt()}"
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("shared")
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        noCompress.addAll(listOf("bin", "fst", "mdl", "txt", "conf", "stats", "dubm", "ie", "mat", "int", "glb"))
    }
}

composeCompiler {
    enableStrongSkippingMode = true
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Jetpack Compose 1.7+ (BOM 2024.10.01) — Modifier.Node + GraphicsLayer + Strong Skipping
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // GPS de alta precisión
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Mapbox Maps SDK v11 (3D GLB + Extrusions)
    implementation("com.mapbox.maps:android:11.9.0")

    // Coil para imágenes y GIFs con caché en memoria/disco
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")

    // CameraX para Dashcam
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-video:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ExoPlayer (Media3) para galería de videos Dashcam con subtítulos .vtt
    val media3Version = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")

    // VectorDrawableCompat para renderizar SVGs/AVDs tintados en el mapa
    implementation("androidx.vectordrawable:vectordrawable:1.2.0")

    // Vosk Offline Speech Recognition (Detección continua de hotword 'Sara'/'Arlet' sin bips)
    implementation("com.alphacephei:vosk-android:0.3.75@aar")
    implementation("net.java.dev.jna:jna:5.18.1@aar")

    testImplementation("junit:junit:4.13.2")
}
