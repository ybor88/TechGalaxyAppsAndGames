package com.scouttable.app.ui.common

/**
 * Nome nazione (come restituito da TheSportsDB, in inglese) -> emoji bandiera, costruita dal
 * codice ISO 3166-1 alpha-2 (coppia di "regional indicator symbol", standard Unicode: A=U+1F1E6).
 * Copre le nazionalità più comuni in calcio/basket; se non riconosciuta non mostra nulla (nessun
 * simbolo rotto).
 */
private val isoCodeByCountry: Map<String, String> = mapOf(
    "Italy" to "IT", "Portugal" to "PT", "Brazil" to "BR", "Argentina" to "AR",
    "France" to "FR", "Spain" to "ES", "Germany" to "DE", "England" to "GB",
    "Scotland" to "GB", "Wales" to "GB", "Northern Ireland" to "GB", "United Kingdom" to "GB",
    "USA" to "US", "United States" to "US", "Serbia" to "RS", "Croatia" to "HR",
    "Netherlands" to "NL", "Holland" to "NL", "Belgium" to "BE", "Uruguay" to "UY",
    "Colombia" to "CO", "Chile" to "CL", "Mexico" to "MX", "Sweden" to "SE",
    "Norway" to "NO", "Denmark" to "DK", "Poland" to "PL", "Ukraine" to "UA",
    "Russia" to "RU", "Turkey" to "TR", "Greece" to "GR", "Switzerland" to "CH",
    "Austria" to "AT", "Slovenia" to "SI", "Slovakia" to "SK", "Czech Republic" to "CZ",
    "Czechia" to "CZ", "Hungary" to "HU", "Romania" to "RO", "Bulgaria" to "BG",
    "Egypt" to "EG", "Morocco" to "MA", "Algeria" to "DZ", "Tunisia" to "TN",
    "Nigeria" to "NG", "Ghana" to "GH", "Senegal" to "SN", "Ivory Coast" to "CI",
    "Cote d'Ivoire" to "CI", "Cameroon" to "CM", "South Africa" to "ZA", "Japan" to "JP",
    "South Korea" to "KR", "Korea Republic" to "KR", "China" to "CN", "Australia" to "AU",
    "Canada" to "CA", "Saudi Arabia" to "SA", "Qatar" to "QA", "United Arab Emirates" to "AE",
    "Iran" to "IR", "Iraq" to "IQ", "Israel" to "IL", "India" to "IN", "Jamaica" to "JM",
    "Ireland" to "IE", "Republic of Ireland" to "IE", "Finland" to "FI", "Iceland" to "IS",
    "Ecuador" to "EC", "Peru" to "PE", "Paraguay" to "PY", "Venezuela" to "VE",
    "Bolivia" to "BO", "Costa Rica" to "CR", "Honduras" to "HN", "Panama" to "PA",
    "Bosnia and Herzegovina" to "BA", "North Macedonia" to "MK", "Montenegro" to "ME",
    "Albania" to "AL", "Kosovo" to "XK", "Georgia" to "GE", "Armenia" to "AM",
    "Lithuania" to "LT", "Latvia" to "LV", "Estonia" to "EE", "Luxembourg" to "LU",
    "Cyprus" to "CY", "Malta" to "MT", "New Zealand" to "NZ", "Dominican Republic" to "DO",
    "Cuba" to "CU", "Puerto Rico" to "PR", "Angola" to "AO", "DR Congo" to "CD",
    "Congo" to "CG", "Mali" to "ML", "Burkina Faso" to "BF", "Guinea" to "GN",
    "Cape Verde" to "CV", "Gabon" to "GA", "Zambia" to "ZM", "Kenya" to "KE",
    "Ethiopia" to "ET", "Uganda" to "UG", "Libya" to "LY", "Sudan" to "SD",
    "Jordan" to "JO", "Lebanon" to "LB", "Syria" to "SY", "Kuwait" to "KW",
    "Bahrain" to "BH", "Oman" to "OM", "Yemen" to "YE", "Pakistan" to "PK",
    "Bangladesh" to "BD", "Sri Lanka" to "LK", "Nepal" to "NP", "Thailand" to "TH",
    "Vietnam" to "VN", "Philippines" to "PH", "Indonesia" to "ID", "Malaysia" to "MY",
    "Singapore" to "SG",
)

fun flagEmojiFor(country: String): String {
    val code = isoCodeByCountry[country.trim()] ?: return ""
    return code.uppercase().map { 0x1F1E6 + (it - 'A') }
        .joinToString("") { String(Character.toChars(it)) }
}
