// © Roberto Di Flumeri
import SwiftUI

/// Palette monocromatica ispirata al logo (anelli bianchi su sfondo nero).
enum AppColor {
    static let logoBlack = Color.black
    static let logoSurface = Color(red: 0x16 / 255, green: 0x16 / 255, blue: 0x16 / 255)
    static let logoSurfaceVariant = Color(red: 0x26 / 255, green: 0x26 / 255, blue: 0x26 / 255)
    static let logoWhite = Color.white
    static let logoGray = Color(red: 0xB3 / 255, green: 0xB3 / 255, blue: 0xB3 / 255)

    static let background = logoBlack
    static let onBackground = logoWhite
    static let surface = logoSurface
    static let onSurface = logoWhite
    static let surfaceVariant = logoSurfaceVariant
    static let onSurfaceVariant = logoGray
}

/// Applica lo stile scuro monocromatico dell'app a tutta la gerarchia di viste,
/// equivalente a `OpticProSuiteTheme` in Compose.
struct AppTheme: ViewModifier {
    func body(content: Content) -> some View {
        content
            .preferredColorScheme(.dark)
            .tint(AppColor.onBackground)
            .background(AppColor.background)
    }
}

extension View {
    func appTheme() -> some View { modifier(AppTheme()) }
}
