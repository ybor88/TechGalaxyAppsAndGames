package com.scouttable.app.data.lookup

// Nome nazione in inglese (stessa convenzione di TheSportsDB/CountryFlags) per i gentilizi più
// comuni tra i giocatori di basket/calcio: usato quando TheSportsDB non ha un profilo per il
// giocatore e la nazionalità va dedotta dal campo "nationality" dell'infobox Wikipedia (es.
// "Italian / British" -> "Italy", per prendere sempre e solo la PRIMA nazionalità elencata).
private val countryByDemonym: Map<String, String> = mapOf(
    "italian" to "Italy", "american" to "United States", "serbian" to "Serbia",
    "croatian" to "Croatia", "french" to "France", "spanish" to "Spain",
    "german" to "Germany", "english" to "England", "scottish" to "Scotland",
    "welsh" to "Wales", "british" to "United Kingdom", "brazilian" to "Brazil",
    "argentine" to "Argentina", "argentinian" to "Argentina", "lithuanian" to "Lithuania",
    "greek" to "Greece", "turkish" to "Turkey", "russian" to "Russia",
    "slovenian" to "Slovenia", "slovene" to "Slovenia", "israeli" to "Israel",
    "canadian" to "Canada", "australian" to "Australia", "nigerian" to "Nigeria",
    "senegalese" to "Senegal", "montenegrin" to "Montenegro", "bosnian" to "Bosnia and Herzegovina",
    "macedonian" to "North Macedonia", "puerto rican" to "Puerto Rico",
    "dominican" to "Dominican Republic", "angolan" to "Angola", "polish" to "Poland",
    "ukrainian" to "Ukraine", "georgian" to "Georgia", "armenian" to "Armenia",
    "latvian" to "Latvia", "estonian" to "Estonia", "finnish" to "Finland",
    "swedish" to "Sweden", "norwegian" to "Norway", "danish" to "Denmark",
    "dutch" to "Netherlands", "belgian" to "Belgium", "swiss" to "Switzerland",
    "austrian" to "Austria", "czech" to "Czech Republic", "slovak" to "Slovakia",
    "hungarian" to "Hungary", "romanian" to "Romania", "bulgarian" to "Bulgaria",
    "egyptian" to "Egypt", "moroccan" to "Morocco", "algerian" to "Algeria",
    "tunisian" to "Tunisia", "ghanaian" to "Ghana", "ivorian" to "Ivory Coast",
    "cameroonian" to "Cameroon", "south african" to "South Africa", "japanese" to "Japan",
    "south korean" to "South Korea", "chinese" to "China", "saudi" to "Saudi Arabia",
    "qatari" to "Qatar", "emirati" to "United Arab Emirates", "iranian" to "Iran",
    "iraqi" to "Iraq", "indian" to "India", "jamaican" to "Jamaica", "irish" to "Ireland",
    "icelandic" to "Iceland", "ecuadorian" to "Ecuador", "peruvian" to "Peru",
    "paraguayan" to "Paraguay", "venezuelan" to "Venezuela", "bolivian" to "Bolivia",
    "costa rican" to "Costa Rica", "panamanian" to "Panama", "albanian" to "Albania",
    "kosovar" to "Kosovo", "cypriot" to "Cyprus", "maltese" to "Malta",
    "cuban" to "Cuba", "zambian" to "Zambia", "kenyan" to "Kenya", "ethiopian" to "Ethiopia",
    "ugandan" to "Uganda", "libyan" to "Libya", "sudanese" to "Sudan", "jordanian" to "Jordan",
    "lebanese" to "Lebanon", "syrian" to "Syria", "kuwaiti" to "Kuwait", "pakistani" to "Pakistan",
    "bangladeshi" to "Bangladesh", "sri lankan" to "Sri Lanka", "thai" to "Thailand",
    "vietnamese" to "Vietnam", "filipino" to "Philippines", "indonesian" to "Indonesia",
    "malaysian" to "Malaysia", "singaporean" to "Singapore", "portuguese" to "Portugal",
    "uruguayan" to "Uruguay", "colombian" to "Colombia", "chilean" to "Chile", "mexican" to "Mexico",
)

/**
 * "Italian / British" -> "Italy" (sempre la prima nazionalità elencata, separata da "/" o ",").
 * Restituisce null se il gentilizio non è riconosciuto, invece di un dato inventato.
 */
fun countryFromDemonym(raw: String): String? {
    val first = raw.split('/', ',').firstOrNull()?.trim()?.lowercase() ?: return null
    return countryByDemonym[first]
}
