#!/usr/bin/env node
// design-check: the checks of the UI design change (F-07) in one script, run locally from the repository root by
// the agent that changes the design (no CI job, like tools/render_roadmap_svg.py and tools/ci-runner/).
//
//   node tools/design-check/design-check.mjs <command> [arguments]
//
// Commands: contrast, pages, shots, inventory, doc, selftest (see tools/design-check/README.md).
// Exit code: 0 only without violations, 1 with violations, 2 for a usage error, 3 for a missing environment
// (szczur-radar-web/node_modules or the Chromium of its Playwright version).
// Playwright and axe come from szczur-radar-web/node_modules; no dependency of its own, no package.json here.
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))
const FIXTURES = path.join(HERE, 'fixtures')
const WEB_PACKAGE = new URL('../../szczur-radar-web/package.json', import.meta.url)

// ---------------------------------------------------------------------------------------------------------------
// Constants copied from the application; keep in step with their source.

// Same list as szczur-radar-web/visual/axe.ts (WCAG 2.0, 2.1 and 2.2 A/AA, no best-practice rules). The rule
// `target-size` (WCAG 2.5.8) comes from the tag wcag22aa; there is no size check of our own.
const WCAG_TAGS = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']

// The colour-literal part of LITERAL in szczur-radar-web/src/styleContract.test.ts (hex, rgb, hsl, oklch).
const COLOUR_LITERAL = /#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\(|oklch\(/

const THEMES = ['light', 'dark']
const PAGE_WIDTHS = [1280, 375, 320]
const SHOT_WIDTHS = [1280, 375]
const WINDOW_HEIGHT = { 1280: 900, 375: 812, 320: 900 }

const DOC_SECTIONS = [
  'Cel i zakres',
  'Użytkownik i zadania',
  'Zasady i standardy',
  'Architektura informacji i nawigacja',
  'Ekrany',
  'Język wizualny',
  'Komponenty',
  'Treść i etykiety',
  'Kontrakt dla testów E2E',
  'Wdrożenie',
  'Różnice wobec roadmapy',
  'Propozycje poza PRD',
  'Decyzja właściciela',
]
const FR_LIST = [
  'FR-007', 'FR-008', 'FR-009', 'FR-010', 'FR-011a', 'FR-011b', 'FR-012', 'FR-013', 'FR-015', 'FR-018', 'FR-021',
  'FR-022', 'FR-030', 'FR-033', 'FR-034', 'FR-035', 'FR-036', 'FR-037', 'FR-038', 'FR-039', 'FR-040',
]
const PLACEHOLDER = 'Do uzupełnienia w fazie'
const DEFAULT_DOC = 'context/foundation/ui-design.md'

const USAGE = `Usage: node tools/design-check/design-check.mjs <command> [arguments]

  contrast <tokens.css> <pairs.json>     contrast of the role pairs in both themes (or: contrast <dir>)
  pages <dir | file.html> [...]          axe, horizontal scroll, landmarks, fonts; light/dark x 1280/375/320
  shots <dir | file.html> [--out <dir>] [--scale <n>] [--full-page] [--only <name>:<width>[,...]]
  inventory <dir | screens.json> [--gallery <file.html>] [--assets]
  doc --stage <1|2|3|4> [--doc <path>]   structure and links of ${DEFAULT_DOC}
  selftest                               the checks against tools/design-check/fixtures/`

class UsageError extends Error {}
class EnvironmentError extends Error {}

// ---------------------------------------------------------------------------------------------------------------
// Violations

/** @typedef {{kind: string, file: string, theme: string, width: string, message: string}} Violation */

/** @returns {Violation} */
function violation(kind, file, message, theme = '-', width = '-') {
  return { kind, file, theme, width: String(width), message }
}

function formatViolation(v) {
  return `VIOLATION [${v.kind}] ${v.file} ${v.theme} ${v.width}: ${v.message}`
}

function report(violations, okText) {
  for (const v of violations) console.log(formatViolation(v))
  console.log(violations.length === 0 ? `OK: ${okText}` : `FAILED: ${violations.length} violation(s)`)
  return violations.length === 0 ? 0 : 1
}

function rel(file) {
  const r = path.relative(process.cwd(), file)
  return r === '' ? '.' : r
}

// ---------------------------------------------------------------------------------------------------------------
// contrast

/** The text between the brace after the first `selector` in `text` and its matching closing brace; null if absent. */
function block(text, selector) {
  const at = text.indexOf(selector)
  const open = at < 0 ? -1 : text.indexOf('{', at)
  if (open < 0) return null
  let depth = 0
  for (let i = open; i < text.length; i++) {
    if (text[i] === '{') depth++
    if (text[i] === '}' && --depth === 0) return text.slice(open + 1, i)
  }
  return null
}

function declarations(text) {
  return new Map([...text.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)].map((m) => [m[1], m[2].trim()]))
}

