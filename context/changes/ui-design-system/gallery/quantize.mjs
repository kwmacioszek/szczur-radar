// One-off helper of the ui-design-system change: shrinks the PNG shots in a directory.
// Needs sharp, which the repository does not depend on: in an empty scratch directory run
//   npm init -y && npm install sharp@0.33.5 && cp <this file> . && node quantize.mjs <repo>/context/foundation/ui-design/screens
// Re-encodes PNG screenshots with an adaptive palette (libimagequant via sharp): same pixels to the eye, a third
// of the size. Usage: node quantize.mjs <dir> [--min-bytes n]
import fs from 'node:fs'
import path from 'node:path'
import sharp from 'sharp'
const dir = process.argv[2]
let before = 0, after = 0
for (const name of fs.readdirSync(dir).filter((n) => n.endsWith('.png'))) {
  const file = path.join(dir, name)
  const input = fs.readFileSync(file)
  const output = await sharp(input).png({ palette: true, quality: 90, effort: 10, compressionLevel: 9 }).toBuffer()
  before += input.length
  if (output.length < input.length) { fs.writeFileSync(file, output); after += output.length } else after += input.length
}
console.log(`${dir}: ${(before / 1048576).toFixed(2)} MB -> ${(after / 1048576).toFixed(2)} MB`)
