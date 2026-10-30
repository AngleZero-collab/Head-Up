import AVFoundation
import UIKit
import MediaPipeTasksVision
import HeadUpCore

struct PoseFrame {
    let points: [LandmarkPoint]
    let size: CGSize
    let monotonicMs: Int64
    let wallClockMs: Int64
}

/// All capture and inference operations are serialized; the main actor receives value snapshots.
final class CameraPipeline: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "HeadUp.camera", qos: .userInitiated)
    private var output: AVCaptureVideoDataOutput?
    private var landmarker: PoseLandmarker?
    private var generation = UUID()
    private var rotation: CGFloat = 90
    private var lastTimestamp = 0
    private var frameHandler: ((UUID, Result<PoseFrame, Error>) -> Void)?

    /// Also used by the model smoke test, without loading a second copy of MediaPipe into XCTest.
    static func detectImage(_ image: UIImage) throws -> [LandmarkPoint] {
        guard let path = Bundle.main.path(forResource: "pose_landmarker_full", ofType: "task") else { throw CameraError.missingModel }
        let options = PoseLandmarkerOptions()
        options.baseOptions.modelAssetPath = path
        options.baseOptions.delegate = .CPU
        options.runningMode = .image
        options.numPoses = 1
        options.minPoseDetectionConfidence = 0.5
        options.minPosePresenceConfidence = 0.5
        options.minTrackingConfidence = 0.5
        let detector = try PoseLandmarker(options: options)
        let result = try detector.detect(image: MPImage(uiImage: image))
        return (result.landmarks.first ?? []).map {
            LandmarkPoint(x: $0.x, y: $0.y, z: $0.z, visibility: $0.visibility?.floatValue ?? 1, presence: $0.presence?.floatValue ?? 1)
        }
    }

    func start(token: UUID, front: Bool, handler: @escaping (UUID, Result<PoseFrame, Error>) -> Void,
               completion: @escaping (UUID, Error?) -> Void) {
        queue.async {
            self.generation = token
            self.frameHandler = handler
            do {
                if self.session.isRunning { self.session.stopRunning() }
                self.session.beginConfiguration()
                for input in self.session.inputs { self.session.removeInput(input) }
                for output in self.session.outputs { self.session.removeOutput(output) }
                self.session.sessionPreset = .vga640x480
                do {
                    guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video,
                                                               position: front ? .front : .back) else {
                        throw CameraError.noCamera
                    }
                    let input = try AVCaptureDeviceInput(device: device)
                    guard self.session.canAddInput(input) else { throw CameraError.configuration }
                    self.session.addInput(input)
                    let output = AVCaptureVideoDataOutput()
                    output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
                    output.alwaysDiscardsLateVideoFrames = true
                    output.setSampleBufferDelegate(self, queue: self.queue)
                    guard self.session.canAddOutput(output) else { throw CameraError.configuration }
                    self.session.addOutput(output)
                    if let connection = output.connection(with: .video) {
                        if connection.isVideoRotationAngleSupported(self.rotation) { connection.videoRotationAngle = self.rotation }
                        if connection.isVideoMirroringSupported {
                            connection.automaticallyAdjustsVideoMirroring = false
                            connection.isVideoMirrored = front
                        }
                    }
                    self.output = output
                    if self.session.isMultitaskingCameraAccessSupported {
                        self.session.isMultitaskingCameraAccessEnabled = true
                    }
                    self.session.commitConfiguration()
                } catch {
                    self.session.commitConfiguration()
                    throw error
                }
                guard let path = Bundle.main.path(forResource: "pose_landmarker_full", ofType: "task") else {
                    throw CameraError.missingModel
                }
                let options = PoseLandmarkerOptions()
                options.baseOptions.modelAssetPath = path
                options.baseOptions.delegate = .CPU
                // Video mode runs synchronously on the serial frame queue, bounding in-flight work.
                options.runningMode = .video
                options.numPoses = 1
                options.minPoseDetectionConfidence = 0.5
                options.minPosePresenceConfidence = 0.5
                options.minTrackingConfidence = 0.5
                self.landmarker = try PoseLandmarker(options: options)
                self.lastTimestamp = 0
                self.session.startRunning()
                DispatchQueue.main.async { completion(token, nil) }
            } catch {
                self.landmarker = nil
                DispatchQueue.main.async { completion(token, error) }
            }
        }
    }

    func stop() {
        queue.async {
            self.generation = UUID()
            self.frameHandler = nil
            self.output?.setSampleBufferDelegate(nil, queue: nil)
            if self.session.isRunning { self.session.stopRunning() }
            self.landmarker = nil
        }
    }

    func setRotation(_ angle: CGFloat) {
        queue.async {
            self.rotation = angle
            if let connection = self.output?.connection(with: .video), connection.isVideoRotationAngleSupported(angle) {
                connection.videoRotationAngle = angle
            }
        }
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer,
                       from connection: AVCaptureConnection) {
        guard let landmarker, let handler = frameHandler else { return }
        let token = generation
        let timestamp = max(lastTimestamp + 1, Int(ProcessInfo.processInfo.systemUptime * 1000))
        lastTimestamp = timestamp
        do {
            let image = try MPImage(sampleBuffer: sampleBuffer, orientation: .up)
            let result = try landmarker.detect(videoFrame: image, timestampInMilliseconds: timestamp)
            let points = (result.landmarks.first ?? []).map {
                LandmarkPoint(x: $0.x, y: $0.y, z: $0.z,
                              visibility: $0.visibility?.floatValue ?? 1,
                              presence: $0.presence?.floatValue ?? 1)
            }
            let buffer = CMSampleBufferGetImageBuffer(sampleBuffer)
            let size = buffer.map { CGSize(width: CVPixelBufferGetWidth($0), height: CVPixelBufferGetHeight($0)) } ?? .zero
            let frame = PoseFrame(points: points, size: size, monotonicMs: Int64(timestamp),
                                  wallClockMs: Int64(Date().timeIntervalSince1970 * 1000))
            DispatchQueue.main.async { handler(token, .success(frame)) }
        } catch {
            DispatchQueue.main.async { handler(token, .failure(error)) }
        }
    }

    enum CameraError: LocalizedError {
        case noCamera, configuration, missingModel
        var errorDescription: String? {
            switch self {
            case .noCamera: return "No camera is available. Run camera monitoring on a physical iPhone or iPad."
            case .configuration: return "The camera could not be configured. Please try again."
            case .missingModel: return "The bundled pose model is missing. Rebuild Heads Up with its resources."
            }
        }
    }
}
