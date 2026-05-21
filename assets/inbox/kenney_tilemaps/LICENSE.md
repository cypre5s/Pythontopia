# Kenney Tilemap & Icon Asset Bundle - License

This folder contains pixel-art tilemap, prop and icon assets bundled by
**Kenney** ([https://kenney.nl](https://kenney.nl)) and released under
**Creative Commons Zero (CC0 1.0 Universal)**. They are free to use in
personal, educational and commercial projects with no attribution required
(crediting Kenney is appreciated but not mandatory).

License text: <https://creativecommons.org/publicdomain/zero/1.0/>

## Packages (8 in this folder + 2 cross-referenced)

> Tiny Town and Tiny Dungeon are owned by a sibling subagent in
> `assets/inbox/kenney_tilesets/` (downloaded earlier in the same parallel
> batch). To avoid duplication on disk, **the zip and extracted copies for
> those two have been removed from this folder**; they are listed here for
> completeness only and live authoritatively in `kenney_tilesets/` with
> their own LICENSE.md.

| # | Pack | Source page | ZIP URL | Local archive / folder | Original release |
| - | --- | --- | --- | --- | --- |
| 1 | Tiny Town (1.1) ↗ | <https://kenney.nl/assets/tiny-town> | <https://kenney.nl/media/pages/assets/tiny-town/a415fbeb49-1735736916/kenney_tiny-town.zip> | **see `../kenney_tilesets/kenney_tiny-town/`** | 2017 (v1.1) |
| 2 | Tiny Dungeon (1.0) ↗ | <https://kenney.nl/assets/tiny-dungeon> | <https://kenney.nl/media/pages/assets/tiny-dungeon/f8422efb44-1674742415/kenney_tiny-dungeon.zip> | **see `../kenney_tilesets/kenney_tiny-dungeon/`** | 2017 (v1.0) |
| 3 | Tiny Battle (1.0) | <https://kenney.nl/assets/tiny-battle> | <https://kenney.nl/media/pages/assets/tiny-battle/c1c25ac1f3-1691487575/kenney_tiny-battle.zip> | `kenney_tiny-battle.zip` / `tiny-battle/` | 2018 (v1.0) |
| 4 | Roguelike Modern City (2.0) | <https://kenney.nl/assets/roguelike-modern-city> | <https://kenney.nl/media/pages/assets/roguelike-modern-city/0ff3dfff2b-1677694743/kenney_roguelike-modern-city.zip> | `kenney_roguelike-modern-city.zip` / `roguelike-modern-city/` | 2016 (v2.0) |
| 5 | Roguelike Caves & Dungeons | <https://kenney.nl/assets/roguelike-caves-dungeons> | <https://kenney.nl/media/pages/assets/roguelike-caves-dungeons/5195ceb8ca-1677694831/kenney_roguelike-caves-dungeons.zip> | `kenney_roguelike-caves-dungeons.zip` / `roguelike-caves-dungeons/` | 2015 |
| 6 | RPG Urban Pack 1.0 | <https://kenney.nl/assets/rpg-urban-pack> | <https://kenney.nl/media/pages/assets/rpg-urban-pack/0a097d1dc7-1677578575/kenney_rpg-urban-pack.zip> | `kenney_rpg-urban-pack.zip` / `rpg-urban-pack/` | 2015 (v1.0) |
| 7 | 1-Bit Pack (1.2) | <https://kenney.nl/assets/1-bit-pack> | <https://kenney.nl/media/pages/assets/1-bit-pack/aa867a1f37-1677578516/kenney_1-bit-pack.zip> | `kenney_1-bit-pack.zip` / `1-bit-pack/` | 2017 (v1.2) |
| 8 | Pixel Platformer (1.2) | <https://kenney.nl/assets/pixel-platformer> | <https://kenney.nl/media/pages/assets/pixel-platformer/33bb4921eb-1696667883/kenney_pixel-platformer.zip> | `kenney_pixel-platformer.zip` / `pixel-platformer/` | 2022 (v1.2) |
| 9 | Generic Items #1 | <https://kenney.nl/assets/generic-items> | <https://kenney.nl/media/pages/assets/generic-items/96b9087204-1677667000/kenney_generic-items.zip> | `kenney_generic-items.zip` / `generic-items/` | 2014 |
| 10 | Game Icons | <https://kenney.nl/assets/game-icons> | <https://kenney.nl/media/pages/assets/game-icons/1ebf9c14af-1677661579/kenney_game-icons.zip> | `kenney_game-icons.zip` / `game-icons/` | 2013 |

All 10 packs are authored by **Kenney Vleugels (Kenney.nl)** and released
under **CC0 1.0 Universal**. Original `License.txt` files inside each
extracted folder confirm the CC0 license.

## Download record

- **Downloaded by:** Pythontopia asset-ingest agent (extension batch)
- **Downloaded at:** 2026-05-21T14:55:39Z (UTC) / 2026-05-21T22:55:39+08:00
- **Egress proxy:** `http://127.0.0.1:7892`
- **Integrity check:** `unzip -tq` reported "No errors detected" for all 10 zips.
- **Concurrency:** Parallel downloads via `xargs -P 6` over the proxy.

## Why these packs

These complement `assets/inbox/kenney_characters/` to give Pythontopia a
full pixel-village toolkit:

- **Tiny Town / Tiny Dungeon / Tiny Battle**: 16×16 packed tilemaps for the
  Stardew-style village, Bug-mine ("矿洞事件") and skill-tree combat scenes.
- **Roguelike Modern City / Roguelike Caves & Dungeons**: alternative city
  and dungeon biomes for KC-themed map variants.
- **RPG Urban Pack**: extra street props, signage and storefronts for the
  Python village board / classroom NPC dialogues.
- **1-Bit Pack**: minimal monochrome variants for accessibility / low-vision
  themes.
- **Pixel Platformer**: platformer tiles for game-jam style mini lessons.
- **Generic Items**: 330 standalone items (food, tools, gems, books, scrolls)
  for the in-game inventory and quest rewards.
- **Game Icons**: 425 UI icons for skill-tree nodes, KC badges, status FX.

## Slug correction

The task brief suggested `roguelike-caves-and-dungeons`, but the live Kenney
slug is `roguelike-caves-dungeons` (no `-and-`). Confirmed by HEAD probing;
the working URL was used.

## Usage inside Pythontopia

These assets sit in `assets/inbox/` as raw upstream material. Before promoting
any tile / item / icon into `assets/pixel/` (or any in-game pipeline), keep
the CC0 line intact and continue to surface Kenney's name in the project's
asset credits, per Pythontopia's "保留来源说明" rule. Do not commingle with
non-CC0 art.
