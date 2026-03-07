import SwiftUI

/// Bezier route arc — climb / cruise / descent phases
struct RouteArcShape: Shape {
    let progress: Double // 0–100

    private let climbF: CGFloat = 0.18
    private let cruiseF: CGFloat = 0.60
    private let descentF: CGFloat = 0.22

    func path(in rect: CGRect) -> Path {
        let padX = rect.width * 0.06
        let xOrig = padX
        let xDest = rect.width - padX
        let yGnd = rect.height - 6
        let span = xDest - xOrig

        let xTOC = xOrig + span * climbF
        let xTOD = xDest - span * descentF

        // Cruise altitude — gentle descent slope
        let descentW = xDest - xTOD
        let maxDropH = descentW * 2.8
        let yCrz = max(rect.height * 0.1, yGnd - maxDropH)

        // Climb bezier control points
        let ccp1 = CGPoint(x: xOrig + (xTOC - xOrig) * 0.1, y: yGnd)
        let ccp2 = CGPoint(x: xTOC - (xTOC - xOrig) * 0.1, y: yCrz)

        // Descent bezier control points
        let dcp1 = CGPoint(x: xTOD + (xDest - xTOD) * 0.1, y: yCrz)
        let dcp2 = CGPoint(x: xDest - (xDest - xTOD) * 0.1, y: yGnd - (yGnd - yCrz) * 0.15)

        var path = Path()
        path.move(to: CGPoint(x: xOrig, y: yGnd))
        path.addCurve(to: CGPoint(x: xTOC, y: yCrz), control1: ccp1, control2: ccp2)
        path.addLine(to: CGPoint(x: xTOD, y: yCrz))
        path.addCurve(to: CGPoint(x: xDest, y: yGnd), control1: dcp1, control2: dcp2)

        return path
    }
}

/// The complete route arc view with both full path and progress overlay
struct RouteArc: View {
    let progress: Double
    let originCode: String
    let destCode: String

    var body: some View {
        GeometryReader { geo in
            ZStack {
                // Full route — dashed gray
                RouteArcShape(progress: 100)
                    .stroke(
                        PTConstants.textDim.opacity(0.3),
                        style: StrokeStyle(lineWidth: 2, dash: [4, 4])
                    )

                // Flown portion — solid gold
                RouteArcShape(progress: progress)
                    .trim(from: 0, to: CGFloat(progress) / 100)
                    .stroke(PTConstants.goldAccent, lineWidth: 2.5)

                // Plane icon at progress point
                let pt = planePosition(in: geo.size)
                Image(systemName: "airplane")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(PTConstants.goldAccent)
                    .rotationEffect(.degrees(20))
                    .position(pt)
            }
        }
    }

    private func planePosition(in size: CGSize) -> CGPoint {
        let rect = CGRect(origin: .zero, size: size)
        let shape = RouteArcShape(progress: progress)
        let path = shape.path(in: rect)

        // Sample points along the path to find the progress position
        let totalLen = pathLength(path, in: rect)
        let targetLen = totalLen * CGFloat(progress) / 100

        return pointAtLength(path, targetLength: targetLen, in: rect)
    }

    private func pathLength(_ path: Path, in rect: CGRect) -> CGFloat {
        // Approximate using many small segments
        let steps = 200
        var length: CGFloat = 0
        var prev: CGPoint?

        for i in 0...steps {
            let t = CGFloat(i) / CGFloat(steps)
            let trimmed = path.trimmedPath(from: 0, to: t)
            let bounds = trimmed.boundingRect
            let pt = CGPoint(x: bounds.maxX, y: bounds.midY)
            if let p = prev {
                length += hypot(pt.x - p.x, pt.y - p.y)
            }
            prev = pt
        }
        return max(length, 1)
    }

    private func pointAtLength(_ path: Path, targetLength: CGFloat, in rect: CGRect) -> CGPoint {
        let fraction = min(max(targetLength / pathLength(path, in: rect), 0), 1)
        let trimmed = path.trimmedPath(from: 0, to: fraction)
        let bounds = trimmed.boundingRect
        return CGPoint(x: bounds.maxX, y: bounds.midY)
    }
}
