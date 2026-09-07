// © Roberto Di Flumeri
import SwiftUI

struct ToolItem: Identifiable, Hashable {
    let title: String
    let route: String
    let icon: String // SF Symbol name
    var id: String { route }
}

struct ToolCategory: Identifiable, Hashable {
    let id: String
    let title: String
    let tools: [ToolItem]
    let icon: String
    let accentColor: Color
    let headerImage: String
}

/// Catalogo completo degli strumenti, organizzato per reparto.
/// Ogni "route" punta a una schermata reale gestita in AppRootView.
enum ToolsCatalog {

    static let optometria = ToolCategory(
        id: "optometria",
        title: "Optometria",
        tools: [
            ToolItem(title: "Tabella acuità visiva (Vision Chart)", route: "vision_chart", icon: "eye"),
            ToolItem(title: "Misurazione distanza interpupillare (PD)", route: "pd_measurement", icon: "ruler"),
            ToolItem(title: "Amsler Grid", route: "amsler_grid", icon: "grid"),
            ToolItem(title: "Duochrome Test", route: "duochrome", icon: "circle.lefthalf.filled"),
            ToolItem(title: "Contrast Sensitivity Chart", route: "contrast_sensitivity", icon: "circle.dotted"),
            ToolItem(title: "Worth 4 Dot Test", route: "worth_4_dot", icon: "circle.grid.2x2"),
            ToolItem(title: "Anisometropia Tool", route: "anisometropia", icon: "scalemass"),
            ToolItem(title: "Crowding bars (ottotipo singolo)", route: "crowding_bars", icon: "rectangle.split.3x1"),
            ToolItem(title: "Near point of accommodation", route: "near_point_accommodation", icon: "scope"),
        ],
        icon: "eye",
        accentColor: Color(red: 0x4F / 255, green: 0xC3 / 255, blue: 0xF7 / 255),
        headerImage: "header_optometria"
    )

    static let oftalmologia = ToolCategory(
        id: "oftalmologia",
        title: "Oftalmologia",
        tools: [
            ToolItem(title: "Lensometro AI (fotocamera)", route: "lensometer_ai", icon: "camera"),
            ToolItem(title: "Astigmatism Dial", route: "astigmatism_dial", icon: "safari"),
            ToolItem(title: "Ishihara / Test colori", route: "color_vision_test", icon: "paintpalette"),
            ToolItem(title: "Topografia con disco di Placido", route: "placido_topography", icon: "target"),
            ToolItem(title: "Esoftalmometro", route: "exophthalmometer", icon: "ruler"),
            ToolItem(title: "Refrazione approssimata al comodino", route: "refraction_bedside", icon: "cross.case"),
            ToolItem(title: "Ullman indiretto (funduscopia)", route: "fundus_photography", icon: "camera.fill"),
            ToolItem(title: "Fotografia lampada a fessura", route: "slit_lamp_photography", icon: "camera.viewfinder"),
            ToolItem(title: "Ricercatore asse per IOL toriche", route: "axis_finder_iol", icon: "gyroscope"),
            ToolItem(title: "Test strabismo selfie", route: "strabismus_selfie", icon: "face.smiling"),
        ],
        icon: "eye",
        accentColor: Color(red: 0xBA / 255, green: 0x68 / 255, blue: 0xC8 / 255),
        headerImage: "header_oftalmologia"
    )

    static let neurologia = ToolCategory(
        id: "neurologia",
        title: "Neurologia",
        tools: [
            ToolItem(title: "Test Clock Drawing", route: "clock_drawing", icon: "clock"),
            ToolItem(title: "NIH Stroke Scale", route: "nih_stroke_scale", icon: "heart.text.square"),
            ToolItem(title: "Vestibular Ocular Motor Screening", route: "voms", icon: "eye"),
            ToolItem(title: "Penlight (torcia + documentazione foto)", route: "penlight", icon: "flashlight.on.fill"),
            ToolItem(title: "OKN Drum", route: "okn_drum", icon: "rectangle.grid.1x2"),
            ToolItem(title: "Mappa dei dermatomeri", route: "dermatome_map", icon: "figure.stand"),
        ],
        icon: "brain.head.profile",
        accentColor: Color(red: 0xFF / 255, green: 0xB7 / 255, blue: 0x4D / 255),
        headerImage: "header_neurologia"
    )

    static let urgenza = ToolCategory(
        id: "urgenza",
        title: "Medicina d'urgenza",
        tools: [
            ToolItem(title: "Calibro pupillare", route: "pupil_gauge", icon: "circle"),
            ToolItem(title: "Luce blu cobalto (abrasioni corneali)", route: "cobalt_blue_light", icon: "lightbulb"),
            ToolItem(title: "Vein finder", route: "vein_finder", icon: "drop"),
            ToolItem(title: "Goniometro", route: "goniometro", icon: "gauge"),
        ],
        icon: "cross.case.fill",
        accentColor: Color(red: 0xE5 / 255, green: 0x73 / 255, blue: 0x73 / 255),
        headerImage: "header_urgenza"
    )

    static let calcolatori = ToolCategory(
        id: "calcolatori",
        title: "Calcolatori clinici",
        tools: [
            ToolItem(title: "BMI Calculator", route: "calc_bmi", icon: "scalemass.fill"),
            ToolItem(title: "CHA2DS2-VASc Score", route: "calc_chads_vasc", icon: "heart.fill"),
            ToolItem(title: "Glasgow Coma Scale (GCS)", route: "calc_gcs", icon: "brain.head.profile"),
            ToolItem(title: "QTc (Bazett)", route: "calc_qtc", icon: "waveform.path.ecg"),
            ToolItem(title: "Cockcroft-Gault (Clearance creatinina)", route: "calc_cockcroft_gault", icon: "drop.fill"),
            ToolItem(title: "CKD-EPI (GFR)", route: "calc_ckd_epi", icon: "flask"),
            ToolItem(title: "Wells' Criteria for DVT", route: "calc_wells_dvt", icon: "exclamationmark.triangle"),
            ToolItem(title: "Wells' Criteria for PE", route: "calc_wells_pe", icon: "wind"),
        ],
        icon: "function",
        accentColor: Color(red: 0x81 / 255, green: 0xC7 / 255, blue: 0x84 / 255),
        headerImage: "header_calcolatori"
    )

    static let all: [ToolCategory] = [optometria, oftalmologia, neurologia, urgenza, calcolatori]
}
