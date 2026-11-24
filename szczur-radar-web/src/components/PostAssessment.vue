<script setup lang="ts">
// The full contract V2 assessment of a post, grouped by target, in the layout of "Cel w ocenie" (ui-design.md;
// prototype post.html): each target is an article with its name (the quote that names it, a level-4 heading under the
// panel's "Ocena"), its kind and the author's stance; under it the claims about it, each with its statement, its kind
// and the quote it rests on. A target without claims says so. Then the general claims and the flags, at the same
// level. Everything in the model's order. The relevance and the date of the assessment are the panel's (PostInspector).
import { computed } from 'vue'
import type { Claim, PostItem, Target } from '../api/posts'
import { label } from '../labels'
import StanceText from './StanceText.vue'
import StatusBadge from './StatusBadge.vue'

const props = defineProps<{ assessment: NonNullable<PostItem['assessment']> }>()

const output = computed(() => props.assessment.output)

const prefilter = computed(() => props.assessment.resultSource === 'prefilter')

/** A target with the claims that have it in `target_ids`, or the general claims (`target: null`). */
interface Group {
  key: string
  target: Target | null
  claims: Claim[]
}

/** One group per target (a claim with several targets is in each of them), then the general claims, if any. */
const groups = computed<Group[]>(() => {
  const { targets, claims } = output.value
  const byTarget = targets.map((target) => ({
    key: target.id,
    target,
    claims: claims.filter((claim) => claim.target_ids.includes(target.id)),
  }))
  const general = claims.filter((claim) => claim.target_ids.length === 0)
  return general.length ? [...byTarget, { key: 'general', target: null, claims: general }] : byTarget
})

/**
 * What a claim is, after its statement: its kind; whose it is when not the author's; "z tytułu" for a quote of the
 * title; "podstawa stanowiska" for a claim the target's stance rests on (`stance.claim_ids`).
 */
function claimNote(target: Target | null, claim: Claim): string {
  const parts = [label('claimKind', claim.kind)]
  if (claim.attribution !== 'author') parts.push(label('attribution', claim.attribution))
  if (claim.evidence.field === 'title') parts.push('z tytułu')
  if (target?.stance.claim_ids.includes(claim.id)) parts.push('podstawa stanowiska')
  return parts.join(', ')
}
</script>

<template>
  <div class="post-assessment">
    <p v-if="output.modifiers.length" class="modifiers">
      <StatusBadge v-for="modifier in output.modifiers" :key="modifier" tone="neutral">{{ label('modifier', modifier) }}</StatusBadge>
    </p>
    <p v-if="prefilter" class="note">za mało treści, bez oceny modelu</p>

    <article v-for="group in groups" :key="group.key" class="target">
      <div class="target-head">
        <h4 class="target-title">{{ group.target ? group.target.anchor.quote : 'Tezy ogólne' }}</h4>
        <template v-if="group.target">
          <span class="kind">{{ label('targetKind', group.target.kind) }}</span>
          <StanceText class="stance" :stance="group.target.stance" />
        </template>
      </div>
      <ul v-if="group.claims.length" class="claims">
        <li v-for="claim in group.claims" :key="claim.id" class="claim">
          <p class="statement">{{ claim.statement }} <span class="claim-note">{{ claimNote(group.target, claim) }}</span></p>
          <blockquote class="evidence">{{ claim.evidence.quote }}</blockquote>
        </li>
      </ul>
      <p v-else class="note">Wspomniany, bez twierdzeń.</p>
    </article>

    <p v-if="!prefilter && !output.targets.length && !output.claims.length" class="empty">Model nie wskazał celów ani tez.</p>

    <article v-if="output.flags.length" class="target">
      <div class="target-head">
        <h4 class="target-title">Zastrzeżenia</h4>
      </div>
      <p class="flags">
        <StatusBadge v-for="flag in output.flags" :key="flag" tone="neutral">{{ label('flag', flag) }}</StatusBadge>
      </p>
    </article>
  </div>
</template>

<style scoped>
.post-assessment {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

p {
  margin: 0;
}

.modifiers,
.flags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.flags {
  margin-top: var(--space-2);
}

.note,
.empty {
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}

/* A target on its soft card: the name in the serif, its kind, the stance at the right end. */
.target {
  padding: var(--space-4) var(--space-5);
  border-radius: var(--radius-lg);
  background: var(--surface-2);
}

.target-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-1) var(--space-2);
}

.target-title {
  margin: 0;
  color: var(--text-h);
  font-family: var(--font-display);
  font-size: var(--font-size-3xl);
  font-weight: var(--weight-medium);
  line-height: var(--leading-snug);
  letter-spacing: var(--tracking-snug);
  overflow-wrap: anywhere;
}

.kind {
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

.stance {
  align-self: center;
  margin-left: auto;
  font-weight: var(--weight-semibold);
}

.target .note {
  margin-top: var(--space-2);
}

.claims {
  display: grid;
  gap: var(--space-4);
  margin: var(--space-2) 0 0;
  padding: 0;
  list-style: none;
}

.statement {
  color: var(--text-h);
  overflow-wrap: anywhere;
}

.claim-note {
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

/* A verbatim quote of the post, in the serif of what is read (upright: the app has no italic face); up to 320
   characters, wraps instead of scrolling at 375 px. */
.evidence {
  margin: var(--space-2) 0 0;
  padding-left: var(--space-4);
  border-left: 2px solid var(--warning);
  color: var(--text);
  font-family: var(--font-display);
  font-size: var(--font-size-xl);
  line-height: var(--leading-normal);
  overflow-wrap: anywhere;
}
</style>
