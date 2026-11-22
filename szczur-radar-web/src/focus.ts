import { nextTick } from 'vue'

/**
 * Gives the focus back to the element that had it when a form was sent. A clicked submit button is disabled while it
 * is busy, and the browser then drops the focus to <body>: a keyboard user would land at the top of the page, before
 * the form's error. Call it once the button is enabled again. A focus the page has moved on purpose (a field with an
 * error) stays where it is, and so does a target the page no longer shows (the form was left).
 */
export async function restoreFocus(target: Element | null): Promise<void> {
  await nextTick()
  const lost = document.activeElement === null || document.activeElement === document.body
  if (lost && target instanceof HTMLElement && target.isConnected) target.focus()
}
