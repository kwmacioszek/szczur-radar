import AppKit

// Renders the current M-1 dependency map from context/foundation/roadmap.md.
// Run: swift tools/render_roadmap.swift
let width = 2800
let height = 3180
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
let stageY: [CGFloat] = [290, 690, 1090, 1440]
let stageTitle = ["01 / FUNDAMENTY I DOSTĘP", "02 / PIERWSZY SYGNAŁ", "03 / FUNKCJE MVP", "04 / NADZÓR AUTOMATU"]
let stageSub = ["Prace mogą ruszyć równolegle", "Ocena LLM trafia do użytkownika", "Widoki i codzienny przebieg", "Historia i sygnał awarii"]
for i in 0..<4 {
    rounded(20, stageY[i] - 10, 455, 60, 16, color(0x1B3048), green, line: 2)
    rounded(20, stageY[i] - 10, 9, 60, 4, green)
    txt(stageTitle[i], 47, stageY[i] + 2, 410, 25, .bold)
    rounded(20, stageY[i] + 55, 455, 50, 14, color(0x152C2E), green, line: 2)
    txt(stageSub[i], 43, stageY[i] + 64, 420, 21, .medium, white)
}

let cards: [Card] = [
    Card(id:"F-01", title:"Bramka danych produkcyjnych", detail:"Limity kosztów, restore drill, sekrety", req:"—", status:"do startu", x:160, y:410, w:650, h:220, accent:amber, star:false),
    Card(id:"F-02", title:"Kontrakt oceny LLM", detail:"Walidacja V1 i próby na rzeczywistym modelu", req:"—", status:"planowanie", x:1075, y:410, w:650, h:220, accent:violet, star:false),
    Card(id:"S-01", title:"Admin i logowanie", detail:"CLI, sesja, ochrona tras i wylogowanie", req:"—", status:"planowanie", x:1990, y:410, w:650, h:220, accent:green, star:false),
    Card(id:"S-02", title:"Ocenione posty z Wykopu", detail:"Pobranie → ocena LLM → lista postów", req:"S-01 + F-02", status:"propozycja", x:1075, y:810, w:650, h:230, accent:cyan, star:true),
    Card(id:"S-07", title:"Onboarding przez CLI", detail:"Konto, link aktywacyjny, odzyskiwanie", req:"S-01", status:"propozycja", x:1990, y:810, w:650, h:220, accent:green, star:false),
    Card(id:"S-05", title:"Codzienny automat", detail:"Harmonogram i data ostatniej aktualizacji", req:"S-02 + F-01", status:"propozycja", x:160, y:1210, w:650, h:220, accent:amber, star:false),
    Card(id:"S-03", title:"Szczegóły posta", detail:"Tezy, cytaty, surowe dane, oryginał", req:"S-02", status:"propozycja", x:1075, y:1210, w:650, h:220, accent:cyan, star:false),
    Card(id:"S-04", title:"Filtrowanie ocen", detail:"Spółka lub temat + stanowisko autora", req:"S-02", status:"propozycja", x:1990, y:1210, w:650, h:220, accent:cyan, star:false),
    Card(id:"S-06", title:"Historia przebiegów", detail:"Dashboard admina i sygnał awarii", req:"S-05 + S-01", status:"propozycja", x:160, y:1560, w:650, h:220, accent:amber, star:false)
]

// Direct downward prerequisite arrows. Independent branches occupy separate columns.
arrow([(485,630),(485,1210)], amber)
arrow([(1400,630),(1400,810)], violet)
arrow([(2315,630),(2315,810)], green)
arrow([(2100,630),(1650,810)], green)
arrow([(1170,1040),(700,1210)], cyan)
arrow([(1400,1040),(1400,1210)], cyan)
arrow([(1630,1040),(2100,1210)], cyan)
arrow([(485,1430),(485,1560)], amber)
// S-01 is an explicit additional prerequisite of S-06; its requirement is
// printed on the S-06 card. The graph's transitive path already includes it.
for c in cards { card(c) }

// The four terminal slices converge at the release gate.
arrow([(485,1780),(485,1830)], amber)
arrow([(1400,1430),(1400,1830)], cyan)
arrow([(2315,1430),(2315,1830)], cyan)
arrow([(1990,1030),(1810,1830)], green)

rounded(140, 1830, 2500, 160, 30, color(0x193148), cyan, line: 4)
rounded(140, 1830, 14, 160, 6, cyan)
txt("BRAMKA WYDANIA MVP", 190, 1850, 1280, 43, .heavy)
txt("Otwiera się po ukończeniu F-01, F-02 oraz S-01–S-07. Cztery końcowe wycinki zbiegają się tutaj.",
    192, 1920, 1740, 25, .medium, muted)
txt("Dalej: niezależne kierunki rozwoju po MVP.", 192, 1954, 1550, 20, .regular, cyan)
rounded(2220, 1862, 355, 90, 20, color(0x0B2332), cyan.withAlphaComponent(0.65), line: 2)
txt("0 / 9 UKOŃCZONYCH", 2250, 1882, 300, 27, .bold, cyan)

