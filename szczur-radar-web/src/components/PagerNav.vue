<script setup lang="ts">
// The pager under a list of pages: the newer (or previous) page, "Strona N z M" and the older (or next) one
// (ui-design.md, "Sterowanie" → "Stronicowanie"). `labels` picks the pair of words: "Nowsze"/"Starsze" for a list in
// time, "Poprzednie"/"Następne" for one in an order. With one page or none it draws nothing. The button of the page
// being loaded is busy ("Wczytywanie…"); the other stays as it is. The container of the list, not this component, is
// `aria-busy`. No outer margin: the place that uses it spaces it.
import { computed } from 'vue'
import AppButton from './AppButton.vue'

const props = withDefaults(
  defineProps<{
    /** The current page, from 0. */
    page: number
    pages: number
    labels?: 'time' | 'order'
    /** The direction whose page is loading. */
    busy?: 'previous' | 'next' | null
  }>(),
  { labels: 'time', busy: null },
)
const emit = defineEmits<{ previous: []; next: [] }>()

const WORDS = {
  time: { previous: 'Nowsze', next: 'Starsze' },
  order: { previous: 'Poprzednie', next: 'Następne' },
} as const

const words = computed(() => WORDS[props.labels])
</script>

<template>
  <nav v-if="pages > 1" class="pager" aria-label="Strony">
    <AppButton
      icon="i-chevron-left"
      :disabled="page === 0"
      :busy="busy === 'previous'"
      busy-label="Wczytywanie…"
      @click="emit('previous')"
    >
      {{ words.previous }}
    </AppButton>
    <span class="position">Strona {{ page + 1 }} z {{ pages }}</span>
    <AppButton
      icon="i-chevron-right"
      icon-position="end"
      :disabled="page + 1 >= pages"
      :busy="busy === 'next'"
      busy-label="Wczytywanie…"
      @click="emit('next')"
    >
      {{ words.next }}
    </AppButton>
  </nav>
</template>

<style scoped>
/* One row where it fits; on a narrow screen with a busy button ("Wczytywanie…" is the longest) the last one wraps, so
   the page never scrolls sideways. */
.pager {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.position {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
</style>