/** Relative luminance (WCAG 2.x, sRGB threshold 0.04045); same as styleContract.test.ts. */
function luminance(hex) {
  const digits = hex.length === 4 ? [...hex.slice(1)].map((d) => d + d).join('') : hex.slice(1)
  const [r, g, b] = [0, 2, 4].map((at) => {
    const channel = Number.parseInt(digits.slice(at, at + 2), 16) / 255
    return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

function contrastRatio(a, b) {
  const [la, lb] = [luminance(a), luminance(b)]
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05)
}

const HEX = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i

/**
 * Reads the token file as styleContract.test.ts does: the first `:root` block is the light theme, the block
 * `:root[data-theme='dark']` the dark one, with no fallback of one to the other.
 * @returns {{violations: Violation[], rows: {fg: string, bg: string, min: number, kind: string, light: string, dark: string}[]}}
 */
function checkContrast(tokensFile, pairsFile) {
  const violations = []
  const rows = []
  const label = rel(tokensFile)
  const source = fs.readFileSync(tokensFile, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '')

  if (source.includes('prefers-color-scheme')) {
    violations.push(violation('prefers-color-scheme', label,
      'the file uses prefers-color-scheme; the theme is the data-theme attribute of <html>'))
  }
  const lightBlock = block(source, ':root')
  const darkBlock = block(source, ":root[data-theme='dark']")
  if (lightBlock === null) violations.push(violation('block', label, 'no :root block (the light theme)', 'light'))
  if (darkBlock === null) violations.push(violation('block', label, "no :root[data-theme='dark'] block (the dark theme)", 'dark'))
  const roles = {
    light: declarations(lightBlock ?? ''),
    dark: declarations(darkBlock ?? ''),
  }

  let pairs
  try {
    pairs = JSON.parse(fs.readFileSync(pairsFile, 'utf8'))
  } catch (error) {
    violations.push(violation('pairs', rel(pairsFile), `cannot read the pairs: ${error.message}`))
    return { violations, rows }
  }
  if (!Array.isArray(pairs) || pairs.length === 0) {
    violations.push(violation('pairs', rel(pairsFile), 'pairs.json must be a non-empty array'))
    return { violations, rows }
  }

  const reported = new Set()
  /** The hex of a role in a theme, or null (a violation is recorded once per role and theme). */
  const role = (theme, name) => {
    if (theme === 'light' && lightBlock === null) return null
    if (theme === 'dark' && darkBlock === null) return null
    const value = roles[theme].get(name)
    const key = `${theme}:${name}`
    if (value === undefined) {
      if (!reported.has(key)) violations.push(violation('undefined-role', label, `${name} is not defined in the ${theme} block`, theme))
      reported.add(key)
      return null
    }
    if (!HEX.test(value)) {
      if (!reported.has(key)) violations.push(violation('non-hex', label, `${name} is not a 3 or 6 digit hex colour: ${value}`, theme))
      reported.add(key)
      return null
    }
    return value
  }

  pairs.forEach((pair, index) => {
    const where = `${rel(pairsFile)}[${index}]`
    const valid = pair !== null && typeof pair === 'object'
      && typeof pair.fg === 'string' && typeof pair.bg === 'string'
      && typeof pair.min === 'number' && Number.isFinite(pair.min) && pair.min > 0
      && (pair.kind === 'text' || pair.kind === 'ui')
    if (!valid) {
      violations.push(violation('pairs', where,
        'a pair needs fg and bg (role names), a numeric min and kind "text" or "ui"'))
      return
    }
    const row = { fg: pair.fg, bg: pair.bg, min: pair.min, kind: pair.kind, light: '-', dark: '-' }
    for (const theme of THEMES) {
      const fg = role(theme, pair.fg)
      const bg = role(theme, pair.bg)
      if (fg === null || bg === null) continue
      const ratio = contrastRatio(fg, bg)
      row[theme] = ratio.toFixed(2)
      if (Number(row[theme]) < pair.min) {
        violations.push(violation('contrast', label,
          `${pair.fg} on ${pair.bg} (${pair.kind}) is ${row[theme]}:1, below ${pair.min}:1`, theme))
        row[theme] += ' (!)'
      }
    }
    rows.push(row)
  })
  return { violations, rows }
}

function printContrastTable(rows) {
  console.log('| pair | kind | min | light | dark |')
  console.log('| --- | --- | --- | --- | --- |')
  for (const r of rows) console.log(`| \`${r.fg}\` on \`${r.bg}\` | ${r.kind} | ${r.min} | ${r.light} | ${r.dark} |`)
}

function runContrast(args) {
  let tokens
  let pairs
  if (args.length === 1) {
    tokens = path.join(args[0], 'tokens.css')
    pairs = path.join(args[0], 'pairs.json')
  } else if (args.length === 2) {
    ;[tokens, pairs] = args
  } else {
    throw new UsageError('contrast takes <tokens.css> <pairs.json> or <dir>')
  }
  for (const file of [tokens, pairs]) if (!fs.existsSync(file)) throw new UsageError(`no such file: ${file}`)
  const { violations, rows } = checkContrast(tokens, pairs)
  printContrastTable(rows)
  return report(violations, `${rows.length} pair(s) meet their thresholds in both themes`)
}

// ---------------------------------------------------------------------------------------------------------------
// Browser

async function loadBrowserTools() {
  const web = fileURLToPath(WEB_PACKAGE)
  if (!fs.existsSync(path.join(path.dirname(web), 'node_modules'))) {
    throw new EnvironmentError('szczur-radar-web/node_modules is missing: run `npm ci` in szczur-radar-web/')
  }
  const requireWeb = createRequire(WEB_PACKAGE)
  try {
    const { chromium } = requireWeb('@playwright/test')
    const AxeBuilder = requireWeb('@axe-core/playwright') // CJS: module.exports is the class (also .default)
    return { chromium, AxeBuilder }
  } catch (error) {
    throw new EnvironmentError(`cannot load Playwright or axe from szczur-radar-web/node_modules (${error.message}): run \`npm ci\` in szczur-radar-web/`)
  }
}

async function launch(chromium) {
  try {
    // Full Chromium (not the headless shell) with a Polish UI language: only then native fields such as
    // <input type="date"> render as the app's users see them (20.10.2026); the context locale does not reach them.
    return await chromium.launch({ channel: 'chromium', args: ['--lang=pl-PL'] })
  } catch (error) {
    throw new EnvironmentError(`cannot launch Chromium (${String(error.message).split('\n')[0]}): run \`npx playwright install chromium\` in szczur-radar-web/`)
  }
}

function pageUrl(file, theme) {
  const url = pathToFileURL(path.resolve(file))
  if (theme === 'dark') url.search = '?theme=dark'
  return url.href
}

/** Opens the page in the theme at the width, waits for the fonts and checks the theme attribute. */
async function openPage(context, file, theme, width, label, violations) {
  const page = await context.newPage()
  await page.goto(pageUrl(file, theme), { waitUntil: 'load' })
  await page.evaluate(() => document.fonts.ready)
  const actual = await page.evaluate(() => document.documentElement.dataset.theme ?? null)
  if (actual !== theme) {
    violations.push(violation('theme', label, `<html data-theme> is ${JSON.stringify(actual)}, expected "${theme}"`, theme, width))
  }
  return page
}

/** Every `*.html` under each target (a directory is searched recursively, a file is taken as it is). */
function collectHtml(targets) {
  const pages = []
  for (const target of targets) {
    if (!fs.existsSync(target)) throw new UsageError(`no such file or directory: ${target}`)
    const stat = fs.statSync(target)
    if (stat.isDirectory()) {
      const walk = (dir) => {
        for (const entry of fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
          const full = path.join(dir, entry.name)
          if (entry.isDirectory()) walk(full)
          else if (entry.name.endsWith('.html')) pages.push({ file: full, label: path.relative(target, full) })
        }
      }
      walk(target)
    } else {
      if (!target.endsWith('.html')) throw new UsageError(`not an html file: ${target}`)
      pages.push({ file: target, label: path.basename(target) })
    }
  }
  if (pages.length === 0) throw new UsageError('no *.html page found')
  return pages
}

// ---------------------------------------------------------------------------------------------------------------
// pages

/** CDP: the fonts Chromium drew the text of each element with (the method of visual/font.spec.ts). */
async function checkFonts(context, page, label, violations) {
  const probes = await page.evaluate(() => {
    const skip = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'TEMPLATE', 'TITLE', 'HEAD', 'HTML', 'META', 'LINK'])
    const labels = []
    for (const el of document.querySelectorAll('body, body *')) {
      if (skip.has(el.tagName)) continue
      const own = [...el.childNodes].find((n) => n.nodeType === 3 && n.textContent.trim() !== '')
      if (!own) continue
      if (!el.getClientRects().length || getComputedStyle(el).visibility === 'hidden') continue
      el.setAttribute('data-dc-probe', '')
      labels.push(`<${el.tagName.toLowerCase()}> "${own.textContent.trim().slice(0, 30)}"`)
    }
    return labels
  })
  const cdp = await context.newCDPSession(page)
  let customUsed = false
  /** family -> {count, example} for system fonts that drew text */
  const systemFonts = new Map()
  try {
    await cdp.send('DOM.enable')
    await cdp.send('CSS.enable')
    const { root } = await cdp.send('DOM.getDocument', { depth: 0 })
    const { nodeIds } = await cdp.send('DOM.querySelectorAll', { nodeId: root.nodeId, selector: '[data-dc-probe]' })
    for (const [index, nodeId] of nodeIds.entries()) {
      const { fonts } = await cdp.send('CSS.getPlatformFontsForNode', { nodeId })
      for (const font of fonts) {
        if (font.isCustomFont === true) customUsed = true
        else if (!/emoji/i.test(font.familyName) && font.glyphCount > 0) {
          const entry = systemFonts.get(font.familyName) ?? { count: 0, example: probes[index] }
          entry.count++
          systemFonts.set(font.familyName, entry)
        }
      }
    }
  } finally {
    await cdp.detach()
  }
  for (const [family, { count, example }] of systemFonts) {
    violations.push(violation('font', label,
      `text drawn with the system font "${family}", not a declared @font-face (${count} element(s), e.g. ${example})`, 'light', 1280))
  }
  if (!customUsed) {
    violations.push(violation('font', label, 'no custom (@font-face) font was used on the page', 'light', 1280))
  }
}

