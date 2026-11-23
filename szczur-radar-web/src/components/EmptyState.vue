<script setup lang="ts">
// A view with nothing to show says what there is not, why, and gives one next step (ui-design.md, "Stany widoku").
// No role: it is a part of the page, not an announcement. The title is the view's own heading of its list area
// (`h2`) or a paragraph where the section has its heading already (the admin panel, the catalogue); the sentence is the
// default slot and the step (a button or a link) the `action` slot. No outer margin: the place that uses it spaces it.
import AppIcon from './AppIcon.vue'
import type { IconName } from './iconNames'

withDefaults(
  defineProps<{
    icon: IconName
    title: string
    titleTag?: 'h2' | 'p'
    /** The id of the title, for the `aria-labelledby` of the area. */
    titleId?: string
  }>(),
  { titleTag: 'p', titleId: undefined },
)
defineSlots<{ default?(): unknown; action?(): unknown }>()
</script>

<template>
  <div class="state">
    <AppIcon class="state-icon" :name="icon" />
    <component :is="titleTag" :id="titleId" class="state-title">{{ title }}</component>
    <p v-if="$slots.default" class="state-text"><slot /></p>
    <div v-if="$slots.action" class="state-action"><slot name="action" /></div>
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

  color: var(--text-muted);
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

.state-action {
  margin-top: var(--space-3);
}
</style>
