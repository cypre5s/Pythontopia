<script setup lang="ts">
// M2 重定向桩：/field 已在路由层 redirect 到 /world（farm_north 由 WorldScene 切换）；
// 保留组件作为旧链接 / 测试入口的兜底，确保不会渲染已被淘汰的 FieldCanvas 副实例。
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
