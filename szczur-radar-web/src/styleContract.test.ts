import { describe, expect, it } from 'vitest'
import appButton from './components/AppButton.vue?raw'
import appHeader from './components/AppHeader.vue?raw'
import postCard from './components/PostCard.vue?raw'
import statusBadge from './components/StatusBadge.vue?raw'
import statusMessage from './components/StatusMessage.vue?raw'
import css from './style.css?raw'
import adminView from './views/AdminView.vue?raw'
import feedView from './views/FeedView.vue?raw'
import kitchenSinkView from './views/KitchenSinkView.vue?raw'
import loginView from './views/LoginView.vue?raw'

type Theme = 'light' | 'dark'

const THEMES: Theme[] = ['light', 'dark']

/** WCAG 2.x AA for normal text. */
const MIN_CONTRAST = 4.5

/** Foreground role on background role, as in the Phase 2 table of the change plan. */
const PAIRS = [
  ['--text', '--bg'],
  ['--text-h', '--bg'],
  ['--accent', '--bg'],
  ['--success', '--bg'],
  ['--warning', '--bg'],
  ['--destructive', '--bg'],
  ['--on-accent', '--accent'],
] as const

const source = css.replace(/\/\*[\s\S]*?\*\//g, '')

/** The text between the brace after the first `selector` in `text` and its matching closing brace. */
function block(text: string, selector: string): string {
  const at = text.indexOf(selector)
  const open = at < 0 ? -1 : text.indexOf('{', at)
  if (open < 0) return ''
  let depth = 0
  for (let i = open; i < text.length; i++) {
    if (text[i] === '{') depth++
    if (text[i] === '}' && --depth === 0) return text.slice(open + 1, i)
  }
  return ''
}

function declarations(text: string): Map<string, string> {
  return new Map(
    [...text.matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)].map((match): [string, string] => [match[1], match[2].trim()]),
  )
}

// The first :root is the light one. The dark theme gets no fallback to it: every colour role is
// tuned per theme, so the dark block must redefine each of them.
const ROLES: Record<Theme, Map<string, string>> = {
  light: declarations(block(source, ':root')),
  dark: declarations(block(block(source, '@media (prefers-color-scheme: dark)'), ':root')),
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
 * Files whose colours all come from the roles of style.css. Add a view or component here once it
 * is cleaned; style.css itself, the token source, is never on the list.
 */
const CLEANED: Record<string, string> = {
  'src/views/AdminView.vue': adminView,
  'src/components/AppHeader.vue': appHeader,
  'src/components/AppButton.vue': appButton,
  'src/components/StatusMessage.vue': statusMessage,
  'src/components/StatusBadge.vue': statusBadge,
  'src/views/KitchenSinkView.vue': kitchenSinkView,
  'src/views/FeedView.vue': feedView,
  'src/views/LoginView.vue': loginView,
  'src/components/PostCard.vue': postCard,
}

/** The hardcoded-value scan of /10x-ui: colour literals, arbitrary values and palette classes. */
const LITERAL =
  /#[0-9a-fA-F]{3,8}\b|rgba?\(|hsla?\(|oklch\(|-\[[0-9.]+(px|rem)\]|\b(bg|text|border|ring|outline|from|via|to|fill|stroke|shadow|divide)-(slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose|white|black)\b/

describe('style contract', () => {
  describe('colour roles', () => {
    const cases = THEMES.flatMap((theme) => PAIRS.map(([fg, bg]) => ({ theme, fg, bg })))

    it.each(cases)(`$fg on $bg ($theme) ≥ ${MIN_CONTRAST}:1`, ({ theme, fg, bg }) => {
      expect(contrast(role(theme, fg), role(theme, bg))).toBeGreaterThanOrEqual(MIN_CONTRAST)
    })
  })

  describe('no literals', () => {
    it.each(Object.entries(CLEANED))('%s takes its values from the tokens', (file, text) => {
      const hits = text.split('\n').flatMap((line, at) => (LITERAL.test(line) ? [`${file}:${at + 1}: ${line.trim()}`] : []))
      expect(hits).toEqual([])
    })
  })
})
