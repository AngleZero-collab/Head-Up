import AppKit

let root = URL(fileURLWithPath: CommandLine.arguments[1])
let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: 1024, pixelsHigh: 1024,
    bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
    colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
NSColor(red: 0.035, green: 0.08, blue: 0.16, alpha: 1).setFill()
NSBezierPath(rect: CGRect(x: 0, y: 0, width: 1024, height: 1024)).fill()
let arrow = NSBezierPath()
arrow.move(to: CGPoint(x: 512, y: 800)); arrow.line(to: CGPoint(x: 280, y: 560))
arrow.move(to: CGPoint(x: 512, y: 800)); arrow.line(to: CGPoint(x: 744, y: 560))
arrow.move(to: CGPoint(x: 512, y: 800)); arrow.line(to: CGPoint(x: 512, y: 300))
arrow.lineWidth = 76; arrow.lineCapStyle = .round; arrow.lineJoinStyle = .round
NSColor(red: 0.22, green: 0.78, blue: 0.96, alpha: 1).setStroke(); arrow.stroke()
let text = "Heads Up" as NSString
let attributes: [NSAttributedString.Key: Any] = [.font: NSFont.systemFont(ofSize: 120, weight: .bold), .foregroundColor: NSColor.white]
let size = text.size(withAttributes: attributes)
text.draw(at: CGPoint(x: (1024-size.width)/2, y: 100), withAttributes: attributes)
NSGraphicsContext.restoreGraphicsState()
try bitmap.representation(using: .png, properties: [:])!.write(to: root)