/**
 * @param {{file: string, label: string}[]} pages
 * @param {{skipFonts?: boolean, widths?: number[]}} options widths: the set of widths (default all three)
 * @returns {Promise<Violation[]>}
 */
async function checkPages(pages, options = {}) {
  const { chromium, AxeBuilder } = await loadBrowserTools()
  const browser = await launch(chromium)
  const violations = []
  const widths = options.widths ?? PAGE_WIDTHS
  try {
    for (const { file, label } of pages) {
      for (const theme of THEMES) {
        for (const width of widths) {
          const context = await browser.newContext({
            viewport: { width, height: WINDOW_HEIGHT[width] ?? 900 },
            colorScheme: 'light',
            reducedMotion: 'reduce',
            locale: 'pl-PL',
            timezoneId: 'Europe/Warsaw',
          })
          try {
            const page = await openPage(context, file, theme, width, label, violations)
            const result = await new AxeBuilder({ page }).withTags(WCAG_TAGS).analyze()
            for (const v of result.violations) {
              const targets = v.nodes.slice(0, 4).map((node) => node.target.join(' ')).join(' | ')
              const more = v.nodes.length > 4 ? ` (+${v.nodes.length - 4} more)` : ''
              violations.push(violation(`axe:${v.id}`, label, `${v.help}: ${targets}${more}`, theme, width))
            }
            const overflow = await page.evaluate(() => ({
              scroll: document.documentElement.scrollWidth,
              client: document.documentElement.clientWidth,
            }))
            if (overflow.scroll > overflow.client) {
              violations.push(violation('overflow', label,
                `horizontal scroll: scrollWidth ${overflow.scroll} > clientWidth ${overflow.client}`, theme, width))
            }
            const mains = await page.getByRole('main').count()
            if (mains !== 1) violations.push(violation('landmark', label, `${mains} elements with the role main, expected exactly 1`, theme, width))
            const banners = await page.getByRole('banner').count()
            if (banners > 1) violations.push(violation('landmark', label, `${banners} elements with the role banner, expected at most 1`, theme, width))
            if (!options.skipFonts && theme === 'light' && width === 1280) {
              await checkFonts(context, page, label, violations)
            }
          } finally {
            await context.close()
          }
        }
      }
    }
  } finally {
    await browser.close()
  }
  return violations
}

