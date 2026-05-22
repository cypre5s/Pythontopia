<script setup lang="ts">
import { RouterView } from 'vue-router';
import AppToast from '@/components/system/AppToast.vue';
import DialogTree from '@/components/game/DialogTree.vue';
</script>

<template>
  <RouterView />
  <AppToast />
  <DialogTree />
</template>
