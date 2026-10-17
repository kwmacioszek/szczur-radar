import AppKit

// Snapshot of M-2 in context/foundation/roadmap.md, 2026-10-17 (v3: M-1 closed with all 10 items done;
// M-2 "MVP Quality & Reliability Hardening" opened from the post-MVP audit — F-04 and S-08–S-19,
// north star S-08; pipeline without Embabel moved from Parked to S-17).
// Run on macOS: swift tools/render_roadmap.swift
// With the installed Command Line Tools SDK:
// CLANG_MODULE_CACHE_PATH=/private/tmp/szczur-clang-cache \
// SWIFT_MODULE_CACHE_PATH=/private/tmp/szczur-swift-cache \
// swift -sdk /Library/Developer/CommandLineTools/SDKs/MacOSX14.5.sdk tools/render_roadmap.swift
let width = 3200
let height = 3260
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

enum Status: String {
    case ready = "gotowe", proposed = "proponowane", blocked = "zablokowane"
}
struct Card {
    let id: String, title: String, detail: String, req: String, status: Status
    let column: Int, row: Int, accent: NSColor
    var x: CGFloat { Layout.columns[column] }
    var y: CGFloat { Layout.rows[row] }
    var star: Bool { id == "S-08" }
    func top(_ fraction: CGFloat = 0.5) -> (CGFloat, CGFloat) {
        (x + Layout.cardWidth * fraction, y)
    }
    func bottom(_ fraction: CGFloat = 0.5) -> (CGFloat, CGFloat) {
        (x + Layout.cardWidth * fraction, y + Layout.cardHeight)
    }
    var left: (CGFloat, CGFloat) { (x, y + Layout.cardHeight / 2) }
    var right: (CGFloat, CGFloat) { (x + Layout.cardWidth, y + Layout.cardHeight / 2) }
}

