<script setup lang="ts">
// The bar of the admin panel's parts under its heading (ui-design.md, "Części panelu admina"): each part has its own
// address, so they are links in a navigation named by `label`, not tabs, and the current one has aria-current="page"
// (the caller decides which, as in AppBar). A part may have a count, drawn as a warning badge only above 0. From props
// only, so it stands on the kitchen sink.
// The current part's underline lies on the bar's line, as in the design: the line is the bar's inset shadow, which the
// link's own inset underline covers (no negative margin, so no value off the scale).
import type { RouteLocationRaw } from 'vue-router'
import { RouterLink } from 'vue-router'
import StatusBadge from './StatusBadge.vue'

export interface AdminPart {
  label: string
  to: RouteLocationRaw
  /** The page is this part. */
  current: boolean
  /** A count beside the name; none at 0, `null` or without it. */
  count?: number | null
}

defineProps<{
  /** The navigation's name. */
  label: string
  items: AdminPart[]
}>()
</script>

<template>
  <nav class="admin-parts" :aria-label="label">
    <!-- Not RouterLink's own link: it marks only the exact address, the caller says which part is current. -->
    <RouterLink v-for="item in items" :key="item.label" v-slot="{ href, navigate }" :to="item.to" custom>
      <a :href="href" :aria-current="item.current ? 'page' : undefined" @click="navigate">
        {{ item.label }}
        <StatusBadge v-if="item.count != null && item.count > 0" tone="warning">{{ item.count }}</StatusBadge>
      </a>
    </RouterLink>
  </nav>
</template>

<style scoped>
.admin-parts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-1);
  box-shadow: inset 0 -1px 0 var(--line);
}

.admin-parts a {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  height: var(--control-height);
  padding: 0 var(--space-4);
  color: var(--text);
  font-weight: var(--weight-medium);
  text-decoration: none;
  white-space: nowrap;
}

.admin-parts a:hover {
  color: var(--text-h);
}

.admin-parts a[aria-current='page'] {
  box-shadow: inset 0 -2px 0 var(--accent);
  color: var(--text-h);
  font-weight: var(--weight-semibold);
}
</style>
