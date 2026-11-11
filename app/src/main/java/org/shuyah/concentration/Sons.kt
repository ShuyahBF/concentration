package org.shuyah.concentration

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * SONS DE CHANGEMENT D'ÉTAT. Quatre petites mélodies, toutes différentes, synthétisées par l'application (aucun fichier son) :
 *  - DÉBUT de concentration : trois notes qui descendent (on se pose) ;
 *  - FIN de concentration : quatre notes qui montent (on est libéré) ;
 *  - PAUSE : deux notes identiques ;
 *  - REPRISE après une pause : deux notes qui montent vite.
 * Le son suit le volume « notifications » du téléphone (muet en mode silencieux) et se coupe avec l'interrupteur « Son ».
 */
object Sons {
    enum class Signal(val notes: List<Pair<Int, Int>>) {   // (fréquence en Hz, durée en ms) ; 0 Hz = silence
        DEBUT(listOf(659 to 170, 523 to 170, 392 to 320)),
        FIN(listOf(392 to 120, 523 to 120, 659 to 120, 784 to 300)),
        PAUSE(listOf(440 to 150, 0 to 80, 440 to 150)),
        REPRISE(listOf(523 to 100, 659 to 220))
    }

    /** Compare l'état actuel au dernier état connu et émet le son qui correspond au changement (sans son au tout premier contrôle). */
    fun controler(c: Context) {
        val actuel = when {
            Reglages.minutesRestantes(c) != null -> "active"
            Reglages.enPause(c) && Reglages.minutesRestantesPlage(c) != null -> "pause"
            else -> "libre"
        }
        val avant = Reglages.dernierEtat(c)
        if (avant == actuel) return
        Reglages.dernierEtat(c, actuel)
        if (avant == null || !Reglages.son(c)) return
        jouer(signalDe(avant, actuel) ?: return)
    }

    /** Quel son pour quel changement d'état (logique pure, testée). */
    fun signalDe(avant: String, apres: String): Signal? = when {
        avant == "libre" && apres == "active" -> Signal.DEBUT
        avant == "pause" && apres == "active" -> Signal.REPRISE
        avant == "active" && apres == "pause" -> Signal.PAUSE
        (avant == "active" || avant == "pause") && apres == "libre" -> Signal.FIN
        else -> null
    }

    /** Joue le signal dans un fil séparé (ne bloque jamais l'appelant). */
    fun jouer(s: Signal) {
        Thread {
            try {
                val taux = 22050
                val donnees = ArrayList<Short>()
                for ((hz, ms) in s.notes) {
                    val n = taux * ms / 1000
                    for (i in 0 until n) {
                        // sinusoïde avec montée et descente douces (évite les claquements)
                        val enveloppe = minOf(1.0, minOf(i, n - i) / (taux * 0.012))
                        donnees.add(if (hz == 0) 0 else (sin(2 * PI * hz * i / taux) * enveloppe * 0.6 * Short.MAX_VALUE).toInt().toShort())
                    }
                }
                val pcm = donnees.toShortArray()
                val piste = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(taux).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.size * 2).setTransferMode(AudioTrack.MODE_STATIC).build()
                piste.write(pcm, 0, pcm.size)
                piste.play()
                Thread.sleep(s.notes.sumOf { it.second }.toLong() + 250)
                piste.release()
            } catch (e: Exception) { /* pas de sortie audio : sans conséquence */ }
        }.also { it.isDaemon = false }.start()
    }
}