for x in [CGFloat(397), 1012, 1627, 2242] {
    arrow([(x,1990),(x,2140)], violet)
}

struct Parked {
    let title: String, refs: String, description: String
}
let parked: [(label: String, first: Parked, second: Parked)] = [
    ("01 / ŹRÓDŁA I JAKOŚĆ",
     Parked(title:"Reddit i komentarze", refs:"FR-023 + komentarze", description:"Drugie źródło postów oraz rozmowy pod postami, mapowane do wspólnego modelu."),
     Parked(title:"Bogatsza ocena sygnału", refs:"Poza MVP / przyszły pipeline", description:"Rozpoznawanie spółek, decyzja o wartości sygnału, nowość, zaangażowanie i modele ML.")),
    ("02 / ANALIZA I ODKRYWANIE",
     Parked(title:"Wykresy i alerty", refs:"FR-024 / FR-025", description:"Trend sygnałów na wykresie giełdowym i powiadomienia o obiecującym trendzie."),
     Parked(title:"Ask szczur", refs:"FR-032", description:"Pytania do LLM o wybrany sygnał bez opuszczania widoku posta.")),
    ("03 / OBSŁUGA UŻYTKOWNIKA",
     Parked(title:"Sortowanie i eksport", refs:"FR-008 / FR-011b / FR-031", description:"Porządkowanie listy po polach oceny, źródle i dacie oraz eksport CSV/JSON."),
     Parked(title:"Panel kont i e-mail", refs:"FR-016 / 017 / 019 / 020", description:"Zarządzanie kontami w aplikacji, e-mail z zaproszeniem i własna nazwa wyświetlana.")),
    ("04 / OPERACJE I SKALA",
     Parked(title:"Sterowanie i sesje", refs:"FR-022 / FR-029", description:"Ręczne uruchomienie pipeline’u z dashboardu i automatyczne wygasanie sesji."),
     Parked(title:"Ochrona i skala", refs:"Poza MVP / infrastruktura", description:"2FA, logowanie zewnętrzne, reset hasła, nowe role, HA i własne kopie zapasowe."))
]
for (i, group) in parked.enumerated() {
    let x = phaseX[i]
    let y: CGFloat = 2140
    rounded(x, y, 514, 590, 22, color(0x111E32), stroke, line: 2)
    rounded(x, y, 8, 590, 4, violet.withAlphaComponent(0.8))
    txt("PO MVP  ·  " + group.label, x + 28, y + 26, 458, 19, .bold, violet)
    rounded(x + 28, y + 78, 458, 1, 0, stroke)
    let features = [group.first, group.second]
    for (j, p) in features.enumerated() {
        let top = y + (j == 0 ? 108 : 340)
        txt(p.title, x + 28, top, 458, 27, .bold)
        txt(p.refs.uppercased(), x + 28, top + 43, 458, 17, .semibold, violet)
        txt(p.description, x + 28, top + 82, 458, 21, .regular, muted)
    }
    rounded(x + 28, y + 321, 458, 1, 0, stroke)
}

rounded(140, 2800, 2360, 268, 28, color(0x101D30), stroke, line: 2)
txt("LEGENDA", 177, 2832, 250, 24, .bold)
rounded(178, 2884, 62, 10, 5, violet)
txt("Kontrakt LLM", 259, 2871, 270, 21, .medium)
rounded(538, 2884, 62, 10, 5, green)
txt("Dostęp i konta", 619, 2871, 290, 21, .medium)
rounded(953, 2884, 62, 10, 5, cyan)
txt("Przegląd sygnałów", 1034, 2871, 340, 21, .medium)
rounded(1418, 2884, 62, 10, 5, amber)
txt("Produkcja i automat", 1499, 2871, 340, 21, .medium)
arrow([(1890,2889),(1960,2889)], muted)
txt("Warunek wstępny", 1982, 2871, 330, 21, .medium)
txt("Przed bramką: strzałka = warunek wstępny; bez strzałki prace mogą iść równolegle. Po bramce: strzałka = możliwy kierunek rozwoju.",
    178, 2935, 2220, 20, .regular, muted)
txt("S-02 to gwiazda przewodnia. Warunek S-01 → S-06 wynika także z łańcucha przez S-02 i S-05.",
    178, 2974, 2220, 20, .regular, muted)
txt("F = fundament: przygotowanie techniczne lub operacyjne.   S = wycinek: działająca funkcja produktu od początku do końca.",
    178, 3013, 2220, 21, .medium, white)

txt("Źródło: context/foundation/roadmap.md  ·  M-1 / v2  ·  27.09.2026  ·  Status: otwarty", 145, 3120, 1950, 19, .regular, muted)
txt("KOLEJNOŚĆ WYNIKA Z ZALEŻNOŚCI, NIE Z DAT", 1850, 3120, 800, 18, .semibold, color(0x71849C))

context.flushGraphics()
NSGraphicsContext.restoreGraphicsState()
let data = bitmap.representation(using: .png, properties: [:])!
try data.write(to: URL(fileURLWithPath: output))
print(output)
