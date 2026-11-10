package org.shuyah.concentration

/** SOURCE UNIQUE de la version (règles 1 et 2) : VERSION +1 à chaque livraison (et versionName dans app/build.gradle.kts). */
object Version {
    const val VERSION = "1.0"
    /** « Version 1.0 · déployée le 10/11/2026 23:10 » (date de compilation injectée par Gradle). */
    fun libelle(): String = "Version $VERSION · déployée le ${BuildConfig.DATE_COMPILATION}"
}
