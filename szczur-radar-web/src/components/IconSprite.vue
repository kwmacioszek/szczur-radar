<script setup lang="ts">
// The icon sprite written into the page once (App.vue), so an icon is ready with the document and costs no request.
// AppIcon draws its icons from it by `<use href="#id">`. Hidden from assistive technology and without a size.
import sprite from '../assets/icons/sprite.svg?raw'
</script>

<template>
  <div class="icon-sprite" aria-hidden="true" v-html="sprite"></div>
</template>

<style scoped>
.icon-sprite {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
}
</style>
