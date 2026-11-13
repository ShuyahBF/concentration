package org.shuyah.concentration

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * BASE DE DONNÉES LOCALE de Concentration (SQLite, sur le téléphone ; rien n'est envoyé ailleurs) :
 *  - `mesures` : le volume Internet restant relevé à chaque vérification, par carte SIM (sert au graphique de consommation) ;
 *  - `paiements` : les SMS de paiement lus dans la boîte de réception (dépense ou recette, montant, tiers, solde).
 *
 * Pour un développeur WinDev : c'est l'équivalent d'un fichier HFSQL local à deux tables, sans serveur. `UNIQUE(adresse, recuLe, corps)` empêche d'enregistrer
 * deux fois le même SMS quand la boîte est relue.
 */
class Historique private constructor(c: Context) : SQLiteOpenHelper(c.applicationContext, "concentration.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE mesures (id INTEGER PRIMARY KEY AUTOINCREMENT, emplacement INTEGER NOT NULL, operateur TEXT NOT NULL, ms INTEGER NOT NULL, restantMo REAL NOT NULL)")
        db.execSQL("CREATE INDEX idx_mesures ON mesures(emplacement, ms)")
        db.execSQL("CREATE TABLE paiements (id INTEGER PRIMARY KEY AUTOINCREMENT, adresse TEXT NOT NULL, recuLe INTEGER NOT NULL, genre TEXT NOT NULL, montant INTEGER NOT NULL, tiers TEXT NOT NULL, solde INTEGER, corps TEXT NOT NULL, reference TEXT, UNIQUE(adresse, recuLe, corps))")
        db.execSQL("CREATE INDEX idx_paiements ON paiements(recuLe)")
        db.execSQL("CREATE UNIQUE INDEX idx_paiements_ref ON paiements(reference) WHERE reference IS NOT NULL")
    }
    /**
     * Version 1 → 2 : colonne `reference` (numéro de transaction) et RELECTURE des paiements déjà enregistrés avec la lecture corrigée des montants
     * (« 2,150.00 FCFA » avait été lu 150), du bénéficiaire et de la date du numéro de transaction. Les données existantes sont conservées.
     */
    override fun onUpgrade(db: SQLiteDatabase, ancienne: Int, nouvelle: Int) {
        if (ancienne < 2) {
            db.execSQL("ALTER TABLE paiements ADD COLUMN reference TEXT")
            val lignes = mutableListOf<Triple<Long, String, Long>>()
            db.rawQuery("SELECT id, adresse, recuLe, corps FROM paiements", null).use { q -> val tmp = mutableListOf<Array<Any>>(); while (q.moveToNext()) tmp += arrayOf(q.getLong(0), q.getString(1), q.getLong(2), q.getString(3)); tmp.forEach { t ->
                val p = Paiements.analyser(t[1] as String, t[3] as String, t[2] as Long)
                try {
                    if (p == null) db.delete("paiements", "id = ?", arrayOf(t[0].toString()))
                    else db.update("paiements", ContentValues().apply { put("recuLe", p.recuLeMs); put("genre", p.genre); put("montant", p.montant); put("tiers", p.tiers); put("solde", p.soldeApres); put("reference", p.reference) }, "id = ?", arrayOf(t[0].toString()))
                } catch (e: Exception) { /* doublon créé par la relecture : la ligne d'origine est conservée telle quelle */ }
            } }
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_paiements_ref ON paiements(reference) WHERE reference IS NOT NULL")
        }
    }

    companion object {
        @Volatile private var instance: Historique? = null
        fun de(c: Context): Historique = instance ?: synchronized(this) { instance ?: Historique(c).also { instance = it } }
    }

    // ---- Mesures du forfait Internet ----
    /** Enregistre un volume restant. Deux mesures identiques à moins d'une minute d'écart (double lecture : USSD puis SMS) ne sont gardées qu'une fois. */
    @Synchronized fun ajouterMesure(emplacement: Int, operateur: String, ms: Long, restantMo: Double) {
        readableDatabase.rawQuery("SELECT ms, restantMo FROM mesures WHERE emplacement = ? ORDER BY ms DESC LIMIT 1", arrayOf(emplacement.toString())).use {
            if (it.moveToFirst() && ms - it.getLong(0) in 0..60_000 && Math.abs(it.getDouble(1) - restantMo) < 0.5) return
        }
        writableDatabase.insert("mesures", null, ContentValues().apply { put("emplacement", emplacement); put("operateur", operateur); put("ms", ms); put("restantMo", restantMo) })
    }

    /** Mesures depuis cet instant (toutes les cartes), de la plus ancienne à la plus récente. */
    @Synchronized fun mesures(depuisMs: Long): List<Consommation.Mesure> =
        readableDatabase.rawQuery("SELECT emplacement, ms, restantMo FROM mesures WHERE ms >= ? ORDER BY ms", arrayOf(depuisMs.toString())).use { q ->
            buildList { while (q.moveToNext()) add(Consommation.Mesure(q.getInt(0), q.getLong(1), q.getDouble(2))) }
        }

    /** Nom d'opérateur de la dernière mesure de cet emplacement (libellé du graphique). */
    @Synchronized fun operateurDe(emplacement: Int): String =
        readableDatabase.rawQuery("SELECT operateur FROM mesures WHERE emplacement = ? ORDER BY ms DESC LIMIT 1", arrayOf(emplacement.toString())).use { if (it.moveToFirst()) it.getString(0) else "" }

    // ---- Paiements lus dans les SMS ----
    /** Ajoute un paiement ; faux si ce SMS est déjà enregistré. */
    @Synchronized fun ajouterPaiement(p: Paiements.Paiement): Boolean =
        writableDatabase.insertWithOnConflict("paiements", null, ContentValues().apply {
            put("adresse", p.adresse); put("recuLe", p.recuLeMs); put("genre", p.genre); put("montant", p.montant); put("tiers", p.tiers); put("solde", p.soldeApres); put("corps", p.corps); put("reference", p.reference)
        }, SQLiteDatabase.CONFLICT_IGNORE) != -1L

    /** Paiements depuis cet instant, du plus récent au plus ancien. */
    @Synchronized fun paiements(depuisMs: Long): List<Paiements.Paiement> =
        readableDatabase.rawQuery("SELECT adresse, recuLe, genre, montant, tiers, solde, corps, reference FROM paiements WHERE recuLe >= ? ORDER BY recuLe DESC", arrayOf(depuisMs.toString())).use { q ->
            buildList { while (q.moveToNext()) add(Paiements.Paiement(q.getString(0), q.getLong(1), q.getString(2), q.getLong(3), q.getString(4), if (q.isNull(5)) null else q.getLong(5), q.getString(6), q.getString(7))) }
        }

    /** Nombre de paiements enregistrés (tous). */
    @Synchronized fun nombrePaiements(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM paiements", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
}
