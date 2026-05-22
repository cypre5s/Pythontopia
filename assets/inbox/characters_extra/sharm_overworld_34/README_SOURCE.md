# Sharm 3/4 Overworld RPG Sprites — README

> **Source slug:** `sharm_overworld_34/`
> **Upstream URL:** https://opengameart.org/content/a-load-of-overworld-34-rpg-sprites
> **Pythontopia role candidates:** generic townsfolk / villager NPCs visible in the Pythontopia plaza.

## File

| File | Pixel size | Mode | Cell candidate | Layout | Notes |
|------|------------|------|----------------|--------|-------|
| `batch_one.png` | 486×706 | RGB (transparent key — see LICENSE.md) | 32×48 (estimated) | grid of ~15 cols × ~14 rows of 32×48 cells | Multiple NPC characters in 3/4 overworld view; each occupies a 3-col × 4-row patch (idle + 2-frame walk × 4 dirs) like the rest of the Sharm 3/4 catalogue. |

The "3/4 view" perspective is the standard JRPG / Zelda angle (camera tilted ~30° down rather than perfectly top-down) and matches the rest of Pythontopia's tilesets in `assets/inbox/tilesets/`.

## Pythontopia role mapping

This sheet is a "big bag of NPCs". Inside `batch_one.png` we can see (visual inspection of the source PNG in any image viewer; do that during the integration step):

- Generic townsfolk in casual clothes — **classroom student NPCs, plaza visitors**.
- Lightly armoured figures — **mine-trail walker, guard NPC**.
- Robed / hooded figure — fallback **teacher NPC** if the LPC wulax robe layer is too tall.

When cutting characters out of `batch_one.png`, save each as a separate file under `assets/inbox/characters_extra/sharm_overworld_34/cuts/<role>_<idx>.png` (this `cuts/` subfolder is intentionally NOT pre-created; downstream agents do it during integration).

## Frame extraction guidance

```
# Each character occupies a 3×4 cell grid:
#   col 0  col 1  col 2
# r0 walkL idle  walkR    (facing down)
# r1 walkL idle  walkR    (facing left)
# r2 walkL idle  walkR    (facing right)
# r3 walkL idle  walkR    (facing up)
# cell size is approximately 32×48 px (verify by counting pixels of the alpha key
# in the actual image before integration)
```

Pre-integration this remains a single 486×706 PNG so the original layout is preserved for traceability.
