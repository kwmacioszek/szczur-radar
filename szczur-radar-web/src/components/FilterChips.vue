<script setup lang="ts">
// The applied filter over the list, one marker per key (ui-design.md, "Filtry"): each marker is a button that takes
// its key off ("Usuń filtr: Cel: orlen"), and "Wyczyść filtry" at the end of the row takes off all of them. Nothing
// without a filter. The view changes the address; `focus` lets it put the focus on the marker that took the place of
// the removed one.
import { computed } from 'vue'
import type { PostFilter } from '../api/posts'
import { type FilterKey, activeFilterKeys } from '../feedFilter'
import { label } from '../labels'
import AppIcon from './AppIcon.vue'

const props = defineProps<{ filter: PostFilter }>()

const emit = defineEmits<{
  remove: [key: FilterKey]
  clear: []
}>()

/** The marker's text before its value. */
const NAMES: Record<FilterKey, string> = { q: 'Cel', kind: 'Rodzaj', direction: 'Stanowisko' }

function value(filter: PostFilter, key: FilterKey): string {
  switch (key) {
    case 'q':
      return filter.q ?? ''
    case 'kind':
      return label('targetKind', filter.kind ?? '')
    case 'direction':
      return label('direction', filter.direction ?? '')
  }
}

const chips = computed(() =>
  activeFilterKeys(props.filter).map((key) => ({ key, text: `${NAMES[key]}: ${value(props.filter, key)}` })),
)

const buttons = new Map<FilterKey, HTMLButtonElement>()

function setButton(key: FilterKey, element: unknown): void {
  if (element) buttons.set(key, element as HTMLButtonElement)
  else buttons.delete(key)
}

/** Puts the focus on the marker of `key`; false when there is none. */
function focus(key: FilterKey): boolean {
  const button = buttons.get(key)
  button?.focus()
  return button !== undefined
}

defineExpose({ focus })
</script>

<template>
  <ul v-if="chips.length > 0" class="filter-chips" aria-label="Włączone filtry">
    <li v-for="chip in chips" :key="chip.key">
      <button
        :ref="(element) => setButton(chip.key, element)"
        type="button"
        class="chip"
        :aria-label="`Usuń filtr: ${chip.text}`"
        @click="emit('remove', chip.key)"
      >
        {{ chip.text }}
        <AppIcon name="i-x" />
      </button>
    </li>
    <li>
      <button type="button" class="clear" @click="emit('clear')">Wyczyść filtry</button>
    </li>
  </ul>
</template>

<style scoped>
.filter-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  padding: 0;
  list-style: none;
}

/* A marker: a pill with its text and the mark that takes it off. */
.chip {
  gap: var(--space-1);
  min-height: 2rem;
  padding: 0 var(--space-2) 0 var(--space-3);
  border-radius: var(--radius-full);
  background: var(--surface);
  font-size: var(--font-size-sm);
  font-weight: var(--weight-regular);
}

.chip:hover {
  background: var(--surface-2);
}

.chip .app-icon {
  --icon-size: 1rem;

  color: var(--text-muted);
}

/* "Wyczyść filtry" reads as a link: the accent, underlined, no frame. */
.clear {
  min-height: 2rem;
  padding: 0 var(--space-2);
  border: 0;
  color: var(--accent);
  font-size: var(--font-size-sm);
  text-decoration: underline;
  text-underline-offset: 0.2em;
}

.clear:hover {
  background: none;
  text-decoration-thickness: 2px;
}
</style>
