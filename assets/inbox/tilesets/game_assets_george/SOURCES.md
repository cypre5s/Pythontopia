# 16x16 Game Assets (George_)

- **Source URL**: https://opengameart.org/content/16x16-game-assets
- **Author**: George_ (OpenGameArt user `george` — https://opengameart.org/users/george)
- **License**: CC-BY 4.0
  - License URL: https://creativecommons.org/licenses/by/4.0/
- **Download time (UTC)**: 2026-05-21T15:18Z
- **Downloader**: Pythontopia R-100 tileset crawler subagent #1 via HTTPS_PROXY=http://127.0.0.1:7892
- **Original archives**:
  - https://opengameart.org/sites/default/files/tilesets_0.zip — 10 PNG tile sheets
  - https://opengameart.org/sites/default/files/Base%20and%20Items.zip — items + base sprites
- **Coverage**: farm fields, dirt/grass terrain, fences, paths, indoor floors, decorative props, item icons; 16x16 cells

## File list (curated)

Original archive `tilesets_0.zip` is shipped as ten thematic sheets `1.png … 10.png`:

| File | Resolution | Likely theme (from inspection) |
| --- | --- | --- |
| `1.png` | 480 x 768 | Outdoor terrain master sheet (grass / dirt / sand / water transitions) |
| `2.png` | 320 x 352 | Farm fields and plowed dirt |
| `3.png` | 480 x 128 | Path / road / fence segments |
| `4.png` | 480 x 448 | Indoor wood / stone floor + walls |
| `5.png` | 288 x 320 | Town storefront / sign decorations |
| `6.png` | 288 x 192 | Forest props (trees, bushes, stones) |
| `7.png` | 560 x 192 | Decorative furniture row |
| `8.png` | 448 x 362 | Mixed crops & garden plants |
| `9.png` | 288 x 256 | Cave / mine interior |
| `10.png` | 224 x 320 | Misc objects (barrels, crates, sacks) |

From `Base and Items.zip` (curated):

| File | Resolution | Notes |
| --- | --- | --- |
| `Items.png` | 256 x 32 | Pickable item icons (potion, key, coin, gem strip) — kept as a tileset row |

Original archive contents that were intentionally **omitted** under the subagent boundary:

- `Base and Items.zip / Player Base.png` — character base sprite (handed off to `characters` subagent)

Total kept: 11 PNGs, 168 KB on disk. Original zips kept under `.work/assets_dl/tilesets/game_assets_george/` for traceability.

## Attribution snippet (please reuse when shipping)

```
"16x16 Game Assets" by George_
https://opengameart.org/content/16x16-game-assets
Licensed under CC-BY 4.0 — https://creativecommons.org/licenses/by/4.0/
```

## Boundary notes

- The page also credits rubberduck as a referenced contributor; the sheets themselves are George_'s release.
- Theme labels in the table above are first-pass visual guesses from `file(1)` dimensions — designers should re-verify when slicing into Tiled or Phaser atlases.
- No Stardew Valley official assets are included.
