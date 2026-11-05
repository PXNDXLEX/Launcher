# Mantener nombres legibles en trazas y preservar reflexión/JNI crítica
-dontobfuscate

# Preservar clases del proyecto (servicios, modelos, estados)
-keep class com.tuusuario.carlauncher.** { *; }

# Vosk Offline Speech + JNA JNI
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn com.sun.jna.**
-dontwarn org.vosk.**
-dontwarn java.awt.**

# Mapbox Maps SDK v11 + GeoJSON + Gson
-keep class com.mapbox.** { *; }
-keep interface com.mapbox.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn com.mapbox.**

# CameraX & Media3
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**
-dontwarn androidx.media3.**