async function runPages(args) {
  if (args.length === 0) throw new UsageError('pages takes at least one directory or html file')
  const pages = collectHtml(args)
  const violations = await checkPages(pages)
  return report(violations, `${pages.length} page(s) checked in light and dark at 1280, 375 and 320 px`)
}

// ---------------------------------------------------------------------------------------------------------------
// shots

/** Splits `--name value` / `--flag` options from positional arguments. */
function parseOptions(args, valueOptions, flagOptions) {
  const positional = []
  const options = {}
  for (let i = 0; i < args.length; i++) {
    const arg = args[i]
    if (valueOptions.includes(arg)) {
      if (i + 1 >= args.length) throw new UsageError(`${arg} needs a value`)
      options[arg.slice(2)] = args[++i]
    } else if (flagOptions.includes(arg)) {
      options[arg.slice(2)] = true
    } else if (arg.startsWith('--')) {
      throw new UsageError(`unknown option ${arg}`)
    } else {
      positional.push(arg)
    }
  }
  return { positional, options }
}

async function runShots(args) {
  const { positional, options } = parseOptions(args, ['--out', '--scale', '--only'], ['--full-page'])
  if (positional.length === 0) throw new UsageError('shots takes at least one directory or html file')
  const scale = options.scale === undefined ? 1 : Number(options.scale)
  if (!Number.isFinite(scale) || scale <= 0) throw new UsageError('--scale takes a positive number')
  let only = null
  if (options.only !== undefined) {
    only = new Set()
    for (const item of options.only.split(',')) {
      const match = /^([^:]+):(\d+)$/.exec(item.trim())
      if (!match || !SHOT_WIDTHS.includes(Number(match[2]))) {
        throw new UsageError(`--only takes <name>:<width> with width 1280 or 375, got "${item}"`)
      }
      only.add(`${match[1]}:${match[2]}`)
    }
  }
  const pages = collectHtml(positional)
  const names = pages.map((p) => path.basename(p.file, '.html'))
  if (options.out !== undefined && new Set(names).size !== names.length) {
    throw new UsageError('--out with two pages of the same name would overwrite shots; shoot them without --out')
  }
  if (only !== null) {
    const unknown = [...only].filter((key) => !names.includes(key.split(':')[0]))
    if (unknown.length > 0) throw new UsageError(`--only names no page found: ${unknown.join(', ')}`)
  }

  const { chromium } = await loadBrowserTools()
  const browser = await launch(chromium)
  const violations = []
  let written = 0
  try {
    for (const { file, label } of pages) {
      const name = path.basename(file, '.html')
      const outDir = options.out !== undefined ? options.out : path.dirname(file)
      fs.mkdirSync(outDir, { recursive: true })
      for (const theme of THEMES) {
        for (const width of SHOT_WIDTHS) {
          if (only !== null && !only.has(`${name}:${width}`)) continue
          const context = await browser.newContext({
            viewport: { width, height: WINDOW_HEIGHT[width] },
            deviceScaleFactor: scale,
            colorScheme: 'light',
            reducedMotion: 'reduce',
            locale: 'pl-PL',
            timezoneId: 'Europe/Warsaw',
          })
          try {
            const page = await openPage(context, file, theme, width, label, violations)
            const target = path.join(outDir, `${name}-${theme}-${width}.png`)
            await page.screenshot({ path: target, fullPage: options['full-page'] === true, animations: 'disabled', caret: 'initial' })
            console.log(`wrote ${rel(target)}`)
            written++
          } finally {
            await context.close()
          }
        }
      }
    }
  } finally {
    await browser.close()
  }
  return report(violations, `${written} shot(s) written`)
}

