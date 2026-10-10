<!--
  Dev only: router/index.ts adds /kitchen-sink when import.meta.env.DEV, so this view is not in the
  production build. Every colour role and shared component in every state on one page, for /10x-ui
  screenshots. Fixed data: no API calls, no session, no AppHeader.
-->
<template>
  <main class="kitchen-sink">
    <h1>Kitchen sink</h1>
    <p>Tylko w trybie dev: role kolorów i komponenty we wszystkich stanach, do zrzutów ekranu.</p>

    <section aria-labelledby="tokens-title">
      <h2 id="tokens-title">Tokeny</h2>
      <p>Wypełniane w kolejnych fazach: próbki ról kolorów z <code>:root</code> w <code>src/style.css</code>.</p>
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
</style>