// One grid controls every row, card and gap: five card columns, one per stream
// (stream C, the engine, takes two), three rows at most per column.
enum Layout {
    static let margin: CGFloat = 100
    static let contentWidth: CGFloat = 2960
    static let columns: [CGFloat] = (0..<5).map { 100 + CGFloat($0) * 600 }
    static let streamY: CGFloat = 590
    static let streamHeight: CGFloat = 96
    static let rows: [CGFloat] = (0..<3).map { 730 + CGFloat($0) * 340 }
    static let cardWidth: CGFloat = 530
    static let cardHeight: CGFloat = 260
    static let gateY: CGFloat = 1790
    static let gateHeight: CGFloat = 220
    static let futureY: CGFloat = 2110
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
    txt(c.status.rawValue.uppercased(), c.x + w - 222, c.y + 29, 195, 18, .semibold, c.status == .blocked ? coral : muted)
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
txt("ROADMAP  /  MVP HARDENING", 140, 130, 2300, 68, .heavy)
txt("M-2  ·  MVP Quality & Reliability Hardening  ·  niezawodność, wdrożenia, testy, obserwowalność", 140, 224, 2350, 31, .medium, muted)
rounded(2530, 105, 530, 130, 22, panel, stroke, line: 2)
txt("13 ELEMENTÓW M-2", 2562, 129, 465, 29, .bold)
txt("1 fundament  /  12 wycinków", 2562, 180, 465, 23, .regular, muted)

// The closed milestone, in one band.
rounded(100, 300, 2960, 110, 18, color(0x10261F), green.withAlphaComponent(0.7), line: 2)
rounded(100, 300, 10, 110, 5, green)
txt("M-1 ZAMKNIĘTY  ·  17.10.2026", 140, 322, 900, 27, .bold, green)
txt("MVP — ocenione sygnały z Wykopu dla zaproszonych osób: 10 / 10 elementów (F-01–F-03, S-01–S-07) na produkcji.", 140, 362, 2700, 23, .regular, muted)
txt("10 / 10", 2860, 330, 180, 40, .heavy, green)

rounded(100, 460, 2960, 80, 18, color(0x101D30), stroke)
txt("F = fundament", 126, 482, 260, 25, .bold, white)
txt("Minimalne przygotowanie, które odblokowuje wycinki", 390, 485, 820, 23, .regular, muted)
txt("S = wycinek", 1240, 482, 230, 25, .bold, white)
txt("Działająca zmiana od początku do końca", 1490, 485, 750, 23, .regular, muted)
txt("CZYTAJ Z GÓRY NA DÓŁ ↓", 2600, 484, 430, 22, .bold, cyan)

// Stream headers: A, B, C (two columns), D.
let streams: [(label: String, sub: String, first: Int, span: Int, ink: NSColor)] = [
    ("A · Sygnał w aplikacji", "Gwiazda przewodnia", 0, 1, cyan),
    ("B · Kanał poza aplikacją", "Czeka na decyzję o kanale", 1, 1, amber),
    ("C · Próby i silnik", "Niezawodność rdzenia przebiegu", 2, 2, violet),
    ("D · Wdrożenia i dane", "Bramka, kopia, dokumenty", 4, 1, green)
]
for s in streams {
    let x = Layout.columns[s.first]
    let w = Layout.cardWidth + CGFloat(s.span - 1) * 600
    rounded(x, Layout.streamY, w, Layout.streamHeight, 18, color(0x101D30), s.ink.withAlphaComponent(0.7), line: 2)
    txt(s.label, x + 24, Layout.streamY + 14, w - 48, 27, .bold, s.ink)
    txt(s.sub, x + 24, Layout.streamY + 55, w - 48, 21, .regular, muted)
}

let cards: [Card] = [
    Card(id:"S-08", title:"Pusty przebieg = sygnał", detail:"0 pobranych lub pominięte\n→ sygnał, świeżość stoi", req:"—", status:.ready, column:0, row:0, accent:cyan),
    Card(id:"S-12", title:"Powiadomienia nie giną", detail:"Każde należne powiadomienie;\nkroki zgodne ze statusem", req:"S-08", status:.proposed, column:0, row:1, accent:cyan),
    Card(id:"F-04", title:"Kanał alarmowy", detail:"Jeden kanał poza aplikacją\nw limicie kosztów", req:"decyzji o kanale", status:.blocked, column:1, row:0, accent:amber),
    Card(id:"S-13", title:"Brakująca noc", detail:"Cron lub backup nie ruszył\n→ alarm; dziury w dniach", req:"F-04", status:.proposed, column:1, row:1, accent:amber),
    Card(id:"S-18", title:"Widoczne błędy", detail:"Błędy ERROR do operatora;\nbez kluczy w logach", req:"F-04", status:.proposed, column:1, row:2, accent:amber),
    Card(id:"S-09", title:"Próby postów", detail:"Awaria dostawcy nie zabija\npostów; limit błędów", req:"—", status:.ready, column:2, row:0, accent:violet),
    Card(id:"S-17", title:"Przebieg bez Embabel", detail:"Przerwanie dociera do oceny;\nskupione testy silnika", req:"S-09", status:.proposed, column:2, row:1, accent:violet),
    Card(id:"S-10", title:"Konfiguracja LLM", detail:"LLM_TIMEOUT bez jednostki\nzatrzymuje start", req:"—", status:.ready, column:3, row:0, accent:violet),
    Card(id:"S-11", title:"Utknięty przebieg", detail:"Wraca bez restartu API;\nwyścig claim domknięty", req:"—", status:.ready, column:3, row:1, accent:violet),
    Card(id:"S-16", title:"Kompletny Wykop", detail:"Stronicowanie nie gubi;\nchwilowy błąd ponowiony", req:"—", status:.ready, column:3, row:2, accent:violet),
    Card(id:"S-14", title:"Bramka wdrożeń", detail:"Testy przed IaC, crony z CI,\nkontrola zdrowia po apply", req:"—", status:.ready, column:4, row:0, accent:green),
    Card(id:"S-15", title:"Pełna kontrola kopii", detail:"Wszystkie tabele;\nodrzuca pustą bazę", req:"—", status:.ready, column:4, row:1, accent:green),
    Card(id:"S-19", title:"Prawdziwe runbooki", detail:"Aktualny deploy-plan,\nmniej zmiennych IaC", req:"—", status:.ready, column:4, row:2, accent:green)
]
let byID = Dictionary(uniqueKeysWithValues: cards.map { ($0.id, $0) })
func connect(_ source: String, _ target: String) {
    let a = byID[source]!, b = byID[target]!
    arrow([a.bottom(), b.top()], a.accent)
}

// Prerequisite arrows. F-04 → S-18 runs in the gap right of column B, past S-13.
connect("S-08", "S-12")
connect("F-04", "S-13")
connect("S-09", "S-17")
let f04 = byID["F-04"]!, s18 = byID["S-18"]!, laneX = f04.x + Layout.cardWidth + 35
arrow([f04.right, (laneX, f04.right.1), (laneX, s18.right.1), s18.right], amber)
// Every column ends at the milestone gate.
for id in ["S-12", "S-18", "S-17", "S-16", "S-19"] {
    let c = byID[id]!, origin = c.bottom()
    arrow([origin, (origin.0, Layout.gateY)], c.accent)
}
for c in cards { card(c) }

rounded(100, Layout.gateY, Layout.contentWidth, Layout.gateHeight, 28, color(0x193148), cyan, line: 3)
rounded(100, Layout.gateY, 10, Layout.gateHeight, 5, cyan)
txt("BRAMKA M-2", 142, Layout.gateY + 30, 2100, 45, .heavy)
txt("Warunek: ukończenie wszystkich 13 elementów — F-04 oraz S-08–S-19. Potem kolejna roadmapa funkcjonalna.", 145, Layout.gateY + 103, 2300, 29, .medium)
txt("Poniżej: odroczone kierunki rozwoju. Ich zakres i kolejność wymagają osobnego planu.", 145, Layout.gateY + 160, 2240, 25, .regular, muted)
rounded(2530, Layout.gateY + 52, 485, 115, 20, color(0x0B2332), cyan.withAlphaComponent(0.65), line: 2)
txt("0 / 13 UKOŃCZONYCH", 2562, Layout.gateY + 88, 430, 30, .bold, cyan)

for x in Layout.futureColumns {
    let center = x + Layout.futureWidth / 2
    arrow([(center,Layout.gateY + Layout.gateHeight),(center,Layout.futureY)], violet)
}

struct Parked {
    let title: String, refs: String, description: String
}
let parked: [(label: String, first: Parked, second: Parked)] = [
    ("ŹRÓDŁA I JAKOŚĆ",
     Parked(title:"Reddit i komentarze", refs:"FR-023 + izolacja źródeł", description:"Drugie źródło postów, rozmowy pod postami, awaria źródła bez skutków dla innych."),
     Parked(title:"Bogatsza ocena sygnału", refs:"Poza MVP / przyszły pipeline", description:"Spółki, wartość sygnału, modele ML, obrazki, hardening LLM i ochrona przed dryfem modelu.")),
    ("ANALIZA I ODKRYWANIE",
     Parked(title:"Wykresy i alerty", refs:"FR-024 / FR-025", description:"Trend sygnałów na wykresie giełdowym i powiadomienia o obiecującym trendzie."),
     Parked(title:"Ask szczur", refs:"FR-032", description:"Pytania do LLM o wybrany sygnał bez opuszczania widoku posta.")),
    ("OBSŁUGA UŻYTKOWNIKA",
     Parked(title:"Sortowanie i eksport", refs:"FR-008 / FR-011b / FR-031", description:"Porządkowanie listy po polach oceny, źródle i dacie oraz eksport CSV/JSON."),
     Parked(title:"Panel kont i e-mail", refs:"FR-016 / 017 / 019 / 020", description:"Zarządzanie kontami w aplikacji, e-mail z zaproszeniem i własna nazwa wyświetlana.")),
    ("OPERACJE I SKALA",
     Parked(title:"Sesje", refs:"FR-029 + wersja poświadczeń", description:"Krótsze limity bezczynności i unieważnianie sesji wersją poświadczeń."),
     Parked(title:"Ochrona i skala", refs:"Poza M-2 / infrastruktura", description:"2FA, logowanie zewnętrzne, nowe role, HA, lokalny runner CI, wydajność filtra, porządki pakietów, drobne ryzyka."))
]
for (i, group) in parked.enumerated() {
    let x = Layout.futureColumns[i], y = Layout.futureY
    let w = Layout.futureWidth, h = Layout.futureHeight
    rounded(x, y, w, h, 22, color(0x111E32), stroke, line: 2)
    rounded(x, y, 8, h, 4, violet.withAlphaComponent(0.8))
    txt("PO M-2  ·  ODROCZONE", x + 30, y + 24, w - 60, 20, .bold, violet)
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

rounded(100, 2810, 2960, 300, 24, color(0x101D30), stroke, line: 2)
txt("LEGENDA I ZASADY CZYTANIA", 136, 2838, 1300, 26, .bold)
let keys: [(CGFloat, String, NSColor)] = [(136,"A · Sygnał w aplikacji",cyan),(700,"B · Kanał poza aplikacją",amber),(1290,"C · Próby i silnik",violet),(1820,"D · Wdrożenia i dane",green)]
for (x, label, ink) in keys {
    rounded(x, 2903, 54, 8, 4, ink)
    txt(label, x + 74, 2889, 480, 24, .medium)
}
arrow([(2640,2906),(2710,2906)], muted)
txt("Zależność", 2734, 2889, 300, 24, .medium)
txt("S-08: gwiazda przewodnia — pierwszy wycinek, który domyka lukę w sygnale awarii (NFR: operator nie dowiaduje się o awarii przez brak danych).", 136, 2955, 2850, 25, .medium)
txt("Karty w jednej kolumnie bez strzałki między sobą są niezależne i mogą iść równolegle. F-04 czeka na decyzję o kanale alarmowym.", 136, 3003, 2850, 24, .regular, muted)
txt("Przed bramką strzałki pokazują zależności. Za bramką wskazują możliwe rozszerzenia, bez ustalonej kolejności.", 136, 3051, 2850, 24, .regular, muted)

txt("Źródło: context/foundation/roadmap.md  ·  M-2 / v3  ·  Stan roadmapy: 17.10.2026  ·  Milestone otwarty", 100, 3170, 2200, 22, .regular, muted)
txt("MAPA ZALEŻNOŚCI · BEZ TERMINÓW", 2520, 3170, 540, 20, .semibold, muted)

context.flushGraphics()
NSGraphicsContext.restoreGraphicsState()
let data = bitmap.representation(using: .png, properties: [:])!
try data.write(to: URL(fileURLWithPath: output))
print(output)
