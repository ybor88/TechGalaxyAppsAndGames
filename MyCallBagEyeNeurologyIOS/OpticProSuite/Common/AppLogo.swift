// © Roberto Di Flumeri
import SwiftUI

/// Logo ufficiale dell'app (occhio + rami neurali su sfondo nero), marchio Optools.
struct AppLogo: View {
    var size: CGFloat = 48

    var body: some View {
        Image("app_logo")
            .resizable()
            .aspectRatio(contentMode: .fill)
            .frame(width: size, height: size)
            .clipShape(Circle())
    }
}
