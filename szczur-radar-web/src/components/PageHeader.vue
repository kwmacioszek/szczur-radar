<script setup lang="ts">
// The title of a page: its one h1, an optional subtitle and, on the right, only what changes the whole view (a scope
// switch, a day picker). A state of the page stands beside the title in the `status` slot (a run's badge). The h1 is
// focusable by script, not by Tab: after the view's data arrives the view moves the focus here with `focus()`; the
// moment is the view's, because it depends on its data.
import { useTemplateRef } from 'vue'

defineProps<{
  title: string
  /** The id of the h1, for the `aria-labelledby` of the view's section. */
  headingId?: string
  subtitle?: string
}>()
defineSlots<{ status?(): unknown; tools?(): unknown }>()

const heading = useTemplateRef<HTMLElement>('heading')

function focus(): void {
  heading.value?.focus()
}

defineExpose({ focus })
</script>

<template>
  <div class="page-head">
    <div>
      <div class="title-row" :class="{ 'with-status': $slots.status }">
        <h1 :id="headingId" ref="heading" tabindex="-1">{{ title }}</h1>
        <slot name="status" />
      </div>
      <p v-if="subtitle" class="page-sub">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.tools" class="view-tools">
      <slot name="tools" />
    </div>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3) var(--space-5);
}

.title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-4);
}

/* A post's title can be one long word (a link): it wraps instead of widening the page. */
h1 {
  margin: 0;
  font-size: var(--font-size-display);
  overflow-wrap: anywhere;
}

/* A title with a state beside it is set smaller, as the run page's in the design. */
.with-status h1 {
  font-size: var(--font-size-6xl);
}

.page-sub {
  margin: var(--space-1) 0 0;
  color: var(--text);
  font-family: var(--font-display);
  font-size: var(--font-size-2xl);
  line-height: var(--leading-snug);
}

.view-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: var(--space-3) var(--space-4);
}

@media (max-width: 899px) {
  h1 {
    font-size: var(--font-size-6xl);
  }

  .with-status h1 {
    font-size: var(--font-size-5xl);
  }
}
</style>
