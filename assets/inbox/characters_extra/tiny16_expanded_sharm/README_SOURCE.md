# Tiny 16: Expanded Character Sprites — README

> **Source slug:** `tiny16_expanded_sharm/`
> **Upstream URL:** https://opengameart.org/content/tiny-16-expanded-character-sprites
> **Pythontopia role candidates:** student variants (male / female / base recolour skin), Bug-Mine wandering skeleton.

## Files and frame layouts

All sheets use **16×16 pixel cells** with the classic Sharm "3 cols × 4 rows" walk-strip per character (cell 1 of each row = idle, cells 0 and 2 = walk-left / walk-right footstep).

| File | Pixel size | Cell | Cols × Rows | Characters | Animation per char | Notes |
|------|------------|------|-------------|------------|--------------------|-------|
| `Tiny16-ExpandedBaseSprites_Nude.png` | 96×64 | 16×16 | 6 × 4 | 2 chars × (3 cols × 4 dirs) | idle + 2 walk frames × 4 dirs | Bare base for skin-tone recolouring |
| `Tiny16-ExpandedBaseSprites_Nude2.png` | 96×64 | 16×16 | 6 × 4 | 2 chars × (3 cols × 4 dirs) | idle + 2 walk frames × 4 dirs | Alt nude base (alt body shape) |
| `Tiny16-ExpandedMaleSprites.png` | 96×160 | 16×16 | 6 × 10 | 5 male variants × (3 cols × 4 dirs) | idle + 2 walk × 4 dirs each | Different hair / clothing colours |
| `Tiny16-ExpandedFemaleSprites.png` | 96×112 | 16×16 | 6 × 7 | 3.5 female variants × (3 cols × 4 dirs) | idle + 2 walk × 4 dirs each | Includes extra hair row |
| `Tiny16-ExpandedSkeletonSprites.png` | 96×96 | 16×16 | 6 × 6 | 3 skeleton variants × (3 cols × 4 dirs) | idle + 2 walk × 4 dirs each | For Bug-Mine wandering skeleton enemy |
| `tiny16_expanded_again.png` | 800×800 | 16×16 | 50 × 50 | composite preview / overview | -- | Reference / mockup, not a runtime sheet |

> Per-character walk cycle row order (Sharm convention): **down / left / right / up** (top to bottom). Col 0 = step-left, Col 1 = idle, Col 2 = step-right. To get a 3-frame walk per direction, loop cols 0 → 1 → 2 → 1 → 0.

## Pythontopia role mapping

| Role | File / character | Notes |
|------|------------------|-------|
| Student boy variants | `Tiny16-ExpandedMaleSprites.png` rows 0–4 | 5 colour variants out of the box |
| Student girl variants | `Tiny16-ExpandedFemaleSprites.png` rows 0–3 | 3 colour variants out of the box |
| Skin-tone derivative base | `Tiny16-ExpandedBaseSprites_Nude.png` + `Nude2.png` | We recolour these into 3–4 skin-tone presets and overlay clothes |
| Bug-Mine skeleton | `Tiny16-ExpandedSkeletonSprites.png` row 0 | wandering enemy |

## Frame extraction shape (for the Phaser 3 loader)

```
loader.spritesheet('tiny16_male', 'Tiny16-ExpandedMaleSprites.png', {
  frameWidth: 16, frameHeight: 16
});
// 5 characters × (3 col × 4 row) = 60 frames
// character index k starts at frame k * 12, rows are dir order down/left/right/up
```
