// © Roberto Di Flumeri
import SwiftUI

/// Nodo di navigazione, equivalente alle route della NavHost di Compose.
enum NavRoute: Hashable {
    case category(String)
    case tool(String)
}

/// Radice dell'app: gestisce lo stack di navigazione (Home → Categoria → Strumento),
/// equivalente a `AppNavGraph` in Compose/Navigation.
struct AppRootView: View {
    @State private var path: [NavRoute] = []

    var body: some View {
        NavigationStack(path: $path) {
            HomeView(onCategoryClick: { path.append(.category($0)) })
                .navigationDestination(for: NavRoute.self) { route in
                    switch route {
                    case .category(let categoryId):
                        if let category = ToolsCatalog.all.first(where: { $0.id == categoryId }) {
                            ToolListView(category: category, onToolClick: { path.append(.tool($0)) })
                        }
                    case .tool(let route):
                        toolDestination(for: route)
                    }
                }
        }
    }
}

/// Dispatch della singola schermata strumento in base alla route, equivalente ai
/// blocchi `composable("...")` di AppNavGraph.kt.
@ViewBuilder
func toolDestination(for route: String) -> some View {
    switch route {
    // Optometria
    case "vision_chart": VisionChartView()
    case "pd_measurement": PdMeasurementView()
    case "amsler_grid": AmslerGridView()
    case "duochrome": DuochromeView()
    case "contrast_sensitivity": ContrastSensitivityView()
    case "worth_4_dot": Worth4DotView()
    case "anisometropia": AnisometropiaView()
    case "crowding_bars": CrowdingBarsView()
    case "near_point_accommodation": NearPointAccommodationView()

    // Oftalmologia
    case "lensometer_ai": LensometerAiView()
    case "astigmatism_dial": AstigmatismDialView()
    case "color_vision_test": ColorVisionTestView()
    case "placido_topography": PlacidoTopographyView()
    case "exophthalmometer": ExophthalmometerView()
    case "refraction_bedside": RefractionBedsideView()
    case "fundus_photography": FundusPhotographyView()
    case "slit_lamp_photography": SlitLampPhotographyView()
    case "axis_finder_iol": AxisFinderIolView()
    case "strabismus_selfie": StrabismusSelfieView()

    // Neurologia
    case "clock_drawing": ClockDrawingView()
    case "nih_stroke_scale": NihStrokeScaleView()
    case "voms": VomsView()
    case "penlight": PenlightView()
    case "okn_drum": OknDrumView()
    case "dermatome_map": DermatomeMapView()

    // Urgenza
    case "pupil_gauge": PupilGaugeView()
    case "cobalt_blue_light": CobaltBlueLightView()
    case "vein_finder": VeinFinderView()
    case "goniometro": GoniometroView()

    // Calcolatori
    case "calc_bmi": BmiCalculatorView()
    case "calc_chads_vasc": ChadsVascCalculatorView()
    case "calc_gcs": GcsCalculatorView()
    case "calc_qtc": QtcCalculatorView()
    case "calc_cockcroft_gault": CockcroftGaultCalculatorView()
    case "calc_ckd_epi": CkdEpiCalculatorView()
    case "calc_wells_dvt": WellsDvtCalculatorView()
    case "calc_wells_pe": WellsPeCalculatorView()

    default: ComingSoonView(title: route)
    }
}
