<template>
  <div class="town-backdrop" :class="{ still: preferences.reducedMotion, travelling: arrivalActive }" ref="backdrop" aria-hidden="true">
    <canvas ref="canvas" :width="bounds.width * 16" :height="bounds.height * 16" :style="tourSize" />
    <div class="town-backdrop-shade" />
  </div>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { getWorldMap, mapRenderBounds, worldConfig } from '../game/config/worldConfig';
import { paintMapPreview } from '../game/config/worldRender';
import { useUiPreferences } from '../shared/uiPreferences';
import { arrivalActive, arrivalCamera, endArrival, backdropReady, resourceError } from "../shared/arrival";
const backdrop = ref<HTMLElement>();
let flight: Animation | undefined;
let reveal: Animation | undefined;
const preferences = useUiPreferences();
const canvas = ref<HTMLCanvasElement>();
const controller = new AbortController();
const mapKey = worldConfig.default_spawn.map_key;
const bounds = mapRenderBounds(getWorldMap(mapKey));
const tourSize = {
  width: `max(145vw, ${145 * bounds.width / bounds.height}vh)`,
  height: `max(${145 * bounds.height / bounds.width}vw, 145vh)`,
};
onMounted(async () => {
  backdropReady.value = false;
  try {
    if (!canvas.value) return;
    await paintMapPreview(canvas.value, mapKey, controller.signal);
    if (!controller.signal.aborted) backdropReady.value = true;
  } catch { if (!controller.signal.aborted) resourceError.value = "地图资源加载失败，请刷新重试。"; }
});
watch([arrivalCamera, backdropReady], async ([view, ready]) => {
  if (!view || !ready || !canvas.value || !backdrop.value) return;
  const rect = canvas.value.getBoundingClientRect();
  canvas.value.style.animation = 'none';
  canvas.value.style.width = `${rect.width}px`;
  canvas.value.style.height = `${rect.height}px`;
  canvas.value.style.transform = `translate(${rect.x}px, ${rect.y}px)`;
  const duration = preferences.reducedMotion ? 0 : 1000;
  try {
    if (view.map === mapKey) {
      flight = canvas.value.animate([
        {width:`${rect.width}px`,height:`${rect.height}px`,transform:`translate(${rect.x}px, ${rect.y}px)`},
        {width:`${bounds.width*16*view.zoom}px`,height:`${bounds.height*16*view.zoom}px`,transform:`translate(${(bounds.x*16-view.x)*view.zoom}px, ${(bounds.y*16-view.y)*view.zoom}px)`},
      ], {duration,easing:'cubic-bezier(.25,.8,.25,1)',fill:'forwards'});
      await flight.finished;
    }
    reveal = backdrop.value.animate([{opacity:1},{opacity:0}],{duration:preferences.reducedMotion?0:180,fill:'forwards'});
    await reveal.finished;
    endArrival();
  } catch { /* An interrupted route cancels its visual handoff. */ }
});
onBeforeUnmount(()=>{controller.abort(); flight?.cancel(); reveal?.cancel(); backdropReady.value = false;});
</script>
<style scoped>
.town-backdrop { position: fixed; inset: 0; overflow: hidden; background: #253e30; pointer-events: none; }
.town-backdrop.travelling { z-index: 40; }
.town-backdrop canvas { position: absolute; max-width: none; image-rendering: pixelated; animation: town-tour 65s ease-in-out infinite; }
.town-backdrop-shade { position: absolute; inset: 0; background: linear-gradient(90deg,#10251b25,#10251b20 40%,#0b1c2670),linear-gradient(0deg,#10251b99,transparent 35%); }
@keyframes town-tour { 0%,100% { transform: translate(-6%, -9%); } 33% { transform: translate(-24%, -4%); } 66% { transform: translate(-15%, -26%); } }
.still canvas { animation: none; transform: translate(-15%, -15%); }
@media(prefers-reduced-motion:reduce) { .town-backdrop.travelling { z-index: 40; }
.town-backdrop canvas { animation: none; } }
</style>
