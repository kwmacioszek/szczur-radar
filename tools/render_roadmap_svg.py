#!/usr/bin/env python3
"""Renders docs/visuals/roadmap.svg: the roadmap as a timeline, M-1 → M-2 → M-3 → Po M-3, left to right.

The data is copied by hand from context/foundation/roadmap.md (v3); the M-1 prerequisites and streams come
from the roadmap before M-1 closed (v2.22, commit 5b6eabb), because v3 keeps M-1 only in `## Done`.
The open M-3 comes from v4: streams from `## Streams`, card titles from `## Slices`, card texts from the
`## At a glance` outcomes, and its slice states drive the progress card.
Every card carries data-id / data-status and every dependency arrow data-from / data-to, so the drawing
can be checked against the roadmap. When roadmap.md changes, update the data below and run:

    python3 tools/render_roadmap_svg.py [output.svg]
"""
import math
import sys
from xml.sax.saxutils import escape

OUTPUT = sys.argv[1] if len(sys.argv) > 1 else "docs/visuals/roadmap.svg"
FONT = "Inter, 'SF Pro Text', -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif"
MONO = "'JetBrains Mono', 'SF Mono', Menlo, Consolas, monospace"

C = dict(
    bg="#0d1316", panel="#11181c", panel2="#141c21", card="#151e23", border="#222d33", border2="#2b373e",
    text="#eaf0f2", muted="#a2afb4", dim="#9aa8ae",
    done="#5fd18f", doneSoft="#14281d", doneLine="#4f9a6d", sky="#8ccbe8", skySoft="#13232e",
    lime="#c3f08c", limeDeep="#9fdc5c", limeSoft="#1b2b1c", green="#4ade80",
    amber="#f2c46d", amberSoft="#33291a", coral="#f28b9c", coralSoft="#3a1f25", gray="#c3cdd0", graySoft="#232d32",
)
STATUS = {
    "done": ("Ukończone", C["done"], C["doneSoft"]),
    "progress": ("W toku", C["amber"], C["amberSoft"]),
    "ready": ("Gotowe do planowania", C["sky"], C["skySoft"]),
    "proposed": ("Proponowane", C["gray"], C["graySoft"]),
    "blocked": ("Zablokowane", C["coral"], C["coralSoft"]),
    "parked": ("Zaparkowane", C["dim"], C["panel2"]),
}

out = []
def add(s): out.append(s)

def text(x, y, s, size=13, weight=400, fill=None, family=FONT, anchor="start", spacing=None, italic=False):
    attrs = f'x="{x:.1f}" y="{y:.1f}" font-family="{family}" font-size="{size}" font-weight="{weight}" fill="{fill or C["text"]}"'
    if anchor != "start": attrs += f' text-anchor="{anchor}"'
    if spacing: attrs += f' letter-spacing="{spacing}"'
    if italic: attrs += ' font-style="italic"'
    add(f'<text {attrs}>{escape(s)}</text>')

def width(s, size, weight=400):
    return len(s) * size * (0.585 if weight >= 600 else 0.545)

def wrap(s, size, maxw, weight=400):
    lines, cur = [], ""
    for word in s.split():
        cand = (cur + " " + word).strip()
        if width(cand, size, weight) <= maxw or not cur:
            cur = cand
        else:
            lines.append(cur); cur = word
    if cur: lines.append(cur)
    return lines

def lines(x, y, ls, size, lh, weight=400, fill=None, family=FONT):
    for i, l in enumerate(ls):
        text(x, y + i * lh, l, size, weight, fill, family)

def rect(x, y, w, h, r=12, fill=None, stroke=None, sw=1, extra=""):
    s = f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" rx="{r}" fill="{fill or "none"}"'
    if stroke: s += f' stroke="{stroke}" stroke-width="{sw}"'
    add(s + f' {extra}/>')

def pill(x, y, label, fg, bg, size=10.5, right=False, weight=600, icon=None):
    w = width(label, size, weight) + 18 + (12 if icon else 0)
    if right: x -= w
    rect(x, y, w, 20, 5, bg, fg + "55", 1)
    tx = x + 9
    if icon == "check":
        add(f'<path d="M{tx:.1f} {y+10:.1f} l3 3 l5.5 -6" fill="none" stroke="{fg}" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>')
        tx += 12
    elif icon == "dot":
        add(f'<circle cx="{tx+3:.1f}" cy="{y+10:.1f}" r="3" fill="{fg}"/>'); tx += 12
    elif icon == "lock":
        add(f'<rect x="{tx:.1f}" y="{y+9:.1f}" width="8" height="6" rx="1.2" fill="{fg}"/><path d="M{tx+1.8:.1f} {y+9:.1f} v-1.6 a2.2 2.2 0 0 1 4.4 0 v1.6" fill="none" stroke="{fg}" stroke-width="1.3"/>')
        tx += 12
    text(tx, y + 14, label, size, weight, fg)
    return w

