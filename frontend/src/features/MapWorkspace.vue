<template>
  <section class="map-workspace" aria-label="小镇地图">
    <div class="map-layout" :class="{ 'is-sidebar-collapsed': sidebarCollapsed }">
      <div class="map-main">
        <div ref="stage" class="map-stage" :aria-busy="loading">
          <div class="map-sheet" :style="{ aspectRatio: `${bounds.width} / ${bounds.height}`, width: fittedWidth ? `${fittedWidth}px` : '100%' }">
            <canvas ref="canvas" :width="bounds.width * 16" :height="bounds.height * 16" aria-label="地图预览" role="img"/>
            <button v-for="target in visibleTargets" :key="target.key" class="map-marker" :class="[target.category, { selected: selectedKey === target.key }]" :style="markerPosition(target.x, target.y)" :aria-label="`地图标记：${target.label}`" :aria-pressed="selectedKey === target.key" @click="selectedKey = target.key">
              <GameIcon :name="target.category === 'person' ? 'people' : target.category === 'building' ? 'building' : 'compass'"/>
              <span>{{ target.label }}</span>
            </button>
            <div v-if="playerPosition" class="map-player" :class="{ 'label-left': playerPosition.tile_x > bounds.width * .76, 'label-below': playerPosition.tile_y < 3 }" :style="markerPosition(playerPosition.tile_x, playerPosition.tile_y)" role="img" aria-label="玩家当前位置">
              <span class="map-player-dot" aria-hidden="true"/><span class="map-player-label" aria-hidden="true">你在这里</span>
            </div>
          </div>
          <p v-if="loading" class="map-load-message" role="status">正在展开地图…</p>
          <div v-if="error" class="map-load-message error" role="alert"><p>{{ error }}</p><button @click="loadPreview">重新加载地图</button></div>
        </div>
      </div>
      <aside :hidden="sidebarCollapsed" :inert="sidebarCollapsed" class="map-sidebar" aria-label="地图地点与人物"><SidebarToggle />
        <div class="map-filters game-segmented" role="group" aria-label="地图标记筛选">
          <button v-for="item in filters" :key="item.id" :aria-label="item.label" :title="item.text" :aria-pressed="filter === item.id" @click="filter = item.id"><GameIcon :name="item.icon"/></button>
        </div>
        <div class="map-target-list" aria-label="地图目标列表">
          <button v-for="target in visibleTargets" :key="target.key" :aria-label="`选择${target.label}`" :aria-pressed="selectedKey === target.key" @click="selectedKey = target.key">
            <GameIcon :name="target.category === 'person' ? 'people' : target.category === 'building' ? 'building' : 'compass'"/>
            <span><strong>{{ target.label }}</strong></span><GameIcon name="chevron"/>
          </button>
          <p v-if="!visibleTargets.length" class="map-empty">这张地图没有此类标记。</p>
        </div>
        <section class="map-target-detail" aria-label="所选地图目标">
          <template v-if="selected">
            <h3>{{ selected.label }}</h3><p>{{ selected.description }}</p>
            <p v-if="navigationBlock" class="map-access-message">{{ navigationBlock }}</p>
          </template>
          <button class="primary" aria-label="前往所选目标" :disabled="!selected || Boolean(navigationBlock)" @click="go"><GameIcon name="compass"/>前往</button>
        </section>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import SidebarToggle from "../shared/SidebarToggle.vue";
import { useWorkspaceSidebar } from "../shared/workspaceSidebar";
const { collapsed: sidebarCollapsed } = useWorkspaceSidebar();
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import GameIcon from "../shared/GameIcon.vue";
import { useSession } from "../shared/session";
import { buildingMessage, getWorldMap, mapTargets, portalAvailable, portalRoute, worldConfig, type WorldTarget } from "../game/config/worldConfig";
import { paintMapPreview } from "../game/config/worldRender";

