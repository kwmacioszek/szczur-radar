<script setup lang="ts">
// The feed filter (FR-011a): text in a target's quote, the target's kind and the author's stance on that same target,
// under the button "Filtry" (ui-design.md, "Filtry"). `filter` is the applied one (from the address); the button says
// how many of its keys are set ("Filtry: 2"). The fields are a draft until "Filtruj" or Enter; they follow `filter`
// whenever it changes (back/forward, clearing) and are set to it again each time the panel opens.
// A disclosure, not a dialog: the panel stands in the document right after its button, Tab goes on into it. "Filtruj"
// and "Wyczyść filtry" close it; Escape, "Zamknij filtry" and a click outside the form close it without applying the
// draft. The focus goes back to the button (after a click outside only when it would be left nowhere, as in
// NotificationMenu.vue).
import { computed, onBeforeUnmount, ref, useId, useTemplateRef, watch } from 'vue'
import { DIRECTIONS, type Direction, type PostFilter, TARGET_KINDS, type TargetKind } from '../api/posts'
import { MAX_QUERY_LENGTH, activeFilterKeys, normalizeQuery, sameFilter } from '../feedFilter'
import { label } from '../labels'
import AppButton from './AppButton.vue'
import FormField from './FormField.vue'

const props = defineProps<{ filter: PostFilter }>()

const emit = defineEmits<{
  /** The filter to apply: the text trimmed, without empty keys. */
  apply: [filter: PostFilter]
  clear: []
}>()

/** The panel is open. The view leaves it to the component; the catalogue opens it. */
const open = defineModel<boolean>('open', { default: false })

const root = useTemplateRef<HTMLFormElement>('root')
const toggle = useTemplateRef<InstanceType<typeof AppButton>>('toggle')
const id = useId()
const panelId = `${id}-panel`
const titleId = `${id}-title`

/** "Filtry", or with the number of the applied filter's keys: "Filtry: 2". */
const toggleText = computed(() => {
  const count = activeFilterKeys(props.filter).length
  return count > 0 ? `Filtry: ${count}` : 'Filtry'
})

const text = ref('')
/** '' = any. */
const kind = ref<TargetKind | ''>('')
const direction = ref<Direction | ''>('')

/** The fields show the applied filter. */
function resetDraft(filter: PostFilter): void {
  text.value = filter.q ?? ''
  kind.value = filter.kind ?? ''
  direction.value = filter.direction ?? ''
}

watch(
  () => props.filter,
  (next, previous) => {
    if (previous && sameFilter(next, previous)) return
    resetDraft(next)
  },
  { immediate: true },
)

function focusToggle(): void {
  ;(toggle.value?.$el as HTMLElement | undefined)?.focus()
}

/** Closes the panel and puts the focus back on its button. */
function close(): void {
  open.value = false
  focusToggle()
}

function submit(): void {
  const q = normalizeQuery(text.value)
  text.value = q ?? ''
  const filter: PostFilter = {}
  if (q) filter.q = q
  if (kind.value) filter.kind = kind.value
  if (direction.value) filter.direction = direction.value
  close()
  emit('apply', filter)
}

function clear(): void {
  close()
  emit('clear')
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Escape') return
  close()
}

function onDocumentClick(event: MouseEvent): void {
  if (root.value?.contains(event.target as Node)) return
  open.value = false
  // A click on another control keeps the focus there; one on the page itself would leave it nowhere.
  const active = document.activeElement
  if (active === null || active === document.body || root.value?.contains(active)) focusToggle()
}

/** Escape and a click outside are heard only while the panel is open. */
function listen(on: boolean): void {
  if (on) {
    document.addEventListener('keydown', onKeydown)
    document.addEventListener('click', onDocumentClick)
  } else {
    document.removeEventListener('keydown', onKeydown)
    document.removeEventListener('click', onDocumentClick)
  }
}

watch(
  open,
  (now, before) => {
    // Opening shows the applied filter, not a draft left by an earlier close.
    if (now && before === false) resetDraft(props.filter)
    listen(now)
  },
  { immediate: true },
)
onBeforeUnmount(() => listen(false))

defineExpose({ focusToggle })
</script>

<template>
  <form ref="root" class="feed-filter" role="search" aria-label="Filtr postów" @submit.prevent="submit">
    <AppButton
      ref="toggle"
      class="toggle"
      size="lg"
      icon="i-list-filter"
      :aria-expanded="open ? 'true' : 'false'"
      :aria-controls="panelId"
      @click="open = !open"
    >
      {{ toggleText }}
    </AppButton>
    <div v-show="open" :id="panelId" class="panel" role="group" :aria-labelledby="titleId">
      <div class="panel-head">
        <p :id="titleId" class="panel-title">Filtry</p>
        <AppButton icon="i-x" icon-only aria-label="Zamknij filtry" @click="close" />
      </div>
      <div class="panel-body">
        <div class="fields">
          <FormField v-slot="{ fieldProps }" class="wide" label="Spółka lub temat">
            <input v-bind="fieldProps" v-model="text" type="search" :maxlength="MAX_QUERY_LENGTH" />
          </FormField>
          <FormField v-slot="{ fieldProps }" label="Rodzaj celu">
            <select v-bind="fieldProps" v-model="kind">
              <option value="">dowolny</option>
              <option v-for="value in TARGET_KINDS" :key="value" :value="value">{{ label('targetKind', value) }}</option>
            </select>
          </FormField>
          <FormField v-slot="{ fieldProps }" label="Stanowisko">
            <select v-bind="fieldProps" v-model="direction">
              <option value="">dowolne</option>
              <option v-for="value in DIRECTIONS" :key="value" :value="value">{{ label('direction', value) }}</option>
            </select>
          </FormField>
        </div>
        <div class="actions">
          <AppButton @click="clear">Wyczyść filtry</AppButton>
          <AppButton type="submit" variant="primary">Filtruj</AppButton>
        </div>
      </div>
    </div>
  </form>
</template>

<style scoped>
/* The button at the end of its row; the panel opens below it, over the list, aligned to its right edge. */
.feed-filter {
  position: relative;
  display: flex;
  justify-content: flex-end;
}

.panel {
  position: absolute;
  top: calc(100% + var(--space-2));
  right: 0;
  z-index: var(--z-overlay);
  box-sizing: border-box;
  width: min(24rem, 100%);
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: var(--shadow-overlay);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line);
}

.panel-title {
  margin: 0;
  color: var(--text-h);
  font-weight: var(--weight-semibold);
}

.panel-body {
  padding: var(--space-4);
}

/* Two columns; the text field takes the whole row. The fields keep the global look of style.css. */
.fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.wide {
  grid-column: 1 / -1;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
  margin-top: var(--space-4);
}

/* Below 900 px the button stands at the start of its own row and the panel below it takes the column's width. */
@media (max-width: 899px) {
  .feed-filter {
    justify-content: flex-start;
  }

  .panel {
    left: 0;
    width: auto;
  }

  .fields {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
