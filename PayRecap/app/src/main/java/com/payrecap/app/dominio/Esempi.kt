// PayRecap — © Roberto Di Flumeri
package com.payrecap.app.dominio

/**
 * Cedolini di esempio (dati fittizi) per provare l'app senza caricare un documento reale.
 * Passano dallo stesso parser usato per i PDF e le foto.
 */
object Esempi {

    val OTTOBRE_2026 = """
        ACME SERVIZI S.R.L.                         CEDOLINO PAGA
        Ragione sociale: Acme Servizi S.r.l.   P.IVA 01234567890
        Periodo di retribuzione: Ottobre 2026
        Dipendente: Mario Rossi        Matricola 000123
        Qualifica: Impiegato   Livello: 7   Tipo rapporto: Tempo indeterminato
        CCNL: Commercio Terziario Distribuzione e Servizi
        Data assunzione 01/03/2019
        VOCI                                   ORE/GG   BASE      COMPETENZE   TRATTENUTE
        Retribuzione ordinaria                 168,00              3.050,00
        Assenza non retribuita                 16,00               -290,48
        Straordinario feriale 25%              6,00     22,6800    136,08
        Contributo IVS 9,19%                                                    266,10
        IRPEF netta                                                             398,20
        Addizionale regionale rata 10/11                                         33,40
        Addizionale comunale saldo                                               10,00
        Acconto add. comunale                                                    5,00
        Imponibile fiscale                                       2.629,50
        TOTALE COMPETENZE        TOTALE TRATTENUTE       NETTO IN BUSTA
        2.895,60                 712,70                  2.182,90
        Ferie e permessi     Res. AP    Maturato    Goduto    Saldo
        Ferie (gg)             4,00      18,00       2,00      20,00
        ROL (ore)             10,00      48,00      12,00      46,00
        Banca ore residua 8,00
    """.trimIndent()

    val NOVEMBRE_2026 = """
        ACME SERVIZI S.R.L.                         CEDOLINO PAGA
        Ragione sociale: Acme Servizi S.r.l.   P.IVA 01234567890
        Periodo di retribuzione: Novembre 2026
        Dipendente: Mario Rossi        Matricola 000123
        Qualifica: Impiegato   Livello: 7   Tipo rapporto: Tempo indeterminato
        CCNL: Commercio Terziario Distribuzione e Servizi
        Data assunzione 01/03/2019
        VOCI                                   ORE/GG   BASE      COMPETENZE   TRATTENUTE
        Retribuzione ordinaria                 168,00              3.050,00
        Straordinario feriale 25%              8,00     22,6800    181,44
        Straordinario festivo 50%              2,00     27,2200     18,56
        Ore accantonate banca ore 2,00
        Contributo IVS 9,18%                                                    298,35
        IRPEF netta                                                             498,57
        Addizionale regionale rata 11/11                                         33,40
        Addizionale comunale saldo                                               10,00
        Acconto add. comunale                                                    5,00
        Imponibile fiscale                                       2.951,65
        TOTALE COMPETENZE        TOTALE TRATTENUTE       NETTO IN BUSTA
        3.250,00                 845,32                  2.404,68
        Ferie e permessi     Res. AP    Maturato    Goduto    Saldo
        Ferie (gg)             4,00      20,00       6,00      18,00
        ROL (ore)             10,00      52,00      14,00      48,00
        Banca ore residua 10,00
    """.trimIndent()
}
