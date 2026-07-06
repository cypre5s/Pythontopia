# Low Fence Path And Demo Offline Design

## Goal

Make the jump module feel like a polished game feature instead of a hidden mechanic:
dedicated low fence sprites mark jumpable path edges, and local demo mode no longer
shows a blocking backend-offline toast over the game.

## Design

- Download CC0 16x16 fence sprites and keep source copies under `assets/inbox/`.
- Load the fence sheet as a Phaser spritesheet key `low_fence_16`.
- Store each jumpable obstacle as a Tiled `low_obstacle` object with `sprite` and
  `frame` properties, then render it from the object layer in `WorldScene`.
- In `town_hub`, place a small fenced route around the main south path while keeping
  the walking lane clear. The fence tiles are still low obstacles, so the player can
  hop over them with Space.
- In dev mode, suppress only `NETWORK_ERROR` global toasts by default. Keep timeout
  toasts and production network notices. Developers can opt back in with
  `VITE_SHOW_BACKEND_OFFLINE_TOAST=true`.

## Verification

- Asset manifest test asserts the dedicated spritesheet is preloaded and exists.
- Generated-map test asserts hub low obstacles have sprite metadata and form a path.
- Network policy test asserts dev-mode backend-offline toast suppression.
