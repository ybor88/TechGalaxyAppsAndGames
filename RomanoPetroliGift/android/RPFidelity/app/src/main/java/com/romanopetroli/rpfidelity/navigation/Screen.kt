package com.romanopetroli.rpfidelity.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Register : Screen("register")
    object Dashboard : Screen("dashboard")
    object LaMiaCard : Screen("la_mia_card")
    object Rifornimenti : Screen("rifornimenti")
    object Voucher : Screen("voucher")
    object AdminRegistraRifornimento : Screen("admin_registra_rifornimento")
    object AdminReports : Screen("admin_reports")
    object AdminVerificaVoucher : Screen("admin_verifica_voucher")
    object Contatti : Screen("contatti")
    object Impostazioni : Screen("impostazioni")
    object Faq : Screen("faq")
    object AdminStatistiche : Screen("admin_statistiche")
    object AdminClienti : Screen("admin_clienti")
    object AdminClienteDettaglio : Screen("admin_cliente_dettaglio")
}
