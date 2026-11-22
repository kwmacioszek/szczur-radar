<script setup lang="ts">
// One icon of the sprite (IconSprite, written into the page once). Always hidden from assistive technology: an icon
// never stands alone, the text beside it or the name of its button says what it means (ui-design.md, "Ikony").
// `i-*` is stroked in the text colour; `s-*` is a filled stance mark. The size is --icon-size (a consumer sets it for
// its own icons, e.g. `--icon-size: 1.125rem` on a button) and the stance mark's own.
import { computed } from 'vue'
import type { IconName } from './iconNames'

const props = defineProps<{ name: IconName }>()

const filled = computed(() => props.name.startsWith('s-'))
</script>

<template>
  <svg class="app-icon" :class="filled ? 'mark' : 'stroked'" aria-hidden="true" focusable="false">
    <use :href="`#${name}`" />
  </svg>
</template>

<style scoped>
.app-icon {
  flex: none;
}

.stroked {
  width: var(--icon-size);
  height: var(--icon-size);
  fill: none;
  stroke: currentColor;
  stroke-width: var(--icon-stroke);
  stroke-linecap: round;
  stroke-linejoin: round;
}

.mark {
  width: 0.625rem;
  height: 0.625rem;
  fill: currentColor;
}
</style>
