// Copyright (c) Roberto Di Flumeri
package com.opticpro.suite.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.BlurCircular
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.opticpro.suite.R

data class ToolItem(val title: String, val route: String, val icon: ImageVector)

data class ToolCategory(
    val id: String,
    val title: String,
    val tools: List<ToolItem>,
    val icon: ImageVector,
    val accentColor: Color,
    @DrawableRes val headerImage: Int
)

/**
 * Catalogo completo degli strumenti, organizzato per reparto.
 * Ogni "route" punta a uno schermo reale registrato in AppNavGraph.
 */
object ToolsCatalog {

    val optometria = ToolCategory(
        id = "optometria",
        title = "Optometria",
        icon = Icons.Filled.RemoveRedEye,
        accentColor = Color(0xFF4FC3F7),
        headerImage = R.drawable.header_optometria,
        tools = listOf(
            ToolItem("Tabella acuità visiva (Vision Chart)", "vision_chart", Icons.Filled.Visibility),
            ToolItem("Misurazione distanza interpupillare (PD)", "pd_measurement", Icons.Filled.Straighten),
            ToolItem("Amsler Grid", "amsler_grid", Icons.Filled.GridOn),
            ToolItem("Duochrome Test", "duochrome", Icons.Filled.Contrast),
            ToolItem("Contrast Sensitivity Chart", "contrast_sensitivity", Icons.Filled.BlurCircular),
            ToolItem("Worth 4 Dot Test", "worth_4_dot", Icons.Filled.BlurCircular),
            ToolItem("Anisometropia Tool", "anisometropia", Icons.Filled.Balance),
            ToolItem("Crowding bars (ottotipo singolo)", "crowding_bars", Icons.Filled.ViewWeek),
            ToolItem("Near point of accommodation", "near_point_accommodation", Icons.Filled.CenterFocusStrong),
        )
    )

    val oftalmologia = ToolCategory(
        id = "oftalmologia",
        title = "Oftalmologia",
        icon = Icons.Filled.Visibility,
        accentColor = Color(0xFFBA68C8),
        headerImage = R.drawable.header_oftalmologia,
        tools = listOf(
            ToolItem("Lensometro AI (fotocamera)", "lensometer_ai", Icons.Filled.CameraAlt),
            ToolItem("Astigmatism Dial", "astigmatism_dial", Icons.Filled.Explore),
            ToolItem("Ishihara / Test colori", "color_vision_test", Icons.Filled.Palette),
            ToolItem("Topografia con disco di Placido", "placido_topography", Icons.Filled.TrackChanges),
            ToolItem("Esoftalmometro", "exophthalmometer", Icons.Filled.Straighten),
            ToolItem("Refrazione approssimata al comodino", "refraction_bedside", Icons.Filled.Biotech),
            ToolItem("Ullman indiretto (funduscopia)", "fundus_photography", Icons.Filled.PhotoCamera),
            ToolItem("Fotografia lampada a fessura", "slit_lamp_photography", Icons.Filled.PhotoCamera),
            ToolItem("Ricercatore asse per IOL toriche", "axis_finder_iol", Icons.Filled.Explore),
            ToolItem("Test strabismo selfie", "strabismus_selfie", Icons.Filled.Face),
        )
    )

    val neurologia = ToolCategory(
        id = "neurologia",
        title = "Neurologia",
        icon = Icons.Filled.Psychology,
        accentColor = Color(0xFFFFB74D),
        headerImage = R.drawable.header_neurologia,
        tools = listOf(
            ToolItem("Test Clock Drawing", "clock_drawing", Icons.Filled.AccessTime),
            ToolItem("NIH Stroke Scale", "nih_stroke_scale", Icons.Filled.MonitorHeart),
            ToolItem("Vestibular Ocular Motor Screening", "voms", Icons.Filled.RemoveRedEye),
            ToolItem("Penlight (torcia + documentazione foto)", "penlight", Icons.Filled.FlashOn),
            ToolItem("OKN Drum", "okn_drum", Icons.Filled.ViewStream),
            ToolItem("Mappa dei dermatomeri", "dermatome_map", Icons.Filled.Accessibility),
        )
    )

    val urgenza = ToolCategory(
        id = "urgenza",
        title = "Medicina d'urgenza",
        icon = Icons.Filled.LocalHospital,
        accentColor = Color(0xFFE57373),
        headerImage = R.drawable.header_urgenza,
        tools = listOf(
            ToolItem("Calibro pupillare", "pupil_gauge", Icons.Filled.Circle),
            ToolItem("Luce blu cobalto (abrasioni corneali)", "cobalt_blue_light", Icons.Filled.Lightbulb),
            ToolItem("Vein finder", "vein_finder", Icons.Filled.Bloodtype),
            ToolItem("Goniometro", "goniometro", Icons.Filled.Architecture),
        )
    )

    val calcolatori = ToolCategory(
        id = "calcolatori",
        title = "Calcolatori clinici",
        icon = Icons.Filled.Calculate,
        accentColor = Color(0xFF81C784),
        headerImage = R.drawable.header_calcolatori,
        tools = listOf(
            ToolItem("BMI Calculator", "calc_bmi", Icons.Filled.MonitorWeight),
            ToolItem("CHA2DS2-VASc Score", "calc_chads_vasc", Icons.Filled.Favorite),
            ToolItem("Glasgow Coma Scale (GCS)", "calc_gcs", Icons.Filled.Psychology),
            ToolItem("QTc (Bazett)", "calc_qtc", Icons.Filled.Timeline),
            ToolItem("Cockcroft-Gault (Clearance creatinina)", "calc_cockcroft_gault", Icons.Filled.WaterDrop),
            ToolItem("CKD-EPI (GFR)", "calc_ckd_epi", Icons.Filled.Science),
            ToolItem("Wells' Criteria for DVT", "calc_wells_dvt", Icons.Filled.Warning),
            ToolItem("Wells' Criteria for PE", "calc_wells_pe", Icons.Filled.Air),
        )
    )

    val all = listOf(optometria, oftalmologia, neurologia, urgenza, calcolatori)
}

