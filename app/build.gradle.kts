import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "org.shuyah.concentration"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.shuyah.concentration"
        minSdk = 26
        targetSdk = 35
        // Règle de version : VERSION +1 à chaque livraison (voir Version.kt).
        versionCode = 8
        versionName = "1.7"
        // Nom du fichier livré : concentration-<version>-debug.apk (copié ensuite en concentration-<version>.apk)
        setProperty("archivesBaseName", "concentration-$versionName")
        // Date et heure de compilation, affichées en bas de l'écran principal.
        buildConfigField("String", "DATE_COMPILATION", "\"" + SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date()) + "\"")
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // Signal de présence à SAWALI (règle 4 du propriétaire)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
