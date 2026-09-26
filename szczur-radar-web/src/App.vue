<template>
  <main class="page">
    <section class="card" aria-labelledby="title">
      <p class="eyebrow">Szczur Radar</p>
      <h1 id="title">W przygotowaniu</h1>
      <p class="lead">Trwają prace nad serwisem. Zapraszamy wkrótce.</p>
    </section>
  </main>
</template>

<style scoped>
.page {
  min-height: 100svh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  box-sizing: border-box;
}

.card {
  max-width: 34rem;
  text-align: center;
}

.eyebrow {
  margin: 0 0 12px;
  font-size: 14px;
  letter-spacing: 2px;
  text-transform: uppercase;
  color: var(--accent);
}

h1 {
  margin: 0 0 16px;
}

.lead {
  color: var(--text);
  line-height: 1.5;
}
</style>
