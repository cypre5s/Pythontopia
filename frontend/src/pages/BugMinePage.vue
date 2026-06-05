<script setup lang="ts">
// 重定向桩：/bug-mine 已在路由层 redirect 到 /world；保留组件以备直接挂载场景，
// 避免连锁删除影响仍引用 BugMinePanel 的旁路逻辑。
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