# ---------- arrows ----------
def route(points, color, dashed=False, marker="lime", width_=1.6, r=10, edge=None):
    d = f"M{points[0][0]:.1f} {points[0][1]:.1f}"
    for i in range(1, len(points) - 1):
        (x0, y0), (x1, y1), (x2, y2) = points[i - 1], points[i], points[i + 1]
        if r == 0:
            d += f" L{x1:.1f} {y1:.1f}"
            continue
        def towards(ax, ay, bx, by, dist):
            L = max(abs(bx - ax), abs(by - ay)); k = min(dist, L / 2) / L if L else 0
            return ax + (bx - ax) * k, ay + (by - ay) * k
        p1 = towards(x1, y1, x0, y0, r); p2 = towards(x1, y1, x2, y2, r)
        d += f" L{p1[0]:.1f} {p1[1]:.1f} Q{x1:.1f} {y1:.1f} {p2[0]:.1f} {p2[1]:.1f}"
    d += f" L{points[-1][0]:.1f} {points[-1][1]:.1f}"
    dash = ' stroke-dasharray="5 5"' if dashed else ""
    data = f' data-from="{edge[0]}" data-to="{edge[1]}"' if edge else ""
    add(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{width_}"{dash} stroke-linecap="round" stroke-linejoin="round" marker-end="url(#arrow-{marker})"{data}/>')

# ---------- generic card ----------
def need_h(title, desc, w, compact=False):
    pad = 14
    tsize, dsize = (13.5, 11.4) if compact else (18, 15)
    tl = len(wrap(title, tsize, w - 2 * pad, 700)); dl = len(wrap(desc, dsize, w - 2 * pad))
    body = 51 + tl * (tsize + 4) + (2 if compact else 6) + (dl - 1) * (dsize + 5)
    return body + (14 if compact else 90)
CARD_BORDER = {"done": "#2f5a42", "ready": "#2e4d60", "blocked": "#7a3b47", "proposed": "#2f3b42", "progress": "#6b5426", "parked": "#222d33"}
def card(x, y, w, h, iid, title, desc, status, req=None, cid=None, star=False, compact=False, note=None):
    lab, fg, bg = STATUS[status]
    add(f'<g data-id="{iid}" data-status="{status}">')
    fill = "url(#star)" if star else C["card"]
    rect(x, y, w, h, 12, fill, C["lime"] if star else CARD_BORDER[status], 1.8 if star else 1)
    pad = 14
    text(x + pad, y + 25, iid, 13, 700, C["lime"] if star else fg, family=MONO)
    tx = x + pad + width(iid, 13, 700) * 1.08 + 8
    if star:
        add(f'<path d="M{tx+6:.1f} {y+13:.1f} l2.3 4.7 5.2 .8 -3.8 3.6 .9 5.1 -4.6 -2.4 -4.6 2.4 .9 -5.1 -3.8 -3.6 5.2 -.8z" fill="{C["lime"]}"/>')
    icon = {"done": "check", "blocked": "lock"}.get(status, "dot")
    pill(x + w - pad, y + 11, lab, fg, bg, 9.5 if compact else 10.5, right=True, icon=icon)
    tsize = 13.5 if compact else 18
    tl = wrap(title, tsize, w - 2 * pad, 700)
    lines(x + pad, y + 51, tl, tsize, tsize + 4, 700)
    dy = y + 51 + len(tl) * (tsize + 4) + (2 if compact else 6)
    dsize = 11.4 if compact else 15
    dl = wrap(desc, dsize, w - 2 * pad)
    lines(x + pad, dy, dl, dsize, dsize + 5, 400, C["muted"])
    if not compact:
        fy = y + h - 56
        add(f'<line x1="{x+pad}" y1="{fy-18}" x2="{x+w-pad}" y2="{fy-18}" stroke="{C["border"]}"/>')
        text(x + pad, fy, "WYMAGA", 9.5, 700, C["dim"], spacing="1.2")
        reqc = C["coral"] if note else (C["text"] if req and req != "—" else C["muted"])
        text(x + pad + 60, fy, req or "—", 11, 700, reqc, family=MONO)
        if cid:
            # Change identifiers may wrap at hyphens; never spill into a neighbour.
            cid_lines = wrap(cid.replace("-", "- "), 12, w - 2 * pad, 600)
            lines(x + pad, fy + 22, [line.replace("- ", "-") for line in cid_lines],
                  12, 16, 500, C["dim"], family=MONO)
    add('</g>')
    return len(tl), len(dl)


M = 56                       # outer margin
TOP = 400                    # top of the milestone containers
PANEL_GAP = 96               # gap between containers (timeline step)
INNER = 24                   # container padding

# ================= data =================
m1 = {  # title, outcome, column, row, prerequisites, change id
    "F-01": ("Bramka produkcyjnych danych", "(foundation) produkcja jest gotowa na prawdziwe dane: limity kosztów, zweryfikowany restore, zapieczętowane sekrety", 0, 3, "—", "production-data-gate"),
    "S-01": ("Pierwszy admin i logowanie", "operator tworzy pierwszego admina przez CLI; admin loguje się i wylogowuje; niezalogowany widzi tylko ekran logowania", 0, 1, "—", "admin-bootstrap-and-login"),
    "F-03": ("Backend w Javie", "(foundation) backend w Javie obsługuje funkcje S-01 przez dotychczasowe API; sesje JDBC, migracje fix-forward i szkielet Embabel", 1, 1, "S-01", "java-api-rewrite"),
    "F-02": ("Egzekwowany kontrakt oceny LLM", "(foundation) kontrakt oceny LLM V2 jest egzekwowany kodem, a model domyślny spełnia progi jakości na zestawie wzorcowym", 2, 1, "F-03", "assessment-contract-harness"),
    "S-07": ("Onboarding użytkownika przez CLI", "admin tworzy konto przez CLI, zaproszony ustawia hasło przez jednorazowy link i się loguje", 3, 0, "S-01, F-03", "cli-user-onboarding"),
    "S-02": ("Pierwsze ocenione posty z Wykopu na liście", "zalogowany użytkownik widzi chronologiczną listę postów z Wykopu ocenionych przez LLM", 3, 1, "S-01, F-02", "first-assessed-wykop-feed"),
    "S-03": ("Widok szczegółowy posta", "użytkownik otwiera widok szczegółowy posta: ocena, surowe dane pod przełącznikiem, link do oryginału", 4, 0, "S-02", "post-detail-view"),
    "S-04": ("Filtrowanie po ocenie LLM", "użytkownik filtruje listę po spółce/temacie (tekstowo) i stanowisku autora wobec niej", 4, 1, "S-02", "filter-by-assessment"),
    "S-05": ("Codzienny automatyczny przebieg i świeżość danych", "przepływ uruchamia się sam codziennie, a użytkownik widzi datę ostatniej udanej aktualizacji", 4, 2, "S-02, F-01", "daily-scheduled-run-freshness"),
    "S-06": ("Dashboard historii przebiegów dla admina", "admin widzi historię przebiegów (kiedy, status, wynik) i wyraźny sygnał nieudanego przebiegu", 5, 2, "S-05, S-01", "pipeline-run-history-dashboard"),
}
m2 = {  # lane, position, title, outcome, status, prerequisites, change id
    "S-08": ("A", 0, "Pusty przebieg daje sygnał", "operator dostaje sygnał, gdy przebieg nic nie pobrał albo pominął elementy; etykieta świeżości wtedy stoi", "done", "—", "empty-run-signal"),
    "S-12": ("A", 1, "Powiadomienia o przebiegach nie giną", "zaparkowane 2026-10-24 (frame: scenariusze tylko z audytu); A5 przeszedł do S-18, A6 do Parked", "parked", "S-08", "reliable-run-notifications"),
    "F-04": ("B", 0, "Kanał alarmowy poza aplikacją", "(foundation) kanał alarmowy poza aplikacją: healthchecks.io → Telegram, $0; crony się meldują, API ma nadawcę zdarzeń i sygnał testowy", "done", "—", "out-of-app-alert-channel"),
    "S-13": ("B", 1, "Brakująca noc nie przechodzi niezauważona", "operator dowie się, że nocny przebieg albo backup nie wystartował, i widzi dni bez danych", "done", "F-04", "missed-night-detection"),
    "S-18": ("B", 2, "Błędy wymagające reakcji docierają do operatora", "błędy wymagające reakcji (ERROR, /api/ready, awaria ścieżki alarmowej, nieudany zapis licznika przebiegu) docierają do operatora; klucze nie trafiają do logów", "done", "F-04", "error-visibility"),
    "S-09": ("C", 0, "Awaria dostawcy nie zabija postów", "awaria dostawcy LLM ani błąd naszego kodu nie zabija postów; post z powtarzalnym błędem technicznym ma limit", "done", "—", "attempt-accounting"),
    "S-17": ("C", 1, "Przebieg bez Embabel", "przebieg działa bez Embabel, przerwanie dociera do oceny, a testy silnika są skupione i niezależne od kolejności", "done", "S-09", "pipeline-without-embabel"),
    "S-10": ("C", 2, "Zła konfiguracja LLM zatrzymuje start", "operator dostaje błąd startu przy LLM_TIMEOUT bez jednostki; czasy są parsowane jedną regułą", "done", "—", "llm-config-fail-fast"),
    "S-11": ("C", 3, "Utknięty przebieg sam wraca do obiegu", "przebieg po martwej instancji i post z wyścigu claim wracają do obiegu bez restartu API", "done", "—", "stuck-run-self-heals"),
    "S-16": ("C", 4, "Wykop nie gubi postów po cichu", "stronicowanie Wykopu nie gubi elementów po cichu, a chwilowy błąd Wykopu nie zabija nocy", "done", "—", "wykop-ingest-completeness"),
    "S-14": ("D", 0, "Bezpieczna bramka wdrożeń", "merge nie wdroży czerwonego commita, crony czekają na CI, po apply jest kontrola zdrowia, ręczne zmienne nie blokują wdrożeń", "done", "—", "safe-deploy-gate"),
    "S-15": ("D", 1, "Kopia bazy sprawdzana w całości", "sprawdzenie kopii bazy obejmuje wszystkie tabele i odrzuca kopię pustej bazy", "done", "—", "backup-check-completeness"),
    "S-19": ("D", 2, "Dokumenty operatora mówią prawdę", "operator czyta prawdziwe runbooki i stan; zbędne zmienne IaC usunięte; otwarty dług operacyjny domknięty", "done", "—", "ops-docs-debt"),
}
lanes = {
    "A": ("Sygnał w aplikacji", "Gwiazda przewodnia", C["lime"]),
    "B": ("Kanał poza aplikacją", "Kanał: healthchecks.io → Telegram", C["amber"]),
    "C": ("Próby i silnik", "S-10, S-11, S-16 są niezależne — mogą iść równolegle", C["sky"]),
    "D": ("Wdrożenia i dane", "Trzy niezależne wycinki — mogą iść równolegle", C["sky"]),
}
m3 = {  # lane, title, outcome (At a glance), status, prerequisites, change id
    "S-20": ("A", "Awaria albo cisza jednego źródła nie psuje drugiego", "awaria albo cisza jednego źródła nie psuje przebiegu pozostałych; czas pobrania wielu źródeł mieści się w progu strażnika; liczniki widać per źródło", "ready", "—", "source-isolation"),
    "S-27": ("A", "Reddit jako drugie źródło", "użytkownik widzi w feedzie posty z wybranych subredditów obok Wykopu, ocenione tym samym kontraktem", "blocked", "S-20", "reddit-source"),
    "S-28": ("A", "Jakość i koszt Reddita zmierzone przed produkcją", "operator wie przed włączeniem Reddita na produkcji, że ocena postów po angielsku spełnia progi, a koszt mieści się w budżecie", "proposed", "S-27", "reddit-quality-and-cost"),
    "S-29": ("A", "Źródło widoczne w feedzie, świeżość bez kłamstwa", "użytkownik odróżnia źródło posta w feedzie i szczegółach, a data aktualizacji nie kłamie przy dwóch źródłach", "blocked", "S-27", "source-aware-feed"),
    "S-21": ("B", "Zmiana IaC bez deployu nie blokuje wdrożeń", "zmiana IaC, która nie wywołuje deployu, nie robi CI czerwonego ani nie blokuje deployu kodu; dokumenty S-14 i #73 mówią prawdę", "done", "—", "deploy-gate-no-deploy-changes"),
    "S-25": ("B", "Jedna reguła adresu check-in alarmów", "adres check-in alarmów ma jedną regułę we wszystkich miejscach, a rozjazd kopii jest wykrywany testem", "ready", "—", "alert-ping-single-rule"),
    "S-22": ("C", "„Wymaga uwagi” liczone jedną regułą", "admin widzi powody „wymaga uwagi” liczone tą samą regułą co powiadomienia i alerty, bez pustego tekstu przy nowym powodzie", "ready", "—", "run-attention-single-rule"),
    "S-23": ("C", "Zamknięte zbiory i kolumny posta pilnowane w CI", "nowa wartość zamkniętego zbioru albo nowa kolumna posta bez migracji lub bez zapisu jest wykrywana w CI, nie w nocy na produkcji", "ready", "—", "closed-sets-guard"),
    "S-24": ("C", "Kontrakt SPA ↔ API pilnowany testem", "rozjazd odpowiedzi API i typów SPA (pełne ciała, kształt błędu 500, kopie reguł) jest wykrywany testem, nie ręczną dyscypliną", "ready", "—", "api-contract-guard"),
    "S-26": ("D", "Testy silnika bez zależności od zegara", "testy silnika nie zależą od czasu zegara na współdzielonym runnerze, a znane okna fałszywych alarmów (F3) są przypięte testami", "ready", "—", "engine-test-stability"),
}
lanes3 = {  # the streams of roadmap v4
    "A": ("Drugie źródło", "Gwiazda przewodnia otwiera strumień; S-28 i S-29 równolegle po S-27", C["lime"]),
    "B": ("Wdrożenia i alarmy", "Niezależne; S-21 wcześnie — zmiany IaC w M-3 idą przez bramkę", C["amber"]),
    "C": ("Jedna reguła, jeden strażnik", "Reguły z kilku miejsc dostają strażnika przed nowym źródłem", C["sky"]),
    "D": ("Silnik", "Stabilne testy silnika przed zmianą szwu źródeł", C["sky"]),
}
groups = [
    ("Źródła i ocena", [
        ("Non-Goal", "Przetwarzanie komentarzy pod postami"),
        ("Non-Goal", "Spółki, „wartościowy sygnał”, trend, raporty"), ("roadmap", "LLM hardening: kierunek i ironia"), ("audyt", "Ochrona przed dryfem modelu"),
        ("roadmap", "Ocena obrazków (multimodalna)"), ("forward", "Lekkie modele ML i embeddingi")]),
    ("Analiza i widok", [
        ("FR-024/025", "Wykres giełdowy i alerty o trendzie"), ("FR-032", "„Ask szczur” — pytanie do LLM"), ("FR-008/011b", "Sortowanie po polach oceny"),
        ("FR-031", "Eksport listy do CSV/JSON"), ("roadmap", "Komunikat błędu daty aktualizacji"), ("roadmap", "Przybliżony koniec przebiegu"),
        ("M-5", "Nowe UI/UX + E2E (Playwright)")]),
    ("Konta i dostęp", [
        ("FR-016/017/019", "Panel kont + e-mail z zaproszeniem"), ("FR-020", "Własna nazwa wyświetlana"), ("FR-029", "Wygasanie krótkich sesji"),
        ("S-07 F1", "Unieważnianie sesji wersją poświadczeń"), ("Non-Goal", "2FA, logowanie zewn., reset, role")]),
    ("Operacje i skala", [
        ("Non-Goal", "HA / multi-region, backup w kodzie"), ("S-04 F6", "Wydajność filtra przy ~20k postów"), ("#30", "Drugi runner CI w Proxmoxie"),
        ("audyt", "Porządki granic pakietów"), ("audyt", "Drobne ryzyka (A8, B8, B10, B11, C8, C14)"),
        ("M-4", "Weryfikacja i regresja (plan testów)")]),
]

DEFERRED = sum(len(items) for _, items in groups)

# ================= geometry (computed before drawing) =================
PAD, GAP, GROUP_GAP = 16, 32, 24
P2W = 1568
CW_CARD = (P2W - 2 * INNER - 4 * PAD - GROUP_GAP - 3 * GAP) / 5   # the M-2 card width, used by M-1 too
CW1, GAP1 = CW_CARD, 48
# Lanes = the streams of roadmap v2.22: A "Dostęp i konta", B "Przegląd sygnałów", C "Codzienny automat".
# Column = position left to right; a card never sits left of its prerequisite.
lanes1 = [
    ("A", "Dostęp i konta", "Od pierwszego admina do kont przez CLI", {"S-01": 0, "F-03": 1, "S-07": 2}),
    ("B", "Przegląd sygnałów", "Ocena LLM trafia do użytkownika", {"F-02": 1, "S-02": 3, "S-03": 4, "S-04": 4}),
    ("C", "Codzienny automat", "Cron, świeżość i historia przebiegów", {"F-01": 0, "S-05": 3, "S-06": 4}),
]
CH1s = [max(need_h(m1[k][0], m1[k][1], CW1) for k in cols) + 6 for _, _, _, cols in lanes1]
M1_COLUMNS = 1 + max(col for _, _, _, cols in lanes1 for col in cols.values())
LANE1_W = 2 * PAD + M1_COLUMNS * CW1 + (M1_COLUMNS - 1) * GAP1
LANE1_GAP = 44                     # separation between streams
MARGIN1 = 30                       # inset shared by all streams
LANE1_HS = [98 + ch * (2 if i == 1 else 1) + (72 if i == 1 else 24)
            for i, ch in enumerate(CH1s)]
P1W = 2 * INNER + MARGIN1 + LANE1_W
P1X = M
P1H = 98 + sum(LANE1_HS) + LANE1_GAP * (len(lanes1) - 1) + 40 + 24
P2X = P1X + P1W + PANEL_GAP
IN2 = P2W - 2 * INNER
M2_GRID_GAP = 2 * PAD + GROUP_GAP
CW2 = (IN2 - 2 * PAD - 4 * M2_GRID_GAP) / 5
HEAD_H, CARD_OFF = 70, 98
rows2 = [[("A", 2), ("B", 3)], [("C", 5)], [("D", 3)]]
def row_cw(row, n):
    return CW2

# Equal column widths and gutters across every M-2 stream. The gutter
# also accommodates the A/B panel borders without shifting the cards.
M2_COLUMNS = [P2X + INNER + PAD + i * (CW2 + M2_GRID_GAP) for i in range(5)]
M2_CARD_COLUMN = {
    "S-08": 0, "S-12": 1, "F-04": 2, "S-13": 3, "S-18": 3,
    "S-09": 0, "S-17": 1, "S-10": 2, "S-11": 3, "S-16": 4,
    "S-14": 0, "S-15": 2, "S-19": 4,
}
row_ch = []
for row in rows2:
    keys = dict(row)
    row_ch.append(max(need_h(v[2], v[3], row_cw(row, keys[v[0]])) for v in m2.values() if v[0] in keys) + 6)
row_ch = [max(row_ch)] * len(rows2)
row_body = [2 * ch + 48 if i == 0 else ch for i, ch in enumerate(row_ch)]
P2H = 98 + sum(CARD_OFF + body + 36 for body in row_body) + 18 * (len(rows2) - 1) + 20

# M-3 reuses the M-2 grid: five equal columns, the same card width and gutters.
P3X = P2X + P2W + PANEL_GAP
P3W = P2W
rows3 = [[("A", 3), ("B", 2)], [("C", 3), ("D", 2)]]   # (stream, columns of its panel)
M3_COLUMNS = [P3X + INNER + PAD + i * (CW2 + M2_GRID_GAP) for i in range(5)]
M3_CARD_COLUMN = {
    "S-20": 0, "S-27": 1, "S-28": 2, "S-29": 2, "S-21": 3, "S-25": 4,
    "S-22": 0, "S-23": 1, "S-24": 2, "S-26": 3,
}
CH3 = max(need_h(v[1], v[2], CW2) for v in m3.values()) + 6
row3_body = [2 * CH3 + 48, CH3]          # the first row stacks S-28 over S-29
P3H = 98 + sum(CARD_OFF + body + 36 for body in row3_body) + 18 * (len(rows3) - 1) + 20

GW = 360
P4W = 2 * GW + 20 + 2 * INNER
P4X = P3X + P3W + PANEL_GAP
ITEM_H = 76
gh = [max(len(groups[0][1]), len(groups[1][1])) * ITEM_H + 76, max(len(groups[2][1]), len(groups[3][1])) * ITEM_H + 76]
P4H = 132 + gh[0] + 20 + gh[1] + 24

PH = max(P1H, P2H, P3H, P4H)
PANEL_H = {"M-1": P1H, "M-2": P2H, "M-3": P3H, "PO M-3": P4H}
W = int(P4X + P4W + M)
H = int(TOP + PH + 72)

# ================= header =================
rect(0, 0, W, H, 0, C["bg"])
rect(0, 0, W, H, 0, "url(#grid)")
rect(M, 40, 40, 40, 10, C["lime"])
text(M + 10, 67, "S", 20, 800, "#0d1316")
add(f'<circle cx="{M+28}" cy="66" r="2.6" fill="#0d1316"/>')
text(M + 52, 57, "Szczur Radar", 17, 700)
text(M + 52, 75, "MARKET INTELLIGENCE", 9.5, 600, C["muted"], spacing="2.4")
text(M, 134, "MILESTONES I ZALEŻNOŚCI", 11, 700, C["muted"], spacing="2")
text(M, 172, "Roadmapa produktu", 31, 700)
text(M, 202, "Źródło: context/foundation/roadmap.md  ·  v4  ·  stan 27.10.2026  ·  bez terminów — kolejność wynika z zależności", 13, 400, C["muted"])

# M-2 slice states; every counter below is derived from them (M-1 closed with all 10 done)
m2_order = [("F-04", "done"), ("S-08", "done"), ("S-09", "done"), ("S-10", "done"), ("S-11", "done"), ("S-12", "parked"),
            ("S-13", "done"), ("S-14", "done"), ("S-15", "done"), ("S-16", "done"), ("S-17", "done"), ("S-18", "done"), ("S-19", "done")]
M1_DONE = M1_TOTAL = 10
M2_TOTAL = len(m2_order)
M2_COUNT = {st: sum(1 for _, s in m2_order if s == st) for st in ("done", "progress", "ready", "proposed", "parked", "blocked")}
M2_DONE = M2_COUNT["done"]
# M-3 (open) slice states, in ID order, straight from the card data
m3_order = [(k, m3[k][3]) for k in sorted(m3)]
M3_TOTAL = len(m3_order)
M3_COUNT = {st: sum(1 for _, s in m3_order if s == st) for st in ("done", "progress", "ready", "proposed", "parked", "blocked")}
M3_DONE = M3_COUNT["done"]

def plural(n, few, many):
    """Polish count agreement: 1 and 2–4 (not 12–14) take `few`, the rest `many`."""
    return few if n == 1 or (n % 10 in (2, 3, 4) and n % 100 not in (12, 13, 14)) else many

# progress card of the current milestone (M-3), over its container
px, py, pw, ph = P3X, 36, 820, 214
rect(px, py, pw, ph, 14, "url(#hero)", C["border2"])
cx, cy, rr = px + 82, py + 100, 46
ALL_DONE, ALL_TOTAL = M1_DONE + M2_DONE + M3_DONE, M1_TOTAL + M2_TOTAL + M3_TOTAL
frac = ALL_DONE / ALL_TOTAL
add(f'<circle cx="{cx}" cy="{cy}" r="{rr}" fill="none" stroke="{C["graySoft"]}" stroke-width="9"/>')
ang = frac * 2 * math.pi
ex, ey = cx + rr * math.sin(ang), cy - rr * math.cos(ang)
add(f'<path d="M{cx} {cy-rr} A{rr} {rr} 0 {1 if frac > 0.5 else 0} 1 {ex:.2f} {ey:.2f}" fill="none" stroke="{C["done"]}" stroke-width="9" stroke-linecap="round"/>')
text(cx, cy + 4, f"{round(frac * 100)}%", 22, 700, anchor="middle")
text(cx, cy + 22, f"{ALL_DONE} / {ALL_TOTAL}", 11, 500, C["muted"], anchor="middle")
text(cx, py + 186, "M-1 + M-2 + M-3", 10.5, 700, C["muted"], anchor="middle", spacing="1.2")
bx = px + 164
text(bx, py + 34, "POSTĘP KAMIENIA MILOWEGO", 10.5, 700, C["muted"], spacing="1.6")
text(bx, py + 62, "M-3 · Drugie źródło na utwardzonym rdzeniu", 17, 700)
text(px + pw - 24, py + 62, f"{M3_DONE} / {M3_TOTAL}", 17, 700, C["text"], anchor="end")
segw = (pw - 164 - 24 - (M3_TOTAL - 1) * 5) / M3_TOTAL
for i, (iid, st) in enumerate(m3_order):
    lab, fg, bg = STATUS[st]
    rect(bx + i * (segw + 5), py + 78, segw, 12, 3, bg, fg + ("aa" if st not in ("proposed", "parked") else "55"), 1)
legend_items = [
    ("done", f"{M3_COUNT['done']} {plural(M3_COUNT['done'], 'ukończone', 'ukończonych')}"),
    ("progress", f"{M3_COUNT['progress']} w toku"),
    ("ready", f"{M3_COUNT['ready']} {plural(M3_COUNT['ready'], 'gotowe', 'gotowych')} do planowania"),
    ("proposed", f"{M3_COUNT['proposed']} {plural(M3_COUNT['proposed'], 'proponowane', 'proponowanych')}"),
    ("parked", f"{M3_COUNT['parked']} {plural(M3_COUNT['parked'], 'zaparkowane', 'zaparkowanych')}"),
    ("blocked", f"{M3_COUNT['blocked']} {plural(M3_COUNT['blocked'], 'zablokowane', 'zablokowanych')}"),
]
for li, (st, lab) in enumerate(legend_items):
    fg = STATUS[st][1]
    lx = bx + (0, 150, 300)[li % 3]
    ly0 = py + 111 + (li // 3) * 22
    add(f'<circle cx="{lx+4}" cy="{ly0}" r="4" fill="{fg if st in ("done","blocked","progress") else STATUS[st][2]}" stroke="{fg}" stroke-width="1.2"/>')
    text(lx + 13, ly0 + 4, lab, 11.5, 500, C["muted"] if lab.startswith("0") else C["text"])
add(f'<line x1="{bx}" y1="{py+156}" x2="{px+pw-24}" y2="{py+156}" stroke="{C["border2"]}"/>')
mx = bx + pill(bx, py + 172, f"M-1  {M1_DONE} / {M1_TOTAL}", C["done"], C["doneSoft"], 10.5, icon="check") + 10
pill(mx, py + 172, f"M-2  {M2_DONE} / {M2_TOTAL}", C["done"], C["doneSoft"], 10.5, icon="check")

# legend over the "Po M-3" container
lgx, lgy, lgw, lgh = P4X, 36, P4W, 214
rect(lgx, lgy, lgw, lgh, 14, C["panel"], C["border2"])
text(lgx + 24, lgy + 34, "LEGENDA", 10.5, 700, C["muted"], spacing="1.6")
x = lgx + 24
for st in ("done", "progress", "ready", "proposed", "parked", "blocked"):
    lab, fg, bg = STATUS[st]
    x += pill(x, lgy + 52, lab, fg, bg, 10.5, icon={"done": "check", "blocked": "lock"}.get(st, "dot")) + 10
add(f'<line x1="{lgx+24}" y1="{lgy+92}" x2="{lgx+lgw-24}" y2="{lgy+92}" stroke="{C["border2"]}"/>')
c1, c2 = lgx + 24, lgx + 24 + 360
y1, y2 = lgy + 126, lgy + 166
text(c1, y1, "F", 12.5, 700, C["text"], family=MONO); text(c1 + 16, y1, "fundament", 12.5, 400, C["muted"])
text(c2, y1, "S", 12.5, 700, C["text"], family=MONO); text(c2 + 16, y1, "wycinek od początku do końca", 12.5, 400, C["muted"])
route([(c1, y2 - 4), (c1 + 40, y2 - 4)], C["muted"], marker="gray")
text(c1 + 52, y2, "warunek → element zależny", 12.5, 400, C["muted"])
add(f'<path d="M{c2+6} {y2-16} l2.3 4.7 5.2 .8 -3.8 3.6 .9 5.1 -4.6 -2.4 -4.6 2.4 .9 -5.1 -3.8 -3.6 5.2 -.8z" fill="{C["lime"]}"/>')
text(c2 + 18, y2, "gwiazda przewodnia", 12.5, 400, C["muted"])
y3 = lgy + 200
add(f'<line x1="{c1}" y1="{y3-4}" x2="{c1+40}" y2="{y3-4}" stroke="{C["muted"]}" stroke-width="3" stroke-linecap="round" stroke-opacity="0.55"/>')
text(c1 + 52, y3, "oś: kolejność kamieni milowych", 12.5, 400, C["muted"])
route([(c2, y3 - 4), (c2 + 40, y3 - 4)], C["coral"], True, "coral", 1.8)
text(c2 + 52, y3, "przerywana: od zablokowanego", 12.5, 400, C["muted"])

# ================= timeline axis =================
AX_Y = 316
x_end = P4X + P4W
# Track: done (M-1, M-2), current (M-3), deferred (after M-3).
add(f'<line x1="{P1X+20}" y1="{AX_Y}" x2="{P2X+20}" y2="{AX_Y}" stroke="{C["done"]}" stroke-width="4" stroke-linecap="round"/>')
add(f'<line x1="{P2X+20}" y1="{AX_Y}" x2="{P3X+20}" y2="{AX_Y}" stroke="{C["done"]}" stroke-width="4" stroke-linecap="round"/>')
add(f'<line x1="{P3X+20}" y1="{AX_Y}" x2="{P4X+20}" y2="{AX_Y}" stroke="{C["border2"]}" stroke-width="4" stroke-linecap="round"/>')
add(f'<line x1="{P4X+20}" y1="{AX_Y}" x2="{x_end-14}" y2="{AX_Y}" stroke="{C["muted"]}" stroke-opacity="0.45" stroke-width="3" stroke-dasharray="2 9" stroke-linecap="round"/>')
add(f'<path d="M{x_end-22} {AX_Y-8} L{x_end-10} {AX_Y} L{x_end-22} {AX_Y+8}" fill="none" stroke="{C["muted"]}" stroke-opacity="0.6" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/>')
nodes = [
    (P1X, "M-1", "MVP — ocenione sygnały z Wykopu", "Zamknięty 17.10.2026  ·  10 / 10", "done"),
    (P2X, "M-2", "MVP Quality & Reliability Hardening", f"Zamknięty 27.10.2026  ·  {M2_DONE} / {M2_TOTAL}", "done"),
    (P3X, "M-3", "Drugie źródło na utwardzonym rdzeniu", f"Otwarty — teraz  ·  {M3_DONE} / {M3_TOTAL}", "now"),
    (P4X, "PO M-3", "Odroczone", f"Backlog  ·  {DEFERRED} {plural(DEFERRED, 'pozycje', 'pozycji')}", "later"),
]
for nx, tag, name, state, kind in nodes:
    cxn = nx + 20
    if kind == "done":
        add(f'<circle cx="{cxn}" cy="{AX_Y}" r="11" fill="{C["done"]}"/>')
        add(f'<path d="M{cxn-5} {AX_Y} l3.5 3.5 l6 -7" fill="none" stroke="#0d1316" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>')
        ink = C["done"]
    elif kind == "now":
        add(f'<circle cx="{cxn}" cy="{AX_Y}" r="15" fill="{C["amber"]}" fill-opacity="0.14"/>')
        add(f'<circle cx="{cxn}" cy="{AX_Y}" r="10" fill="{C["bg"]}" stroke="{C["amber"]}" stroke-width="3"/>')
        add(f'<circle cx="{cxn}" cy="{AX_Y}" r="3.5" fill="{C["amber"]}"/>')
        ink = C["amber"]
    else:
        add(f'<circle cx="{cxn}" cy="{AX_Y}" r="9" fill="{C["bg"]}" stroke="{C["muted"]}" stroke-width="2" stroke-dasharray="3 3"/>')
        ink = C["muted"]
    text(cxn + 26, AX_Y - 26, tag, 12, 700, ink, family=MONO)
    text(cxn + 26 + width(tag, 12, 700) * 1.1 + 10, AX_Y - 26, name, 13, 700)
    text(cxn + 26, AX_Y + 30, state, 12, 600, ink if kind != "later" else C["muted"])
    # Drop line from the node to its container.
    add(f'<line x1="{cxn}" y1="{AX_Y+16}" x2="{cxn}" y2="{TOP}" stroke="{ink}" stroke-opacity="0.35" stroke-width="1.5" stroke-dasharray="2 5"/>')

# ================= M-1 container =================
def container(x, w, tag, title, pill_spec, summary, prog, border):
    rect(x, TOP, w, PANEL_H[tag], 16, C["panel"], border)
    text(x + 28, TOP + 40, tag, 13, 700, C["lime"] if tag in ("M-2", "M-3") else (C["done"] if tag == "M-1" else C["muted"]), family=MONO)
    tx = x + 28 + width(tag, 13, 700) * 1.1 + 12
    text(tx, TOP + 40, title, 18, 700)
    if pill_spec:
        pill(tx + width(title, 18, 700) + 18, TOP + 25, *pill_spec)
    lines(x + 28, TOP + 66, wrap(summary, 13, w - 56 - (220 if prog else 0)), 13, 18, 400, C["muted"])
    if prog:
        val, frac_, ink = prog
        rect(x + w - 240, TOP + 30, 160, 8, 4, C["graySoft"])
        if frac_ > 0: rect(x + w - 240, TOP + 30, 160 * frac_, 8, 4, ink)
        text(x + w - 28, TOP + 40, val, 14, 700, ink if frac_ > 0 else C["muted"], anchor="end")

container(P1X, P1W, "M-1", "MVP — ocenione sygnały z Wykopu dla zaproszonych osób",
          ("Zamknięty 17.10.2026", C["done"], C["doneSoft"], 10.5, False, 600, "check"),
          "Codzienny przepływ zamienia posty z #gielda w ocenione przez LLM sygnały; zaproszeni użytkownicy je przeglądają i filtrują.",
          ("10 / 10", 1.0, C["done"]), C["border"])
L1X = P1X + INNER + MARGIN1
box = {}
lane1_y = {}
ry = TOP + 98
for (key, name, sub, cols), ch, lh in zip(lanes1, CH1s, LANE1_HS):
    ink = C["sky"]
    lane1_y[key] = (ry, ry + lh)
    rect(L1X, ry, LANE1_W, lh, 12, C["panel2"], C["border"])
    rect(L1X + PAD, ry + 14, 26, 26, 7, ink + "22", ink + "77")
    text(L1X + PAD + 13, ry + 32, key, 13, 700, ink, family=MONO, anchor="middle")
    text(L1X + PAD + 38, ry + 32, name, 15, 700)
    text(L1X + PAD + 38, ry + 56, sub, 14, 400, C["muted"])
    text(L1X + LANE1_W - PAD, ry + 32, f"{len(cols)} / {len(cols)} ukończonych", 11.5, 600, C["dim"], anchor="end")
    add(f'<line x1="{L1X+PAD}" y1="{ry+HEAD_H+4}" x2="{L1X+LANE1_W-PAD}" y2="{ry+HEAD_H+4}" stroke="{C["border"]}"/>')
    for k, c in cols.items():
        offset = (0 if k == "S-03" else ch + 48 if k == "S-04" else (ch + 48) / 2) if key == "B" else 0
        box[k] = (L1X + PAD + c * (CW1 + GAP1), ry + CARD_OFF + offset, CW1, ch)
    ry += lh + LANE1_GAP
def L(k, f=0.5): x, y, w, h = box[k]; return (x, y + h * f)
def R(k): x, y, w, h = box[k]; return (x + w, y + h / 2)
def T(k, f=0.5): x, y, w, h = box[k]; return (x + w * f, y)
def B(k, f=0.5): x, y, w, h = box[k]; return (x + w * f, y + h)
# Transitive reduction: S-01 → S-07 / S-02 / S-06 are already reachable
# through prerequisites. Keep the full requirements on the cards.
e1 = [
    (("S-01", "F-03"), [R("S-01"), L("F-03")]),
    (("F-03", "S-07"), [R("F-03"), L("S-07")]),
    (("F-02", "S-02"), [R("F-02"), L("S-02")]),
    (("S-02", "S-03"), [T("S-02", 0.75), (T("S-02", 0.75)[0], L("S-03")[1]), L("S-03")]),
    (("S-02", "S-04"), [B("S-02", 0.75), (B("S-02", 0.75)[0], L("S-04")[1]), L("S-04")]),
    (("F-01", "S-05"), [R("F-01"), L("S-05")]),
    (("S-05", "S-06"), [R("S-05"), L("S-06")]),
    (("F-03", "F-02"), [B("F-03"), T("F-02")]),
    (("S-02", "S-05"), [B("S-02"), T("S-05")]),
]
for pair, pts in e1:
    route(pts, C["sky"], False, "sky", 2.5, r=0, edge=pair)
for k, (title, desc, c, r, req, cid) in m1.items():
    x, y, w, h = box[k]
    card(x, y, w, h, k, title, desc, "done", req=req, cid=cid)

# ================= M-2 container =================
container(P2X, P2W, "M-2", "MVP Quality & Reliability Hardening",
          ("Zamknięty 27.10.2026", C["done"], C["doneSoft"], 10.5, False, 600, "check"),
          "Złe noce, awarie dostawców i błędne wdrożenia są widoczne dla operatora, nie niszczą danych ani nie blokują pracy — zanim ruszy kolejna roadmapa funkcjonalna.",
          (f"{M2_DONE} / {M2_TOTAL}", M2_DONE / M2_TOTAL, C["done"]), C["border"])
box2 = {}
ry = TOP + 98
for ri, row in enumerate(rows2):
    ch = row_ch[ri]
    row_h = CARD_OFF + row_body[ri] + 36
    x = P2X + INNER
    for key, n in row:
        cw = row_cw(row, n)
        if len(row) == 1:
            pw_ = IN2
            gap = (pw_ - 2 * PAD - n * cw) / (n - 1)
        else:
            gap = M2_GRID_GAP
            pw_ = 2 * PAD + n * cw + (n - 1) * gap
        name, sub, ink = lanes[key]
        rect(x, ry, pw_, row_h, 12, C["panel2"], C["border"])
        rect(x + PAD, ry + 14, 26, 26, 7, ink + "22", ink + "77")
        text(x + PAD + 13, ry + 32, key, 13, 700, ink, family=MONO, anchor="middle")
        text(x + PAD + 38, ry + 32, name, 15, 700)
        text(x + PAD + 38, ry + 56, sub, 14, 400, C["muted"])
        cnt = sum(1 for v in m2.values() if v[0] == key)
        done = sum(1 for v in m2.values() if v[0] == key and v[4] == "done")
        counter = f"{done} / {cnt} ukończonych"
        text(x + pw_ - PAD, ry + 32, counter, 11.5, 600, C["dim"], anchor="end")
        add(f'<line x1="{x+PAD}" y1="{ry+HEAD_H+4}" x2="{x+pw_-PAD}" y2="{ry+HEAD_H+4}" stroke="{C["border"]}"/>')
        for k, v in m2.items():
            if v[0] == key:
                offset = (0 if k == "S-13" else ch + 48 if k == "S-18" else (ch + 48) / 2) if ri == 0 else 0
                box2[k] = (M2_COLUMNS[M2_CARD_COLUMN[k]], ry + CARD_OFF + offset, cw, ch)
        x += pw_ + GROUP_GAP
    ry += row_h + 18
def L2(k): x, y, w, h = box2[k]; return (x, y + h / 2)
def R2(k): x, y, w, h = box2[k]; return (x + w, y + h / 2)
def T2(k, f=0.5): x, y, w, h = box2[k]; return (x + w * f, y)
def B2(k, f=0.5): x, y, w, h = box2[k]; return (x + w * f, y + h)
e2 = [
    (("S-08", "S-12"), [R2("S-08"), L2("S-12")], C["muted"], True, "gray"),
    (("F-04", "S-13"), [T2("F-04", 0.75), (T2("F-04", 0.75)[0], L2("S-13")[1]), L2("S-13")], C["amber"], False, "amber"),
    (("F-04", "S-18"), [B2("F-04", 0.75), (B2("F-04", 0.75)[0], L2("S-18")[1]), L2("S-18")], C["amber"], False, "amber"),
    (("S-09", "S-17"), [R2("S-09"), L2("S-17")], C["sky"], False, "sky"),
]
for pair, pts, col, dashed, mk in e2:
    route(pts, col, dashed, mk, 2.5, r=0, edge=pair)
for k, (ln, col, title, desc, st, req, cid) in m2.items():
    x, y, w, h = box2[k]
    card(x, y, w, h, k, title, desc, st, req=req, cid=cid, star=(k == "S-08"))
for ids in (("S-10", "S-11", "S-16"), ("S-14", "S-15", "S-19")):
    x0 = box2[ids[0]][0]; x1 = box2[ids[-1]][0] + box2[ids[-1]][2]
    y = box2[ids[0]][1] + box2[ids[0]][3] + 20
    add(f'<line x1="{x0}" y1="{y}" x2="{x1}" y2="{y}" stroke="{C["muted"]}" stroke-opacity="0.35" stroke-dasharray="2 4"/>')
    tw = width("niezależne · równolegle", 10, 600) + 24
    rect((x0 + x1) / 2 - tw / 2, y - 9, tw, 18, 9, C["panel2"])
    text((x0 + x1) / 2, y + 4, "niezależne · równolegle", 10, 600, C["dim"], anchor="middle", spacing="1")

# ================= M-3 container (open) =================
container(P3X, P3W, "M-3", "Drugie źródło na utwardzonym rdzeniu",
          ("Otwarty", C["amber"], C["amberSoft"], 10.5, False, 600, "dot"),
          "Awaria albo cisza jednego źródła nie psuje drugiego, ślepe punkty audytu po M-2 są domknięte, a posty z subredditów idą do feedu i oceny jak Wykop — po pomiarze jakości i kosztu.",
          (f"{M3_DONE} / {M3_TOTAL}", M3_DONE / M3_TOTAL, C["lime"]), C["lime"] + "40")
box3 = {}
ry = TOP + 98
for ri, row in enumerate(rows3):
    row_h = CARD_OFF + row3_body[ri] + 36
    x = P3X + INNER
    for key, n in row:
        pw_ = 2 * PAD + n * CW2 + (n - 1) * M2_GRID_GAP
        name, sub, ink = lanes3[key]
        rect(x, ry, pw_, row_h, 12, C["panel2"], C["border"])
        rect(x + PAD, ry + 14, 26, 26, 7, ink + "22", ink + "77")
        text(x + PAD + 13, ry + 32, key, 13, 700, ink, family=MONO, anchor="middle")
        text(x + PAD + 38, ry + 32, name, 15, 700)
        text(x + PAD + 38, ry + 56, sub, 14, 400, C["muted"])
        cnt = sum(1 for v in m3.values() if v[0] == key)
        done = sum(1 for v in m3.values() if v[0] == key and v[3] == "done")
        text(x + pw_ - PAD, ry + 32, f"{done} / {cnt} ukończonych", 11.5, 600, C["dim"], anchor="end")
        add(f'<line x1="{x+PAD}" y1="{ry+HEAD_H+4}" x2="{x+pw_-PAD}" y2="{ry+HEAD_H+4}" stroke="{C["border"]}"/>')
        for k, v in m3.items():
            if v[0] == key:
                # First row is two cards tall: S-28 above S-29, everything else centred between them.
                offset = (0 if k == "S-28" else CH3 + 48 if k == "S-29" else (CH3 + 48) / 2) if ri == 0 else 0
                box3[k] = (M3_COLUMNS[M3_CARD_COLUMN[k]], ry + CARD_OFF + offset, CW2, CH3)
        x += pw_ + GROUP_GAP
    ry += row_h + 18
def L3(k): x, y, w, h = box3[k]; return (x, y + h / 2)
def R3(k): x, y, w, h = box3[k]; return (x + w, y + h / 2)
def T3(k, f=0.5): x, y, w, h = box3[k]; return (x + w * f, y)
def B3(k, f=0.5): x, y, w, h = box3[k]; return (x + w * f, y + h)
e3 = [  # dashed coral: from a blocked slice
    (("S-20", "S-27"), [R3("S-20"), L3("S-27")], C["lime"], False, "lime"),
    (("S-27", "S-28"), [T3("S-27", 0.75), (T3("S-27", 0.75)[0], L3("S-28")[1]), L3("S-28")], C["coral"], True, "coral"),
    (("S-27", "S-29"), [B3("S-27", 0.75), (B3("S-27", 0.75)[0], L3("S-29")[1]), L3("S-29")], C["coral"], True, "coral"),
]
for pair, pts, col, dashed, mk in e3:
    route(pts, col, dashed, mk, 2.5, r=0, edge=pair)
for k, (ln, title, desc, st, req, cid) in m3.items():
    x, y, w, h = box3[k]
    card(x, y, w, h, k, title, desc, st, req=req, cid=cid, star=(k == "S-20"), note=(st == "blocked"))
for ids in (("S-21", "S-25"), ("S-22", "S-23", "S-24")):
    x0 = box3[ids[0]][0]; x1 = box3[ids[-1]][0] + box3[ids[-1]][2]
    y = box3[ids[0]][1] + box3[ids[0]][3] + 20
    add(f'<line x1="{x0}" y1="{y}" x2="{x1}" y2="{y}" stroke="{C["muted"]}" stroke-opacity="0.35" stroke-dasharray="2 4"/>')
    tw = width("niezależne · równolegle", 10, 600) + 24
    rect((x0 + x1) / 2 - tw / 2, y - 9, tw, 18, 9, C["panel2"])
    text((x0 + x1) / 2, y + 4, "niezależne · równolegle", 10, 600, C["dim"], anchor="middle", spacing="1")

# ================= Po M-3 container (backlog) =================
container(P4X, P4W, "PO M-3", "Odroczone",
          ("Parked", C["gray"], C["graySoft"], 10.5, False, 600, "dot"),
          f"{DEFERRED} {plural(DEFERRED, 'pozycje', 'pozycji')} z sekcji Parked roadmapy (zaparkowany S-12 jest na karcie w M-2), w tym kandydaci na M-4 (weryfikacja i regresja) i M-5 (nowe UI/UX z E2E). Bez ustalonej kolejności; każda wymaga osobnego planu, zanim wejdzie do roadmapy.",
          None, C["border"])
for gi, (gname, items) in enumerate(groups):
    col, row = gi % 2, gi // 2
    gx = P4X + INNER + col * (GW + 20)
    gy = TOP + 132 + (0 if row == 0 else gh[0] + 20)
    rect(gx, gy, GW, gh[row], 12, C["panel2"], C["border"])
    text(gx + 18, gy + 30, gname, 15, 700)
    text(gx + GW - 18, gy + 30, str(len(items)), 13, 700, C["muted"], family=MONO, anchor="end")
    add(f'<line x1="{gx+18}" y1="{gy+46}" x2="{gx+GW-18}" y2="{gy+46}" stroke="{C["border"]}"/>')
    for ii, (ref, label) in enumerate(items):
        iy = gy + 74 + ii * ITEM_H
        add(f'<circle cx="{gx+22}" cy="{iy-4}" r="2.6" fill="{C["dim"]}"/>')
        label_lines = wrap(label, 15, GW - 54, 500)
        lines(gx + 34, iy, label_lines, 15, 20, 500)
        text(gx + 34, iy + len(label_lines) * 20, ref, 12, 600, C["dim"], family=MONO)

# ================= footer + assembly =================
text(M, H - 30, "Strzałki pomijają zależności wynikające ze ścieżek pośrednich. Pełne wymagania: na kartach. · Źródło: context/foundation/roadmap.md v4", 11, 400, C["dim"])
text(W - M, H - 30, "MAPA ZALEŻNOŚCI · BEZ TERMINÓW", 10.5, 700, C["dim"], anchor="end", spacing="1.6")
head = [
    f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img" aria-labelledby="t d">',
    '<title id="t">Szczur Radar — roadmapa produktu: M-1 → M-2 → M-3 → Po M-3</title>',
    '<desc id="d">Roadmapa z context/foundation/roadmap.md v4 jako oś od lewej do prawej: kamień milowy M-1 (F-01–F-03, S-01–S-07, ukończony), M-2 MVP Quality &amp; Reliability Hardening (F-04, S-08–S-19, zamknięty 27.10.2026, S-12 zaparkowany), M-3 Drugie źródło na utwardzonym rdzeniu (S-20–S-29, otwarty, gwiazda przewodnia S-20) i pozycje odroczone. Strzałka prowadzi od warunku wstępnego do elementu, który od niego zależy.</desc>',
    '<defs>',
]
for name, col in (("lime", C["lime"]), ("done", C["done"]), ("sky", C["sky"]), ("coral", C["coral"]), ("amber", C["amber"]), ("gray", C["muted"])):
    head.append(f'<marker id="arrow-{name}" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M1 1 L9 5 L1 9" fill="none" stroke="{col}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></marker>')
head += [
    f'<linearGradient id="star" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#1d2b1d"/><stop offset="1" stop-color="{C["card"]}"/></linearGradient>',
    f'<linearGradient id="hero" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="{C["panel2"]}"/><stop offset="1" stop-color="#121c17"/></linearGradient>',
    '<pattern id="grid" width="32" height="32" patternUnits="userSpaceOnUse"><path d="M32 0 H0 V32" fill="none" stroke="#ffffff" stroke-opacity="0.025" stroke-width="1"/></pattern>',
    '</defs>',
]
open(OUTPUT, "w").write("\n".join(head + out + ['</svg>']))
print(OUTPUT, W, H, "panels:", round(P1H), round(P2H), round(P3H), round(P4H))
