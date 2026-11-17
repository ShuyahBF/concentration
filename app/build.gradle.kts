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
        versionCode = 44
        versionName = "1.43"
        // Fichier brut : concentration-<version>-<saveur>-release.apk, livré sous le nom concentration-<version>[-sms].apk (sans « debug » ni « release »)
        setProperty("archivesBaseName", "concentration-$versionName")
        // Enregistrement d'écran de débogage (icône discrète) : actif par défaut ; -PsansEnregistrement=true le retire
        buildConfigField("boolean", "ENREGISTREMENT_ECRAN", if ((project.findProperty("sansEnregistrement") as String?) == "true") "false" else "true")
        // Date et heure de compilation, affichées en bas de l'écran principal.
        buildConfigField("String", "DATE_COMPILATION", "\"" + SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date()) + "\"")
    }
    // Deux versions de la MÊME application (même code) :
    //  - « standard » : sans l'autorisation de lire les SMS (s'installe sans souci avec Google Play Protect) ; les SMS passés s'importent depuis un fichier de sauvegarde ;
    //  - « sms »     : déclare l'autorisation de lire les SMS (src/sms/AndroidManifest.xml) ; lit directement toute la boîte de réception. Play Protect peut la bloquer à l'installation.
    flavorDimensions += "lecture"
    productFlavors {
        create("standard") { dimension = "lecture"; buildConfigField("boolean", "SMS_DIRECT", "false") }
        create("sms") { dimension = "lecture"; buildConfigField("boolean", "SMS_DIRECT", "true") }
    }
    // Signature PERMANENTE (clé ShuyahStore) : les 4 valeurs sont lues dans les variables d'environnement ANDROID_KEYSTORE_FILE, ANDROID_KEYSTORE_PASSWORD,
    // ANDROID_KEY_ALIAS et ANDROID_KEY_PASSWORD (ou en propriétés Gradle -P…). Aucune clé ni mot de passe n'est dans le dépôt.
    // Si l'une manque ou si le fichier est absent, on garde le comportement précédent (clé locale de l'environnement avec -PsignerAvecCleLocale=true).
    fun valeurSignature(nom: String): String? =
        ((project.findProperty(nom) as String?) ?: System.getenv(nom))?.takeIf { it.isNotBlank() }
    val cheminCle = valeurSignature("ANDROID_KEYSTORE_FILE")
    val mdpFichier = valeurSignature("ANDROID_KEYSTORE_PASSWORD")
    val aliasCle = valeurSignature("ANDROID_KEY_ALIAS")
    val mdpCle = valeurSignature("ANDROID_KEY_PASSWORD")
    val signaturePermanente = cheminCle != null && file(cheminCle).exists() && mdpFichier != null && aliasCle != null && mdpCle != null
    signingConfigs {
        if (signaturePermanente) {
            create("permanente") {
                storeFile = file(cheminCle!!)
                storePassword = mdpFichier
                keyAlias = aliasCle
                keyPassword = mdpCle
            }
        }
    }
    // Version COMPACTE (-Pcompact=true) : code et ressources réduits par R8, signée avec la clé de debug pour pouvoir s'installer par-dessus les versions de test.
    // Sert à obtenir un fichier léger (moins de 10 Mo) quand le téléchargement du fichier habituel (16 Mo) échoue. Par défaut, rien ne change.
    buildTypes {
        release {
            if (project.findProperty("compact") == "true") {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                signingConfig = signingConfigs.getByName("debug")
            } else {
                isMinifyEnabled = false
                // Livraison à la main : -PsignerAvecCleLocale=true signe le build « release » (non débogable) avec la clé de l'environnement pour qu'il s'installe
                // directement ; aucune clé n'est dans le dépôt, et sans l'option rien ne change.
                // Clé permanente si disponible ; sinon clé locale de l'environnement avec l'option ci-dessus
                if (signaturePermanente) signingConfig = signingConfigs.getByName("permanente")
                else if (project.findProperty("signerAvecCleLocale") == "true") signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
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
    // Empreinte digitale (verrou de l'application et code secret mémorisé)
    implementation("androidx.biometric:biometric:1.1.0")
    // CORRECTIF DU PLANTAGE DU VPN (1.42) : « biometric 1.1.0 » amène un très vieux composant « fragment » (1.2.x), dont l'activité refuse les codes de demande
    // des fenêtres système modernes (« Can only use lower 16 bits for requestCode »). Chaque rememberLauncherForActivityResult (VPN, autorisations, fichier, dictée) plantait.
    // On impose un composant récent.
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
