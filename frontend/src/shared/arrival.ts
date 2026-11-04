import { ref } from 'vue';
export interface ArrivalCamera { x: number; y: number; zoom: number; map: string }
export const arrivalActive = ref(false);
export const arrivalCamera = ref<ArrivalCamera | null>(null);
export const arrivalFinished = ref(false);
export const backdropReady = ref(false);
export const worldAssetsReady = ref(false);
export const resourceError = ref("");
let preload: Promise<void> | undefined;
export function preloadWorld() {
  return preload ??= Promise.all([
    import('../features/WorldPage.vue'),
    import('../game/adapters/world'),
    ...['chara_0','chara_2','chara_4','chara_6','chara_10'].map(key => new Promise<void>((resolve,reject)=>{
      const image = new Image(); image.onload=()=>resolve(); image.onerror=()=>reject(new Error('角色资源加载失败，请重试')); image.src=`/assets/pixel/sprites/super-retro-world/characters/${key}.png`;
    })),
  ]).then(()=>{ worldAssetsReady.value=true; }).catch(error=>{preload=undefined; throw error;});
}
export function startArrival() { arrivalCamera.value=null; arrivalFinished.value=false; arrivalActive.value=true; }
export function endArrival() { arrivalFinished.value=true; arrivalActive.value=false; arrivalCamera.value=null; }
