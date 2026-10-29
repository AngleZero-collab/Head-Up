import AppKit
import AVFoundation
import Foundation

// Extract source-video stills at their display resolution; no new artwork is generated.
// Run from the ios directory: swift RunScripts/extract_dragon_stills.swift
let root = URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
let assets = root.appendingPathComponent("HeadUpWidgets/Assets.xcassets")
try FileManager.default.createDirectory(at: assets, withIntermediateDirectories: true)
let catalog = "{\"info\":{\"author\":\"xcode\",\"version\":1}}\n"
try catalog.write(to: assets.appendingPathComponent("Contents.json"), atomically: true, encoding: .utf8)
for (video, name) in [("blue_dragon", "dragonBlue"), ("angry_dragon", "dragonAngry")] {
    let asset = AVURLAsset(url: root.appendingPathComponent("HeadUp/Resources/\(video).mp4"))
    let generator = AVAssetImageGenerator(asset: asset)
    generator.appliesPreferredTrackTransform = true
    generator.maximumSize = CGSize(width: 256, height: 128)
    let frame = try await generator.image(at: CMTime(seconds: 0.5, preferredTimescale: 600)).image
    let side = min(frame.width, frame.height)
    guard let image = frame.cropping(to: CGRect(x: (frame.width - side) / 2, y: (frame.height - side) / 2,
                                               width: side, height: side)) else {
        fatalError("Could not extract centered dragon still")
    }
    let folder = assets.appendingPathComponent("\(name).imageset")
    try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    guard let data = NSBitmapImageRep(cgImage: image).representation(using: .png, properties: [:]) else {
        fatalError("Could not encode \(name)")
    }
    try data.write(to: folder.appendingPathComponent("\(name).png"))
    let contents = "{\"images\":[{\"filename\":\"\(name).png\",\"idiom\":\"universal\"}],\"info\":{\"author\":\"xcode\",\"version\":1}}\n"
    try contents.write(to: folder.appendingPathComponent("Contents.json"), atomically: true, encoding: .utf8)
    print("\(name): \(image.width)×\(image.height)")
}
