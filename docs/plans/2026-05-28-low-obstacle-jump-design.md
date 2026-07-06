# Low Obstacle Jump Design

## Goal

Add a mature-feeling jump module for the Web world: the player can press Space to jump across one-tile low obstacles such as fences, logs, and low barriers. Jumping must feel intentional, respect collision, and not replace normal walking or NPC interaction.

## Rules

- Space is jump. F and E remain interaction keys.
- A jump uses the player's current facing direction.
- If the tile directly ahead is a low obstacle and the tile beyond it is walkable, the player jumps to the landing tile.
- If the landing tile is blocked, outside the map, or another low obstacle, the player does not move.
- Jumping over non-low blocked tiles is not allowed.
- While jumping, walking input is ignored to avoid overlapping tweens.

## Components

- `input.ts`: expose `isJumpPressed()` on the existing direction controller.
- `JumpSystem.ts`: pure resolver for low-obstacle jumps, tested without Phaser.
- `sprite.ts`: add `jumpToTile()` to the player handle with a short arc tween and landing frame reset.
- `WorldScene.ts` and `TownScene.ts`: wire Space to the resolver, update the world store only on successful landing.
- `generate-maps.mjs`: add `low_obstacle` objects and visible decoration tiles to the generated maps.
- `BootScene.ts` and license docs: load and document CC0 Kenney jump/landing SFX.

## Verification

Automated tests cover input separation, jump resolution, generated maps, player handle behavior, and dialog black text. Typecheck and the full frontend test suite must pass. A local browser smoke check should verify the world still renders and dialog text remains black.
