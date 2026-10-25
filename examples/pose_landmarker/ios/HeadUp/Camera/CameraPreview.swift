import AVFoundation
import SwiftUI
import HeadUpCore

struct CameraPreview: UIViewRepresentable {
    @ObservedObject var monitor: MonitoringCoordinator

    func makeUIView(context: Context) -> PreviewSurface {
        let view = PreviewSurface()
        view.preview.session = monitor.camera.session
        view.rotationChanged = { [weak monitor] angle in
            DispatchQueue.main.async { monitor?.setRotation(angle) }
        }
        return view
    }

    func updateUIView(_ view: PreviewSurface, context: Context) {
        view.front = monitor.isFrontCamera
        view.points = monitor.landmarks
        view.imageSize = monitor.frameSize
        view.setNeedsLayout()
    }
}

final class PreviewSurface: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var preview: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    var points: [LandmarkPoint] = []
    var imageSize = CGSize(width: 480, height: 640)
    var front = true
    var rotationChanged: ((CGFloat) -> Void)?
    private let skeleton = CAShapeLayer()
    private var lastRotation: CGFloat?

    override init(frame: CGRect) {
        super.init(frame: frame)
        preview.videoGravity = .resizeAspectFill
        backgroundColor = .black
        clipsToBounds = true
        skeleton.strokeColor = UIColor.systemCyan.cgColor
        skeleton.fillColor = UIColor.clear.cgColor
        skeleton.lineWidth = 2
        layer.addSublayer(skeleton)
        isAccessibilityElement = true
        accessibilityLabel = "Camera preview / 相機預覽"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func layoutSubviews() {
        super.layoutSubviews()
        let angle: CGFloat
        switch window?.windowScene?.interfaceOrientation {
        case .landscapeLeft: angle = 180
        case .landscapeRight: angle = 0
        case .portraitUpsideDown: angle = 270
        default: angle = 90
        }
        if lastRotation != angle { lastRotation = angle; rotationChanged?(angle) }
        if let connection = preview.connection {
            if connection.isVideoRotationAngleSupported(angle) { connection.videoRotationAngle = angle }
            if connection.isVideoMirroringSupported {
                connection.automaticallyAdjustsVideoMirroring = false
                connection.isVideoMirrored = front
            }
        }
        skeleton.frame = bounds
        guard imageSize.width > 0, imageSize.height > 0 else { skeleton.path = nil; return }
        let scale = max(bounds.width / imageSize.width, bounds.height / imageSize.height)
        let offset = CGPoint(x: (bounds.width - imageSize.width * scale) / 2,
                             y: (bounds.height - imageSize.height * scale) / 2)
        func point(_ index: Int) -> CGPoint? {
            guard points.indices.contains(index) else { return nil }
            let p = points[index]
            guard p.visibility >= 0.2, p.presence >= 0.2 else { return nil }
            return CGPoint(x: CGFloat(p.x) * imageSize.width * scale + offset.x,
                           y: CGFloat(p.y) * imageSize.height * scale + offset.y)
        }
        let path = UIBezierPath()
        let edges = [(0,1),(1,2),(2,3),(3,7),(0,4),(4,5),(5,6),(6,8),(9,10),
                     (11,12),(11,13),(13,15),(12,14),(14,16),(11,23),(12,24),(23,24),
                     (23,25),(24,26),(25,27),(26,28)]
        for (a,b) in edges {
            if let p = point(a), let q = point(b) { path.move(to: p); path.addLine(to: q) }
        }
        for index in points.indices {
            if let p = point(index) { path.append(UIBezierPath(ovalIn: CGRect(x: p.x - 2, y: p.y - 2, width: 4, height: 4))) }
        }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        skeleton.path = path.cgPath
        CATransaction.commit()
    }
}
