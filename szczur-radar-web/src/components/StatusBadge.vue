<script setup lang="ts">
// A state marker. The text always says the state; the tone's colour on its soft background only reinforces it. The
// optional icon marks a state that needs attention (warning, destructive); the other tones have none.
import AppIcon from './AppIcon.vue'
import type { IconName } from './iconNames'

export type BadgeTone = 'success' | 'warning' | 'destructive' | 'neutral' | 'info'

const ICONS: Partial<Record<BadgeTone, IconName>> = { warning: 'i-triangle-alert', destructive: 'i-circle-alert' }

defineProps<{
  tone: BadgeTone
  /** The icon of the tone, where it has one. */
  icon?: boolean
}>()
defineSlots<{ default(): unknown }>()
</script>

<template>
  <span class="status-badge" :data-tone="tone">
    <AppIcon v-if="icon && ICONS[tone]" class="icon" :name="ICONS[tone]!" />
    <slot />
  </span>
</template>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: var(--space-0) var(--space-3);
  border-radius: var(--radius-full);
  background: var(--tone-soft);
  color: var(--tone);
  font-size: var(--font-size-sm);
  font-weight: var(--weight-medium);
}

.icon {
  --icon-size: 0.875rem;
}

[data-tone='success'] {
  --tone: var(--success);
  --tone-soft: var(--success-soft);
}

[data-tone='warning'] {
  --tone: var(--warning);
  --tone-soft: var(--warning-soft);
}

[data-tone='destructive'] {
  --tone: var(--destructive);
  --tone-soft: var(--destructive-soft);
}

[data-tone='neutral'] {
  --tone: var(--stance-neutral);
  --tone-soft: var(--neutral-soft);
}

[data-tone='info'] {
  --tone: var(--accent);
  --tone-soft: var(--selected);
}
</style>
