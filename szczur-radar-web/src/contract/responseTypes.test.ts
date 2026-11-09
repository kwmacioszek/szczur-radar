import { describe, expect, it } from 'vitest'

// The API writes every key of a response, a null one as `"x": null` (Jackson's default inclusion, no @JsonInclude), so
// no field of a response type is optional. An optional field is a hole in the contract: a file and its sample could
// both lose the field and every test, vue-tsc included, would stay green (audit X3). This scan reads the sources of
// src/api and refuses a declaration `name?:` there, apart from the few below, which are no response body.
const sources = Object.fromEntries(
  Object.entries(
    import.meta.glob<string>(['../api/*.ts', '!../api/*.test.ts'], { query: '?raw', import: 'default', eager: true }),
  ).map(([path, text]) => [path.replace(/^\.\.\/api\//, ''), text]),
)

/** The optional fields allowed in src/api, per file, and why they are not part of a response. */
const ALLOWED: Record<string, string[]> = {
  // PostFilter: the feed filter the SPA sends as query parameters; an absent key is no condition.
  'posts.ts': ['q', 'kind', 'direction'],
  // ApiFetchOptions.handleUnauthorized: an option of the SPA's own client. ApiError.retryAfter: read from the
  // Retry-After header of an error answer, absent when the API sends none; never a field of a JSON body.
  'client.ts': ['handleUnauthorized', 'retryAfter'],
}

/**
 * Every optional field, member or parameter (`name?:`, also `readonly name?:` and `name ?:`), as `name`: at the start
 * of a line, and after `{`, `;`, `,` or `(` for a type or parameter list written on one line.
 */
function optionalFields(text: string): string[] {
  return [...text.matchAll(/(?:^|[{;,(])\s*(?:readonly\s+)?([A-Za-z_$][\w$]*)\s*\?:/gm)].map((match) => match[1]!)
}

const found = Object.entries(sources).flatMap(([file, text]) => optionalFields(text).map((name) => ({ file, name })))

describe('the response types of src/api', () => {
  it('reads the sources of src/api', () => {
    expect(Object.keys(sources)).toEqual(expect.arrayContaining(['client.ts', 'pipeline.ts', 'posts.ts']))
  })

  it('have no optional field but the allowed ones', () => {
    const notAllowed = found.filter(({ file, name }) => !(ALLOWED[file] ?? []).includes(name))
    expect(notAllowed).toEqual([])
  })

  it('finds a field of a type written on one line', () => {
    expect(optionalFields('type T = { a: string; b?: number }\ntype U = { c ?: string }')).toEqual(['b', 'c'])
  })

  it('have no Partial<…> type, which makes every field optional', () => {
    const partial = Object.entries(sources)
      .filter(([, text]) => /\bPartial</.test(text))
      .map(([file]) => file)
    expect(partial).toEqual([])
  })

  it('finds every allowed one, so a changed format does not pass in silence', () => {
    const missing = Object.entries(ALLOWED).flatMap(([file, names]) =>
      names.filter((name) => !found.some((hit) => hit.file === file && hit.name === name)).map((name) => ({ file, name })),
    )
    expect(missing).toEqual([])
  })
})