// ---------------------------------------------------------------------------------------------------------------
// inventory

const LICENCE_FILE = /licen[cs]e|OFL/i

function listFiles(dir) {
  return fs.existsSync(dir) && fs.statSync(dir).isDirectory() ? fs.readdirSync(dir) : []
}

function inventoryVariants(dir, galleryText, violations) {
  const files = ['tokens.css', 'pairs.json', 'direction.css', 'stream.html', 'overview.html']
  const shots = ['stream-light-1280', 'stream-dark-1280', 'overview-light-1280', 'overview-dark-1280', 'stream-light-375', 'stream-dark-375']
  if (!fs.existsSync(path.join(dir, 'directions.md'))) {
    violations.push(violation('inventory', rel(path.join(dir, 'directions.md')), 'missing file'))
  }
  let count = 0
  // The three variants of a round are required; a further directory with its own tokens.css (a combination the
  // owner asked for, e.g. `d`) is held to the same inventory.
  const extra = fs
    .readdirSync(dir, { withFileTypes: true })
    .filter((e) => e.isDirectory() && !['a', 'b', 'c'].includes(e.name) && fs.existsSync(path.join(dir, e.name, 'tokens.css')))
    .map((e) => e.name)
    .sort()
  for (const variant of ['a', 'b', 'c', ...extra]) {
    const base = path.join(dir, variant)
    if (!fs.existsSync(base) || !fs.statSync(base).isDirectory()) {
      violations.push(violation('inventory', rel(base), 'missing variant directory'))
      continue
    }
    const expected = [...files, ...shots.map((s) => `${s}.png`)]
    for (const name of expected) {
      if (!fs.existsSync(path.join(base, name))) violations.push(violation('inventory', rel(path.join(base, name)), 'missing file'))
      else count++
      if (galleryText !== null && name.endsWith('.png') && !galleryText.includes(`${variant}/${name}`)) {
        violations.push(violation('gallery', `${variant}/${name}`, 'the gallery does not refer to this shot'))
      }
    }
  }
  return `${count} file(s) of the variants present`
}

function inventoryScreens(jsonFile, galleryText, violations) {
  let data
  try {
    data = JSON.parse(fs.readFileSync(jsonFile, 'utf8'))
  } catch (error) {
    violations.push(violation('inventory', rel(jsonFile), `cannot read screens.json: ${error.message}`))
    return ''
  }
  if (!data || !Array.isArray(data.screens) || data.screens.length === 0) {
    violations.push(violation('inventory', rel(jsonFile), 'screens.json needs a non-empty "screens" array'))
    return ''
  }
  const base = path.dirname(jsonFile)
  const seen = new Set()
  for (const [index, screen] of data.screens.entries()) {
    const at = `${rel(jsonFile)} screens[${index}]`
    const textFields = ['name', 'title', 'html', 'slice', 'data']
    const bad = textFields.filter((k) => typeof screen?.[k] !== 'string' || screen[k] === '')
    if (!Array.isArray(screen?.fr) || screen.fr.some((x) => typeof x !== 'string')) bad.push('fr')
    if (bad.length > 0) {
      violations.push(violation('inventory', at, `missing or invalid field(s): ${bad.join(', ')}`))
      continue
    }
    if (seen.has(screen.name)) violations.push(violation('inventory', at, `duplicate name "${screen.name}"`))
    seen.add(screen.name)
    const html = path.join(base, screen.html)
    if (!fs.existsSync(html)) violations.push(violation('inventory', rel(html), `html of screen "${screen.name}" is missing`))
    for (const theme of THEMES) {
      for (const width of SHOT_WIDTHS) {
        const name = `${screen.name}-${theme}-${width}.png`
        const shot = path.join(base, 'screens', name)
        if (!fs.existsSync(shot)) violations.push(violation('inventory', rel(shot), 'missing shot'))
        if (galleryText !== null && !galleryText.includes(`screens/${name}`)) {
          violations.push(violation('gallery', `screens/${name}`, 'the gallery does not refer to this shot'))
        }
      }
    }
  }
  return `${data.screens.length} screen(s)`
}

