<script setup lang="ts">
// A target's stance as a sign, a word and a colour on a soft background (three signals, so it reads without telling
// green from red). The word comes from labels.ts; "(ostrożnie)" follows it when the author hedges. With `target`
// (and `kind`) it is the target with its stance in one marker: the name first, which is cut short with an ellipsis on a
// narrow screen, never the stance.
import type { Direction, Stance } from '../api/posts'
import { label } from '../labels'
import AppIcon from './AppIcon.vue'
import type { IconName } from './iconNames'

withDefaults(
  defineProps<{
    stance: Stance
    /** The element: `dd` when the stance is the description of a target term (PostCard). */
    tag?: 'span' | 'dd'
    /** The target's name: the marker is then a target with its stance. */
    target?: string
    /** The target's kind, after its name. */
    kind?: string
  }>(),
  { tag: 'span', target: undefined, kind: undefined },
)

/** The sign of each direction; "bez stanowiska" and "niejasno" share one. */
const MARKS: Record<Direction, IconName> = {
  bullish: 's-up',
  bearish: 's-down',
  neutral: 's-flat',
  mixed: 's-mixed',
  not_expressed: 's-none',
  unclear: 's-none',
}
</script>

<template>
  <component :is="tag" class="stance" :class="stance.direction">
    <span v-if="target" class="t-name">{{ target }}</span>
    <span v-if="kind" class="kind">{{ kind }}</span>
    <AppIcon class="mark" :name="MARKS[stance.direction]" />
    <span class="t-stance">{{ label('direction', stance.direction) }}</span>
    <span v-if="stance.assertiveness === 'hedged'" class="t-hedge"> (ostrożnie)</span>
  </component>
</template>

<style scoped>
/* The marker stays on one line; a very narrow screen cuts the target's name, not the stance. */
.stance {
  display: inline-flex;
  align-items: center;
  justify-self: start;
  gap: var(--space-2);
  width: fit-content;
  /* As a flex item it may shrink below its text, so max-width holds and the target's name is cut. */
  min-width: 0;
  max-width: 100%;
  padding: var(--space-0) var(--space-3);
  border-radius: var(--radius-full);
  background: var(--tone-soft);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.bullish {
  --tone: var(--success);
  --tone-soft: var(--success-soft);
}

.bearish {
  --tone: var(--destructive);
  --tone-soft: var(--destructive-soft);
}

.neutral {
  --tone: var(--stance-neutral);
  --tone-soft: var(--neutral-soft);
}

.mixed {
  --tone: var(--stance-mixed);
  --tone-soft: var(--mixed-soft);
}

.not_expressed,
.unclear {
  --tone: var(--text-muted);
  --tone-soft: var(--neutral-soft);
}

.t-name {
  min-width: 0;
  overflow: hidden;
  color: var(--text-h);
  font-weight: var(--weight-semibold);
  text-overflow: ellipsis;
}

.kind {
  color: var(--text);
  font-size: var(--font-size-xs);
}

.mark,
.t-stance,
.t-hedge {
  color: var(--tone);
}
</style>
