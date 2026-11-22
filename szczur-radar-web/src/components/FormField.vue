<script setup lang="ts">
// A form field as the design lays it out ("Pola"): the label above the field, a hint or an error below it. The
// component binds `for`, `aria-describedby` and `aria-invalid`: the view puts `fieldProps` on its input, or on the input
// inside a wrapper it renders in the slot (a search field with its icon). The field itself keeps the global look of
// style.css; types text, email, password, search, date and select (a checkbox is not a field of this kind).
//
// The error lives in a polite live region that is always rendered, empty without an error: an error appearing in it is
// announced also when the focus already stands on the field (Enter in a field, a server error after Enter), where
// moving the focus would change nothing. The empty region takes no room: the spacing under the label and the hint or
// error belongs to them, not to the root's gap.
import { computed, useId } from 'vue'
import AppIcon from './AppIcon.vue'

const props = defineProps<{
  label: string
  hint?: string
  error?: string | null
}>()

defineSlots<{
  default(props: {
    fieldProps: { id: string; 'aria-describedby': string | undefined; 'aria-invalid': 'true' | undefined }
  }): unknown
}>()

const id = useId()
const hintId = `${id}-hint`
const errorId = `${id}-error`

const fieldProps = computed(() => {
  const described = [props.hint ? hintId : null, props.error ? errorId : null].filter((part) => part !== null)
  return {
    id,
    'aria-describedby': described.length > 0 ? described.join(' ') : undefined,
    'aria-invalid': props.error ? ('true' as const) : undefined,
  }
})
</script>

<template>
  <div class="form-field">
    <label :for="id">{{ label }}</label>
    <slot :fieldProps="fieldProps" />
    <p v-if="hint" :id="hintId" class="hint">{{ hint }}</p>
    <div aria-live="polite">
      <p v-if="error" :id="errorId" class="error">
        <AppIcon name="i-circle-alert" />
        {{ error }}
      </p>
    </div>
  </div>
</template>

<style scoped>
.form-field {
  display: grid;
  min-width: 0;
  align-content: start;
}

label {
  margin-bottom: var(--space-1);
  color: var(--text-h);
  font-size: var(--font-size-sm);
  font-weight: var(--weight-medium);
}

.form-field :deep(input:not([type='checkbox'])),
.form-field :deep(select) {
  width: 100%;
}

.hint,
.error {
  margin: var(--space-1) 0 0;
  font-size: var(--font-size-xs);
}

.hint {
  color: var(--text-muted);
}

.error {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  color: var(--destructive);
  font-weight: var(--weight-medium);
}

.error .app-icon {
  --icon-size: 0.9375rem;
}
</style>
