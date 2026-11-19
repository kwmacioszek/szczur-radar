# design-check

The checks of the UI design change (F-07, `context/foundation/ui-design.md` and its assets) as one Node script. The agent that changes the design runs it locally from the repository root, so conformance to the standards is verified by a command, not by eye. There is no CI job for it (like `tools/render_roadmap_svg.py` and `tools/ci-runner/`); it lives in `tools/` so that it stays at a fixed path after the change folder is archived.

## Requirements

- Node 24, from the repository root.
- `npm ci` in `szczur-radar-web/` — the script takes `chromium` from `@playwright/test` and `AxeBuilder` from `@axe-core/playwright` out of `szczur-radar-web/node_modules` (`createRequire` on `szczur-radar-web/package.json`). It has no dependencies and no `package.json` of its own.
- `npx playwright install chromium` in `szczur-radar-web/` (once per Playwright version) for `pages`, `shots` and `selftest`.
- The browser is the full Chromium build (`channel: 'chromium'`, not the headless shell) started with `--lang=pl-PL`, and every page runs with the `pl-PL` locale in the `Europe/Warsaw` time zone: only then native fields such as `<input type="date">` render as the app's users see them (`20.10.2026`).

Exit codes: `0` no violations, `1` violations, `2` usage error, `3` missing environment (no `node_modules`, no Chromium). Every violation is one line: `VIOLATION [kind] <file> <theme> <width>: <message>` (`-` where theme or width does not apply), then a summary line.

## Commands

```bash
node tools/design-check/design-check.mjs contrast <tokens.css> <pairs.json>   # or: contrast <dir> (tokens.css + pairs.json)
node tools/design-check/design-check.mjs pages <dir | file.html> [...]
node tools/design-check/design-check.mjs shots <dir | file.html> [--out <dir>] [--scale <n>] [--full-page] [--only <name>:<width>[,...]]
node tools/design-check/design-check.mjs inventory <dir | screens.json> [--gallery <file.html>] [--assets]
node tools/design-check/design-check.mjs doc --stage <1|2|3|4> [--doc <path>]
node tools/design-check/design-check.mjs selftest
```

