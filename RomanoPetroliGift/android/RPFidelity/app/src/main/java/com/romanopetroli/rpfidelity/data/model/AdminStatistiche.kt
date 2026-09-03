package com.romanopetroli.rpfidelity.data.model

import org.json.JSONObject

data class RiscattoCatalogo(
    val nome: String,
    val costoPunti: Int,
    val totale: Int
) {
    companion object {
        fun fromJson(json: JSONObject) = RiscattoCatalogo(
            nome = json.optString("nome"),
            costoPunti = json.optInt("costo_punti"),
            totale = json.optInt("totale")
        )
    }
}

data class RegistrazioneMese(
    val mese: String,
    val totale: Int
) {
    companion object {
        fun fromJson(json: JSONObject) = RegistrazioneMese(
            mese = json.optString("mese"),
            totale = json.optInt("totale")
        )
    }
}

data class AdminStatistiche(
    val contaClienti: Int,
    val puntiInCircolazione: Double,
    val voucherRiscattati: Int,
    val voucherUsati: Int,
    val riscattiPerCatalogo: List<RiscattoCatalogo>,
    val registrazioniPerMese: List<RegistrazioneMese>
) {
    companion object {
        fun fromJson(json: JSONObject): AdminStatistiche {
            val riscattiArray = json.optJSONArray("riscatti_per_catalogo")
            val registrazioniArray = json.optJSONArray("registrazioni_per_mese")
            return AdminStatistiche(
                contaClienti = json.optInt("conta_clienti"),
                puntiInCircolazione = json.optDouble("punti_in_circolazione", 0.0),
                voucherRiscattati = json.optInt("voucher_riscattati"),
                voucherUsati = json.optInt("voucher_usati"),
                riscattiPerCatalogo = (0 until (riscattiArray?.length() ?: 0)).map {
                    RiscattoCatalogo.fromJson(riscattiArray!!.getJSONObject(it))
                },
                registrazioniPerMese = (0 until (registrazioniArray?.length() ?: 0)).map {
                    RegistrazioneMese.fromJson(registrazioniArray!!.getJSONObject(it))
                }
            )
        }
    }
}
