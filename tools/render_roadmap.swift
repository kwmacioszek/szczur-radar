import AppKit

// Renders the current M-1 dependency map from context/foundation/roadmap.md.
// Run: swift tools/render_roadmap.swift
let width = 2800
let height = 1760
let output = "docs/visuals/roadmap-mvp.png"

func color(_ hex: UInt32, _ alpha: CGFloat = 1) -> NSColor {
    NSColor(calibratedRed: CGFloat((hex >> 16) & 0xff) / 255,
            green: CGFloat((hex >> 8) & 0xff) / 255,
            blue: CGFloat(hex & 0xff) / 255, alpha: alpha)
}
let bg = color(0x0B1220)
let panel = color(0x142238)
let stroke = color(0x33465F)
let muted = color(0x9FB1C8)
let white = color(0xF4F7FC)
let cyan = color(0x59D8F7)
let violet = color(0xAE8CFF)
let amber = color(0xF4BC62)
let green = color(0x66D9AC)

func rect(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) -> NSRect {
    NSRect(x: x, y: CGFloat(height) - y - h, width: w, height: h)
}
func rounded(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ radius: CGFloat,
             _ fill: NSColor, _ border: NSColor? = nil, line: CGFloat = 1) {
    let p = NSBezierPath(roundedRect: rect(x, y, w, h), xRadius: radius, yRadius: radius)
    fill.setFill(); p.fill()
    if let border { border.setStroke(); p.lineWidth = line; p.stroke() }
}
func txt(_ s: String, _ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ size: CGFloat,
         _ weight: NSFont.Weight = .regular, _ ink: NSColor = white,
         lineHeight: CGFloat? = nil) {
    let style = NSMutableParagraphStyle()
    style.lineBreakMode = .byWordWrapping
    style.lineSpacing = lineHeight ?? 4
    let a = NSAttributedString(string: s, attributes: [
        .font: NSFont.systemFont(ofSize: size, weight: weight),
        .foregroundColor: ink,
        .paragraphStyle: style
    ])
    a.draw(in: rect(x, y, w, 220))
}
func path(_ points: [(CGFloat, CGFloat)], _ ink: NSColor, _ line: CGFloat = 4,
          dash: [CGFloat] = []) {
    guard let first = points.first else { return }
    let p = NSBezierPath()
    p.move(to: NSPoint(x: first.0, y: CGFloat(height) - first.1))
    for q in points.dropFirst() { p.line(to: NSPoint(x: q.0, y: CGFloat(height) - q.1)) }
    p.lineWidth = line
    p.lineCapStyle = .round
    p.lineJoinStyle = .round
    if !dash.isEmpty { p.setLineDash(dash, count: dash.count, phase: 0) }
    ink.setStroke(); p.stroke()
}
func arrow(_ points: [(CGFloat, CGFloat)], _ ink: NSColor, dashed: Bool = false) {
    path(points, ink.withAlphaComponent(dashed ? 0.42 : 0.74), dashed ? 3 : 4, dash: dashed ? [11, 10] : [])
    guard let end = points.last, let prev = points.dropLast().last else { return }
    let dx = end.0 - prev.0, dy = end.1 - prev.1
    let len = hypot(dx, dy), ux = dx / len, uy = dy / len
    let ax = end.0 - ux * 17, ay = end.1 - uy * 17
    path([(ax - uy * 9, ay + ux * 9), end, (ax + uy * 9, ay - ux * 9)], ink, 4)
}
struct Card {
    let id: String, title: String, detail: String, req: String, status: String
    let x: CGFloat, y: CGFloat, w: CGFloat, h: CGFloat, accent: NSColor
    let star: Bool
}
func card(_ c: Card) {
    if c.star { rounded(c.x - 11, c.y - 11, c.w + 22, c.h + 22, 30, cyan.withAlphaComponent(0.09), cyan.withAlphaComponent(0.32), line: 2) }
    rounded(c.x, c.y, c.w, c.h, 23, panel, c.star ? cyan : stroke, line: c.star ? 3 : 2)
    rounded(c.x, c.y, 10, c.h, 5, c.accent)
    txt(c.id, c.x + 32, c.y + 22, 120, 28, .bold, c.accent)
    txt(c.status.uppercased(), c.x + c.w - 153, c.y + 26, 130, 17, .semibold, muted)
    txt(c.title, c.x + 32, c.y + 67, c.w - 58, 29, .bold)
    txt(c.detail, c.x + 32, c.y + 115, c.w - 58, 22, .regular, muted)
    rounded(c.x + 30, c.y + c.h - 51, c.w - 60, 1, 0, stroke)
    txt("WYMAGA  " + c.req, c.x + 32, c.y + c.h - 38, c.w - 60, 18, .medium, c.accent)
}

let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: width, pixelsHigh: height,
    bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
    colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
let context = NSGraphicsContext(bitmapImageRep: bitmap)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = context
context.imageInterpolation = .high
bg.setFill(); NSBezierPath(rect: rect(0, 0, CGFloat(width), CGFloat(height))).fill()

// Restrained blueprint grid.
for x in stride(from: CGFloat(80), through: CGFloat(width), by: 80) {
    path([(x, 0), (x, CGFloat(height))], color(0x24354C, 0.21), 1)
}
for y in stride(from: CGFloat(80), through: CGFloat(height), by: 80) {
    path([(0, y), (CGFloat(width), y)], color(0x24354C, 0.21), 1)
}

rounded(112, 94, 12, 84, 5, cyan)
txt("SZCZUR RADAR", 150, 94, 620, 25, .bold, cyan)
txt("ROADMAP  /  MVP", 150, 126, 1500, 67, .heavy)
txt("M-1  ·  Ocenione sygnały z Wykopu dla zaproszonych osób", 153, 215, 1950, 31, .medium, muted)
rounded(2270, 105, 380, 120, 22, panel, stroke, line: 2)
txt("9 ELEMENTÓW", 2300, 129, 320, 25, .bold)
txt("2 fundamenty / 7 wycinków", 2300, 172, 320, 21, .regular, muted)

