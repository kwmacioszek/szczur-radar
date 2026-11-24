<script setup lang="ts">
// The layout of the admin panel (S-50), the parent route of its three parts (router/index.ts): the h1 "Panel admina",
// the bar of the parts and the part itself. It stays mounted while the part changes, so the bar keeps its count and
// the clicked link keeps the focus. The count of failed posts is the `total` of the list's first page of one, read
// on entry, after every part change and when a part asks (a restore); while it is read again the old count stays, and
// a failed read only hides it: the part shows its own state.
import { computed, onMounted, provide, ref, useTemplateRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { fetchFailedPosts } from '../../api/posts'
import AdminPartsNav, { type AdminPart } from '../../components/AdminPartsNav.vue'
import PageHeader from '../../components/PageHeader.vue'
import { ADMIN_PANEL_KEY } from './adminPanel'

const PARTS = [
  { label: 'Przebiegi', name: 'admin' },
  { label: 'Jakość ocen', name: 'admin-quality' },
  { label: 'Nieudane posty', name: 'admin-posts', counted: true },
] as const

const route = useRoute()
const header = useTemplateRef<InstanceType<typeof PageHeader>>('header')

/** `null`: no count (not read yet, or the read failed). */
const failedCount = ref<number | null>(null)
/** The number of the latest read; the answer or error of any earlier one is dropped. */
let latestCount = 0

async function readFailedCount(): Promise<void> {
  const current = ++latestCount
  try {
    const { total } = await fetchFailedPosts(0, 1)
    if (current === latestCount) failedCount.value = total
  } catch {
    if (current === latestCount) failedCount.value = null
  }
}

const items = computed<AdminPart[]>(() =>
  PARTS.map((part) => ({
    label: part.label,
    to: { name: part.name },
    current: route.name === part.name,
    count: 'counted' in part ? failedCount.value : undefined,
  })),
)

const partNames = new Set<unknown>(PARTS.map((part) => part.name))

/** The title takes the focus once per mount of the layout, and never after a part change. */
let titleFocusUsed = false

watch(
  () => route.name,
  (name) => {
    // Leaving the panel (a run's page) changes the route before the layout unmounts: nothing to read then.
    if (!partNames.has(name)) return
    titleFocusUsed = true
    void readFailedCount()
  },
)

provide(ADMIN_PANEL_KEY, {
  refreshFailedCount: () => void readFailedCount(),
  focusTitle(): void {
    if (titleFocusUsed) return
    titleFocusUsed = true
    header.value?.focus()
  },
})

onMounted(() => {
  void readFailedCount()
})
</script>

<template>
  <div class="admin">
    <PageHeader ref="header" class="head" title="Panel admina" />
    <AdminPartsNav label="Panel admina" :items="items" />
    <RouterView />
  </div>
</template>

<style scoped>
.head {
  margin-bottom: var(--space-4);
}
</style>
