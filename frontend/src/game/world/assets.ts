import { api } from "../../shared/api";
import { worldConfig } from "../config/worldConfig";
import { mapTextureUrls, parseRenderMap } from "../config/worldRender";
import { SRW_CHARA_FRAME } from "../config/srwAssets.config";
import type Phaser from "phaser";
import type { WorldCallbacks, WorldSceneSurface, WorldState } from "./types";
export const textureKey = (url: string) => `world-image:${url}`;
export function preloadWorld(
  scene: WorldSceneSurface,
  state: WorldState,
  callbacks: WorldCallbacks,
) {
  if (worldConfig.portal_marker)
    scene.load.image("portal-marker", worldConfig.portal_marker.asset);
  scene.load.spritesheet(
    "chara_0",
    "/assets/pixel/sprites/super-retro-world/characters/chara_0.png",
    SRW_CHARA_FRAME,
  );
  queueCharacterSprites(scene, state);
  scene.load.on("progress", (n: number) => callbacks.progress(n));
  scene.load.on("loaderror", (file: Phaser.Loader.File) =>
    callbacks.error(`素材未能加载：${file.key}`),
  );
  for (const [mapKey, config] of Object.entries(worldConfig.maps)) {
    if (config.renderer !== "tiled" || !config.asset) continue;
    const cacheKey = `world-map-${mapKey}`;
    scene.load.json(cacheKey, config.asset);
    scene.load.once(`filecomplete-json-${cacheKey}`, () => {
      const map = parseRenderMap(scene.cache.json.get(cacheKey), config.asset!);
      scene.renderMaps.set(mapKey, map);
      for (const url of mapTextureUrls(map)) {
        if (!scene.queuedTextures.has(url)) {
          scene.queuedTextures.add(url);
          scene.load.image(textureKey(url), url);
        }
      }
    });
  }
}
export function queueCharacterSprites(
  scene: WorldSceneSurface,
  state: WorldState,
) {
  let queued = false;
  for (const npc of state.npcs) {
    const key = textureKey(npc.sprite.url);
    if (scene.textures.exists(key) || scene.queuedTextures.has(key)) continue;
    scene.queuedTextures.add(key);
    scene.load.spritesheet({
      key,
      url: npc.sprite.url,
      frameConfig: {
        frameWidth: npc.sprite.frame_width,
        frameHeight: npc.sprite.frame_height,
      },
      xhrSettings: npc.sprite.url.startsWith("/api/")
        ? {
            responseType: "blob",
            headers: { Authorization: `Bearer ${api.token() ?? ""}` },
          }
        : undefined,
    });
    queued = true;
  }
  return queued;
}
