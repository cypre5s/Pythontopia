import Phaser from "phaser";
import { fromWorldCanvas } from "../pointerBoundary";
import {
  getWorldMap,
  mapCameraBounds,
  mapRenderBounds,
  mapTargets,
  portalAvailable,
  worldConfig,
} from "../config/worldConfig";
import { drawTileLayers, mapTextureUrls } from "../config/worldRender";
import { textureKey } from "./assets";
import type { WorldCallbacks, WorldSceneSurface, WorldState } from "./types";
export function buildWorld(
  scene: WorldSceneSurface,
  state: WorldState,
  callbacks: WorldCallbacks,
) {
  scene.tweens.killAll();
  scene.busy = false;
  scene.children.removeAll(true);
  scene.activeNpcs = [];
  scene.objects = [];
  scene.lastHint = "";
  callbacks.hint("");
  const config = getWorldMap(scene.mapKey);
  const indoor = mapCameraBounds(config) === null;
  scene.width = config.width;
  scene.height = config.height;
  scene.objects = mapTargets(scene.mapKey, state.runtime).flatMap(
    (target) =>
      target.points?.map((point) => ({ ...target, ...point })) ?? [target],
  );
  const data = scene.renderMaps.get(scene.mapKey);
  if (!data) throw new Error("地图素材尚未准备好。");
  const bounds = mapRenderBounds(config);
  const groundKey = `${scene.mapKey}-ground`;
  if (!scene.textures.exists(groundKey)) {
    const ground = scene.textures.createCanvas(
      groundKey,
      bounds.width * 16,
      bounds.height * 16,
    );
    if (!ground) throw new Error("无法创建地图纹理");
    const context = ground.getContext();
    context.translate(-bounds.x * 16, -bounds.y * 16);
    const images = new Map(
      mapTextureUrls(data).map((url) => [
        url,
        scene.textures
          .get(textureKey(url))
          .getSourceImage() as HTMLImageElement,
      ]),
    );
    drawTileLayers(context, data, images);
    ground.refresh();
  }
  scene.add
    .image(bounds.x * 16, bounds.y * 16, groundKey)
    .setOrigin(0)
    .setDepth(-1000);
  scene.collision = data.collision;
  for (const prop of data.props) {
    const key = textureKey(prop.textureUrl),
      texture = scene.textures.get(key);
    const frameKey = prop.frame
      ? `crop:${prop.frame.x}:${prop.frame.y}:${prop.frame.width}:${prop.frame.height}`
      : undefined;
    if (prop.frame && frameKey && !texture.has(frameKey))
      texture.add(
        frameKey,
        0,
        prop.frame.x,
        prop.frame.y,
        prop.frame.width,
        prop.frame.height,
      );
    const image = scene.add
      .image(prop.x, prop.y, key, frameKey)
      .setOrigin(0.5, 1)
      .setDepth(prop.depth);
    const building = config.buildings.find(
      (item) => item.object_name === prop.name,
    );
    const authored = state.runtime?.interactions?.find(
      (item) =>
        item.map_key === scene.mapKey &&
        "object_id" in item.target &&
        [
          prop.name,
          prop.interactionId,
          building?.id,
          building?.portal_id,
        ].includes(item.target.object_id),
    );
    if (authored) {
      image.setInteractive({ useHandCursor: true });
      image.on(
        "pointerdown",
        fromWorldCanvas(scene.game.canvas, () => scene.approach(authored.id)),
      );
    } else if (building) {
      image.setInteractive({ useHandCursor: true });
      image.on(
        "pointerdown",
        fromWorldCanvas(scene.game.canvas, () => scene.approach(building.id)),
      );
    } else if (prop.interactionId && !state.runtime?.interactions) {
      image.setInteractive({ useHandCursor: true });
      image.on(
        "pointerdown",
        fromWorldCanvas(scene.game.canvas, () =>
          scene.approach(prop.interactionId!, {
            x: Math.floor(prop.x / 16),
            y: Math.floor(prop.y / 16),
          }),
        ),
      );
    }
    if (building) {
      const label = scene.add
        .text(prop.x, prop.y - image.height - 3, building.label, {
          fontSize: "8px",
          fontFamily: "sans-serif",
          resolution: 3,
          color: "#fff2d1",
          stroke: "#233a2b",
          strokeThickness: 2,
        })
        .setOrigin(0.5, 1)
        .setDepth(80)
        .setInteractive({ useHandCursor: true });
      label.on(
        "pointerdown",
        fromWorldCanvas(scene.game.canvas, () =>
          scene.approach(authored?.id ?? building.id),
        ),
      );
    }
  }
  for (const interaction of state.runtime?.interactions ?? []) {
    if (
      interaction.map_key !== scene.mapKey ||
      !("region" in interaction.target)
    )
      continue;
    const region = interaction.target.region;
    scene.add
      .rectangle(
        region.x * 16,
        region.y * 16,
        region.width * 16,
        region.height * 16,
        0,
        0,
      )
      .setOrigin(0)
      .setDepth(-1)
      .setInteractive({ useHandCursor: true })
      .on(
        "pointerdown",
        fromWorldCanvas(scene.game.canvas, () =>
          scene.approach(interaction.id),
        ),
      );
  }
  if (worldConfig.portal_marker && scene.textures.exists("portal-marker")) {
    for (const target of scene.objects.filter((item) => item.portal)) {
      const marker = worldConfig.portal_marker;
      const angle: Record<string, number> = {
        up: 0,
        right: 90,
        down: 180,
        left: 270,
      };
      const arrow = scene.add
        .image(target.x * 16 + 8, target.y * 16 + 8, "portal-marker")
        .setDisplaySize(marker.width, marker.height)
        .setAngle(angle[target.portal!.to.direction] ?? 0)
        .setAlpha(
          portalAvailable(target.portal!, state.flags, state.runtime)
            ? 0.92
            : 0.55,
        )
        .setDepth(target.y + 4);
      // Enlarge only the invisible hit area; the source artwork remains a small unobtrusive chevron.
      arrow.setInteractive({
        useHandCursor: true,
        hitArea: new Phaser.Geom.Rectangle(
          -arrow.width / 2,
          -arrow.height / 2,
          arrow.width * 2,
          arrow.height * 2,
        ),
        hitAreaCallback: Phaser.Geom.Rectangle.Contains,
      });
      arrow.on(
        "pointerdown",
        fromWorldCanvas(scene.game.canvas, () => scene.approach(target.id)),
      );
    }
  }
  const available = state.npcs.filter((n) => n.map_key === scene.mapKey);
  available.forEach((npc) => {
    const x = npc.tile_x,
      y = npc.tile_y;
    const texture = textureKey(npc.sprite.url);
    if (!scene.textures.exists(texture)) return;
    const frame =
      Math.max(0, ["down", "left", "right", "up"].indexOf(npc.direction)) * 3 +
      1;
    const sprite = scene.add
      .sprite(x * 16 + 8, y * 16 + 12, texture, frame)
      .setOrigin(0.5, 1)
      .setDepth(y + 3)
      .setInteractive({ useHandCursor: true });
    const label = scene.add
      .text(x * 16 + 8, y * 16 - 12, npc.name, {
        fontSize: "9px",
        fontFamily: "sans-serif",
        resolution: 3,
        color: "#fff6d4",
        backgroundColor: "#1e3927bb",
        padding: { x: 3, y: 2 },
      })
      .setOrigin(0.5, 1)
      .setDepth(90);
    sprite.on(
      "pointerdown",
      fromWorldCanvas(scene.game.canvas, () => scene.approach(npc.key)),
    );
    scene.activeNpcs.push({ npc, sprite, label, homeX: x, homeY: y });
  });
  scene.player = scene.add
    .sprite(scene.tileX * 16 + 8, scene.tileY * 16 + 12, "chara_0", 1)
    .setOrigin(0.5, 1)
    .setDepth(scene.tileY + 3);
  callbacks.mapPosition?.(scene.mapPosition());
  for (const name of ["chara_0"])
    for (let d = 0; d < 4; d++) {
      const key = `${name}-${d}`;
      if (!scene.anims.exists(key))
        scene.anims.create({
          key,
          frames: scene.anims.generateFrameNumbers(name, {
            start: d * 3,
            end: d * 3 + 2,
          }),
          frameRate: 8,
          repeat: -1,
        });
    }
  scene.shade = scene.add
    .rectangle(
      bounds.x * 16,
      bounds.y * 16,
      bounds.width * 16,
      bounds.height * 16,
      0x14204f,
      0.0,
    )
    .setOrigin(0)
    .setDepth(100)
    .setBlendMode(Phaser.BlendModes.MULTIPLY);
  scene.weather = scene.add.graphics().setDepth(101);
  scene.cameras.main.removeBounds();
  scene.cameras.main.setBackgroundColor(indoor ? "#142019" : "#324c36");
  scene.cameras.main.startFollow(
    scene.player,
    true,
    indoor ? 1 : 0.18,
    indoor ? 1 : 0.18,
    0,
    10,
  );
  scene.resize();
}
