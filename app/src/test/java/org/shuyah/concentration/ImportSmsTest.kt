package org.shuyah.concentration

import org.junit.Assert.*
import org.junit.Test

/** Tests de la lecture d'une sauvegarde de SMS (format « SMS Backup & Restore ») : SMS reçus seulement, entités XML, balises sur plusieurs lignes. */
class ImportSmsTest {
    private val xml = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<smses count="4">
  <sms protocol="0" address="OrangeMoney" date="1794573540000" type="1" body="Paiement de 2,150.00 FCFA a ALIMENTATION &amp; CIE a ete effectue avec succes. Trans id: MP261113.1339.95787510&#10;Solde: 6403 FCFA" read="1" />
  <sms protocol="0" address="+22670112233" date="1794573600000" type="2" body="Envoyé par moi : 5000 FCFA" />
  <sms protocol="0" address="MOOV"
       date="1794573700000" type="1" body="Vous avez re&#xE7;u 25.000 F CFA de OUEDRAOGO Awa. Solde: 40 000 F CFA" />
  <sms protocol="0" address="AMI" date="1794573800000" type="1" body="On se voit à 18 h ? &quot;ok&quot;" />
</smses>"""

    @Test fun seulsLesSmsRecusSontLus() {
        val lus = ArrayList<ImportSms.Sms>()
        ImportSms.lireXml(xml.byteInputStream(), { lus += it })
        assertEquals(listOf("OrangeMoney", "MOOV", "AMI"), lus.map { it.adresse })                    // le SMS envoyé (type 2) est ignoré
        assertEquals(1794573540000L, lus[0].dateMs)
    }

    @Test fun entitesXmlEtBaliseSurPlusieursLignes() {
        val lus = ArrayList<ImportSms.Sms>()
        ImportSms.lireXml(xml.byteInputStream(), { lus += it })
        assertTrue(lus[0].corps.contains("ALIMENTATION & CIE") && lus[0].corps.contains("\nSolde: 6403"))   // &amp; et &#10; décodés
        assertTrue(lus[1].corps.startsWith("Vous avez reçu"))                                           // &#xE7; décodé ; balise écrite sur deux lignes
        assertEquals("On se voit à 18 h ? \"ok\"", lus[2].corps)
    }

    @Test fun lesPaiementsSontReconnusDansLeFichier() {
        val paiements = ArrayList<Paiements.Paiement>()
        ImportSms.lireXml(xml.byteInputStream(), { s -> Paiements.analyser(s.adresse, s.corps, s.dateMs)?.let { paiements += it } })
        assertEquals(2, paiements.size)                                                                  // le SMS « On se voit… » n'est pas un paiement
        assertEquals(2150L, paiements[0].montant); assertEquals("DEPENSE", paiements[0].genre)
        assertEquals(25000L, paiements[1].montant); assertEquals("RECETTE", paiements[1].genre)
    }

    @Test fun fichierVideOuSansSms() {
        val lus = ArrayList<ImportSms.Sms>()
        ImportSms.lireXml("<smses count=\"0\"></smses>".byteInputStream(), { lus += it })
        ImportSms.lireXml("n'importe quoi".byteInputStream(), { lus += it })
        assertTrue(lus.isEmpty())
    }
}