| Command | What it checks |
| --- | --- |
| `contrast` | Reads the token file as `szczur-radar-web/src/styleContract.test.ts` does (comments removed; the light roles from the first `:root` block, the dark ones from `:root[data-theme='dark']`, no fallback of one to the other) and prints a markdown table of every pair (light, dark), also on success. Violations: contrast below the pair's `min` (`contrast`), a role that is not a 3 or 6 digit hex (`non-hex`), a role undefined in a theme (`undefined-role`), `prefers-color-scheme` anywhere in the file, a missing block, a malformed pair (`pairs`). |
| `pages` | Every `*.html` (directories are searched recursively) in light and dark at 1280, 375 and 320 px (window height 900): axe-core with the tags of `szczur-radar-web/visual/axe.ts` (`axe:<rule>`; the target size, WCAG 2.5.8, is the rule `target-size` of `wcag22aa`), no horizontal scroll (`overflow`), exactly one `main` and at most one `banner` (`landmark`), and `<html data-theme>` equal to the theme asked for (`theme`). Once per page (light, 1280) the fonts: every element with its own text is asked for `CSS.getPlatformFontsForNode`, and text drawn by a font that is not a declared `@font-face` is a `font` violation (emoji families excepted), as is a page with no custom font at all. Waits for `document.fonts.ready`. |
| `shots` | `<name>-<theme>-<width>.png` for light/dark at 1280 (window 1280x900) and 375 (375x812), next to the page or in `--out`. `--scale` is the `deviceScaleFactor` (default 1), `--full-page` shoots the whole page instead of the window, `--only stream:1280,stream:375,overview:1280` limits the set. Animations are off, the shot is taken after `document.fonts.ready`. |
| `inventory <variants dir>` | Directory with `a`, `b`, `c` (required) and any further variant directory that has its own `tokens.css`, e.g. `d`: each has `tokens.css`, `pairs.json`, `direction.css`, `stream.html`, `overview.html` and the six shots (`stream-{light,dark}-{1280,375}`, `overview-{light,dark}-1280`), plus `directions.md` in the directory. |
| `inventory <screens.json>` | Each screen has its `html` and four shots `screens/<name>-{light,dark}-{1280,375}.png` next to the json; names are unique. |
| `inventory ... --gallery <file>` | The gallery text contains `<parent dir>/<file>` for every expected shot (`a/stream-light-1280.png`, `screens/overview-dark-375.png`). |
| `inventory <ui-design dir> --assets` | `fonts/` has a `*.woff2` and a licence file (name matching `licen[cs]e` or `OFL`), `icons/` a `*.svg` and a licence file; `prototypes/prototype.css` and every `icons/*.svg` have no colour literal (hex, `rgb()`, `hsl()`, `oklch()`; `url(...)` and `href="#..."` are ignored). |
| `doc` | `ui-design.md` (default `context/foundation/ui-design.md`; assets in `ui-design/` beside it): the 13 `## ` sections in order; every relative link exists; no link or backticked path into `context/changes/`. Stage 1: the first five sections filled (not empty, no "Do uzupełnienia w fazie") and every FR of the list in "Ekrany". Stage 2: also "Język wizualny", "Komponenty", "Treść i etykiety" filled, at least one link `ui-design/prototypes/components.html#c-<slug>` in "Komponenty", each with `id="c-<slug>"` in the sheet, and the shots `ui-design/screens/components-{light,dark}-{1280,375}.png`. Stage 3: also `ui-design/screens.json` and "Ekrany" link each other both ways, and the `fr` of the screens cover the list. Stage 4: all 13 sections filled and "Kontrakt dla testów E2E" names every screen `name` in backticks. |
| `selftest` | Runs the checks on `fixtures/`: `ok/` must pass `contrast` and `pages`, and each sample of `broken/` must give a violation of its own kind: `low-contrast` (`contrast`), `non-hex` (`non-hex`), `prefers-color-scheme`, `overflow-375.html` (`overflow`), `button-no-name.html` (`axe:button-name`), `system-font.html` (`font`). Exits 0 only when all of that holds. |

Every theme-aware page sets `data-theme` on `<html>` itself from the address: `?theme=dark` is dark, no parameter is light. The tool opens `file://.../page.html` and `file://.../page.html?theme=dark`.

## Files

| Path | What it is |
| --- | --- |
| `design-check.mjs` | the script |
| `fixtures/ok/` | a small valid set (`tokens.css`, `pairs.json`, `page.html`); `selftest` runs its `pages` without the font check, because the repository holds no text font file to declare (the font check is proved by `broken/system-font.html`) |
| `fixtures/broken/` | deliberately broken samples, one per kind of violation |
| `fixtures/app-pairs.json` | the 19 pairs of `PAIRS` in `szczur-radar-web/src/styleContract.test.ts` with `min` 4.5: `contrast szczur-radar-web/src/style.css tools/design-check/fixtures/app-pairs.json` exits 0, which proves the parser reads the application's file as its test does |

## `pairs.json`

An array; every pair has all four fields:

```json
[
  { "fg": "--text", "bg": "--bg", "min": 4.5, "kind": "text" },
  { "fg": "--ring", "bg": "--bg", "min": 3.0, "kind": "ui" }
]
```

`min` is 4.5 for text (WCAG 1.4.3) and 3.0 for interface elements and focus indicators (WCAG 1.4.11); `kind` is `text` or `ui`. Each role must be defined in both blocks, as a hex of 3 or 6 digits.

## `screens.json`

Paths are relative to the directory of the json file:

```json
{
  "screens": [
    {
      "name": "overview",
      "title": "Przegląd dnia",
      "html": "prototypes/screens/overview.html",
      "fr": ["FR-039"],
      "slice": "S-59",
      "data": "dziś"
    }
  ]
}
```

`name` is unique and gives the shot names (`screens/overview-light-1280.png`); `fr` lists the requirements the screen covers; `slice` and `data` say when and with what data the screen exists in the application.
