<script setup lang="ts">
// The full contract V2 assessment of a post, grouped by target: each target with its stance and the
// claims about it, so the stance stands beside the claims it rests on; then the general claims and
// the flags. Everything in the model's order.
import { computed } from 'vue'
import type { Claim, PostItem, Target } from '../api/posts'
import { formatDateTime, label } from '../labels'
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

/** A claim the target's stance rests on (`stance.claim_ids`). */
function isBasis(target: Target | null, claim: Claim): boolean {
  return target?.stance.claim_ids.includes(claim.id) ?? false
}
</script>

<template>
  <div class="post-assessment">
    <p class="meta">
      <StatusBadge tone="neutral">{{ label('relevance', output.relevance) }}</StatusBadge>
      <StatusBadge v-for="modifier in output.modifiers" :key="modifier" class="modifier" tone="neutral">{{ label('modifier', modifier) }}</StatusBadge>
      <span v-if="prefilter" class="note">bez treści, bez oceny modelu</span>
      <span class="assessed">oceniono <time :datetime="assessment.assessedAt">{{ formatDateTime(assessment.assessedAt) }}</time></span>
    </p>

    <section v-for="group in groups" :key="group.key" class="group">
      <template v-if="group.target">
        <h3 class="heading">{{ group.target.anchor.quote }}</h3>
        <p class="target-meta">{{ label('targetKind', group.target.kind) }} · {{ label('reference', group.target.reference) }}</p>
        <p class="stance"><StanceText :stance="group.target.stance" /></p>
      </template>
      <h3 v-else class="heading">Tezy ogólne</h3>
      <ul v-if="group.claims.length" class="claims">
        <li v-for="claim in group.claims" :key="claim.id" class="claim">
          <p class="claim-meta">
            {{ label('claimKind', claim.kind) }} · {{ label('attribution', claim.attribution) }}<template v-if="isBasis(group.target, claim)"> · <span class="basis">podstawa stanowiska</span></template>
          </p>
          <p class="statement">{{ claim.statement }}</p>
          <blockquote class="evidence">{{ claim.evidence.quote }}</blockquote>
        </li>
      </ul>
    </section>

    <p v-if="!prefilter && !output.targets.length && !output.claims.length" class="empty">Model nie wskazał celów ani tez.</p>

    <section v-if="output.flags.length" class="group">
      <h3 class="heading">Zastrzeżenia</h3>
      <p class="flags">
        <StatusBadge v-for="flag in output.flags" :key="flag" tone="neutral">{{ label('flag', flag) }}</StatusBadge>
      </p>
    </section>
  </div>
</template>

<style scoped>
.post-assessment {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  min-width: 0;
  font-size: var(--font-size-md);
}

p {
  margin: 0;
}

.meta,
.flags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-1) var(--space-3);
  font-size: var(--font-size-sm);
}

.flags {
  gap: var(--space-2);
}

/* Date and time stay on one line. */
.assessed {
  white-space: nowrap;
}

.group {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
  padding-top: var(--space-3);
  border-top: 1px solid var(--border);
}

.heading {
  margin: 0;
  font-size: var(--font-size-md);
  font-weight: 600;
  line-height: 1.45;
  color: var(--text-h);
  overflow-wrap: anywhere;
}

.target-meta,
.claim-meta {
  font-size: var(--font-size-sm);
}

.claims {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin: var(--space-2) 0 0;
  padding: 0;
  list-style: none;
}

.claim {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.basis {
  color: var(--text-h);
  font-weight: 600;
}

.statement {
  color: var(--text-h);
  overflow-wrap: anywhere;
}

/* A verbatim quote of the post; up to 320 characters, wraps instead of scrolling at 375 px. */
.evidence {
  margin: 0;
  padding-left: var(--space-3);
  border-left: 2px solid var(--border-strong);
  font-style: italic;
  overflow-wrap: anywhere;
}
</style>
