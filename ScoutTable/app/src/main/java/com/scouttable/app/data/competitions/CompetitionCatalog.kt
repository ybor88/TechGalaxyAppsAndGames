// Copyright © Roberto Di Flumeri
package com.scouttable.app.data.competitions

import com.scouttable.app.data.Sport

/** Raggruppamento delle card in "Detentori", nell'ordine in cui vengono mostrate. */
enum class CompetitionCategory(val label: String) {
    NAZIONALI("Nazionali"),
    CLUB_INTERNAZIONALI("Coppe internazionali per club"),
    CAMPIONATI("Campionati"),
    COPPE_NAZIONALI("Coppe nazionali"),
}

/**
 * Una competizione della sezione "Detentori": [label] è il nome mostrato (in italiano), [wikiTitle] la voce
 * di en.wikipedia.org da cui si leggono squadra più titolata, detentore e logo (vedi
 * [WikipediaCompetitionStats]). La voce inglese e non quella italiana perché il suo infobox ha
 * campi dedicati ("most successful club", "current champions") con il numero di titoli, presenti
 * in modo uniforme su quasi tutte le competizioni.
 */
data class CompetitionDef(
    val sport: Sport,
    val category: CompetitionCategory,
    val label: String,
    val wikiTitle: String,
    /** true se non è nell'elenco fisso ma è stata trovata su Wikipedia premendo "Aggiorna" (es. una
     *  competizione nata dopo l'ultima versione dell'app): vedi [CompetitionRepository.refreshAll]. */
    val discovered: Boolean = false,
) {
    val id: String get() = wikiTitle

    /** true per le competizioni femminili (filtro Maschile/Femminile in "Detentori"). */
    val isWomen: Boolean get() = womenRegex.containsMatchIn("$label $wikiTitle")
}

private val womenRegex = Regex("""women|femminil|\bWNBA\b|ladies|frauen|féminin|feminin|femenin""", RegexOption.IGNORE_CASE)

/**
 * Competizioni professionistiche di calcio e basket, nazionali e internazionali, maschili e
 * femminili. Elenco scelto a mano (Wikipedia non ha un indice affidabile e leggibile da app di
 * "tutte le competizioni"): ogni voce è stata verificata su en.wikipedia.org e ha nell'infobox la
 * squadra più titolata e/o il detentore. Escluse le voci senza questi campi nell'infobox iniziale
 * (es. Major League Soccer, la Pro League belga e i tornei olimpici di calcio e basket).
 *
 * È solo la base, con i nomi in italiano e le categorie già assegnate: "Aggiorna" aggiunge da solo
 * tutte le altre competizioni attive che trova su Wikipedia (vedi
 * [WikipediaCompetitionStats.discoverTitles]), comprese quelle nuove.
 */
object CompetitionCatalog {

    private fun calcio(category: CompetitionCategory, label: String, wikiTitle: String) =
        CompetitionDef(Sport.CALCIO, category, label, wikiTitle)

    private fun basket(category: CompetitionCategory, label: String, wikiTitle: String) =
        CompetitionDef(Sport.BASKET, category, label, wikiTitle)

