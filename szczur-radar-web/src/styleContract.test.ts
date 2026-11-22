import { describe, expect, it } from 'vitest'
import appBar from './components/AppBar.vue?raw'
import appBrand from './components/AppBrand.vue?raw'
import appButton from './components/AppButton.vue?raw'
import appIcon from './components/AppIcon.vue?raw'
import appShell from './components/AppShell.vue?raw'
import authLayout from './components/AuthLayout.vue?raw'
import feedFilter from './components/FeedFilter.vue?raw'
import formField from './components/FormField.vue?raw'
import iconSprite from './components/IconSprite.vue?raw'
import notificationBell from './components/NotificationBell.vue?raw'
import notificationMenu from './components/NotificationMenu.vue?raw'
import pageHeader from './components/PageHeader.vue?raw'
import postAssessment from './components/PostAssessment.vue?raw'
import postCard from './components/PostCard.vue?raw'
import stanceText from './components/StanceText.vue?raw'
import statusBadge from './components/StatusBadge.vue?raw'
import statusMessage from './components/StatusMessage.vue?raw'
import themeToggle from './components/ThemeToggle.vue?raw'
import pairs from './contrastPairs.json'
import css from './style.css?raw'
import activateView from './views/ActivateView.vue?raw'
import adminView from './views/AdminView.vue?raw'
import feedView from './views/FeedView.vue?raw'
import kitchenSinkView from './views/KitchenSinkView.vue?raw'
import loginView from './views/LoginView.vue?raw'
import postDetailView from './views/PostDetailView.vue?raw'
import runDetailView from './views/RunDetailView.vue?raw'

type Theme = 'light' | 'dark'

const THEMES: Theme[] = ['light', 'dark']

/**
 * Foreground role on background role with its own threshold: 4.5 for text, 3.0 for interface elements and
 * focus. The pairs are src/contrastPairs.json, a byte copy of context/foundation/ui-design/pairs.json.
 */
interface Pair {
  fg: string
  bg: string
  min: number
  kind: string
}

const PAIRS: Pair[] = pairs

