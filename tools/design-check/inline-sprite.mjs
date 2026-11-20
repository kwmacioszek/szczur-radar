#!/usr/bin/env node
// Puts the icon sprite of context/foundation/ui-design/icons/sprite.svg into prototype pages, between the markers
// <!--sprite--> … <!--/sprite--> (a bare <!--sprite--> on the first run). A page opened from a file cannot load
// an external <use href="sprite.svg#id">, so every prototype carries its own copy; rerun after the sprite changes.
// Usage: node tools/design-check/inline-sprite.mjs <page.html | directory>...
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
const spriteFile = path.join(root, 'context', 'foundation', 'ui-design', 'icons', 'sprite.svg')
const symbols = fs.readFileSync(spriteFile, 'utf8').replace(/^\s*<svg[^>]*>\s*/, '').replace(/\s*<\/svg>\s*$/, '')
const block = `<!--sprite-->\n<svg class="sprite" aria-hidden="true" focusable="false">\n${symbols}\n</svg>\n<!--/sprite-->`

const pages = process.argv.slice(2).flatMap((target) =>
  fs.statSync(target).isDirectory()
    ? fs.readdirSync(target).filter((n) => n.endsWith('.html')).map((n) => path.join(target, n))
    : [target])
if (pages.length === 0) {
  console.error('usage: inline-sprite.mjs <page.html | directory>...')
  process.exit(2)
}
let failed = false
for (const page of pages) {
  const html = fs.readFileSync(page, 'utf8')
  const opening = html.split('<!--sprite-->').length - 1
  const closing = html.split('<!--/sprite-->').length - 1
  // A bare opening marker is a first run; an opening marker without its closing one on a page that already has a
  // sprite would put a second sprite with the same ids in front of the old one, so it is an error.
  if (opening > 1 || closing > 1 || (closing === 0 && opening === 1 && html.includes('class="sprite"'))) {
    console.error(`${page}: the sprite markers are broken (${opening} <!--sprite-->, ${closing} <!--/sprite-->); fix the page by hand`)
    failed = true
    continue
  }
  // A function, so that a "$&" or "$'" in the sprite is never read as a replacement pattern.
  const next = html.replace(/<!--sprite-->(?:[\s\S]*?<!--\/sprite-->)?/, () => block)
  if (!next.includes('<!--/sprite-->')) {
    console.error(`${page}: no <!--sprite--> marker`)
    failed = true
    continue
  }
  if (next !== html) fs.writeFileSync(page, next)
  console.log(`${page}: sprite ${next === html ? 'unchanged' : 'written'}`)
}
process.exit(failed ? 1 : 0)