    private val all: List<CompetitionDef> = listOf(
        // ---- Calcio: nazionali ----
        calcio(CompetitionCategory.NAZIONALI, "Mondiali FIFA", "FIFA World Cup"),
        calcio(CompetitionCategory.NAZIONALI, "Europei UEFA", "UEFA European Championship"),
        calcio(CompetitionCategory.NAZIONALI, "Copa América", "Copa América"),
        calcio(CompetitionCategory.NAZIONALI, "Coppa d'Africa", "Africa Cup of Nations"),
        calcio(CompetitionCategory.NAZIONALI, "Coppa d'Asia", "AFC Asian Cup"),
        calcio(CompetitionCategory.NAZIONALI, "CONCACAF Gold Cup", "CONCACAF Gold Cup"),
        calcio(CompetitionCategory.NAZIONALI, "UEFA Nations League", "UEFA Nations League"),
        calcio(CompetitionCategory.NAZIONALI, "Confederations Cup", "FIFA Confederations Cup"),
        calcio(CompetitionCategory.NAZIONALI, "Mondiali femminili", "FIFA Women's World Cup"),
        calcio(CompetitionCategory.NAZIONALI, "Europei femminili", "UEFA Women's Championship"),
        // ---- Calcio: coppe internazionali per club ----
        calcio(CompetitionCategory.CLUB_INTERNAZIONALI, "Champions League", "UEFA Champions League"),
        calcio(CompetitionCategory.CLUB_INTERNAZIONALI, "Europa League", "UEFA Europa League"),
        calcio(CompetitionCategory.CLUB_INTERNAZIONALI, "Conference League", "UEFA Conference League"),
        calcio(CompetitionCategory.CLUB_INTERNAZIONALI, "Supercoppa UEFA", "UEFA Super Cup"),
        calcio(CompetitionCategory.CLUB_INTERNAZIONALI, "Mondiale per club FIFA", "FIFA Club World Cup"),
        calcio(CompetitionCategory.CLUB_INTERNAZIONALI, "Coppa Intercontinentale FIFA", "FIFA Intercontinental Cup"),
        calcio(CompetitionCategory.CLUB_INTERNAZIONALI, "Copa Libertadores", "Copa Libertadores"),
        calcio(CompetitionCategory.CLUB_INTERNAZIONALI, "Copa Sudamericana", "Copa Sudamericana"),
        calcio(CompetitionCategory.CLUB_INTERNAZIONALI, "CAF Champions League", "CAF Champions League"),
        calcio(CompetitionCategory.CLUB_INTERNAZIONALI, "AFC Champions League Elite", "AFC Champions League Elite"),
        calcio(CompetitionCategory.CLUB_INTERNAZIONALI, "CONCACAF Champions Cup", "CONCACAF Champions Cup"),
        calcio(CompetitionCategory.CLUB_INTERNAZIONALI, "Champions League femminile", "UEFA Women's Champions League"),
        // ---- Calcio: campionati ----
        calcio(CompetitionCategory.CAMPIONATI, "Serie A", "Serie A"),
        calcio(CompetitionCategory.CAMPIONATI, "Serie B", "Serie B"),
        calcio(CompetitionCategory.CAMPIONATI, "Premier League", "Premier League"),
        calcio(CompetitionCategory.CAMPIONATI, "Championship (Inghilterra)", "EFL Championship"),
        calcio(CompetitionCategory.CAMPIONATI, "LaLiga", "La Liga"),
        calcio(CompetitionCategory.CAMPIONATI, "Bundesliga", "Bundesliga"),
        calcio(CompetitionCategory.CAMPIONATI, "Ligue 1", "Ligue 1"),
        calcio(CompetitionCategory.CAMPIONATI, "Eredivisie", "Eredivisie"),
        calcio(CompetitionCategory.CAMPIONATI, "Primeira Liga", "Primeira Liga"),
        calcio(CompetitionCategory.CAMPIONATI, "Süper Lig", "Süper Lig"),
        calcio(CompetitionCategory.CAMPIONATI, "Scottish Premiership", "Scottish Premiership"),
        calcio(CompetitionCategory.CAMPIONATI, "Super League (Svizzera)", "Swiss Super League"),
        calcio(CompetitionCategory.CAMPIONATI, "Bundesliga austriaca", "Austrian Football Bundesliga"),
        calcio(CompetitionCategory.CAMPIONATI, "Superliga danese", "Danish Superliga"),
        calcio(CompetitionCategory.CAMPIONATI, "Eliteserien (Norvegia)", "Eliteserien"),
        calcio(CompetitionCategory.CAMPIONATI, "Allsvenskan (Svezia)", "Allsvenskan"),
        calcio(CompetitionCategory.CAMPIONATI, "Super League (Grecia)", "Super League Greece"),
        calcio(CompetitionCategory.CAMPIONATI, "Prem'er-Liga (Russia)", "Russian Premier League"),
        calcio(CompetitionCategory.CAMPIONATI, "Premier League ucraina", "Ukrainian Premier League"),
        calcio(CompetitionCategory.CAMPIONATI, "HNL (Croazia)", "Croatian Football League"),
        calcio(CompetitionCategory.CAMPIONATI, "SuperLiga serba", "Serbian SuperLiga"),
        calcio(CompetitionCategory.CAMPIONATI, "Prima lega ceca", "Czech First League"),
        calcio(CompetitionCategory.CAMPIONATI, "Ekstraklasa (Polonia)", "Ekstraklasa"),
        calcio(CompetitionCategory.CAMPIONATI, "Brasileirão", "Campeonato Brasileiro Série A"),
        calcio(CompetitionCategory.CAMPIONATI, "Primera División (Argentina)", "Argentine Primera División"),
        calcio(CompetitionCategory.CAMPIONATI, "Liga MX", "Liga MX"),
        calcio(CompetitionCategory.CAMPIONATI, "Saudi Pro League", "Saudi Pro League"),
        calcio(CompetitionCategory.CAMPIONATI, "J1 League (Giappone)", "J1 League"),
        calcio(CompetitionCategory.CAMPIONATI, "K League 1 (Corea del Sud)", "K League 1"),
        calcio(CompetitionCategory.CAMPIONATI, "A-League (Australia)", "A-League Men"),
        calcio(CompetitionCategory.CAMPIONATI, "Premier League egiziana", "Egyptian Premier League"),
        calcio(CompetitionCategory.CAMPIONATI, "Chinese Super League", "Chinese Super League"),
        calcio(CompetitionCategory.CAMPIONATI, "Serie A femminile", "Serie A (women's football)"),
        calcio(CompetitionCategory.CAMPIONATI, "Women's Super League", "Women's Super League"),
        // ---- Calcio: coppe nazionali ----
        calcio(CompetitionCategory.COPPE_NAZIONALI, "Coppa Italia", "Coppa Italia"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "Supercoppa italiana", "Supercoppa Italiana"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "FA Cup", "FA Cup"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "EFL Cup", "EFL Cup"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "Community Shield", "FA Community Shield"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "Copa del Rey", "Copa del Rey"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "Supercopa de España", "Supercopa de España"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "DFB-Pokal", "DFB-Pokal"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "Coupe de France", "Coupe de France"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "KNVB beker (Paesi Bassi)", "KNVB Cup"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "Taça de Portugal", "Taça de Portugal"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "Scottish Cup", "Scottish Cup"),
        // ---- Calcio femminile (tutte verificate su en.wikipedia con la squadra più titolata nell'infobox) ----
        calcio(CompetitionCategory.NAZIONALI, "Copa América femminile", "Copa América Femenina"),
        calcio(CompetitionCategory.NAZIONALI, "Coppa d'Africa femminile", "Women's Africa Cup of Nations"),
        calcio(CompetitionCategory.NAZIONALI, "Coppa d'Asia femminile", "AFC Women's Asian Cup"),
        calcio(CompetitionCategory.NAZIONALI, "CONCACAF W Championship (femminile)", "CONCACAF W Championship"),
        calcio(CompetitionCategory.CLUB_INTERNAZIONALI, "Copa Libertadores femminile", "Copa Libertadores Femenina"),
        calcio(CompetitionCategory.CLUB_INTERNAZIONALI, "CAF Champions League femminile", "CAF Women's Champions League"),
        calcio(CompetitionCategory.CLUB_INTERNAZIONALI, "AFC Champions League femminile", "AFC Women's Champions League"),
        calcio(CompetitionCategory.CLUB_INTERNAZIONALI, "Coppa dei Campioni FIFA femminile", "FIFA Women's Champions Cup"),
        calcio(CompetitionCategory.CAMPIONATI, "Frauen-Bundesliga femminile (Germania)", "Frauen-Bundesliga"),
        calcio(CompetitionCategory.CAMPIONATI, "Liga F femminile (Spagna)", "Liga F"),
        calcio(CompetitionCategory.CAMPIONATI, "Première Ligue femminile (Francia)", "Première Ligue"),
        calcio(CompetitionCategory.CAMPIONATI, "NWSL femminile (Stati Uniti)", "National Women's Soccer League"),
        calcio(CompetitionCategory.CAMPIONATI, "Damallsvenskan femminile (Svezia)", "Damallsvenskan"),
        calcio(CompetitionCategory.CAMPIONATI, "Toppserien femminile (Norvegia)", "Toppserien"),
        calcio(CompetitionCategory.CAMPIONATI, "Vrouwen Eredivisie femminile (Paesi Bassi)", "Vrouwen Eredivisie"),
        calcio(CompetitionCategory.CAMPIONATI, "A-Liga femminile (Danimarca)", "A-Liga"),
        calcio(CompetitionCategory.CAMPIONATI, "Super League femminile (Svizzera)", "Swiss Women's Super League"),
        calcio(CompetitionCategory.CAMPIONATI, "Campeonato Nacional femminile (Portogallo)", "Campeonato Nacional Feminino"),
        calcio(CompetitionCategory.CAMPIONATI, "Scottish Women's Premier League", "Scottish Women's Premier League"),
        calcio(CompetitionCategory.CAMPIONATI, "Campionato russo femminile", "Russian Women's Football Championship"),
        calcio(CompetitionCategory.CAMPIONATI, "Higher League femminile (Ucraina)", "Ukrainian Women's Higher League"),
        calcio(CompetitionCategory.CAMPIONATI, "Super League femminile (Turchia)", "Turkish Women's Football Super League"),
        calcio(CompetitionCategory.CAMPIONATI, "Ekstraliga femminile (Polonia)", "Ekstraliga (women's football)"),
        calcio(CompetitionCategory.CAMPIONATI, "First League femminile (Rep. Ceca)", "Czech Women's First League"),
        calcio(CompetitionCategory.CAMPIONATI, "Kansallinen Liiga femminile (Finlandia)", "Kansallinen Liiga"),
        calcio(CompetitionCategory.CAMPIONATI, "Women's Premier Division (Irlanda)", "League of Ireland Women's Premier Division"),
        calcio(CompetitionCategory.CAMPIONATI, "Brasileirão femminile", "Campeonato Brasileiro de Futebol Feminino Série A1"),
        calcio(CompetitionCategory.CAMPIONATI, "Liga MX Femenil (Messico)", "Liga Femenil"),
        calcio(CompetitionCategory.CAMPIONATI, "A-League Women (Australia)", "A-League Women"),
        calcio(CompetitionCategory.CAMPIONATI, "WE League femminile (Giappone)", "WE League"),
        calcio(CompetitionCategory.CAMPIONATI, "WK League femminile (Corea del Sud)", "WK League"),
        calcio(CompetitionCategory.CAMPIONATI, "Super League femminile (Cina)", "Chinese Women's Super League"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "Coppa Italia femminile", "Coppa Italia (women)"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "Supercoppa italiana femminile", "Supercoppa Italiana (women)"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "Women's FA Cup", "Women's FA Cup"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "Women's League Cup", "WSL Players Cup"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "Copa de la Reina femminile (Spagna)", "Copa de la Reina de Fútbol"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "DFB-Pokal femminile", "DFB-Pokal Frauen"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "Coupe de France femminile", "Coupe de France Féminine"),
        calcio(CompetitionCategory.COPPE_NAZIONALI, "FAI Women's Cup (Irlanda)", "FAI Women's Cup"),

        // ---- Basket: nazionali ----
        basket(CompetitionCategory.NAZIONALI, "Mondiali FIBA", "FIBA Basketball World Cup"),
        basket(CompetitionCategory.NAZIONALI, "EuroBasket", "EuroBasket"),
        basket(CompetitionCategory.NAZIONALI, "FIBA AmeriCup", "FIBA AmeriCup"),
        basket(CompetitionCategory.NAZIONALI, "FIBA Asia Cup", "FIBA Asia Cup"),
        basket(CompetitionCategory.NAZIONALI, "AfroBasket", "AfroBasket"),
        basket(CompetitionCategory.NAZIONALI, "Mondiali femminili FIBA", "FIBA Women's Basketball World Cup"),
        basket(CompetitionCategory.NAZIONALI, "EuroBasket femminile", "EuroBasket Women"),
        // ---- Basket: coppe internazionali per club ----
        basket(CompetitionCategory.CLUB_INTERNAZIONALI, "EuroLeague", "EuroLeague"),
        basket(CompetitionCategory.CLUB_INTERNAZIONALI, "EuroCup", "EuroCup Basketball"),
        basket(CompetitionCategory.CLUB_INTERNAZIONALI, "Basketball Champions League", "Basketball Champions League"),
        basket(CompetitionCategory.CLUB_INTERNAZIONALI, "FIBA Europe Cup", "FIBA Europe Cup"),
        basket(CompetitionCategory.CLUB_INTERNAZIONALI, "Coppa Intercontinentale FIBA", "FIBA Intercontinental Cup"),
        basket(CompetitionCategory.CLUB_INTERNAZIONALI, "EuroLeague femminile", "EuroLeague Women"),
        basket(CompetitionCategory.CLUB_INTERNAZIONALI, "Basketball Africa League", "Basketball Africa League"),
        // ---- Basket: campionati ----
        basket(CompetitionCategory.CAMPIONATI, "NBA", "National Basketball Association"),
        basket(CompetitionCategory.CAMPIONATI, "WNBA", "Women's National Basketball Association"),
        basket(CompetitionCategory.CAMPIONATI, "Serie A (LBA)", "Lega Basket Serie A"),
        basket(CompetitionCategory.CAMPIONATI, "Liga ACB (Spagna)", "Liga ACB"),
        basket(CompetitionCategory.CAMPIONATI, "BSL (Turchia)", "Basketbol Süper Ligi"),
        basket(CompetitionCategory.CAMPIONATI, "Basket League (Grecia)", "Greek Basket League"),
        basket(CompetitionCategory.CAMPIONATI, "LNB Élite (Francia)", "LNB Élite"),
        basket(CompetitionCategory.CAMPIONATI, "BBL (Germania)", "Basketball Bundesliga"),
        basket(CompetitionCategory.CAMPIONATI, "VTB United League", "VTB United League"),
        basket(CompetitionCategory.CAMPIONATI, "Lega Adriatica (ABA)", "ABA League"),
        basket(CompetitionCategory.CAMPIONATI, "Ligat Winner (Israele)", "Israeli Basketball Premier League"),
        basket(CompetitionCategory.CAMPIONATI, "LKL (Lituania)", "Lithuanian Basketball League"),
        basket(CompetitionCategory.CAMPIONATI, "Liga Nacional (Argentina)", "Liga Nacional de Básquet"),
        basket(CompetitionCategory.CAMPIONATI, "NBB (Brasile)", "Novo Basquete Brasil"),
        basket(CompetitionCategory.CAMPIONATI, "CBA (Cina)", "Chinese Basketball Association"),
        basket(CompetitionCategory.CAMPIONATI, "NBL (Australia)", "National Basketball League (Australia)"),
        basket(CompetitionCategory.CAMPIONATI, "B.League (Giappone)", "B.League"),
        basket(CompetitionCategory.CAMPIONATI, "NCAA Division I", "NCAA Division I men's basketball tournament"),
        // ---- Basket: coppe nazionali ----
        basket(CompetitionCategory.COPPE_NAZIONALI, "Coppa Italia", "Italian Basketball Cup"),
        basket(CompetitionCategory.COPPE_NAZIONALI, "Supercoppa italiana", "Italian Basketball Supercup"),
        basket(CompetitionCategory.COPPE_NAZIONALI, "Copa del Rey", "Copa del Rey de Baloncesto"),
        basket(CompetitionCategory.COPPE_NAZIONALI, "NBA Cup", "NBA Cup"),
        // ---- Basket femminile (tutte verificate su en.wikipedia con la squadra più titolata nell'infobox) ----
        basket(CompetitionCategory.NAZIONALI, "AmeriCup femminile FIBA", "FIBA Women's AmeriCup"),
        basket(CompetitionCategory.NAZIONALI, "Asia Cup femminile FIBA", "FIBA Women's Asia Cup"),
        basket(CompetitionCategory.NAZIONALI, "AfroBasket femminile", "AfroBasket Women"),
        basket(CompetitionCategory.CLUB_INTERNAZIONALI, "EuroCup femminile", "EuroCup Women"),
        basket(CompetitionCategory.CLUB_INTERNAZIONALI, "SuperCup femminile FIBA Europe", "FIBA Europe SuperCup Women"),
        basket(CompetitionCategory.CAMPIONATI, "Serie A1 femminile (LBF)", "Lega Basket Femminile"),
        basket(CompetitionCategory.CAMPIONATI, "Liga Femenina femminile (Spagna)", "Liga Femenina de Baloncesto"),
        basket(CompetitionCategory.CAMPIONATI, "Ligue Féminine femminile (Francia)", "Ligue Féminine de Basketball"),
        basket(CompetitionCategory.CAMPIONATI, "Bundesliga femminile (Germania)", "Damen-Basketball-Bundesliga"),
        basket(CompetitionCategory.CAMPIONATI, "Super League femminile (Turchia)", "Women's Basketball Super League"),
        basket(CompetitionCategory.CAMPIONATI, "Premier League femminile (Russia)", "Russian Women's Basketball Premier League"),
        basket(CompetitionCategory.CAMPIONATI, "Basket Liga Kobiet femminile (Polonia)", "Basket Liga Kobiet"),
        basket(CompetitionCategory.CAMPIONATI, "Lega greca femminile", "Greek Women's Basketball League"),
        basket(CompetitionCategory.CAMPIONATI, "WNBL femminile (Australia)", "Women's National Basketball League"),
        basket(CompetitionCategory.CAMPIONATI, "WCBA femminile (Cina)", "Women's Chinese Basketball Association"),
        basket(CompetitionCategory.CAMPIONATI, "WKBL femminile (Corea del Sud)", "Women's Korean Basketball League"),
        basket(CompetitionCategory.CAMPIONATI, "Liga Femenina femminile (Uruguay)", "Liga Femenina de Básquetbol"),
        basket(CompetitionCategory.CAMPIONATI, "NCAA Division I femminile", "NCAA Division I women's basketball tournament"),
        basket(CompetitionCategory.CAMPIONATI, "Unrivaled femminile (Stati Uniti)", "Unrivaled (basketball)"),
        basket(CompetitionCategory.COPPE_NAZIONALI, "Copa de la Reina femminile (Spagna)", "Copa de la Reina de Baloncesto"),
        basket(CompetitionCategory.COPPE_NAZIONALI, "Coppa di Turchia femminile", "Turkish Women's Basketball Cup"),
        basket(CompetitionCategory.COPPE_NAZIONALI, "WNBA Commissioner's Cup (femminile)", "WNBA Commissioner's Cup"),
    )

    fun forSport(sport: Sport): List<CompetitionDef> = all.filter { it.sport == sport }
}
