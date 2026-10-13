<script setup lang="ts">
// A target's stance as text: the direction, in its role's colour for bullish and bearish (the text
// says it, the colour only reinforces it), and "(ostrożnie)" when the author hedges it.
import type { Stance } from '../api/posts'
import { label } from '../labels'

withDefaults(
  defineProps<{
    stance: Stance
    /** The element: `dd` when the stance is the description of a target term (PostCard). */
    tag?: 'span' | 'dd'
  }>(),
  { tag: 'span' },
)
</script>

<template>
  <component :is="tag" class="stance" :class="stance.direction">{{ label('direction', stance.direction) }}<template v-if="stance.assertiveness === 'hedged'"> (ostrożnie)</template></component>
</template>

<style scoped>
.bullish {
  color: var(--success);
}

.bearish {
  color: var(--destructive);
}
</style>
