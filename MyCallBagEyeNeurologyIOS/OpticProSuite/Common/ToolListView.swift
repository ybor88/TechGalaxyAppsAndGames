// © Roberto Di Flumeri
import SwiftUI

struct ToolListView: View {
    let category: ToolCategory
    let onToolClick: (String) -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                CategoryHeader(category: category)
                ForEach(category.tools) { tool in
                    Button(action: { onToolClick(tool.route) }) {
                        HStack {
                            ZStack {
                                Circle()
                                    .fill(category.accentColor.opacity(0.15))
                                    .frame(width: 36, height: 36)
                                Image(systemName: tool.icon)
                                    .font(.system(size: 14))
                                    .foregroundColor(category.accentColor)
                            }
                            Text(tool.title)
                                .foregroundColor(AppColor.onSurface)
                                .multilineTextAlignment(.leading)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption2)
                                .foregroundColor(AppColor.onSurfaceVariant)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                    }
                    .buttonStyle(.plain)
                    Divider().padding(.leading, 68)
                }
            }
            .padding(.bottom, 24)
        }
        .background(AppColor.background)
        .navigationBarHidden(true)
        .ignoresSafeArea(edges: .top)
    }
}

private struct CategoryHeader: View {
    let category: ToolCategory
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Image(category.headerImage)
                .resizable()
                .aspectRatio(contentMode: .fill)
                .frame(height: 200)
                .clipped()
            LinearGradient(
                colors: [Color.black.opacity(0.55), Color.black.opacity(0.05), Color.black.opacity(0.75)],
                startPoint: .top, endPoint: .bottom
            )
            .frame(height: 200)

            Button(action: { dismiss() }) {
                Image(systemName: "chevron.left")
                    .foregroundColor(.white)
                    .padding(10)
                    .background(Color.black.opacity(0.35))
                    .clipShape(Circle())
            }
            .padding(.leading, 8)
            .padding(.top, 48)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)

            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .fill(category.accentColor.opacity(0.85))
                        .frame(width: 44, height: 44)
                    Image(systemName: category.icon)
                        .foregroundColor(.white)
                }
                VStack(alignment: .leading) {
                    Text(category.title)
                        .font(.title2)
                        .foregroundColor(.white)
                    Text("\(category.tools.count) strumenti")
                        .font(.footnote)
                        .foregroundColor(.white.opacity(0.85))
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
        }
        .frame(height: 200)
    }
}