interface MapChoice {
  key: string; id: string; label: string; mapKey: string; x: number; y: number;
  category: "building" | "person" | "place"; description: string; target?: WorldTarget;
}
const session = useSession();
const previewMap = session.world?.player.map_key ?? worldConfig.default_spawn.map_key;
const filter = ref<"all" | "buildings" | "people">("all");
const selectedKey = ref("");
const canvas = ref<HTMLCanvasElement>();
const stage = ref<HTMLElement>();
const stageSize = ref({ width: 0, height: 0 }), compact = ref(false);
const loading = ref(false), error = ref("");
const filters = [
  { id: "all", label: "筛选全部", text: "全部", icon: "compass" },
  { id: "buildings", label: "筛选建筑", text: "建筑", icon: "building" },
  { id: "people", label: "筛选人物", text: "人物", icon: "people" },
] as const;
const currentMap = computed(() => getWorldMap(previewMap));
const bounds = computed(() => ({ x: 0, y: 0, width: currentMap.value.width, height: currentMap.value.height }));
const playerPosition = computed(() => session.mapPosition?.map_key === previewMap ? session.mapPosition : null);
const fittedWidth = computed(() => compact.value ? Math.max(560, stageSize.value.width) : stageSize.value.width && stageSize.value.height ? Math.min(stageSize.value.width, stageSize.value.height * bounds.value.width / bounds.value.height) : 0);
const targets = computed<MapChoice[]>(() => {
  const places = mapTargets(previewMap).map(target => {
    const building = target.building ?? currentMap.value.buildings.find(item => item.id === target.portal?.building_id);
    return {
      key: `place:${target.id}`, id: building?.id ?? target.id, label: building?.label ?? target.label,
      mapKey: previewMap, x: target.x, y: target.y, target,
      category: building || target.portal ? "building" as const : "place" as const,
      description: building ? buildingMessage(building) : target.portal ? target.portal.action_label : target.label,
    };
  });
  const people: MapChoice[] = (session.world?.npcs ?? []).filter(npc => npc.map_key === previewMap).map(npc => ({
    key: `person:${npc.key}`, id: npc.key, label: npc.name, mapKey: npc.map_key, x: npc.tile_x, y: npc.tile_y,
    category: "person", description: `前往${npc.name}身边交谈。`,
  }));
  return [...places, ...people];
});
const visibleTargets = computed(() => targets.value.filter(target => filter.value === "all" || (filter.value === "people" ? target.category === "person" : target.category === "building")));
const selected = computed(() => visibleTargets.value.find(target => target.key === selectedKey.value));
const navigationBlock = computed(() => {
  const target = selected.value, player = session.world?.player, flags = session.world?.flags ?? [];
  if (!target || !player) return "";
  if (target.target?.portal && !portalAvailable(target.target.portal, flags)) return target.target.portal.blocked_message || "这个入口尚未开放。";
  if (player.map_key === target.mapKey) return "";
  if (portalRoute(player.map_key, target.mapKey, flags)) return "";
  const allFlags = [...new Set(worldConfig.portals.flatMap(portal => portal.required_flags))];
  const route = portalRoute(player.map_key, target.mapKey, allFlags);
  return route?.find(portal => !portalAvailable(portal, flags))?.blocked_message || "暂时没有可通行的路线。";
});
function markerPosition(x: number, y: number) {
  return { left: `${(x + 0.5 - bounds.value.x) / bounds.value.width * 100}%`, top: `${(y + 0.5 - bounds.value.y) / bounds.value.height * 100}%` };
}
function go() { if (selected.value && !navigationBlock.value) session.guideTo(selected.value.id); }
let controller: AbortController | undefined, generation = 0;
let observer: ResizeObserver | undefined;
function measurePreview() {
  compact.value = window.innerWidth <= 800;
  if (stage.value) stageSize.value = { width: stage.value.clientWidth, height: stage.value.clientHeight };
  void nextTick(revealPlayer);
}
function revealPlayer() {
  const panel = stage.value, player = playerPosition.value;
  if (!compact.value || !panel || !player) return;
  const width = fittedWidth.value || panel.clientWidth, height = width * bounds.value.height / bounds.value.width;
  const x = (player.tile_x + .5 - bounds.value.x) / bounds.value.width * width;
  const y = (player.tile_y + .5 - bounds.value.y) / bounds.value.height * height;
  panel.scrollLeft = Math.max(0, Math.min(x - panel.clientWidth / 2, width - panel.clientWidth));
  panel.scrollTop = Math.max(0, Math.min(y - panel.clientHeight / 2, height - panel.clientHeight));
}
async function loadPreview() {
  controller?.abort();
  controller = new AbortController();
  const current = ++generation, mapKey = previewMap, signal = controller.signal;
  loading.value = true;
  error.value = "";
  await nextTick();
  if (!canvas.value || signal.aborted) return;
  try {
    await paintMapPreview(canvas.value, mapKey, signal, bounds.value);
  } catch (failure) {
    if (current === generation && !signal.aborted) error.value = failure instanceof Error ? failure.message : "地图暂时无法展开，请重试。";
  } finally {
    if (current === generation) loading.value = false;
  }
}
watch(filter, () => { selectedKey.value = ""; });
onMounted(() => {
  measurePreview();
  void loadPreview();
  if (typeof ResizeObserver !== "undefined" && stage.value) { observer = new ResizeObserver(measurePreview); observer.observe(stage.value); }
  window.addEventListener("resize", measurePreview);
});
onBeforeUnmount(() => { generation++; controller?.abort(); observer?.disconnect(); window.removeEventListener("resize", measurePreview); });
</script>

