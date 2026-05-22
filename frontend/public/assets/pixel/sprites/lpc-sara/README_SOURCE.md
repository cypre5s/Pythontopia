# LPC Sara — README

> **Source slug:** `lpc_sara/`
> **Upstream URL:** https://opengameart.org/content/lpc-sara
> **Pythontopia role candidates:** **female student protagonist** (or female teacher variant) — Sara is the OGA mascot wizard, modular LPC-compatible.

## Files and frame layouts

All sheets are **64×64 px per cell** in LPC convention. Row order: **up / left / down / right** (top to bottom).

`SaraFullSheet.png` is the **fully-composited** ready-to-use character. The remaining files are **layer separations** so that we can swap shirt / leggings / shoes / hair colour without re-doing the body.

| File | Purpose | Pixel size | Cell | Cols × Rows | Animation rows | Notes |
|------|---------|------------|------|-------------|----------------|-------|
| `SaraFullSheet.png` | **Composited base** — body + clothes + hair, fully painted | unknown until inspected (typical LPC composite ≈ 832×1344) | 64×64 | 13 × 21 | full LPC sheet | Walk row, slash row, spellcast row, thrust row, bow row, hurt row — full set |
| `SaraHairBottomLayer.png` | Hair (behind head) layer | same as full sheet | 64×64 | 13 × 21 | full LPC | Bottom hair pass |
| `SaraHairTopLayer.png` | Hair (in front of head) layer | same | 64×64 | 13 × 21 | full LPC | Top hair pass — bangs / fringe |
| `SaraHairShadowOnFace.png` | Hair shadow / face highlights layer | same | 64×64 | 13 × 21 | full LPC | Optional |
| `SaraShirt.png` | Shirt layer | same | 64×64 | 13 × 21 | full LPC | Recolourable for school uniform |
| `SaraLeggings.png` | Leggings layer | same | 64×64 | 13 × 21 | full LPC | Recolourable for school uniform |
| `SaraShoes.png` | Shoes layer | same | 64×64 | 13 × 21 | full LPC | Recolourable for school uniform |

> Run `python -c "from PIL import Image; print(Image.open('SaraFullSheet.png').size)"` during integration to confirm the exact full-sheet dimensions; this is just before extraction. The author followed the standard LPC kit sheet so the rows are:
>
> 1. spellcast — 7 frames × 4 dirs
> 2. thrust — 8 frames × 4 dirs
> 3. walkcycle — 9 frames × 4 dirs (col 0 = idle, cols 1–8 = walk; **this is what we need**)
> 4. slash — 6 frames × 4 dirs
> 5. shoot (bow) — 13 frames × 4 dirs
> 6. hurt — 6 frames × 1 dir (down only)
>
> Total height = (7+8+9+6+13+6) × 4 × 64 = 21 × 4 × 64 = 5376 / 4 = 1344 px (standard LPC universal sheet).

## Pythontopia role mapping

| Role | Layers to stack | Re-paint? |
|------|------------------|-----------|
| **Sara as Pythontopia female student protagonist** | `SaraFullSheet.png` minus `SaraShirt.png` / `SaraLeggings.png` / `SaraShoes.png` (so we keep body + hair from full sheet), then re-paint shirt+leggings+shoes to the Pythontopia uniform palette | Yes — recolour shirt/leggings/shoes only |
| **Sara as guest wizard NPC** | `SaraFullSheet.png` unchanged | No |
| **Female teacher variant** | Sara base + add a robe overlay (we paint our own) | Yes — paint robe overlay |

## Why we keep all 7 layer files

For OGA-BY / CC-BY 4.0 we are allowed to remix, but re-creating the layer decomposition from scratch is a big art task. Keeping the layers means we can:

1. Make several Pythontopia school-uniform variants (just by recolouring shirt / leggings / shoes).
2. Add hair colour variants by recolouring `SaraHairBottomLayer.png` + `SaraHairTopLayer.png` + `SaraHairShadowOnFace.png` consistently.
3. Cleanly attribute the upstream layer art separately from our recolours.

## Pythontopia recolour outputs (planned, NOT in this folder)

R-100 follow-up agents will place Pythontopia recolour variants under

`assets/inbox/characters_extra/lpc_sara_pythontopia_uniform/`

(separate sibling source slug) so the upstream `lpc_sara/` folder stays clean for traceability.