let phaseX: [CGFloat] = [140, 755, 1370, 1985]
let phaseTitle = ["01 / START RÓWNOLEGŁY", "02 / PIERWSZY SYGNAŁ", "03 / ROZBUDOWA MVP", "04 / NADZÓR"]
let phaseSub = ["Fundamenty i dostęp", "Główna hipoteza produktu", "Widoki i automatyzacja", "Kontrola przebiegów"]
for i in 0..<4 {
    rounded(phaseX[i], 333, 514, 3, 0, i == 1 ? cyan : stroke)
    txt(phaseTitle[i], phaseX[i], 353, 514, 23, .bold, i == 1 ? cyan : white)
    txt(phaseSub[i], phaseX[i], 392, 514, 20, .regular, muted)
}

let cards: [Card] = [
    Card(id:"F-02", title:"Kontrakt oceny LLM", detail:"Walidacja V1 i próby na rzeczywistym modelu", req:"—", status:"planowanie", x:140, y:474, w:514, h:210, accent:violet, star:false),
    Card(id:"S-01", title:"Admin i logowanie", detail:"CLI, sesja, ochrona tras i wylogowanie", req:"—", status:"planowanie", x:140, y:797, w:514, h:210, accent:green, star:false),
    Card(id:"F-01", title:"Bramka danych produkcyjnych", detail:"Limity kosztów, restore drill, sekrety", req:"—", status:"do startu", x:140, y:1120, w:514, h:210, accent:amber, star:false),
    Card(id:"S-02", title:"Ocenione posty z Wykopu", detail:"Pobranie → ocena LLM → lista postów", req:"S-01 + F-02", status:"propozycja", x:755, y:636, w:514, h:228, accent:cyan, star:true),
    Card(id:"S-07", title:"Onboarding przez CLI", detail:"Konto, link aktywacyjny, odzyskiwanie", req:"S-01", status:"propozycja", x:755, y:1108, w:514, h:210, accent:green, star:false),
    Card(id:"S-03", title:"Szczegóły posta", detail:"Tezy, cytaty, surowe dane, oryginał", req:"S-02", status:"propozycja", x:1370, y:474, w:514, h:210, accent:cyan, star:false),
    Card(id:"S-04", title:"Filtrowanie ocen", detail:"Spółka lub temat + stanowisko autora", req:"S-02", status:"propozycja", x:1370, y:797, w:514, h:210, accent:cyan, star:false),
    Card(id:"S-05", title:"Codzienny automat", detail:"Harmonogram i data ostatniej aktualizacji", req:"S-02 + F-01", status:"propozycja", x:1370, y:1120, w:514, h:210, accent:amber, star:false),
    Card(id:"S-06", title:"Historia przebiegów", detail:"Dashboard admina i sygnał awarii", req:"S-05 + S-01", status:"propozycja", x:1985, y:1120, w:514, h:210, accent:amber, star:false)
]

// Edges are routed in the gutters so labels stay legible.
arrow([(654,579),(704,579),(704,692),(755,692)], violet)
arrow([(654,902),(704,902),(704,805),(755,805)], green)
arrow([(654,902),(704,902),(704,1150),(755,1150)], green)
arrow([(1269,695),(1320,695),(1320,579),(1370,579)], cyan)
arrow([(1269,750),(1320,750),(1320,902),(1370,902)], cyan)
arrow([(1269,815),(1320,815),(1320,1200),(1370,1200)], cyan)
arrow([(654,1270),(690,1270),(690,1364),(1320,1364),(1320,1270),(1370,1270)], amber)
arrow([(1884,1225),(1985,1225)], amber)
// S-01 is an explicit additional prerequisite of S-06; its requirement is
// printed on the S-06 card. The graph's transitive path already includes it.
for c in cards { card(c) }

rounded(140, 1418, 2360, 218, 28, color(0x101D30), stroke, line: 2)
txt("LEGENDA", 177, 1453, 250, 24, .bold)
rounded(178, 1505, 62, 10, 5, violet)
txt("Kontrakt LLM", 259, 1492, 270, 21, .medium)
rounded(538, 1505, 62, 10, 5, green)
txt("Dostęp i konta", 619, 1492, 290, 21, .medium)
rounded(953, 1505, 62, 10, 5, cyan)
txt("Przegląd sygnałów", 1034, 1492, 340, 21, .medium)
rounded(1418, 1505, 62, 10, 5, amber)
txt("Produkcja i automat", 1499, 1492, 340, 21, .medium)
arrow([(1890,1510),(1960,1510)], muted)
txt("Warunek wstępny", 1982, 1492, 330, 21, .medium)
txt("Karty mogą powstawać równolegle, gdy nie łączy ich strzałka. S-02 to gwiazda przewodnia. S-01 → S-06 wynika też z łańcucha przez S-02 i S-05.",
    178, 1565, 2220, 21, .regular, muted)
txt("Źródło: context/foundation/roadmap.md  ·  M-1 / v2  ·  27.09.2026  ·  Status: otwarty", 145, 1683, 1950, 19, .regular, muted)
txt("ODROCZONE: REDDIT · WYKRESY · POWIADOMIENIA · ASK SZCZUR", 1530, 1683, 1120, 18, .semibold, color(0x71849C))

context.flushGraphics()
NSGraphicsContext.restoreGraphicsState()
let data = bitmap.representation(using: .png, properties: [:])!
try data.write(to: URL(fileURLWithPath: output))
print(output)
