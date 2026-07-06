# LPC Medieval Fantasy Character Sprites — README

> **Source slug:** `lpc_medieval_fantasy/`
> **Upstream URL:** https://opengameart.org/content/lpc-medieval-fantasy-character-sprites
> **Pythontopia role candidates:** student (clothes + hair layers), teacher (robe layer), merchant NPC (leather armor + hat), mine adventurer (chain / plate armor + helmet).

## Pack layout

This is a **modular LPC-format character kit**. Each PNG is one layer (BODY / HEAD / TORSO / LEGS / FEET / HANDS / BELT / WEAPON / BEHIND), and you stack PNGs of the same animation type on top of `BODY_male.png` or `BODY_skeleton.png` (Z-order documented in `lpc_entry/README`):

```
1. WEAPON
2. HANDS
3. HEAD
4. BELT
5. TORSO
6. LEGS
7. FEET
```

`BEHIND_quiver.png` sits *behind* the body (Z = -1).

## Subdirectory map

| Subdir | Animation | Sheet size (px) | Cell | Cols | Rows | Frames per direction | Notes |
|--------|-----------|-----------------|------|------|------|----------------------|-------|
| `lpc_entry/png/walkcycle/` | **Walk + Idle** | 576×256 | 64×64 | 9 | 4 | 1 idle + 8 walk | Row order: up / left / down / right. Col 0 = idle, cols 1–8 = walk. **This is what Pythontopia needs.** |
| `lpc_entry/png/hurt/` | Hurt | 384×64 | 64×64 | 6 | 1 | 6 | Single down-facing row |
| `lpc_entry/png/slash/` | Slash | 384×256 | 64×64 | 6 | 4 | 6 | 4 directions |
| `lpc_entry/png/spellcast/` | Spellcast | 448×256 | 64×64 | 7 | 4 | 7 | 4 directions |
| `lpc_entry/png/thrust/` | Thrust | 512×256 | 64×64 | 8 | 4 | 8 | 4 directions |
| `lpc_entry/png/bow/` | Bow | 832×256 | 64×64 | 13 | 4 | 13 | 4 directions |
| `lpc_entry/png/combat_dummy/BODY_animation.png` | Combat dummy idle/hit | 512×64 | 64×64 | 8 | 1 | 8 | NPC sandbag prop |
| `expansion_pack/png/64x64/bow/` | Bow extension (skeleton archer) | 832×256 | 64×64 | 13 | 4 | 13 | Adds quiver + skeleton bow |
| `expansion_pack/png/64x64/combat_dummy/` | Combat dummy death | 64×64 | 64×64 | 1 | 1 | 1 | death pose |
| `expansion_pack/png/64x64/slash/` | Slash extras | 384×256 | 64×64 | 6 | 4 | 6 | rapier + shield cutouts |
| `expansion_pack/png/192x192/slash/` | Slash big weapons | 192×192 | 192×192 | 1 | 1 | 1 | longsword / rapier WEAPON layers oversized |
| `expansion_pack/png/192x192/thrust/WEAPON_long_spear.png` | Thrust big weapon | 192×192 | 192×192 | 1 | 1 | 1 | long spear |

## Pythontopia role mapping

| Role | Required base + clothes layers | Why this combination |
|------|--------------------------------|----------------------|
| **Student in casual clothes** | `BODY_male.png` + `HEAD_hair_blonde.png` + `TORSO_leather_armor_shirt_white.png` + `LEGS_pants_greenish.png` + `FEET_shoes_brown.png` | Clean look, no weapon, lightest costume in the kit. Use the `walkcycle/` 9-frame row for idle + walk. |
| **Teacher (robe-style)** | `BODY_male.png` + `HEAD_robe_hood.png` (or `HEAD_hair_blonde.png` if hood off) + `TORSO_robe_shirt_brown.png` + `LEGS_robe_skirt.png` + `FEET_shoes_brown.png` | "Robed scholar" silhouette, hood/un-hood for two visual states. |
| **Merchant NPC** | `BODY_male.png` + `HEAD_leather_armor_hat.png` + `TORSO_leather_armor_torso.png` + `BELT_leather.png` + `LEGS_pants_greenish.png` + `FEET_shoes_brown.png` | Tradesman costume; hat plus leather kit reads as merchant in LPC visual language. |
| **Mine adventurer (knight)** | `BODY_male.png` + `HEAD_plate_armor_helmet.png` + `HANDS_plate_armor_gloves.png` + `TORSO_plate_armor_torso.png` + `TORSO_plate_armor_arms_shoulders.png` + `LEGS_plate_armor_pants.png` + `FEET_plate_armor_shoes.png` | Full plate, suitable for the Pythontopia Bug-Mine adventurer. |
| **Mine adventurer (chain-armor variant)** | `BODY_male.png` + `HEAD_chain_armor_helmet.png` + `TORSO_chain_armor_torso.png` (+ `TORSO_chain_armor_jacket_purple.png` for colour) + `LEGS_pants_greenish.png` + `FEET_shoes_brown.png` | Lighter armour variant for early mine levels. |
| **Skeleton (Bug-Mine enemy)** | `BODY_skeleton.png` (+ optional armor / weapon layers) | Sets the wandering bug skeleton enemy in the Bug-Mine. |

## Recolouring guidance for Pythontopia

- Hair / skin variants need to be re-painted by us: the kit ships **only `HEAD_hair_blonde.png`**, so to satisfy "多种发型 / 肤色 / 校服" we will:
  - Recolour `HEAD_hair_blonde.png` to brown / black / red / pink layers (R-100 will add `HEAD_hair_brown.png` etc. as derivative works).
  - Recolour `BODY_male.png` skin tone in 3–4 ramps (cool-pale / warm-light / warm-mid / deep) and save as `BODY_male_skin_*.png`.
  - Recolour `TORSO_leather_armor_shirt_white.png` to school-uniform colours (`TORSO_uniform_navy.png`, `TORSO_uniform_red.png`, etc.).
  These derivative files will be saved under `assets/inbox/characters_extra/lpc_medieval_fantasy_pythontopia_recolors/` (separate sibling source slug, **not** in this folder) so the upstream attribution and the Pythontopia-original colour passes stay legally distinct.
- The OGA-BY 3.0 licence permits this; we must still attribute wulax + Redshrike for the underlying sheets.

## Frame layout sanity check

```
walkcycle row layout (576×256, 9×4 cells of 64×64):
  Row 0 (y=0)    → up    : idle(0)  walk(1..8)
  Row 1 (y=64)   → left  : idle(0)  walk(1..8)
  Row 2 (y=128)  → down  : idle(0)  walk(1..8)
  Row 3 (y=192)  → right : idle(0)  walk(1..8)
```

Satisfies the R-100 hard requirement *"4 方向 × ≥3 帧 walk + idle"* with margin (4 × 8 = 32 walk frames + 4 idle frames per built character).

## What we did NOT copy from the upstream zip

- `.xcf` Gimp source files (`lpc_entry/xcf/*.xcf`, `expansion_pack-0.04/xcf/*.xcf`) — kept in `.work/R-100/assets_dl/characters_extra/lpc_medieval_fantasy/` only; not shipped in the inbox to keep the inbox small and avoid editor lock-in.
- The original `.zip` files — kept in the working dir only.
- `examples/*.gif` are copied because they document which kit combinations look correct.
