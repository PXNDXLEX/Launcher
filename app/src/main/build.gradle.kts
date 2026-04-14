dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended") // Para los íconos
    // Mapbox Maps SDK
    implementation("com.mapbox.maps:android:11.2.0")
    // Para obtener la ubicación precisa
    implementation("com.google.android.gms:play-services-location:21.1.0")
}