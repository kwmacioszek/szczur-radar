import AppKit

// Snapshot of M-1 in context/foundation/roadmap.md, updated 2026-10-13 (v2.16: S-05 archived (done), FR-022 done in S-02; S-06 risk note; S-07 deployed (#49 54c4fc2, production verified), v2.17: link valid 12 h by default; v2.18: session revocation by credentials version parked; v2.19: S-06 run-monitoring note, run ETA parked; S-03 done and archived before merge (PR #53)).
// Run on macOS: swift tools/render_roadmap.swift
// With the installed Command Line Tools SDK:
// CLANG_MODULE_CACHE_PATH=/private/tmp/szczur-clang-cache \
// SWIFT_MODULE_CACHE_PATH=/private/tmp/szczur-swift-cache \
// swift -sdk /Library/Developer/CommandLineTools/SDKs/MacOSX14.5.sdk tools/render_roadmap.swift
let width = 3200
let height = 3740
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
let coral = color(0xF2889B)

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
    let column: Int, row: Int, accent: NSColor
    var x: CGFloat { Layout.columns[column] }
    var y: CGFloat { Layout.rows[row] }
    var star: Bool { id == "S-02" }
    func top(_ fraction: CGFloat = 0.5) -> (CGFloat, CGFloat) {
        (x + Layout.cardWidth * fraction, y)
    }
    func bottom(_ fraction: CGFloat = 0.5) -> (CGFloat, CGFloat) {
        (x + Layout.cardWidth * fraction, y + Layout.cardHeight)
    }
    var left: (CGFloat, CGFloat) { (x, y + Layout.cardHeight / 2) }
    var right: (CGFloat, CGFloat) { (x + Layout.cardWidth, y + Layout.cardHeight / 2) }
}

