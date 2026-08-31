package com.volcanoescape.app.ui.navigation

sealed class Destination(val route: String) {
    data object Splash : Destination("splash")
    data object VolcanoList : Destination("volcano_list")
    data object Monitoring : Destination("monitoring/{volcanoId}") {
        fun createRoute(volcanoId: String) = "monitoring/$volcanoId"
    }
    data object EscapeRoute : Destination("escape_route/{volcanoId}") {
        fun createRoute(volcanoId: String) = "escape_route/$volcanoId"
    }
}
