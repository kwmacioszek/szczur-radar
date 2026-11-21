<script setup lang="ts">
// The outcome of an action or a state to know, with the icon of its tone. `ok` and `warning` are announced politely
// (role status), `error` at once (role alert). It has no outer margin: the place that uses it spaces it.
import AppIcon from './AppIcon.vue'
import type { IconName } from './iconNames'

type Tone = 'ok' | 'error' | 'warning'

defineProps<{ tone: Tone }>()

const ROLES: Record<Tone, 'status' | 'alert'> = { ok: 'status', error: 'alert', warning: 'status' }
const ICONS: Record<Tone, IconName> = { ok: 'i-circle-check', error: 'i-circle-alert', warning: 'i-triangle-alert' }
</script>

<template>
  <div class="status-message" :class="tone" :role="ROLES[tone]">
    <AppIcon class="icon" :name="ICONS[tone]" />
    <p class="text"><slot /></p>
  </div>
</template>

<style scoped>
/* --text-h on the soft tint keeps the contrast of --text-h on --bg in both themes. */
.status-message {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  margin: 0;
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius);
  background: var(--tone-soft);
  color: var(--text-h);
}

.icon {
  margin-top: var(--space-0);
  color: var(--tone);
}

.text {
  min-width: 0;
  margin: 0;
}

.ok {
  --tone: var(--success);
  --tone-soft: var(--success-soft);
}

.error {
  --tone: var(--destructive);
  --tone-soft: var(--destructive-soft);
}

.warning {
  --tone: var(--warning);
  --tone-soft: var(--warning-soft);
}
</style>
