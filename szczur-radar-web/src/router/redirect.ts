/**
 * The post-login target from `?redirect=`: only a same-site path. Anything that
 * could leave the site (`//evil.com`, `/\evil.com`, `https://…`) becomes `/`.
 */
export function safeRedirect(value: unknown): string {
  if (
    typeof value === 'string' &&
    value.startsWith('/') &&
    !value.startsWith('//') &&
    !value.startsWith('/\\')
  ) {
    return value
  }
  return '/'
}
