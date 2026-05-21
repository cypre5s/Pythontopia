<script setup lang="ts">
import AppLayout from '@/app/AppLayout.vue';

defineProps<{
  title: string;
  subtitle: string;
}>();
</script>

<template>
  <AppLayout>
    <section class="placeholder-page">
      <h1>{{ title }}</h1>
      <p>{{ subtitle }}</p>
    </section>
  </AppLayout>
</template>
