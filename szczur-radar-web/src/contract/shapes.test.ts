import { describe, expect, it } from 'vitest'
import { SAMPLES } from './samples'

// The files are what the API's integration tests compare the real, masked response bodies with; the samples are the
// TS types' reading of the same bodies. Equal, they say the types describe exactly what the server sends. The files
// are found, not listed: a new file without a sample (or a sample without its file) is red here. rules.json is the
// registry of copied rules (rules.test.ts), not a response body.
const FILES = Object.fromEntries(
  Object.entries(
    import.meta.glob<unknown>(['./*.json', '!./rules.json'], { eager: true, import: 'default' }),
  ).map(([path, body]) => [path.replace(/^\.\//, ''), body]),
)

describe('the shared full bodies', () => {
  it('every contract file has its typed sample, and every sample its file', () => {
    expect(Object.keys(SAMPLES).sort()).toEqual(Object.keys(FILES).sort())
  })

  it.each(Object.keys(FILES).sort())('%s equals its typed sample', (file) => {
    expect(SAMPLES[file]).toEqual(FILES[file])
  })
})
