import SwiftUI

struct ContentView: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "airplane")
                .font(.system(size: 48))
                .foregroundColor(Color(red: 0.83, green: 0.66, blue: 0.28))

            Text("Plane Tracker")
                .font(.system(size: 24, weight: .bold, design: .monospaced))

            Text("Add the widget to your desktop\nvia the widget gallery.")
                .font(.system(size: 13))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            Divider().frame(width: 200)

            Text("Right-click desktop → Edit Widgets\n→ Search \"Plane Tracker\"")
                .font(.system(size: 12, design: .monospaced))
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(red: 0.08, green: 0.08, blue: 0.09))
    }
}

#Preview {
    ContentView()
}
