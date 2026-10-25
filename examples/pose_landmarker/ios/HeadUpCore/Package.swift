// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "HeadUpCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "HeadUpCore", targets: ["HeadUpCore"])],
    targets: [
        .target(name: "HeadUpCore"),
        .testTarget(name: "HeadUpCoreTests", dependencies: ["HeadUpCore"]),
    ]
)
