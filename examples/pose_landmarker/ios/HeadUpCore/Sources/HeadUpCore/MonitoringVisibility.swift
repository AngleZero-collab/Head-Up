/// Decides whether visibility permits capture; monitoring intent and camera availability
/// remain the coordinator's responsibility.
public struct MonitoringVisibility: Equatable, Sendable {
    public enum Scene: Equatable, Sendable { case active, inactive, background }
    public enum PictureInPicture: Equatable, Sendable { case stopped, starting, active }

    public var scene: Scene
    public var pictureInPicture: PictureInPicture
    public var protectedDataAvailable: Bool

    public init(scene: Scene = .active, pictureInPicture: PictureInPicture = .stopped,
                protectedDataAvailable: Bool = true) {
        self.scene = scene
        self.pictureInPicture = pictureInPicture
        self.protectedDataAvailable = protectedDataAvailable
    }

    /// Keep the handoff alive between PiP's will-start and did-start callbacks.
    /// A failed/stopped PiP must be changed to `.stopped` by the coordinator.
    public var canProcessFrames: Bool {
        protectedDataAvailable && (scene == .active || pictureInPicture != .stopped)
    }

    public var shouldKeepCapture: Bool { canProcessFrames }
}
