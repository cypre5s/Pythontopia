<template>
  <div class="town-backdrop" :class="{ still: preferences.reducedMotion, travelling: arrivalActive }" ref="backdrop" aria-hidden="true">
    <canvas ref="canvas" />
    <div class="town-backdrop-shade" />
  </div>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { SRW_HOUSE_KEYS, SRW_PROP_GROUPS, SRW_TILESET_URL } from '../game/config/srwAssets.config';
import { useUiPreferences } from '../shared/uiPreferences';
import { arrivalActive, arrivalCamera, endArrival, backdropReady, resourceError } from "../shared/arrival";
const backdrop = ref<HTMLElement>();
let flight: Animation | undefined;
let reveal: Animation | undefined;
const preferences = useUiPreferences();
const canvas = ref<HTMLCanvasElement>();
const controller = new AbortController();
interface MapObject { x: number; y: number; width: number; height: number; properties?: {name: string; value: unknown}[] }
interface TownMap { width: number; height: number; layers: {name: string; type: string; data?: number[]; objects?: MapObject[]}[] }
const loadImage = (src: string) => new Promise<HTMLImageElement>((resolve, reject) => {
  const image = new Image(); image.onload = () => resolve(image); image.onerror = reject; image.src = src;
});
onMounted(async () => {
  backdropReady.value = false;
  try {
    const response = await fetch('/assets/maps/town_hub.json', { signal: controller.signal });
    if (!response.ok) throw new Error('Map unavailable');
    const map: TownMap = await response.json();
    const tiles = await loadImage(SRW_TILESET_URL);
    const paths = new Map<string, string>();
    SRW_HOUSE_KEYS.forEach(key => paths.set(`srw_${key}`, `/assets/pixel/buildings/super-retro-world/${key}.png`));
    SRW_PROP_GROUPS.forEach(group => group.stems.forEach(key => paths.set(`srw_${key}`, `/assets/pixel/props/super-retro-world/${group.dir}/${key}.png`)));
    const objects = map.layers.flatMap(layer => layer.objects ?? []).sort((a,b) => a.y+a.height-b.y-b.height);
    const used = [...new Set(objects.map(item => String(item.properties?.find(p=>p.name==='sprite')?.value ?? '')))];
    const images = new Map(await Promise.all(used.filter(key=>paths.has(key)).map(async key => [key, await loadImage(paths.get(key)!)] as const)));
    if (controller.signal.aborted || !canvas.value) return;
    canvas.value.width = map.width * 16; canvas.value.height = map.height * 16;
    const ctx = canvas.value.getContext('2d'); if (!ctx) return;
    ctx.imageSmoothingEnabled = false;
    for (const layer of map.layers) {
      if (layer.type !== 'tilelayer' || layer.name === 'collision') continue;
      layer.data?.forEach((gid, i) => { if (!gid) return; const tile = gid - 1; ctx.drawImage(tiles, tile%32*16, Math.floor(tile/32)*16,16,16,i%map.width*16,Math.floor(i/map.width)*16,16,16); });
    }
    for (const item of objects) {
      const img = images.get(String(item.properties?.find(p=>p.name==='sprite')?.value ?? ''));
      if (img) ctx.drawImage(img, item.x+item.width/2-img.width/2,item.y+item.height-img.height);
    }
    backdropReady.value = true;
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
    if (view.map === 'town_hub') {
      flight = canvas.value.animate([
        {width:`${rect.width}px`,height:`${rect.height}px`,transform:`translate(${rect.x}px, ${rect.y}px)`},
        {width:`${800*view.zoom}px`,height:`${640*view.zoom}px`,transform:`translate(${-view.x*view.zoom}px, ${-view.y*view.zoom}px)`},
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
.town-backdrop canvas { position: absolute; width: max(145vw, 181.25vh); height: max(116vw, 145vh); max-width: none; image-rendering: pixelated; animation: town-tour 65s ease-in-out infinite; }
.town-backdrop-shade { position: absolute; inset: 0; background: linear-gradient(90deg,#10251b25,#10251b20 40%,#0b1c2670),linear-gradient(0deg,#10251b99,transparent 35%); }
@keyframes town-tour { 0%,100% { transform: translate(-6%, -9%); } 33% { transform: translate(-24%, -4%); } 66% { transform: translate(-15%, -26%); } }
.still canvas { animation: none; transform: translate(-15%, -15%); }
@media(prefers-reduced-motion:reduce) { .town-backdrop.travelling { z-index: 40; }
.town-backdrop canvas { animation: none; } }
</style>
