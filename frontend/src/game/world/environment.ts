import { getWorldMap, mapCameraBounds } from "../config/worldConfig";
import type { WorldSceneSurface, WorldState } from "./types";

export function updateEnvironment(
  scene: WorldSceneSurface,
  state: WorldState,
  time: number,
) {
  const offset = scene.cameras.main.followOffset;
  if (mapCameraBounds(getWorldMap(scene.mapKey)) === null) {
    offset.set(0, 10);
  } else if (state.drifting) {
    if (!state.driftStart) state.driftStart = time;
    const t = (time - state.driftStart) / 1000;
    const easeIn = Math.min(1, t / 1.2);
    offset.x = Math.sin(t * 0.38) * 18 * easeIn;
    offset.y = 10 + Math.sin(t * 0.27) * 10 * easeIn;
  } else {
    offset.x *= 0.88;
    offset.y = 10 + (offset.y - 10) * 0.88;
    if (Math.abs(offset.x) < 0.01) offset.x = 0;
    if (Math.abs(offset.y - 10) < 0.01) offset.y = 10;
  }
  if (time - scene.saveTimer > 1500) {
    scene.saveTimer = time;
    scene.save();
  }

  scene.weather.clear();
  if (scene.mapKey === "town_hub" && new Date().getDate() % 3 === 0) {
    scene.weather.lineStyle(1, 0xa4ced3, 0.16);
    for (let i = 0; i < 36; i++) {
      const x = (i * 47 + time * 0.016) % (scene.width * 16),
        y = (i * 83 + time * 0.08) % (scene.height * 16);
      scene.weather.lineBetween(x, y, x - 2, y + 7);
    }
  }
  const hour = new Date().getHours(),
    night = hour >= 19 || hour < 6;
  scene.shade.setAlpha(night ? 0.25 : 0);
}
