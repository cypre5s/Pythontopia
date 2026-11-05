<template>
  <section class="workspace-load-state" :role="error ? 'alert' : 'status'">
    <h2>{{ error ? '卡片暂时未能打开' : '正在打开…' }}</h2>
    <p v-if="error">页面可能已更新，或网络暂时中断。请刷新后重新打开；刷新前请先保存尚未提交的内容。</p>
    <button v-if="error" @click="reload">刷新页面</button>
  </section>
</template>
<script setup lang="ts">
defineProps<{ error?: Error }>();
function reload() { window.location.reload(); }
</script>
<style scoped>
.workspace-load-state { margin:24px;padding:32px;background:#f7efdc;color:#493b28;border:1px solid #bca77d;border-radius:12px; }
.workspace-load-state h2 { margin-top:0; }.workspace-load-state p { line-height:1.8; }
</style>
