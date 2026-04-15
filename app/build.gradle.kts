plugins { 
    id("com.android.application") 
    id("org.jetbrains.kotlin.android") 
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
    buildFeatures { compose = true } 
    composeOptions { kotlinCompilerExtensionVersion = "1.5.10" } 
    compileOptions { 
        sourceCompatibility = JavaVersion.VERSION_17 
        targetCompatibility = JavaVersion.VERSION_17 
    } 
    kotlinOptions { jvmTarget = "17" } 
} 
