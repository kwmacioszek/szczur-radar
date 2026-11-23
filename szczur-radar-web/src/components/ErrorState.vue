<script setup lang="ts">
// A view that did not load says what failed and what stayed (the sentence), and gives one button that tries again
// and shows that it is trying (ui-design.md, "Stany widoku"). `role="alert"`, so it is announced at once. While
// `retrying` the button is busy and the message loses its role: it stays on the screen until the answer comes. When
// the retry ends the message is drawn anew (a new `key`), so a second failure is announced a second time; setting
// `role` back on the same element would not. The busy button is disabled, so the browser drops the focus: the view
// moves it with `focus()` after a failed retry, and to its heading after a successful one. No outer margin: the place
// that uses it spaces it.
import { ref, useTemplateRef, watch } from 'vue'
import AppButton from './AppButton.vue'
import AppIcon from './AppIcon.vue'

const props = withDefaults(
  defineProps<{
    title: string
    titleTag?: 'h2' | 'p'
    /** The id of the title, for the `aria-labelledby` of the area. */
    titleId?: string
    /** The retry is in progress. */
    retrying?: boolean
  }>(),
  { titleTag: 'p', titleId: undefined, retrying: false },
)
const emit = defineEmits<{ retry: [] }>()
defineSlots<{ default?(): unknown }>()

const retryButton = useTemplateRef<InstanceType<typeof AppButton>>('retryButton')
const drawing = ref(0)

watch(
  () => props.retrying,
  (now, before) => {
    if (before && !now) drawing.value++
  },
)

/** Puts the focus on "Spróbuj ponownie". */
function focus(): void {
  const button = retryButton.value?.$el
  if (button instanceof HTMLElement) button.focus()
}

defineExpose({ focus })
</script>

<template>
  <div :key="drawing" class="state state-error" :role="retrying ? undefined : 'alert'">
    <AppIcon class="state-icon" name="i-circle-alert" />
    <component :is="titleTag" :id="titleId" class="state-title">{{ title }}</component>
    <p v-if="$slots.default" class="state-text"><slot /></p>
    <AppButton
      ref="retryButton"
      class="state-retry"
      icon="i-refresh-cw"
      :busy="retrying"
      busy-label="Wczytywanie…"
      @click="emit('retry')"
    >
      Spróbuj ponownie
    </AppButton>
  </div>
</template>

<style scoped>
.state {
  display: grid;
  justify-items: center;
  gap: var(--space-2);
  padding: var(--space-8) var(--space-5);
  border: 1px solid var(--line);
  border-radius: var(--radius-xl);
  background: var(--surface);
  text-align: center;
}

.state-icon {
  --icon-size: 2rem;

  color: var(--destructive);
}

.state-title {
  margin: 0;
  color: var(--text-h);
  font-family: var(--font-display);
  font-size: var(--font-size-3xl);
  font-weight: var(--weight-medium);
  line-height: var(--leading-snug);
  letter-spacing: var(--tracking-snug);
}

.state-text {
  max-width: 28rem;
  margin: 0;
  color: var(--text);
}

.state-retry {
  margin-top: var(--space-3);
}
</style>
