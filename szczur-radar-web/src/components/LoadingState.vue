<script setup lang="ts">
// The state of a view whose data has not come yet: a skeleton in the shape of the result (rows of a list, lines of a
// card, a form) instead of text (ui-design.md, "Stany widoku"). The bars are empty elements without a role; the one
// text, "Wczytywanie…", is for assistive technology. `aria-busy="true"` is set by the container of the view or the
// section, which holds more than the skeleton (a pager beside a list), never by this component. It has no outer margin:
// the place that uses it spaces it. The pulse runs only where motion is welcome; without it the bars stand still.
import { computed } from 'vue'

type Shape = 'rows' | 'lines' | 'form'

const props = withDefaults(
  defineProps<{
    shape: Shape
    /** The rows of `rows` (default 6) or the bars of `lines` (default 2); `form` has no count. */
    count?: number
    /** The text for assistive technology. */
    label?: string
  }>(),
  { count: undefined, label: 'Wczytywanie…' },
)

/** The widths of the three bars of a row, in the pattern of the design's list (it repeats after six rows). */
const ROW_WIDTHS: readonly (readonly [string, string, string])[] = [
  ['70%', '85%', '100%'],
  ['55%', '60%', '70%'],
  ['65%', '75%', '50%'],
  ['60%', '90%', '80%'],
  ['70%', '55%', '60%'],
  ['50%', '70%', '100%'],
]
const LINE_WIDTHS = ['70%', '50%'] as const

const rows = computed(() =>
  Array.from({ length: props.count ?? 6 }, (_, at) => ROW_WIDTHS[at % ROW_WIDTHS.length]),
)
const lines = computed(() => Array.from({ length: props.count ?? 2 }, (_, at) => LINE_WIDTHS[at % LINE_WIDTHS.length]))
</script>

<template>
  <div class="loading-state" :class="shape">
    <span class="visually-hidden">{{ label }}</span>
    <template v-if="shape === 'rows'">
      <div v-for="(widths, at) in rows" :key="at" class="skeleton-row">
        <span v-for="(width, bar) in widths" :key="bar" class="skeleton" :style="{ width }"></span>
      </div>
    </template>
    <div v-else-if="shape === 'lines'" class="skeleton-lines">
      <span v-for="(width, at) in lines" :key="at" class="skeleton" :style="{ width }"></span>
    </div>
    <div v-else class="skeleton-form">
      <div class="form-row">
        <span class="skeleton control"></span>
        <span class="skeleton control"></span>
      </div>
      <span class="skeleton" style="width: 40%"></span>
    </div>
  </div>
</template>

<style scoped>
.loading-state {
  position: relative;
}

.skeleton {
  display: block;
  height: 0.875rem;
  border-radius: var(--radius-sm);
  background: var(--line);
}

/* The list's frame, as the posts' one: a line, rounded, on the surface. */
.rows {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  background: var(--surface);
}

.skeleton-row {
  display: grid;
  grid-template-columns: 13.5rem minmax(0, 1fr) 14rem;
  align-items: center;
  gap: var(--space-5);
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--line);
}

.skeleton-row:last-child {
  border-bottom: 0;
}

.skeleton-lines,
.skeleton-form {
  display: grid;
  gap: var(--space-4);
}

.form-row {
  display: flex;
  gap: var(--space-3) var(--space-4);
}

/* Two fields, each as tall as a field. */
.control {
  flex: 1;
  height: var(--control-height);
}

@media (max-width: 899px) {
  .skeleton-row {
    grid-template-columns: minmax(0, 1fr);
    gap: var(--space-3);
  }
}

@media (prefers-reduced-motion: no-preference) {
  .skeleton {
    animation: skeleton-pulse 1.6s var(--ease-out) infinite alternate;
  }
}

/* Only the opacity moves, so the layout never does. */
@keyframes skeleton-pulse {
  from {
    opacity: 1;
  }

  to {
    opacity: 0.45;
  }
}
</style>