function inventoryAssets(dir, violations) {
  for (const [sub, ext] of [['fonts', '.woff2'], ['icons', '.svg']]) {
    const names = listFiles(path.join(dir, sub))
    if (!names.some((n) => n.endsWith(ext))) violations.push(violation('assets', rel(path.join(dir, sub)), `no *${ext} file`))
    if (!names.some((n) => LICENCE_FILE.test(n))) {
      violations.push(violation('assets', rel(path.join(dir, sub)), 'no licence file (a name matching licen[cs]e or OFL)'))
    }
  }
  const noLiterals = (file, text) => {
    const stripped = text.replace(/url\([^)]*\)/g, '').replace(/href="#[^"]*"/g, '')
    const match = COLOUR_LITERAL.exec(stripped)
    if (match) violations.push(violation('literal', rel(file), `colour literal "${match[0]}"; use the roles or currentColor`))
  }
  const css = path.join(dir, 'prototypes', 'prototype.css')
  if (!fs.existsSync(css)) violations.push(violation('assets', rel(css), 'missing file'))
  else noLiterals(css, fs.readFileSync(css, 'utf8').replace(/\/\*[\s\S]*?\*\//g, ''))
  for (const name of listFiles(path.join(dir, 'icons')).filter((n) => n.endsWith('.svg'))) {
    const file = path.join(dir, 'icons', name)
    noLiterals(file, fs.readFileSync(file, 'utf8'))
  }
  return 'fonts and icons with licences, no colour literals'
}

function runInventory(args) {
  const { positional, options } = parseOptions(args, ['--gallery'], ['--assets'])
  if (positional.length !== 1) throw new UsageError('inventory takes exactly one target')
  const [target] = positional
  if (!fs.existsSync(target)) throw new UsageError(`no such file or directory: ${target}`)
  let galleryText = null
  if (options.gallery !== undefined) {
    if (!fs.existsSync(options.gallery)) throw new UsageError(`no such gallery file: ${options.gallery}`)
    galleryText = fs.readFileSync(options.gallery, 'utf8')
  }
  const violations = []
  let summary
  if (options.assets === true) {
    if (!fs.statSync(target).isDirectory()) throw new UsageError('--assets takes the ui-design directory')
    summary = inventoryAssets(target, violations)
  } else if (path.basename(target) === 'screens.json') {
    summary = inventoryScreens(target, galleryText, violations)
  } else if (fs.statSync(target).isDirectory()) {
    summary = inventoryVariants(target, galleryText, violations)
  } else {
    throw new UsageError('inventory takes a variants directory, a screens.json or (with --assets) the ui-design directory')
  }
  return report(violations, summary)
}

// ---------------------------------------------------------------------------------------------------------------
// doc

/** Level-2 sections of a markdown text, ignoring fenced code: [{title, body, line}]. */
function sections(text) {
  const result = []
  let inFence = false
  let current = null
  for (const [index, line] of text.split('\n').entries()) {
    if (/^\s*(```|~~~)/.test(line)) inFence = !inFence
    const heading = !inFence && /^## (.*?)\s*#*\s*$/.exec(line)
    if (heading) {
      current = { title: heading[1].trim(), body: '', line: index + 1 }
      result.push(current)
    } else if (current) {
      current.body += `${line}\n`
    }
  }
  return result
}

/** Relative markdown links `[x](target)` of a text, outside fenced code: [{target, anchor, path}] (anchor and query cut). */
function relativeLinks(text) {
  const links = []
  let inFence = false
  const withoutFences = text.split('\n').filter((line) => {
    if (/^\s*(```|~~~)/.test(line)) {
      inFence = !inFence
      return false
    }
    return !inFence
  }).join('\n')
  for (const match of withoutFences.matchAll(/\[[^\]]*\]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)/g)) {
    const target = match[1]
    if (/^[a-z][a-z0-9+.-]*:/i.test(target) || target.startsWith('//') || target.startsWith('#')) continue
    const [withoutAnchor, anchor = ''] = target.split('#')
    const filePart = withoutAnchor.split('?')[0]
    if (filePart === '') continue
    links.push({ target, anchor, path: decodeURI(filePart) })
  }
  return links
}

