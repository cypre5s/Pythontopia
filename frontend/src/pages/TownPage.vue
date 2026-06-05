<script setup lang="ts">
// 重定向桩：/town 已在路由层 redirect 到 /world；保留组件兜底深链 / 旧入口直接挂载的边角场景。
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
