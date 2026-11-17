<script setup lang="ts">
// The theme switch: one toggle button, pressed in the dark theme. The name stays the same in both states,
// aria-pressed carries the state. The choice is kept by src/theme.ts.
import { currentTheme, toggleTheme } from '../theme'
import AppButton from './AppButton.vue'
</script>

<template>
  <AppButton :aria-pressed="currentTheme === 'dark'" @click="toggleTheme">Ciemny motyw</AppButton>
</template>