// One grid controls every row, card and gap. Stage descriptions have their
// own column; the outer account column keeps S-07's gate arrow clear.
enum Layout {
    static let margin: CGFloat = 100
    static let contentWidth: CGFloat = 2960
    static let stageWidth: CGFloat = 420
    static let columns: [CGFloat] = (0..<4).map { 640 + CGFloat($0) * 630 }
    static let rows: [CGFloat] = (0..<4).map { 440 + CGFloat($0) * 440 }
    static let cardWidth: CGFloat = 530
    static let cardHeight: CGFloat = 260
    static let gateY: CGFloat = 2200
    static let gateHeight: CGFloat = 220
    static let futureY: CGFloat = 2600
    static let futureWidth: CGFloat = 665
    static let futureHeight: CGFloat = 600
    static let futureColumns: [CGFloat] = (0..<4).map { 100 + CGFloat($0) * 765 }
}
func card(_ c: Card) {
    let w = Layout.cardWidth, h = Layout.cardHeight
    if c.star { rounded(c.x - 10, c.y - 10, w + 20, h + 20, 30, cyan.withAlphaComponent(0.08), cyan.withAlphaComponent(0.3), line: 2) }
    rounded(c.x, c.y, w, h, 22, panel, c.star ? cyan : stroke, line: c.star ? 3 : 2)
    rounded(c.x, c.y, 8, h, 4, c.accent)
    txt(c.id, c.x + 30, c.y + 22, 110, 29, .bold, c.accent)
    txt(c.status.uppercased(), c.x + w - 172, c.y + 29, 145, 18, .semibold, muted)
    txt(c.title, c.x + 30, c.y + 74, w - 60, 30, .bold)
    txt(c.detail, c.x + 30, c.y + 123, w - 60, 25, .regular, muted)
    rounded(c.x + 30, c.y + 209, w - 60, 1, 0, stroke)
    txt("WYMAGA  " + c.req, c.x + 30, c.y + 225, w - 60, 20, .medium, c.accent)
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

rounded(100, 100, 10, 93, 5, cyan)
txt("SZCZUR RADAR", 140, 94, 620, 26, .bold, cyan)
txt("ROADMAP  /  MVP", 140, 130, 1800, 68, .heavy)
txt("M-1  ·  Ocenione sygnały z Wykopu dla zaproszonych osób", 140, 224, 2300, 31, .medium, muted)
rounded(2530, 105, 530, 130, 22, panel, stroke, line: 2)
txt("10 ELEMENTÓW DO MVP", 2562, 129, 465, 29, .bold)
txt("3 fundamenty  /  7 wycinków produktu", 2562, 180, 465, 23, .regular, muted)

rounded(100, 300, 2960, 80, 18, color(0x101D30), stroke)
txt("F = fundament", 126, 322, 260, 25, .bold, white)
txt("Przygotowanie techniczne lub operacyjne", 390, 325, 730, 23, .regular, muted)
txt("S = wycinek", 1240, 322, 230, 25, .bold, white)
txt("Działająca funkcja od początku do końca", 1490, 325, 750, 23, .regular, muted)
txt("CZYTAJ Z GÓRY NA DÓŁ ↓", 2600, 324, 430, 22, .bold, cyan)

let stageTitle = ["Fundamenty\ni dostęp", "Pierwszy\nsygnał", "Funkcje\nMVP", "Nadzór\nautomatu"]
let stageSub = ["Prace mogą ruszyć\nrównolegle", "Ocena LLM trafia\ndo użytkownika", "Widoki i codzienny\nprzebieg", "Historia i sygnał\nawarii"]
path([(580,440),(580,2020)], stroke.withAlphaComponent(0.6), 1)
for i in 0..<4 {
    let x = Layout.margin, y = Layout.rows[i]
    rounded(x, y, Layout.stageWidth, Layout.cardHeight, 22, color(0x132B2D), green.withAlphaComponent(0.85), line: 2)
    rounded(x + 26, y + 28, 66, 58, 13, green.withAlphaComponent(0.14))
    txt(String(format:"%02d", i + 1), x + 37, y + 36, 60, 32, .bold, green)
    txt(stageTitle[i], x + 116, y + 27, 275, 29, .bold)
    rounded(x + 26, y + 118, 368, 1, 0, green.withAlphaComponent(0.3))
    txt(stageSub[i], x + 28, y + 148, 365, 26, .medium, white)
}

let cards: [Card] = [
    Card(id:"F-01", title:"Gotowość produkcji", detail:"Dzienna kopia z odtworzeniem\nlimity kosztów i sekrety", req:"—", status:"wdrożone", column:0, row:0, accent:amber),
    Card(id:"F-02", title:"Kontrakt oceny LLM", detail:"Kontrakt V2, walidator i pomiar\nmodel domyślny: GPT-6 Luna", req:"F-03", status:"wdrożone", column:1, row:0, accent:violet),
    Card(id:"F-03", title:"Backend w Javie", detail:"Sesje JDBC, Flyway fix-forward\nEmbabel; to samo API dla SPA", req:"S-01", status:"wdrożone", column:2, row:0, accent:coral),
    Card(id:"S-01", title:"Admin i logowanie", detail:"CLI, sesja, ochrona tras\ni wylogowanie", req:"—", status:"wdrożone", column:3, row:0, accent:green),
    Card(id:"S-02", title:"Pierwsze ocenione posty", detail:"Wykop → ocena LLM → lista\nRęczne uruchomienie przebiegu", req:"S-01 + F-02", status:"wdrożone", column:1, row:1, accent:cyan),
    Card(id:"S-07", title:"Onboarding przez CLI", detail:"Konto, link aktywacyjny\ni odzyskiwanie dostępu", req:"S-01 + F-03", status:"wdrożone", column:3, row:1, accent:green),
    Card(id:"S-05", title:"Codzienny automat", detail:"Cron o 00:30 UTC (dni D-2…D-1)\ni data ostatniej aktualizacji", req:"S-02 + F-01", status:"wdrożone", column:0, row:2, accent:amber),
    Card(id:"S-03", title:"Szczegóły posta", detail:"Tezy, cytaty, surowe dane\ni link do oryginału", req:"S-02", status:"gotowe", column:1, row:2, accent:cyan),
    Card(id:"S-04", title:"Filtrowanie ocen", detail:"Spółka lub temat\ni stanowisko autora", req:"S-02", status:"propozycja", column:2, row:2, accent:cyan),
    Card(id:"S-06", title:"Historia przebiegów", detail:"Dashboard admina\ni widoczny sygnał awarii", req:"S-05 + S-01", status:"propozycja", column:0, row:3, accent:amber)
]
let byID = Dictionary(uniqueKeysWithValues: cards.map { ($0.id, $0) })
func connect(_ source: String, _ target: String, from: CGFloat = 0.5, to: CGFloat = 0.5) {
    let a = byID[source]!, b = byID[target]!
    arrow([a.bottom(from), b.top(to)], a.accent)
}

// Direct downward prerequisite arrows. Independent branches occupy separate columns.
connect("F-01", "S-05")
connect("F-02", "S-02")
connect("S-01", "S-07")
connect("F-03", "S-07", from:0.8, to:0.2)
// The first row reads right to left: S-01 → F-03 → F-02. S-01 → S-02 is
// transitive through that chain, so it is not drawn.
arrow([byID["S-01"]!.left, byID["F-03"]!.right], green)
arrow([byID["F-03"]!.left, byID["F-02"]!.right], coral)
connect("S-02", "S-05", from:0.2, to:0.82)
connect("S-02", "S-03")
connect("S-02", "S-04", from:0.8, to:0.35)
connect("S-05", "S-06")
// S-01 is an explicit additional prerequisite of S-06; its requirement is
// printed on the S-06 card. The graph's transitive path already includes it.
// The four terminal slices converge at the release gate.
for id in ["S-06", "S-03", "S-04", "S-07"] {
    let c = byID[id]!, origin = byID[id]!.bottom()
    arrow([origin, (origin.0, Layout.gateY)], c.accent)
}
for c in cards { card(c) }

rounded(100, Layout.gateY, Layout.contentWidth, Layout.gateHeight, 28, color(0x193148), cyan, line: 3)
rounded(100, Layout.gateY, 10, Layout.gateHeight, 5, cyan)
txt("BRAMKA WYDANIA MVP", 142, Layout.gateY + 30, 2100, 45, .heavy)
txt("Warunek wydania: ukończenie wszystkich 10 elementów — F-01–F-03 oraz S-01–S-07.", 145, Layout.gateY + 103, 2150, 29, .medium)
txt("Poniżej: odroczone kierunki rozwoju. Ich zakres i kolejność wymagają osobnego planu.", 145, Layout.gateY + 160, 2240, 25, .regular, muted)
rounded(2530, Layout.gateY + 52, 485, 115, 20, color(0x0B2332), cyan.withAlphaComponent(0.65), line: 2)
txt("7 / 10 UKOŃCZONYCH", 2562, Layout.gateY + 88, 430, 30, .bold, cyan)

for x in Layout.futureColumns {
    let center = x + Layout.futureWidth / 2
    arrow([(center,Layout.gateY + Layout.gateHeight),(center,Layout.futureY)], violet)
}

struct Parked {
    let title: String, refs: String, description: String
}
let parked: [(label: String, first: Parked, second: Parked)] = [
    ("ŹRÓDŁA I JAKOŚĆ",
     Parked(title:"Reddit i komentarze", refs:"FR-023 + komentarze", description:"Drugie źródło postów oraz rozmowy pod postami, mapowane do wspólnego modelu."),
     Parked(title:"Bogatsza ocena sygnału", refs:"Poza MVP / przyszły pipeline", description:"Spółki, wartość sygnału, nowość, modele ML, obrazki i hardening LLM (kierunek, ironia).")),
    ("ANALIZA I ODKRYWANIE",
     Parked(title:"Wykresy i alerty", refs:"FR-024 / FR-025", description:"Trend sygnałów na wykresie giełdowym i powiadomienia o obiecującym trendzie."),
     Parked(title:"Ask szczur", refs:"FR-032", description:"Pytania do LLM o wybrany sygnał bez opuszczania widoku posta.")),
    ("OBSŁUGA UŻYTKOWNIKA",
     Parked(title:"Sortowanie i eksport", refs:"FR-008 / FR-011b / FR-031", description:"Porządkowanie listy po polach oceny, źródle i dacie oraz eksport CSV/JSON."),
     Parked(title:"Panel kont i e-mail", refs:"FR-016 / 017 / 019 / 020", description:"Zarządzanie kontami w aplikacji, e-mail z zaproszeniem i własna nazwa wyświetlana.")),
    ("OPERACJE I SKALA",
     Parked(title:"Sesje", refs:"FR-029", description:"Krótsze limity bezczynności sesji. Ręczne uruchomienie przebiegu (FR-022) jest już w S-02."),
     Parked(title:"Ochrona i skala", refs:"Poza MVP / infrastruktura", description:"2FA, logowanie zewnętrzne, reset hasła, nowe role, HA, kopie zapasowe, lokalny runner CI, wersja sesji."))
]
for (i, group) in parked.enumerated() {
    let x = Layout.futureColumns[i], y = Layout.futureY
    let w = Layout.futureWidth, h = Layout.futureHeight
    rounded(x, y, w, h, 22, color(0x111E32), stroke, line: 2)
    rounded(x, y, 8, h, 4, violet.withAlphaComponent(0.8))
    txt("PO MVP  ·  ODROCZONE", x + 30, y + 24, w - 60, 20, .bold, violet)
    txt(group.label, x + 30, y + 61, w - 60, 25, .bold)
    rounded(x + 30, y + 110, w - 60, 1, 0, stroke)
    let features = [group.first, group.second]
    for (j, p) in features.enumerated() {
        let top = y + 144 + CGFloat(j) * 230
        txt(p.title, x + 30, top, w - 60, 29, .bold)
        txt(p.refs.uppercased(), x + 30, top + 47, w - 60, 19, .semibold, violet)
        txt(p.description, x + 30, top + 90, w - 60, 25, .regular, muted)
    }
    rounded(x + 30, y + 342, w - 60, 1, 0, stroke)
}

rounded(100, 3300, 2960, 300, 24, color(0x101D30), stroke, line: 2)
txt("LEGENDA I ZASADY CZYTANIA", 136, 3328, 1300, 26, .bold)
let keys: [(CGFloat, String, NSColor)] = [(136,"Kontrakt LLM",violet),(590,"Dostęp i konta",green),(1060,"Przegląd sygnałów",cyan),(1580,"Produkcja i automat",amber),(2100,"Platforma backendu",coral)]
for (x, label, ink) in keys {
    rounded(x, 3393, 54, 8, 4, ink)
    txt(label, x + 74, 3379, 510, 24, .medium)
}
arrow([(2640,3396),(2710,3396)], muted)
txt("Zależność", 2734, 3379, 300, 24, .medium)
txt("S-02: gwiazda przewodnia — pierwszy pełny przepływ od Wykopu do użytkownika. Wyróżnia go obramowanie.", 136, 3445, 2850, 25, .medium)
txt("Pierwszy rząd czytaj od prawej: S-01 → F-03 → F-02. S-01 jest też warunkiem S-02 i S-06 — wynika z tej ścieżki.", 136, 3493, 2850, 24, .regular, muted)
txt("Przed bramką strzałki pokazują zależności. Za bramką wskazują możliwe rozszerzenia, bez ustalonej kolejności.", 136, 3541, 2850, 24, .regular, muted)

txt("Źródło: context/foundation/roadmap.md  ·  M-1 / v2.19  ·  Stan roadmapy: 13.10.2026  ·  Milestone otwarty", 100, 3660, 2200, 22, .regular, muted)
txt("MAPA ZALEŻNOŚCI · BEZ TERMINÓW", 2520, 3660, 540, 20, .semibold, muted)

context.flushGraphics()
NSGraphicsContext.restoreGraphicsState()
let data = bitmap.representation(using: .png, properties: [:])!
try data.write(to: URL(fileURLWithPath: output))
print(output)