/** Text in backticks (inline spans and fenced code). */
function codeText(text) {
  const parts = []
  let inFence = false
  for (const line of text.split('\n')) {
    if (/^\s*(```|~~~)/.test(line)) {
      inFence = !inFence
      continue
    }
    if (inFence) parts.push(line)
    else for (const m of line.matchAll(/`([^`]+)`/g)) parts.push(m[1])
  }
  return parts
}

function filled(section) {
  return section.body.trim() !== '' && !section.body.includes(PLACEHOLDER)
}

function checkDoc(docFile, stage) {
  const violations = []
  const label = rel(docFile)
  const uiDir = path.join(path.dirname(docFile), 'ui-design')
  const text = fs.readFileSync(docFile, 'utf8')
  const all = sections(text)
  const byTitle = new Map(all.map((s) => [s.title, s]))

  // The 13 headings, in order (other level-2 headings may sit between them).
  let last = -1
  for (const title of DOC_SECTIONS) {
    const found = all.findIndex((s) => s.title === title)
    if (found < 0) violations.push(violation('doc-heading', label, `missing section "## ${title}"`))
    else if (found < last) violations.push(violation('doc-heading', label, `section "## ${title}" is out of order`))
    else last = found
  }

  // Links: every relative link exists, none leads to context/changes/ (it stops working after /10x-archive).
  for (const link of relativeLinks(text)) {
    const resolved = path.resolve(path.dirname(docFile), link.path)
    if (!fs.existsSync(resolved)) violations.push(violation('doc-link', label, `link target does not exist: ${link.target}`))
    if (/(^|[\\/])context[\\/]changes([\\/]|$)/.test(resolved) || /context\/changes\//.test(link.path)) {
      violations.push(violation('doc-changes', label, `link into context/changes/: ${link.target}`))
    }
  }
  for (const code of codeText(text)) {
    if (/context\/changes\//.test(code)) violations.push(violation('doc-changes', label, `path into context/changes/ in backticks: ${code}`))
  }

  const mustFill = (titles) => {
    for (const title of titles) {
      const section = byTitle.get(title)
      if (section && !filled(section)) violations.push(violation('doc-empty', label, `section "${title}" is empty or still a placeholder`))
    }
  }

  if (stage >= 1) {
    mustFill(DOC_SECTIONS.slice(0, 5))
    const screens = byTitle.get('Ekrany')
    if (screens) {
      for (const fr of FR_LIST) {
        if (!new RegExp(`${fr}(?![A-Za-z0-9])`).test(screens.body)) {
          violations.push(violation('doc-fr', label, `section "Ekrany" does not mention ${fr}`))
        }
      }
    }
  }

  if (stage >= 2) {
    mustFill(['Język wizualny', 'Komponenty', 'Treść i etykiety'])
    const components = byTitle.get('Komponenty')
    if (components) {
      const sheet = path.join(uiDir, 'prototypes', 'components.html')
      const sheetHtml = fs.existsSync(sheet) ? fs.readFileSync(sheet, 'utf8') : ''
      const anchored = relativeLinks(components.body).filter((l) =>
        path.resolve(path.dirname(docFile), l.path) === sheet && /^c-/.test(l.anchor))
      if (anchored.length === 0) violations.push(violation('doc-component', label, 'section "Komponenty" has no link to ui-design/prototypes/components.html#c-<slug>'))
      for (const link of anchored) {
        if (!new RegExp(`\\bid=["']${link.anchor.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}["']`).test(sheetHtml)) {
          violations.push(violation('doc-component', label, `components.html has no element with id="${link.anchor}"`))
        }
      }
    }
    for (const theme of THEMES) {
      for (const width of SHOT_WIDTHS) {
        const shot = path.join(uiDir, 'screens', `components-${theme}-${width}.png`)
        if (!fs.existsSync(shot)) violations.push(violation('doc-shot', rel(shot), 'missing component sheet shot'))
      }
    }
  }

  let screensJson = null
  if (stage >= 3) {
    const jsonFile = path.join(uiDir, 'screens.json')
    try {
      screensJson = JSON.parse(fs.readFileSync(jsonFile, 'utf8'))
      if (!Array.isArray(screensJson.screens)) throw new Error('no "screens" array')
    } catch (error) {
      screensJson = null
      violations.push(violation('doc-screens', rel(jsonFile), `cannot read screens.json: ${error.message}`))
    }
    const section = byTitle.get('Ekrany')
    if (screensJson && section) {
      const listed = new Set(screensJson.screens.map((s) => path.resolve(uiDir, s.html)))
      const linked = new Set(relativeLinks(section.body).map((l) => path.resolve(path.dirname(docFile), l.path)))
      for (const screen of screensJson.screens) {
        if (!linked.has(path.resolve(uiDir, screen.html))) {
          violations.push(violation('doc-screens', label, `section "Ekrany" does not link the prototype of "${screen.name}" (${screen.html})`))
        }
      }
      const screensDir = path.join(uiDir, 'prototypes', 'screens') + path.sep
      for (const target of linked) {
        if (target.startsWith(screensDir) && target.endsWith('.html') && !listed.has(target)) {
          violations.push(violation('doc-screens', label, `section "Ekrany" links ${rel(target)}, which is not in screens.json`))
        }
      }
      const covered = new Set(screensJson.screens.flatMap((s) => s.fr ?? []))
      for (const fr of FR_LIST) {
        if (!covered.has(fr)) violations.push(violation('doc-fr', rel(path.join(uiDir, 'screens.json')), `no screen in screens.json covers ${fr}`))
      }
    }
  }

  if (stage >= 4) {
    mustFill(DOC_SECTIONS)
    const e2e = byTitle.get('Kontrakt dla testów E2E')
    if (e2e && screensJson) {
      for (const screen of screensJson.screens) {
        if (!e2e.body.includes(`\`${screen.name}\``)) {
          violations.push(violation('doc-e2e', label, `section "Kontrakt dla testów E2E" does not name the screen \`${screen.name}\` in backticks`))
        }
      }
    }
  }
  return violations
}

function runDoc(args) {
  const { options, positional } = parseOptions(args, ['--stage', '--doc'], [])
  if (positional.length > 0) throw new UsageError(`unexpected argument ${positional[0]}`)
  const stage = Number(options.stage)
  if (![1, 2, 3, 4].includes(stage)) throw new UsageError('doc needs --stage 1, 2, 3 or 4')
  const docFile = options.doc ?? DEFAULT_DOC
  if (!fs.existsSync(docFile)) throw new UsageError(`no such file: ${docFile}`)
  return report(checkDoc(docFile, stage), `${rel(docFile)} passes stage ${stage}`)
}

// ---------------------------------------------------------------------------------------------------------------
// selftest

async function runSelftest(args) {
  if (args.length > 0) throw new UsageError('selftest takes no arguments')
  let failed = 0
  const line = (name, expected, ok, found) => {
    console.log(`${ok ? 'PASS' : 'FAIL'} ${name}: ${expected} ${ok ? '' : `(got: ${found})`}`.trimEnd())
    if (!ok) failed++
  }
  const kinds = (violations) => [...new Set(violations.map((v) => v.kind))].join(', ') || 'no violation'

  // The green path.
  const okDir = path.join(FIXTURES, 'ok')
  const okContrast = checkContrast(path.join(okDir, 'tokens.css'), path.join(okDir, 'pairs.json'))
  line('ok/contrast', 'no violation', okContrast.violations.length === 0, kinds(okContrast.violations))
  // No woff2 is available in the repository, so the font check is proved by broken/system-font.html alone.
  const okPages = await checkPages(collectHtml([okDir]), { skipFonts: true })
  line('ok/pages (fonts skipped)', 'no violation', okPages.length === 0, kinds(okPages))

  // The breaks: each must give a violation of the expected kind.
  const broken = path.join(FIXTURES, 'broken')
  for (const [name, expected] of [['low-contrast', 'contrast'], ['non-hex', 'non-hex'], ['prefers-color-scheme', 'prefers-color-scheme']]) {
    const dir = path.join(broken, name)
    const { violations } = checkContrast(path.join(dir, 'tokens.css'), path.join(dir, 'pairs.json'))
    line(`broken/${name}`, `violation ${expected}`, violations.some((v) => v.kind === expected), kinds(violations))
  }
  const samples = [
    ['overflow-375.html', 'overflow', { skipFonts: true }],
    ['button-no-name.html', 'axe:button-name', { skipFonts: true }],
    ['system-font.html', 'font', {}],
  ]
  for (const [name, expected, options] of samples) {
    const violations = await checkPages(collectHtml([path.join(broken, name)]), options)
    line(`broken/${name}`, `violation ${expected}`, violations.some((v) => v.kind === expected), kinds(violations))
  }
  console.log(failed === 0 ? 'OK: the green path passes and every break is detected' : `FAILED: ${failed} check(s) of the selftest`)
  return failed === 0 ? 0 : 1
}

// ---------------------------------------------------------------------------------------------------------------

async function main(argv) {
  const [command, ...args] = argv
  try {
    switch (command) {
      case 'contrast': return runContrast(args)
      case 'pages': return await runPages(args)
      case 'shots': return await runShots(args)
      case 'inventory': return runInventory(args)
      case 'doc': return runDoc(args)
      case 'selftest': return await runSelftest(args)
      default: throw new UsageError(command === undefined ? 'no command given' : `unknown command "${command}"`)
    }
  } catch (error) {
    if (error instanceof UsageError) {
      console.error(`error: ${error.message}\n\n${USAGE}`)
      return 2
    }
    if (error instanceof EnvironmentError) {
      console.error(`error: ${error.message}`)
      return 3
    }
    throw error
  }
}

process.exitCode = await main(process.argv.slice(2))
