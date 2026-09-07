package com.opticpro.suite.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.opticpro.suite.ui.calculators.*
import com.opticpro.suite.ui.common.ComingSoonScreen
import com.opticpro.suite.ui.common.ToolListScreen
import com.opticpro.suite.ui.emergency.CobaltBlueLightScreen
import com.opticpro.suite.ui.emergency.GoniometroScreen
import com.opticpro.suite.ui.emergency.PupilGaugeScreen
import com.opticpro.suite.ui.emergency.VeinFinderScreen
import com.opticpro.suite.ui.home.HomeScreen
import com.opticpro.suite.ui.neurology.ClockDrawingScreen
import com.opticpro.suite.ui.neurology.DermatomeMapScreen
import com.opticpro.suite.ui.neurology.NihStrokeScaleScreen
import com.opticpro.suite.ui.neurology.OknDrumScreen
import com.opticpro.suite.ui.neurology.PenlightScreen
import com.opticpro.suite.ui.neurology.VomsScreen
import com.opticpro.suite.ui.ophthalmology.AstigmatismDialScreen
import com.opticpro.suite.ui.ophthalmology.AxisFinderIolScreen
import com.opticpro.suite.ui.ophthalmology.ColorVisionTestScreen
import com.opticpro.suite.ui.ophthalmology.ExophthalmometerScreen
import com.opticpro.suite.ui.ophthalmology.FundusPhotographyScreen
import com.opticpro.suite.ui.ophthalmology.LensometerAiScreen
import com.opticpro.suite.ui.ophthalmology.PlacidoTopographyScreen
import com.opticpro.suite.ui.ophthalmology.RefractionBedsideScreen
import com.opticpro.suite.ui.ophthalmology.SlitLampPhotographyScreen
import com.opticpro.suite.ui.ophthalmology.StrabismusSelfieScreen
import com.opticpro.suite.ui.optometry.*
import java.net.URLDecoder

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(onCategoryClick = { navController.navigate("category/$it") })
        }
        composable(
            "category/{categoryId}",
            arguments = listOf(navArgument("categoryId") { type = NavType.StringType })
        ) { entry ->
            val categoryId = entry.arguments?.getString("categoryId")
            val category = ToolsCatalog.all.first { it.id == categoryId }
            ToolListScreen(
                category = category,
                onBack = { navController.popBackStack() },
                onToolClick = { navController.navigate(it) }
            )
        }
        composable(
            "coming_soon/{title}",
            arguments = listOf(navArgument("title") { type = NavType.StringType })
        ) { entry ->
            val title = URLDecoder.decode(entry.arguments?.getString("title") ?: "", "UTF-8")
            ComingSoonScreen(title = title, onBack = { navController.popBackStack() })
        }

        // Optometria
        composable("vision_chart") { VisionChartScreen(onBack = { navController.popBackStack() }) }
        composable("pd_measurement") { PdMeasurementScreen(onBack = { navController.popBackStack() }) }
        composable("amsler_grid") { AmslerGridScreen(onBack = { navController.popBackStack() }) }
        composable("duochrome") { DuochromeScreen(onBack = { navController.popBackStack() }) }
        composable("contrast_sensitivity") { ContrastSensitivityScreen(onBack = { navController.popBackStack() }) }
        composable("worth_4_dot") { Worth4DotScreen(onBack = { navController.popBackStack() }) }
        composable("anisometropia") { AnisometropiaScreen(onBack = { navController.popBackStack() }) }
        composable("crowding_bars") { CrowdingBarsScreen(onBack = { navController.popBackStack() }) }
        composable("near_point_accommodation") { NearPointAccommodationScreen(onBack = { navController.popBackStack() }) }

        // Oftalmologia
        composable("lensometer_ai") { LensometerAiScreen(onBack = { navController.popBackStack() }) }
        composable("astigmatism_dial") { AstigmatismDialScreen(onBack = { navController.popBackStack() }) }
        composable("color_vision_test") { ColorVisionTestScreen(onBack = { navController.popBackStack() }) }
        composable("placido_topography") { PlacidoTopographyScreen(onBack = { navController.popBackStack() }) }
        composable("exophthalmometer") { ExophthalmometerScreen(onBack = { navController.popBackStack() }) }
        composable("refraction_bedside") { RefractionBedsideScreen(onBack = { navController.popBackStack() }) }
        composable("fundus_photography") { FundusPhotographyScreen(onBack = { navController.popBackStack() }) }
        composable("slit_lamp_photography") { SlitLampPhotographyScreen(onBack = { navController.popBackStack() }) }
        composable("axis_finder_iol") { AxisFinderIolScreen(onBack = { navController.popBackStack() }) }
        composable("strabismus_selfie") { StrabismusSelfieScreen(onBack = { navController.popBackStack() }) }

        // Neurologia
        composable("clock_drawing") { ClockDrawingScreen(onBack = { navController.popBackStack() }) }
        composable("nih_stroke_scale") { NihStrokeScaleScreen(onBack = { navController.popBackStack() }) }
        composable("voms") { VomsScreen(onBack = { navController.popBackStack() }) }
        composable("penlight") { PenlightScreen(onBack = { navController.popBackStack() }) }
        composable("okn_drum") { OknDrumScreen(onBack = { navController.popBackStack() }) }
        composable("dermatome_map") { DermatomeMapScreen(onBack = { navController.popBackStack() }) }

        // Urgenza
        composable("pupil_gauge") { PupilGaugeScreen(onBack = { navController.popBackStack() }) }
        composable("cobalt_blue_light") { CobaltBlueLightScreen(onBack = { navController.popBackStack() }) }
        composable("vein_finder") { VeinFinderScreen(onBack = { navController.popBackStack() }) }
        composable("goniometro") { GoniometroScreen(onBack = { navController.popBackStack() }) }

        // Calcolatori
        composable("calc_bmi") { BmiCalculatorScreen(onBack = { navController.popBackStack() }) }
        composable("calc_chads_vasc") { ChadsVascCalculatorScreen(onBack = { navController.popBackStack() }) }
        composable("calc_gcs") { GcsCalculatorScreen(onBack = { navController.popBackStack() }) }
        composable("calc_qtc") { QtcCalculatorScreen(onBack = { navController.popBackStack() }) }
        composable("calc_cockcroft_gault") { CockcroftGaultCalculatorScreen(onBack = { navController.popBackStack() }) }
        composable("calc_ckd_epi") { CkdEpiCalculatorScreen(onBack = { navController.popBackStack() }) }
        composable("calc_wells_dvt") { WellsDvtCalculatorScreen(onBack = { navController.popBackStack() }) }
        composable("calc_wells_pe") { WellsPeCalculatorScreen(onBack = { navController.popBackStack() }) }
    }
}