const source = css.replace(/\/\*[\s\S]*?\*\//g, '')

// Skip quoted and escaped characters when looking for structural CSS delimiters.
const CSS_DELIMITERS = /"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|\\.|[{}\[\]();]/gs
const CUSTOM_PROPERTY = /^\s*(--[\w-]+)\s*:/

/** The text between the brace after the first `selector` in `text` and its matching closing brace. */
function block(text: string, selector: string): string {
  const at = text.indexOf(selector)
  const open = at < 0 ? -1 : text.indexOf('{', at)
  if (open < 0) return ''
  let depth = 0
  for (const match of text.slice(open).matchAll(CSS_DELIMITERS)) {
    if (match[0] === '{') depth++
    if (match[0] === '}' && --depth === 0) return text.slice(open + 1, open + match.index)
  }
  return ''
}

function declarations(text: string): Map<string, string> {
  // Keep in step with design-check.mjs. Read direct declarations, including one at the end without a semicolon.
  const tokens = new Map<string, string>()
  let start = 0
  let depth = 0
  let nestedRule = false
  for (const match of (text + ';').matchAll(CSS_DELIMITERS)) {
    const delimiter = match[0]
    if ('{[('.includes(delimiter)) {
      if (delimiter === '{' && depth === 0) nestedRule = !CUSTOM_PROPERTY.test(text.slice(start, match.index))
      depth++
    } else if ('}])'.includes(delimiter)) {
      depth--
      if (delimiter === '}' && depth === 0 && nestedRule) {
        start = match.index + 1
        nestedRule = false
      }
    } else if (delimiter === ';' && depth === 0) {
      const statement = text.slice(start, match.index)
      const property = CUSTOM_PROPERTY.exec(statement)
      if (property) tokens.set(property[1], statement.slice(property[0].length).trim())
      start = match.index + 1
    }
  }
  return tokens
}

// The first :root is the light one; the dark theme is the :root[data-theme='dark'] block. It gets no
// fallback to the light one: every colour role is tuned per theme, so the dark block must redefine each of them.
const ROLES: Record<Theme, Map<string, string>> = {
  light: declarations(block(source, ':root')),
  dark: declarations(block(source, ":root[data-theme='dark']")),
}

function role(theme: Theme, name: string): string {
  const value = ROLES[theme].get(name)
  if (value === undefined) throw new Error(`${name} is not defined in the ${theme} :root of style.css`)
  if (!/^#([0-9a-f]{3}|[0-9a-f]{6})$/i.test(value)) throw new Error(`${name} (${theme}) is not a hex colour: ${value}`)
  return value
}

/** Relative luminance (WCAG 2.x, sRGB threshold 0.04045). */
function luminance(hex: string): number {
  const digits = hex.length === 4 ? [...hex.slice(1)].map((digit) => digit + digit).join('') : hex.slice(1)
  const [r, g, b] = [0, 2, 4].map((at) => {
    const channel = Number.parseInt(digits.slice(at, at + 2), 16) / 255
    return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

function contrast(a: string, b: string): number {
  const [la, lb] = [luminance(a), luminance(b)]
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05)
}

/**
 * Files without colour literals and without values off the scale: colours come from the roles of
 * style.css, spacing, type and radii from its --space-*, --font-size-* and --radius tokens. Add a
 * view or component here once it is cleaned; style.css itself, the token source, is never on the
 * list.
 */
const CLEANED: Record<string, string> = {
  'src/views/AdminView.vue': adminView,
  'src/components/AppButton.vue': appButton,
  'src/components/AppIcon.vue': appIcon,
  'src/components/IconSprite.vue': iconSprite,
  'src/components/StatusMessage.vue': statusMessage,
  'src/components/StatusBadge.vue': statusBadge,
  'src/views/KitchenSinkView.vue': kitchenSinkView,
  'src/views/FeedView.vue': feedView,
  'src/components/PostCard.vue': postCard,
  'src/views/ActivateView.vue': activateView,
  'src/components/StanceText.vue': stanceText,
  'src/components/PostAssessment.vue': postAssessment,
  'src/views/PostDetailView.vue': postDetailView,
  'src/components/FeedFilter.vue': feedFilter,
  'src/components/NotificationBell.vue': notificationBell,
  'src/components/NotificationMenu.vue': notificationMenu,
  'src/views/RunDetailView.vue': runDetailView,
  'src/components/ThemeToggle.vue': themeToggle,
  'src/components/AppBar.vue': appBar,
  'src/components/AppShell.vue': appShell,
  'src/components/PageHeader.vue': pageHeader,
  'src/components/AppBrand.vue': appBrand,
  'src/components/FormField.vue': formField,
  'src/components/AuthLayout.vue': authLayout,
  'src/views/LoginView.vue': loginView,
}

/** The hardcoded-value scan of /10x-ui: colour literals, arbitrary values and palette classes. */
const LITERAL =
  /#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\(|oklch\(|-\[[0-9.]+(px|rem)\]|\b(bg|text|border|ring|outline|from|via|to|fill|stroke|shadow|divide)-(slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose|white|black)\b/

/**
 * A px or rem value in type, spacing or radius, which take the scale's tokens. Anchored to the
 * property name, so borders (`border: 1px …`), layout widths (`max-width`, `grid-template-columns`),
 * media queries and the token names themselves (`--space-4`) stay out of it.
 */
const OFF_SCALE =
  /(?:^|[\s{;"'])(?:font-size|margin(?:-[a-z]+)*|padding(?:-[a-z]+)*|(?:row-|column-)?gap|letter-spacing|border-radius)\s*:[^;{}]*\d(?:px|rem)\b/

const PATTERNS = [LITERAL, OFF_SCALE]

function literalHits(file: string, text: string): string[] {
  const lines = text.split('\n')
  const hits = new Set<number>()
  for (const pattern of PATTERNS) {
    for (const match of text.matchAll(new RegExp(pattern.source, 'g'))) {
      // OFF_SCALE includes a boundary; report the property, not a preceding brace or newline.
      const start = match.index + match[0].search(/[^\s{;"']/)
      hits.add(text.slice(0, start).split('\n').length - 1)
    }
  }
  return [...hits].sort((a, b) => a - b).map((at) => `${file}:${at + 1}: ${lines[at].trim()}`)
}

describe('style contract', () => {
  describe('token declarations', () => {
    it('reads the last declaration without a trailing semicolon', () => {
      const stylesheet = ':root { --text: #111111; --bg: #ffffff }'
      expect(declarations(block(stylesheet, ':root')).get('--bg')).toBe('#ffffff')
    })

    it('keeps the base token value when a nested rule redefines it', () => {
      const stylesheet = ':root { --text: #333333; @media (min-width: 100rem) { --text: #111111; } --bg: #ffffff; }'
      expect([...declarations(block(stylesheet, ':root'))]).toEqual([
        ['--text', '#333333'],
        ['--bg', '#ffffff'],
      ])
    })
  })

  describe('colour roles', () => {
    const cases = THEMES.flatMap((theme) => PAIRS.map((pair) => ({ theme, ...pair })))

    it.each(cases)('$fg on $bg ($theme, $kind) ≥ $min:1', ({ theme, fg, bg, min }) => {
      expect(contrast(role(theme, fg), role(theme, bg))).toBeGreaterThanOrEqual(min)
    })
  })

  describe('theme source', () => {
    it('has no prefers-color-scheme media query: data-theme is the only source of the theme', () => {
      expect(source).not.toContain('prefers-color-scheme')
    })
  })

  describe('no literals', () => {
    it.each(Object.entries(CLEANED))('%s takes its values from the tokens', (file, text) => {
      expect(literalHits(file, text)).toEqual([])
    })

    it('rejects multiline spacing and type values and reports the property line', () => {
      const text = '.card {\n  padding:\n    16px\n    1rem;\n  font-size:\n    13px;\n}'
      expect(literalHits('Example.vue', text)).toEqual([
        'Example.vue:2: padding:',
        'Example.vue:5: font-size:',
      ])
    })

    it('rejects a multiline radius when the property follows a brace on the previous line', () => {
      expect(literalHits('Example.vue', '{\n  border-radius:\n    1rem;\n}')).toEqual([
        'Example.vue:2: border-radius:',
      ])
    })

    it('still rejects colour literals and palette classes and reports each line once', () => {
      const text = '<div class="text-red-500">\n.card { font-size: 13px; color: #fff; }'
      expect(literalHits('Example.vue', text)).toEqual([
        'Example.vue:1: <div class="text-red-500">',
        'Example.vue:2: .card { font-size: 13px; color: #fff; }',
      ])
    })

    it('allows multiline token values, borders, layout widths and media queries', () => {
      const text = `@media (min-width: 40rem) {
  .card {
    padding:
      var(--space-4)
      var(--space-5);
    font-size: var(--font-size-md);
    border-radius: var(--radius);
    color: var(--text);
    border: 1px solid var(--line);
    max-width: 48rem;
    grid-template-columns: 9rem minmax(0, 1fr);
  }
}`
      expect(literalHits('Example.vue', text)).toEqual([])
    })
  })
})
