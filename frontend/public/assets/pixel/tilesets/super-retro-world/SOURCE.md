# Retained Super Retro World source image

`farming_source.png` is an unmodified copy of the user-provided
`D:\godot-one\Tilesets\SuperRetroWorld_Farming_Tileset.png` (1792 × 1280).
Only this original atlas, one original door spritesheet and the separately recorded portal icon were added;
the source package was not copied wholesale.

The user confirmed on 2026-11-05 that the supplied materials had been purchased
and could be used in this project. This records that user confirmation; no
original receipt or source-package license file was found locally or invented.

Author/product reference: [Gif — Super Retro World: Farming](https://store.godotengine.org/asset/gif/super-retro-world/).
The store links its [current custom license](https://storage.rpgpowerforge.com/super-retro-world.license).
These current pages identify the source; they are not a claim about which terms
accompanied the user's earlier download. Existing root attribution records remain applicable.

Library frame coordinates were selected by visual inspection of the original
16-pixel grid, then stored in `maps/library.json` and `scripts/generate-library.mjs`.
No PNG was cropped, recolored or recomposed.

| Use | Source rectangle (x, y, width, height) |
| --- | --- |
| Wood floor | tile column 1, row 43 (16 × 16) |
| Brick wall top/body/base | columns 65–67, rows 25–27 (16 × 16 tiles) |
| Wooden bookcase | 1696, 576, 32, 32 |
| Colored book rows | 1696, 560, 32, 16; arranged over the wooden bookcase as map objects |
| Librarian desk with open book | 1600, 416, 32, 32 |
| Wide reading tables, complete top and legs | 1616, 520, 48, 36 |
| Independent short wooden stools, complete legs | 1552, 512, 16, 16 |

`props/super-retro-world/library/door-source.png` is an unmodified copy of
`D:\godot-one\Props\Door\door_0_16x16.png`; the map uses its first 16 × 16 frame
in the south wall, aligned with the existing exit at (9,12).

The library remains 18 × 14. Its entrance (9,11), exit (9,12), librarian (9,5)
and historical shelf point (4,4) are unchanged and reachable. The current layout
has six wall bookcases and two aligned pairs in the book zones, two wide reading
tables with four independent short wooden stools, and a centered service desk.
The table and stool pixels have a visible gap; each table occupies a 3 × 2 tile
collision footprint and each stool has its own blocked tile. Source alpha
boundaries were inspected to preserve the complete table top and furniture legs.
The large rug and isolated entrance table were removed.

All ten bookcase bases bind the same `library_shelf` interaction. Each has a real
front point in `data/world/world.json`; the original (4,4) point remains among them.
Book rows are decorative layers over the case, not extra interaction targets.
Collision uses one dedicated tile layer. The two-row north-wall art has one thin
blocked foot row and an open floor corridor at y=2. Every historical interior
position is either connected to the entrance or can step from a new furniture
footprint into that connected floor area; no saved progress/position is reset.

The outdoor `landscape` and `landscape_trees` layers reuse the existing retained
SRW atlas and tree PNGs. Their render-only extent is (-18,-14,86,68); the original
50 × 40 town art, objects and collision layer remain unchanged.
