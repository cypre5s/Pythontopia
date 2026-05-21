# Pythontopia Tileset Inbox — MANIFEST

Curated by Pythontopia R-100 tileset crawler subagent #1 on **2026-05-21T15:19Z (UTC)**.

This directory holds raw, license-cleared tileset PNGs ready for slicing / re-cutting by
the art pipeline. Every set ships with a `SOURCES.md` containing exact URLs, authors,
licenses, download time, and per-file notes. Every license below is one of
**CC0 1.0** or **CC-BY 4.0** — no CC-BY-SA / GPL / unknown-license assets were imported.

> Sister inboxes that already exist and are **not duplicated** here:
>
> - `../kenney_tilesets/` (286 files, Kenney CC0)
> - `../kenney_tilemaps/` (2813 files, Kenney CC0)
> - `../sprout_lands/` (78 files, Cup Nooble — separate license note in that folder)

## Sets shipped in this batch

| # | Folder | Source | Author | License | Files | Disk | Theme coverage |
| - | --- | --- | --- | --- | ---:| ---:| --- |
| 1 | `tiny_16_basic/` | https://opengameart.org/content/tiny-16-basic | Sharm | **CC-BY 4.0** | 5 | 47 KB | overworld, town, dungeon, road, fences, props (16x16) |
| 2 | `zelda_like_armm1998/` | https://opengameart.org/content/zelda-like-tilesets-and-sprites | ArMM1998 | **CC0 1.0** | 6 | 188 KB | overworld grass / water, indoor floors, cave, village props (16x16) |
| 3 | `castle_rubberduck/` | https://opengameart.org/content/pixel-art-castle-tileset | rubberduck | **CC0 1.0** | 4 | 192 KB | town / castle stonework, indoor stone floor, walls, decor |
| 4 | `game_assets_george/` | https://opengameart.org/content/16x16-game-assets | George_ | **CC-BY 4.0** | 11 | 168 KB | farm field, dirt/grass terrain, paths, indoor floor, props, item strip (16x16) |
|   | **Total** | | | | **26** | **~600 KB** | farm + grass + town + road + interior floor + cave/mine |

All 4 sets are confirmed compatible with the Pythontopia "education-first pixel town"
art constraint: original artwork, no Stardew Valley official assets reused, no
unconfirmed-license assets bundled.

## Theme matrix vs. Pythontopia map needs

| Map zone (per AGENTS.md) | Best sets to mine |
| --- | --- |
| Farm fields (crops, plow rows, irrigation) | `game_assets_george/2.png`, `game_assets_george/8.png`, `tiny_16_basic/basic.png` |
| Grass / overworld background | `zelda_like_armm1998/Overworld.png`, `tiny_16_basic/overworld.png`, `game_assets_george/1.png` |
| Town square (storefront, signs, fence) | `tiny_16_basic/overworld.png`, `zelda_like_armm1998/Overworld.png`, `game_assets_george/5.png`, `castle_rubberduck/*` |
| Roads / dirt paths | `tiny_16_basic/basic.png`, `game_assets_george/3.png`, `zelda_like_armm1998/Overworld.png` |
| Indoor floor / classroom interior | `zelda_like_armm1998/Inner.png`, `tiny_16_basic/basictiles_2.png`, `game_assets_george/4.png`, `castle_rubberduck/castle_tileset_part1.png` |
| Bug mines / cave events | `tiny_16_basic/dungeon.png`, `zelda_like_armm1998/cave.png`, `game_assets_george/9.png` |
| Props (barrels, signs, chests) | `tiny_16_basic/things_0.png`, `zelda_like_armm1998/objects.png`, `castle_rubberduck/castle_tileset_part3.png`, `game_assets_george/10.png`, `game_assets_george/Items.png` |

## Subagent boundary log

Explicitly **excluded** during curation to respect other subagents' scope:

| Type | Skipped files | Owner subagent |
| --- | --- | --- |
| Fonts | `tiny_16_basic/fontlarge.png`, `zelda_like_armm1998/gfx/font.png` | `fonts` agent |
| Characters | `tiny_16_basic/characters_1.png`, `tiny_16_basic/dead_1.png`, `zelda_like_armm1998/gfx/character.png`, `zelda_like_armm1998/gfx/NPC_test.png`, `game_assets_george/Player Base.png` | `characters` agent |
| Standalone building sprites | (none in this batch — Castle set is a true tileset, not a single building sprite) | `buildings` agent |
| Icons (UI) | (none in this batch) | `icons` agent |

No files ≥ 50 MB were encountered, so no skip-by-size events were triggered.

## Provenance / raw downloads

Raw downloaded archives (`gfx_3.zip`, `tilesets_0.zip`, `Base and Items.zip`) are
preserved in `/home/cypress/Pythontopia/.work/assets_dl/tilesets/` so the pipeline can
re-extract assets without re-hitting the network. All HTTP requests went through the
mandated proxy `http://127.0.0.1:7892`. No failed downloads, no integrity errors:
`file(1)` confirmed every kept artifact is a valid PNG or Zip.

## License obligations cheat-sheet

- **CC0** sets (`zelda_like_armm1998`, `castle_rubberduck`): no legal attribution
  required. Pythontopia credits the authors anyway in shipped `CREDITS.md`.
- **CC-BY 4.0** sets (`tiny_16_basic`, `game_assets_george`): must keep attribution.
  Suggested credit lines are in each set's `SOURCES.md` under "Attribution snippet".

Designers/integrators: when slicing these PNGs into Phaser/Tiled atlases, please copy
the attribution snippet into the final `assets/CREDITS.md` and keep the per-set
`SOURCES.md` next to any redistributed cut.
