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
.game-workspace :is(.town-sidebar, .journal-index, .map-sidebar) { position: relative; }
.game-workspace :is(.town-sidebar, .journal-index, .map-sidebar) > .sidebar-toggle { position: absolute; top: 10px; right: 10px; z-index: 1; }
.game-workspace .town-sidebar > .sidebar-toggle { color: var(--game-paper, #fbf5e9); }
.game-workspace .town-sidebar > .sidebar-toggle:hover { background: #ffffff15; }
.game-workspace :is(.town-book, .journal-book, .map-layout).is-sidebar-collapsed { grid-template-columns: minmax(0, 1fr); }
.game-workspace .journal-book.is-sidebar-collapsed { grid-template-rows: minmax(0, 1fr); }
.game-workspace :is(.town-sidebar, .journal-index, .map-sidebar, .learning-support)[hidden] { display: none !important; }
.game-workspace .town-sidebar:has(> .sidebar-toggle) { padding-top: 60px; }
.game-workspace .journal-index > .journal-cover { padding-right: 62px; }
.game-workspace .map-sidebar:has(> .sidebar-toggle) > .map-filters { padding-right: 60px; }
</style>
