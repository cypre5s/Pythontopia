# 24x32 Pepper&Carrot Characters — README

> **Source slug:** `peppercarrot_24x32/`
> **Upstream URL:** https://opengameart.org/content/24x32-peppercarrot-characters
> **Pythontopia role candidates:** cameo NPCs near library / magic shop (each character is a story figure with a distinct visual identity — they are not generic students).

## Files and frame layouts

All sheets use **24×32 pixel cells** with Cabbit's standard "3 cols × 4 rows" walk-strip per character. Sheets vary slightly in width because some characters have extra accessories (hat / staff) drawn in a 6th column.

| File | Character | Pixel size | Cell | Cols × Rows | Walk frames | Notes |
|------|-----------|------------|------|-------------|-------------|-------|
| `pepper.png`     | Pepper — witch | 180×128 | 24×32 (with overflow) | ~7.5 × 4 | 3 walk + idle × 4 dirs | Pythontopia's "magic shop" cameo |
| `carrot.png`     | Carrot — cat | 153×128 | 24×32 | ~6.4 × 4 | 3 walk + idle × 4 dirs | Pet NPC |
| `coriander.png`  | Coriander | 187×128 | 24×32 (with overflow) | ~7.8 × 4 | 3 walk + idle × 4 dirs | Witch NPC |
| `saffron.png`    | Saffron | 163×128 | 24×32 | ~6.8 × 4 | 3 walk + idle × 4 dirs | Witch NPC |
| `shichimi.png`   | Shichimi | 153×128 | 24×32 | ~6.4 × 4 | 3 walk + idle × 4 dirs | Witch NPC |

Row order per Cabbit's other sheets in this repo (`assets/inbox/characters/cabbit_24x32/`): **down / left / right / up** (top to bottom). Col 0 = step-left, Col 1 = idle, Col 2 = step-right, optional cols 3+ = accessory animation frames (broom / staff / cape).

## Pythontopia role mapping

| Pepper&Carrot character | Pythontopia role | Placement idea |
|--------------------------|-------------------|-----------------|
| Pepper | "Visiting witch" NPC near the magic / arcade shop | Has a hat → reads as eccentric senior student |
| Coriander, Saffron, Shichimi | Friendly witches at the plaza or in the library | Variety of robes / hair colours for diversity |
| Carrot | Pet cat sprite, follows Pepper | Pure decoration; do **not** make Carrot a player character |

These are **cameo NPCs**, not main characters. Don't reskin them into a Pythontopia school uniform — the costume identifies them as Pepper&Carrot.

## Frame extraction snippet

```
# Pepper is approximately 180x128 with 24x32 cells, but the sprite has a hat that overflows up,
# so the effective bounding cell is 30x40. Trim padding before slicing.
trim_padding = (alpha_bbox(pepper.png) - native_24x32_grid)
```
