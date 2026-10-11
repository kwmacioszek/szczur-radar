<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { type Page, type PostItem, fetchPosts } from '../api/posts'
import AppHeader from '../components/AppHeader.vue'
import PostCard from '../components/PostCard.vue'

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
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-else-if="loading && !result" class="status">Wczytywanie…</p>
    <p v-else-if="result && result.total === 0" class="status empty">
      Brak ocenionych postów. Pojawią się po pierwszym przebiegu.
    </p>
    <template v-else-if="result">
      <PostCard v-for="post in result.items" :key="post.id" :post="post" />
      <nav class="pager" aria-label="Strony">
        <button type="button" :disabled="page === 0 || loading" @click="goTo(page - 1)">Nowsze</button>
        <span class="position">Strona {{ page + 1 }} z {{ pages }}</span>
        <button type="button" :disabled="page + 1 >= pages || loading" @click="goTo(page + 1)">Starsze</button>
      </nav>
    </template>
  </main>
</template>

<style scoped>
.feed {
  max-width: 48rem;
  margin: 0 auto;
  padding: 16px;
  box-sizing: border-box;
}

h1 {
  margin: 8px 0 16px;
  font-size: 32px;
}

.status {
  color: var(--text);
}

.error {
  color: var(--destructive);
}

.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 0;
}
</style>
