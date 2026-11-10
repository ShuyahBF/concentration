package org.shuyah.concentration

import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Règle 4 du propriétaire : toute application déclare sa présence à SAWALI au démarrage puis toutes les 5 minutes, en arrière-plan,
 * jamais bloquant, sans donnée personnelle (application, version, date de compilation, modèle du téléphone).
 */
object Presence {
    private val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
    private var lancee = false

    fun demarrer(portee: CoroutineScope) {
        if (lancee) return
        lancee = true
        portee.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val corps = """{"application":"Concentration (Android)","version":"${Version.VERSION}","deploye_le":"${BuildConfig.DATE_COMPILATION}","machine":"${Build.MODEL}","systeme":"Android ${Build.VERSION.RELEASE}"}"""
                    http.newCall(Request.Builder().url("https://api.sawalismartsystems.com/api/presence-logiciel")
                        .post(corps.toRequestBody("application/json; charset=utf-8".toMediaType())).build()).execute().close()
                } catch (e: Exception) { /* SAWALI injoignable : nouvel essai dans 5 minutes, sans rien afficher */ }
                delay(5 * 60 * 1000L)
            }
        }
    }
}
