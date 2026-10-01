import AppKit

let destination = URL(fileURLWithPath: CommandLine.arguments[1])
try FileManager.default.createDirectory(at: destination, withIntermediateDirectories: true)
let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: 1024, pixelsHigh: 1024, bitsPerSample: 8,
    samplesPerPixel: 3, hasAlpha: false, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
let bounds = NSRect(x: 0, y: 0, width: 1024, height: 1024)
let background = NSGradient(starting: NSColor(srgbRed: 0.04, green: 0.23, blue: 0.56, alpha: 1),
                            ending: NSColor(srgbRed: 0.09, green: 0.60, blue: 0.86, alpha: 1))!
background.draw(in: bounds, angle: 60)
NSColor.white.withAlphaComponent(0.12).setFill()
NSBezierPath(ovalIn: NSRect(x: 520, y: 570, width: 600, height: 600)).fill()
let shaft = NSBezierPath()
shaft.move(to: NSPoint(x: 512, y: 735)); shaft.line(to: NSPoint(x: 512, y: 400))
shaft.move(to: NSPoint(x: 345, y: 550)); shaft.line(to: NSPoint(x: 512, y: 380)); shaft.line(to: NSPoint(x: 679, y: 550))
shaft.lineWidth = 74; shaft.lineCapStyle = .round; shaft.lineJoinStyle = .round
NSColor.white.setStroke(); shaft.stroke()
let base = NSBezierPath(roundedRect: NSRect(x: 280, y: 242, width: 464, height: 65), xRadius: 32.5, yRadius: 32.5)
NSColor(srgbRed: 0.65, green: 0.95, blue: 0.87, alpha: 1).setFill(); base.fill()
NSGraphicsContext.restoreGraphicsState()
try bitmap.representation(using: .png, properties: [:])!.write(to: destination.appendingPathComponent("AppIcon.png"))
let contents = #"{"images":[{"filename":"AppIcon.png","idiom":"universal","platform":"ios","size":"1024x1024"}],"info":{"author":"xcode","version":1}}"#
try contents.write(to: destination.appendingPathComponent("Contents.json"), atomically: true, encoding: .utf8)
