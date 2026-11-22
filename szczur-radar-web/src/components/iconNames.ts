/**
 * The ids of the symbols of src/assets/icons/sprite.svg, the one source of icons (ui-design.md, "Ikony"): `i-*` are
 * stroked icons on a 24 grid, `s-*` the filled stance marks on a 12 grid. AppIcon.test.ts requires this tuple to equal
 * the sprite's `<symbol id>` list, so a symbol added to or removed from the sprite fails there until this follows.
 */
export const ICON_NAMES = [
  'i-arrow-down-up',
  'i-bell',
  'i-braces',
  'i-chevron-down',
  'i-chevron-left',
  'i-chevron-right',
  'i-circle-alert',
  'i-circle-check',
  'i-external-link',
  'i-inbox',
  'i-info',
  'i-list-filter',
  'i-moon',
  'i-play',
  'i-refresh-cw',
  'i-rotate-ccw',
  'i-search',
  'i-triangle-alert',
  'i-x',
  'i-brand',
  's-up',
  's-down',
  's-flat',
  's-mixed',
  's-none',
] as const

export type IconName = (typeof ICON_NAMES)[number]
