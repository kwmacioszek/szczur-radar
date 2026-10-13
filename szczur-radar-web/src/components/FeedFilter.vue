<script setup lang="ts">
// The feed filter (FR-011a): text in a target's quote, the target's kind and the author's stance on
// that same target. `filter` is the applied one (from the address); the fields are a draft until
// "Filtruj" or Enter. The fields follow `filter` whenever it changes (back/forward, clearing).
import { ref, watch } from 'vue'
import { DIRECTIONS, type Direction, type PostFilter, TARGET_KINDS, type TargetKind } from '../api/posts'
import { MAX_QUERY_LENGTH, isFilterActive, normalizeQuery, sameFilter } from '../feedFilter'
import { label } from '../labels'
import AppButton from './AppButton.vue'

const props = defineProps<{ filter: PostFilter }>()

const emit = defineEmits<{
  /** The filter to apply: the text trimmed, without empty keys. */
  apply: [filter: PostFilter]
  clear: []
}>()

const text = ref('')
/** '' = any. */
const kind = ref<TargetKind | ''>('')
const direction = ref<Direction | ''>('')

watch(
  () => props.filter,
  (next, previous) => {
    if (previous && sameFilter(next, previous)) return
    text.value = next.q ?? ''
    kind.value = next.kind ?? ''
    direction.value = next.direction ?? ''
  },
  { immediate: true },
)

function submit(): void {
  const q = normalizeQuery(text.value)
  text.value = q ?? ''
  const filter: PostFilter = {}
  if (q) filter.q = q
  if (kind.value) filter.kind = kind.value
  if (direction.value) filter.direction = direction.value
  emit('apply', filter)
}
</script>

<template>
  <form class="feed-filter" role="search" aria-label="Filtr postów" @submit.prevent="submit">
    <label class="field text">
      Spółka lub temat
      <input v-model="text" type="search" :maxlength="MAX_QUERY_LENGTH" />
    </label>
    <label class="field">
      Rodzaj celu
      <select v-model="kind">
        <option value="">dowolny</option>
        <option v-for="value in TARGET_KINDS" :key="value" :value="value">{{ label('targetKind', value) }}</option>
      </select>
    </label>
    <label class="field">
      Stanowisko
      <select v-model="direction">
        <option value="">dowolne</option>
        <option v-for="value in DIRECTIONS" :key="value" :value="value">{{ label('direction', value) }}</option>
      </select>
    </label>
    <div class="actions">
      <AppButton type="submit" variant="primary">Filtruj</AppButton>
      <AppButton v-if="isFilterActive(filter)" @click="emit('clear')">Wyczyść filtry</AppButton>
    </div>
  </form>
</template>

<style scoped>
/* One wrapping row; the buttons line up with the bottom of the fields. */
.feed-filter {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--space-3) var(--space-4);
}

/* The caption above its field. The fields keep the global look of style.css. */
.field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.field.text {
  flex: 1 1 14rem;
}

.field input,
.field select {
  box-sizing: border-box;
  width: 100%;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

/* Narrow screens: one field under the other at full width. */
@media (max-width: 40rem) {
  .field,
  .field.text {
    flex: 1 1 100%;
  }
}
</style>
