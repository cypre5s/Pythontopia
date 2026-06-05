<script setup lang="ts">
// 重定向桩：/field 已在路由层 redirect 到 /world；保留组件作为旧链接 / 测试入口的兜底，
// 避免渲染已弃用的 FieldCanvas 副实例。
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