<style scoped>
.map-workspace { --map-border:#c5b98f;display:flex;flex-direction:column;min-height:0;flex:1;color:#344439; }
.map-layout { display:grid;grid-template-columns:minmax(0,1fr) minmax(250px,300px);gap:var(--game-gap,22px);min-height:0;flex:1; }
.map-main { display:flex;flex-direction:column;min-width:0;min-height:0;border:1px solid var(--game-line,#d7c7a9);border-radius:var(--game-radius,16px);background:#344e39;overflow:hidden; }
.map-stage { position:relative;display:flex;align-items:center;justify-content:center;min-height:0;flex:1;overflow:auto;overscroll-behavior:contain;border-radius:inherit;background:#344e39; }
.map-sheet { position:relative;flex-shrink:0;width:100%; }.map-sheet canvas { display:block;width:100%;height:100%;image-rendering:pixelated; }
.map-marker { position:absolute;z-index:2;display:flex;align-items:center;flex-direction:column;gap:1px;min-height:34px;min-width:34px;padding:2px;transform:translate(-50%,-50%);background:transparent;border:0;color:#fff4cf;text-shadow:0 1px 2px #15251d; }
.map-marker .game-icon { width:22px;height:22px;padding:3px;border:1px solid #d8bf78;border-radius:6px;background:#294f39eb;box-shadow:0 2px 5px #10281e88; }
.map-marker span { padding:2px 4px;border-radius:3px;font-size:11px;line-height:1.3;white-space:nowrap;background:#1f342be8; }.map-marker.person .game-icon { color:#fff4c1;background:#7b5637; }.map-marker.selected { z-index:4; }.map-marker.selected .game-icon { outline:3px solid #ffe09b;outline-offset:2px;background:#6a7435; }.map-marker.selected span { background:#e1c37d;color:#263a2b;text-shadow:none; }
.map-player { position:absolute;z-index:5;width:16px;height:16px;transform:translate(-50%,-50%);pointer-events:none; }
.map-player-dot { display:block;width:16px;height:16px;border:3px solid #fff9df;border-radius:50%;background:#42bdd0;box-shadow:0 0 0 2px #173d43,0 2px 5px #10281e88; }
.map-player-label { position:absolute;left:calc(100% + 5px);bottom:calc(100% - 2px);padding:3px 7px;border:1px solid #c7ddce;border-radius:10px;background:#163f3aef;color:#fff7df;font-size:11px;font-weight:600;line-height:1.3;white-space:nowrap;text-shadow:0 1px 1px #10281e; }
.map-player.label-left .map-player-label { left:auto;right:calc(100% + 5px); }.map-player.label-below .map-player-label { top:calc(100% - 2px);bottom:auto; }
.map-load-message { position:absolute;z-index:5;left:50%;top:50%;transform:translate(-50%,-50%);margin:0;max-width:90%;padding:16px 22px;border:1px solid #c9b989;border-radius:8px;background:#f8f0da;color:#4c513a; }.map-load-message p { margin:0 0 12px; }
.map-sidebar { display:flex;flex-direction:column;min-height:0;border:1px solid var(--game-line,#d7c7a9);border-radius:var(--game-radius,16px);background:var(--game-paper,#fbf5e9);overflow:hidden; }
.map-filters { align-self:center;flex-shrink:0;margin:16px; }.map-filters .game-icon { width:22px;height:22px; }
.map-target-list { flex:1;min-height:0;overflow:auto;overscroll-behavior:contain;padding:10px; }.map-target-list button { display:flex;align-items:center;gap:10px;width:100%;min-height:52px;padding:10px;border:1px solid transparent;border-radius:9px;background:transparent;text-align:left;color:#41513c; }.map-target-list button[aria-pressed="true"] { background:#e6dbb9;border-color:#b49d61; }.map-target-list button > .game-icon { width:22px;height:22px;flex-shrink:0;color:#7e8d63; }.map-target-list button > .game-icon:last-child { width:14px;margin-left:auto; }.map-target-list strong { font-size:.9rem; }.map-empty { padding:16px;color:#7e896e;line-height:1.8;font-size:.85rem; }
.map-target-detail { flex-shrink:0;padding:16px;border-top:1px solid var(--map-border);background:#eee6d1; }.map-target-detail h3 { margin:0 0 8px;font-size:1rem; }.map-target-detail p { margin:0 0 14px;line-height:1.75;font-size:.82rem; }.map-target-detail button { display:flex;justify-content:center;align-items:center;gap:8px;width:100%;min-height:44px; }.map-target-detail .game-icon { width:20px;height:20px; }.map-access-message { color:#866033; }.map-workspace button:focus-visible { outline:2px solid #87703d;outline-offset:2px; }
@media(max-width:800px) { .map-layout { grid-template-columns:minmax(0,1fr);gap:14px;padding:8px 3px 12px;overflow:auto; }.map-main { min-height:340px; }.map-stage { display:block;min-height:300px;max-height:50dvh; }.map-sheet { min-width:560px; }.map-sidebar { min-height:280px;max-height:560px; }.map-target-list { max-height:240px;flex:auto; }.map-filters { padding:12px; } }
</style>
