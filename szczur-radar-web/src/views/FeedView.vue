<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type Page, type PostItem, fetchPosts } from '../api/posts'
import AppButton from '../components/AppButton.vue'
import AppHeader from '../components/AppHeader.vue'
import PostCard from '../components/PostCard.vue'
import StatusMessage from '../components/StatusMessage.vue'

const PAGE_SIZE = 50

const route = useRoute()
const router = useRouter()

const result = ref<Page<PostItem> | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

/** The page in the address (`?page=`, from 0), so a refresh keeps it. */
const page = computed(() => {
  const value = Number.parseInt(String(route.query.page ?? '0'), 10)
  return Number.isFinite(value) && value > 0 ? value : 0
})
const pages = computed(() => (result.value ? Math.max(1, Math.ceil(result.value.total / PAGE_SIZE)) : 1))

async function load(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    result.value = await fetchPosts(page.value, PAGE_SIZE)
  } catch {
    error.value = 'Nie udało się wczytać postów. Spróbuj odświeżyć stronę.'
  } finally {
    loading.value = false
  }
}

function goTo(target: number): void {
  void router.push({ query: { ...route.query, page: target > 0 ? String(target) : undefined } })
}

watch(page, load, { immediate: true })
</script>

<template>
  <AppHeader />
  <main class="feed">
    <h1>Posty</h1>
    <StatusMessage v-if="error" tone="error">{{ error }}</StatusMessage>
    <p v-else-if="loading && !result" class="loading">Wczytywanie…</p>
    <p v-else-if="result && result.total === 0" class="empty">
      Brak ocenionych postów. Pojawią się po najbliższej aktualizacji danych.
    </p>
    <template v-else-if="result">
      <PostCard v-for="post in result.items" :key="post.id" :post="post" />
      <nav class="pager" aria-label="Strony">
        <AppButton :disabled="page === 0 || loading" @click="goTo(page - 1)">Nowsze</AppButton>
        <span class="position">Strona {{ page + 1 }} z {{ pages }}</span>
        <AppButton :disabled="page + 1 >= pages || loading" @click="goTo(page + 1)">Starsze</AppButton>
      </nav>
    </template>
  </main>
</template>

<style scoped>
.feed {
  max-width: 48rem;
  margin: 0 auto;
  padding: var(--space-4);
  box-sizing: border-box;
}

h1 {
  margin: var(--space-2) 0 var(--space-4);
}

/* The cards keep their own spacing; the pager sits under the last one. */
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-4) 0;
}
</style>
