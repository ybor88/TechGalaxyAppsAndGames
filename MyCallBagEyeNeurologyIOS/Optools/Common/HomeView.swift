// © Roberto Di Flumeri
import SwiftUI

struct HomeView: View {
    let onCategoryClick: (String) -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                HeroHeader()
                ForEach(ToolsCatalog.all) { category in
                    CategoryCard(category: category) { onCategoryClick(category.id) }
                }
                FooterCredit()
            }
            .padding(.bottom, 24)
        }
        .background(AppColor.background)
        .navigationBarHidden(true)
    }
}

private struct HeroHeader: View {
    @State private var glow: Double = 0.55

    var body: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(AppColor.onBackground.opacity(0.08 * glow))
                    .frame(width: 96, height: 96)
                AppLogo(size: 64)
            }
            Image("home_banner")
                .resizable()
                .aspectRatio(1536.0 / 1024.0, contentMode: .fit)
                .clipShape(RoundedRectangle(cornerRadius: 24))
                .padding(.horizontal, 20)
            Text("Optools")
                .font(.title2)
                .foregroundColor(AppColor.onBackground)
            Text("Il tuo digital call bag per ottica, oftalmologia e neurologia")
                .font(.footnote)
                .foregroundColor(AppColor.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        .padding(.top, 32)
        .padding(.bottom, 28)
        .frame(maxWidth: .infinity)
        .background(
            LinearGradient(colors: [AppColor.surfaceVariant, AppColor.background], startPoint: .top, endPoint: .bottom)
        )
        .onAppear {
            withAnimation(.easeInOut(duration: 1.8).repeatForever(autoreverses: true)) {
                glow = 1.0
            }
        }
    }
}

private struct CategoryCard: View {
    let category: ToolCategory
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 16) {
                ZStack {
                    Circle()
                        .fill(category.accentColor.opacity(0.18))
                        .frame(width: 52, height: 52)
                    Image(systemName: category.icon)
                        .foregroundColor(category.accentColor)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(category.title)
                        .font(.headline)
                        .foregroundColor(AppColor.onSurface)
                    Text("\(category.tools.count) strumenti")
                        .font(.caption)
                        .foregroundColor(AppColor.onSurfaceVariant)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundColor(AppColor.onSurfaceVariant)
            }
            .padding(16)
            .background(AppColor.surface)
            .cornerRadius(20)
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
        }
        .buttonStyle(.plain)
    }
}

private struct FooterCredit: View {
    var body: some View {
        VStack(spacing: 6) {
            Text("Ideato da Antonio Di Somma")
                .font(.caption)
                .foregroundColor(AppColor.onSurfaceVariant)
            Text("© Roberto Di Flumeri")
                .font(.caption2)
                .foregroundColor(AppColor.onSurfaceVariant.opacity(0.7))
        }
        .padding(.top, 24)
        .padding(.bottom, 8)
        .frame(maxWidth: .infinity)
    }
}
