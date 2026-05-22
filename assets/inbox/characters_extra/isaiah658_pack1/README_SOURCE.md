# isaiah658's Pixel Pack #1 — README

> **Source slug:** `isaiah658_pack1/`
> **Upstream URL:** https://opengameart.org/content/isaiah658s-pixel-pack-1
> **Pythontopia role candidates:** modern / casually-dressed student NPCs and townsfolk for the Pythontopia plaza.

## Pack layout

Each character is **16×16 px per frame**, on a **3 cols × 4 rows** walk strip (RPG Maker MV/MZ convention).

Row order: **down / left / right / up**. Col 0 / 1 / 2 = step-left / idle / step-right.

| File | Pixel size | Cell | Cols × Rows | Apparent role |
|------|------------|------|-------------|---------------|
| `Characters/character-1.png`         | 48×72 | 16×24 | 3 × 3 | Casual character (tall) |
| `Characters/child-1.png`             | 48×64 | 16×16 | 3 × 4 | Child / younger student variant |
| `Characters/child-2.png`             | 48×64 | 16×16 | 3 × 4 | Child variant |
| `Characters/child-3.png`             | 48×64 | 16×16 | 3 × 4 | Child variant |
| `Characters/cloaked-figure.png`      | 48×96 | 16×24 | 3 × 4 | Cloaked / hooded NPC (teacher? recluse?) |
| `Characters/green-cap-character.png` | 48×72 | 16×24 | 3 × 3 | Green-cap casual NPC |
| `Characters/kaylee.png`              | 48×76 | 16×19 | 3 × 4 | Named NPC "Kaylee" (street clothes) |
| `Characters/luke.png`                | 48×64 | 16×16 | 3 × 4 | Named NPC "Luke" |
| `Characters/luke-swimming-trunks.png`| 48×64 | 16×16 | 3 × 4 | Luke alt outfit |
| `Characters/noah.png`                | 48×72 | 16×18 | 3 × 4 | Named NPC "Noah" |
| `Characters/salley.png`              | 48×64 | 16×16 | 3 × 4 | Named NPC "Salley" |
| `Characters/tuxedo-man.png`          | 48×72 | 16×24 | 3 × 3 | Suited NPC (good for teacher / merchant) |
| `Characters/zack.png`                | 48×80 | 16×20 | 3 × 4 | Named NPC "Zack" |

> Sheets where the height isn't a clean multiple of 16 (e.g. 48×72) use slightly **taller cells** (16×24 or 16×18) to make room for hair / hats. The walk cycle still respects the 3-col × 4-row layout. Always check the alpha bbox before assuming a fixed cell size.

## Pythontopia role mapping

| Sprite | Suggested Pythontopia role | Why |
|--------|-----------------------------|-----|
| `child-1.png`, `child-2.png`, `child-3.png` | Younger / shorter student NPCs at the plaza | Diversity, scale variation |
| `kaylee.png`, `luke.png`, `salley.png`, `noah.png`, `zack.png` | Named classmate NPCs (give them dialogue lines that hint at Python KCs they struggle with) | Each has a distinct silhouette / colour |
| `tuxedo-man.png` | Teacher / school principal NPC | Formal suit |
| `cloaked-figure.png` | Mystery vendor in the Bug-Mine entrance | Hood reads as adventurer / scholar |
| `green-cap-character.png` | Merchant NPC | Outdoor cap reads as itinerant trader |
| `luke-swimming-trunks.png` | (Skip — too casual / off-tone for Pythontopia educational setting) | — |

## Frame extraction snippet

```
loader.spritesheet('isaiah_luke', 'Characters/luke.png',
                   { frameWidth: 16, frameHeight: 16 });
// 4 rows = down / left / right / up
// 3 cols per row = walkL / idle / walkR
// walk cycle = col 0 -> 1 -> 2 -> 1 -> 0
```
