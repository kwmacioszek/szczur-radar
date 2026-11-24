<script setup lang="ts">
// A table of data in its frame (ui-design.md, "Tabela"): runs, failed posts, later the targets of a day. The caller
// gives its <thead> and <tbody> in the default slot, since every table has its own cells; this component gives the
// frame, the caption (hidden: the section's heading says it on the screen) and the cells' look. Below 900 px a wide
// table scrolls sideways inside its frame, never the page (WCAG 1.4.10 allows it for a table); the frame is a region
// named by the caption and takes the focus, so the keyboard can scroll it too (WCAG 2.1.1).
// The caller marks its cells: `num` on a number's header and cells (to the right, tabular figures), `zero` on a 0
// (muted), `down` on a number that is bad news above 0 (the failed and dead posts of a run: the destructive tone),
// `is-selected` on a row chosen by its checkbox. The row's first header cell is `th scope="row"`.
import { useId } from 'vue'

defineProps<{
  /** The table's caption and the frame's name. */
  caption: string
}>()
defineSlots<{ default(): unknown }>()

const captionId = useId()
</script>

<template>
  <div class="data-table" role="region" tabindex="0" :aria-labelledby="captionId">
    <table>
      <caption :id="captionId" class="visually-hidden">{{ caption }}</caption>
      <slot />
    </table>
  </div>
</template>

<style scoped>
.data-table {
  overflow-x: auto;
  padding: var(--space-2) var(--space-5) var(--space-3);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  background: var(--surface);
}

table {
  width: 100%;
  border-collapse: collapse;
}

:slotted(th),
:slotted(td) {
  height: 3rem;
  padding: 0 var(--space-3) 0 0;
  border-bottom: 1px solid var(--line);
  text-align: left;
  white-space: nowrap;
}

:slotted(tbody > tr:last-child > *) {
  border-bottom: 0;
}

:slotted(thead th) {
  height: var(--control-height);
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  font-weight: var(--weight-regular);
}

:slotted(tbody > tr:hover > *) {
  background: var(--surface-2);
}

:slotted(tbody > tr.is-selected > *) {
  background: var(--selected);
}

:slotted(.num) {
  padding-right: var(--space-4);
  text-align: right;
  font-variant-numeric: tabular-nums;
}

:slotted(td.num) {
  color: var(--text-h);
}

:slotted(td.zero) {
  color: var(--text-muted);
}

:slotted(td.down) {
  color: var(--destructive);
  font-weight: var(--weight-semibold);
}

@media (max-width: 899px) {
  .data-table {
    padding: var(--space-1) var(--space-4) var(--space-2);
  }
}
</style>
