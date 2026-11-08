<template>
  <button class="sidebar-toggle" type="button" aria-label="折叠侧边栏" aria-expanded="true" title="折叠侧边栏" @click="sidebar.collapse()"><GameIcon name="panelClose" /></button>
</template>
<script setup lang="ts">
import GameIcon from "./GameIcon.vue";
import { useWorkspaceSidebar } from "./workspaceSidebar";
const sidebar = useWorkspaceSidebar();
</script>
<style>
.game-workspace .sidebar-toggle { display: inline-grid; place-items: center; width: 44px; min-width: 44px; height: 44px; min-height: 44px; padding: 10px; border: 1px solid transparent; background: transparent; color: inherit; border-radius: var(--game-control-radius, 8px); box-shadow: none; }
.game-workspace .sidebar-toggle:hover { background: #284b4310; border-color: currentColor; }
.game-workspace .sidebar-toggle .game-icon { width: 22px; height: 22px; }
.game-workspace .learning-support[hidden] { display: none !important; }
</style>
