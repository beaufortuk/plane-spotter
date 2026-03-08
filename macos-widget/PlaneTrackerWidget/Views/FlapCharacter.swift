import SwiftUI

/// Single split-flap character tile
struct FlapCharacter: View {
    let character: String
    var isLarge: Bool = false

    var body: some View {
        Text(character)
            .font(.system(size: isLarge ? 22 : 14, weight: .bold, design: .monospaced))
            .foregroundColor(PTConstants.textPrimary)
            .frame(
                width: isLarge ? 26 : 16,
                height: isLarge ? 32 : 22
            )
            .background(
                RoundedRectangle(cornerRadius: 3)
                    .fill(PTConstants.flapBg)
                    .overlay(
                        // Horizontal split line
                        Rectangle()
                            .fill(Color.black.opacity(0.4))
                            .frame(height: 1)
                            .offset(y: 0),
                        alignment: .center
                    )
                    .overlay(
                        // Bottom gold accent
                        Rectangle()
                            .fill(PTConstants.goldAccent.opacity(0.3))
                            .frame(height: 1),
                        alignment: .bottom
                    )
            )
            .overlay(
                RoundedRectangle(cornerRadius: 3)
                    .stroke(PTConstants.flapBorder, lineWidth: 0.5)
            )
    }
}

/// Row of split-flap characters
struct FlapText: View {
    let text: String
    var isLarge: Bool = false
    var spacing: CGFloat = 2

    var body: some View {
        HStack(spacing: spacing) {
            ForEach(Array(text.enumerated()), id: \.offset) { _, ch in
                FlapCharacter(character: String(ch), isLarge: isLarge)
            }
        }
    }
}

#Preview {
    VStack(spacing: 12) {
        FlapText(text: "BAW123")
        FlapText(text: "LHR", isLarge: true)
        FlapText(text: "CDG", isLarge: true)
    }
    .padding()
    .background(PTConstants.panelBg)
}
