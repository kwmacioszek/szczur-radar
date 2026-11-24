<script setup lang="ts">
// One post of the list in one line (ui-design.md, "Wiersz posta"): the hour, the source and channel, the title, the
// first targets with their stance and the assessment's notes. The whole row is the one link to the post; the selected
// one is `aria-current="true"`. The stance is a target's, never the post's: the contract has none for a post (PRD
// FR-012). Below 900 px the row takes a few lines: the metadata, the title (up to three lines), the targets.
// The element is the list's item: the caller gives it its list (`ol`) and the frame of its day.
import { computed } from 'vue'
import { type RouteLocationRaw, RouterLink } from 'vue-router'
import type { PostItem } from '../api/posts'
import { formatTime, label, plural, postOrigin, postType } from '../labels'
import StanceText from './StanceText.vue'

const props = defineProps<{
  post: PostItem
  /** Where the row leads: the post. */
  to: RouteLocationRaw
  /** The post is the one open. */
  selected: boolean
}>()

/** How many targets the row names; the rest is "i N inne". */
const SHOWN_TARGETS = 2

/** The start of a long text: the row shows a line of it, its link's name needs no more. */
const EXCERPT_LENGTH = 200

/** The title, or the start of the text when there is none (a Wykop entry or a comment). */
const heading = computed(() => {
  const text = props.post.title ?? props.post.body ?? ''
  return text.length > EXCERPT_LENGTH ? `${text.slice(0, EXCERPT_LENGTH)}…` : text
})

const output = computed(() => props.post.assessment?.output ?? null)
const shownTargets = computed(() => output.value?.targets.slice(0, SHOWN_TARGETS) ?? [])
const otherTargets = computed(() => Math.max(0, (output.value?.targets.length ?? 0) - SHOWN_TARGETS))
const notInvestment = computed(() => output.value?.relevance === 'not_investment_related')
/** A model's assessment that found no target; a prefilter's result says why with its own flag instead. */
const noTargets = computed(
  () => props.post.assessment?.resultSource === 'model' && output.value !== null && output.value.targets.length === 0,
)
</script>

<template>
  <li class="post-row">
    <RouterLink class="row" :to="to" :aria-current="selected ? 'true' : undefined">
      <span class="meta">
        <time :datetime="post.publishedAt">{{ formatTime(post.publishedAt) }}</time>
        <!-- API and web deploy independently: an older API has no source or channel, so the origin is left out. -->
        <span v-if="post.source" class="origin">{{ postOrigin(post.source, post.channel) }}</span>
        <!-- For a screen reader: the line has no room for it, the panel shows it. -->
        <span class="visually-hidden">{{ postType(post.source, post.itemType) }}</span>
      </span>
      <span class="title" :class="{ excerpt: post.title === null }">{{ heading }}</span>
      <span v-if="output" class="targets">
        <StanceText
          v-for="target in shownTargets"
          :key="target.id"
          :stance="target.stance"
          :target="target.anchor.quote"
          :kind="label('targetKind', target.kind)"
        />
        <span v-if="otherTargets > 0" class="note">i {{ otherTargets }} {{ plural(otherTargets, 'inny', 'inne', 'innych') }}</span>
        <span v-for="flag in output.flags" :key="flag" class="flag">{{ label('flag', flag) }}</span>
        <span v-if="notInvestment" class="note">{{ label('relevance', 'not_investment_related') }}</span>
        <span v-if="noTargets" class="note">bez celów</span>
      </span>
    </RouterLink>
  </li>
</template>

<style scoped>
/* One column below 900 px: the metadata, the title and the targets, each on its own line. */
.row {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--line);
  color: var(--text);
  text-decoration: none;
}

.post-row:last-child .row {
  border-bottom: 0;
}

.row:hover {
  background: var(--surface-2);
}

/* The global ring, inside the row: the frame of the day clips what is outside it. */
.row:focus-visible {
  outline-offset: -2px;
}

.row[aria-current='true'] {
  background: var(--selected);
  box-shadow: inset 3px 0 0 var(--accent);
}

/* A long channel name is cut, the hour stays. */
.meta {
  display: flex;
  align-items: baseline;
  gap: 0 var(--space-2);
  min-width: 0;
  overflow: hidden;
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta time {
  color: var(--text-h);
  font-weight: var(--weight-medium);
}

/* The title in the serif of the design, up to three lines on a narrow screen; a long word (a link) wraps. */
.title {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-h);
  font-family: var(--font-display);
  font-size: var(--font-size-xl);
  font-weight: var(--weight-medium);
  line-height: var(--leading-snug);
  letter-spacing: var(--tracking-snug);
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  line-clamp: 3;
}

/* A post without a title shows the start of its text, which must not pass for a title. */
.title.excerpt {
  color: var(--text);
  font-weight: var(--weight-regular);
}

.targets {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1) var(--space-2);
  min-width: 0;
}

.note {
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  white-space: nowrap;
}

/* An uncertainty of the assessment ("możliwy sarkazm", "brak kontekstu") after the targets. */
.flag {
  color: var(--warning);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

/* From 900 px one line: the metadata, the title cut with an ellipsis, the targets on the right. */
@media (min-width: 900px) {
  .row {
    grid-template-columns: 13.5rem minmax(0, 1fr) auto;
    align-items: center;
    gap: var(--space-5);
    padding: var(--space-3) var(--space-5);
  }

  .title {
    display: block;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .targets {
    justify-content: flex-end;
    max-width: 31rem;
  }
}
</style>
