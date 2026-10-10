<!--
  Dev only: router/index.ts adds /kitchen-sink when import.meta.env.DEV, so this view is not in the
  production build. Every colour role and shared component in every state on one page, for /10x-ui
  screenshots. Fixed data: no API calls, no session, no AppHeader.
-->
<script setup lang="ts">
/** The colour roles of :root in src/style.css, in the order of the plan's token table. */
const colourRoles = [
  '--bg',
  '--text',
  '--text-h',
  '--accent',
  '--on-accent',
  '--success',
  '--warning',
  '--destructive',
  '--border',
  '--ring',
] as const

type ColourRole = (typeof colourRoles)[number]

/** A tile filled with the role; --on-accent is a text colour, so it shows as text on --accent. */
function swatchStyle(role: ColourRole): Record<string, string> {
  return role === '--on-accent'
    ? { background: 'var(--accent)', color: 'var(--on-accent)' }
    : { background: `var(${role})` }
}
</script>

<template>
  <main class="kitchen-sink">
    <h1>Kitchen sink</h1>
    <p>Tylko w trybie dev: role kolorów i komponenty we wszystkich stanach, do zrzutów ekranu.</p>

    <section aria-labelledby="tokens-title">
      <h2 id="tokens-title">Tokeny</h2>
      <ul class="swatches">
        <li v-for="role in colourRoles" :key="role" class="swatch">
          <span class="tile" :style="swatchStyle(role)" aria-hidden="true">
            <template v-if="role === '--on-accent'">Aa</template>
          </span>
          <code>{{ role }}</code>
        </li>
      </ul>
    </section>

    <section aria-labelledby="buttons-title">
      <h2 id="buttons-title">Przyciski</h2>
      <p>Wypełniane w kolejnych fazach: wariant główny i pomocniczy — domyślny, wyłączony, w toku.</p>
    </section>

    <section aria-labelledby="messages-title">
      <h2 id="messages-title">Komunikaty</h2>
      <p>Wypełniane w kolejnych fazach: komunikat powodzenia i błędu.</p>
    </section>

    <section aria-labelledby="badges-title">
      <h2 id="badges-title">Znaczniki</h2>
      <p>Wypełniane w kolejnych fazach: znaczniki stanu we wszystkich tonach.</p>
    </section>

    <section aria-labelledby="fields-title">
      <h2 id="fields-title">Pola</h2>
      <p>Wypełniane w kolejnych fazach: pole daty i pole wyboru — zaznaczone, wyłączone.</p>
    </section>
  </main>
</template>

<style scoped>
.kitchen-sink {
  max-width: 56rem;
  margin: 0 auto;
  padding: 16px;
  box-sizing: border-box;
}

h1 {
  margin: 8px 0 16px;
  font-size: 32px;
}

h2 {
  margin: 24px 0 8px;
  font-size: 20px;
  color: var(--text-h);
}

.swatches {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(11rem, 1fr));
  gap: var(--space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.swatch {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-h);
}

/* Outlined, so --bg and --border stay visible on the page background. */
.tile {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 2.5rem;
  height: 2.5rem;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  font-weight: 600;
}
</style>
