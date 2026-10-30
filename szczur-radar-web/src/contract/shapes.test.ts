import { describe, expect, it } from 'vitest'
import failedPostJson from './failed-post.json'
import freshnessJson from './freshness.json'
import postJson from './post.json'
import runJson from './run.json'
import { sampleFailedPost, sampleFreshness, samplePost, sampleRun } from './samples'

// The files are what the API's integration tests compare the real, masked response bodies with; the samples are the
// TS types' reading of the same bodies. Equal, they say the types describe exactly what the server sends.
describe('the shared full bodies', () => {
  it('a run (GET /api/admin/pipeline-runs/{id}) is the Run type', () => {
    expect(sampleRun).toEqual(runJson)
  })

  it('a post (GET /api/posts/{id}) is the PostItem type', () => {
    expect(samplePost).toEqual(postJson)
  })

  it('a failed post (GET /api/admin/posts/failed) is the FailedPost type', () => {
    expect(sampleFailedPost).toEqual(failedPostJson)
  })

  it('the freshness (GET /api/freshness) is the Freshness type', () => {
    expect(sampleFreshness).toEqual(freshnessJson)
  })
})
