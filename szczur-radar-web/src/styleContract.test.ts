import { describe, expect, it } from 'vitest'
import css from './style.css?raw'

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

describe('style contract', () => {
  describe('colour roles', () => {
    const cases = THEMES.flatMap((theme) => PAIRS.map(([fg, bg]) => ({ theme, fg, bg })))

    it.each(cases)(`$fg on $bg ($theme) ≥ ${MIN_CONTRAST}:1`, ({ theme, fg, bg }) => {
      expect(contrast(role(theme, fg), role(theme, bg))).toBeGreaterThanOrEqual(MIN_CONTRAST)
    })
  })
})
