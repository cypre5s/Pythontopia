<script setup lang="ts">
// M2 重定向桩：/town 已在路由层 redirect 到 /world；保留组件仅作向后兼容，
// 兜底直接挂载到该组件的边角场景（深链 / 旧入口）。
import { onMounted } from 'vue';
import { useRouter } from 'vue-router';

const router = useRouter();

onMounted(() => {
  void router.replace('/world');
});
</script>

<template>
  <div
    class="legacy-redirect"
    aria-hidden="true"
  />
</template>
