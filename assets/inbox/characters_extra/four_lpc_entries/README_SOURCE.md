# Four Characters: My LPC Entries — README

> **Source slug:** `four_lpc_entries/`
> **Upstream URL:** https://opengameart.org/content/four-characters-my-lpc-entries
> **Pythontopia role candidates:** **teacher** (Mage with robe + staff) and **mine adventurer** (Baldric the knight).

## Files and frame layouts

All sheets use **64×64 pixel cells** in the LPC convention. Row order: **up / left / down / right** (top to bottom).

### Mage (Pythontopia: lead teacher candidate)

| File | Pixel size | Cell | Cols × Rows | Animation | Notes |
|------|------------|------|-------------|-----------|-------|
| `mage_walk_sheet.png` | 576×256 | 64×64 | 9 × 4 | walk + idle | LPC standard: col 0 = idle, cols 1–8 = walk × 4 dirs |
| `mage_cast_sheet.png` | 448×256 | 64×64 | 7 × 4 | spellcast | 4 dirs × 7 cast frames |
| `mage_fall_sheet.png` | 384×64  | 64×64 | 6 × 1 | fall / death | 6-frame fall sequence, single direction (down-facing) |

### Baldric (Pythontopia: knight / mine adventurer candidate)

| File | Pixel size | Cell | Cols × Rows | Animation | Notes |
|------|------------|------|-------------|-----------|-------|
| `baldric_walk_sheet.png`  | 576×256 | 64×64 | 9 × 4 | walk + idle | LPC standard (idle + 8 walk × 4 dirs) |
| `baldric_slash_sheet.png` | 384×256 | 64×64 | 6 × 4 | slash | 4 dirs × 6 frames |
| `baldric_spell_sheet.png` | 448×256 | 64×64 | 7 × 4 | spellcast | 4 dirs × 7 frames |
| `baldric_fall_sheet.png`  | 384×64  | 64×64 | 6 × 1 | fall / death | down-facing only |

### Misc

| File | Pixel size | Notes |
|------|------------|-------|
| `unfinishedchars1.PNG` | small | Concept work / unfinished sprites from the same contest entry. Keep for documentation; not for runtime use. |

## Pythontopia role mapping

| Role | File | Frames for game |
|------|------|-----------------|
| **Teacher (Mage)** — Pythontopia's "代码师" (Code Master) NPC who walks between school and arcade | `mage_walk_sheet.png` for idle + walk; `mage_cast_sheet.png` for cinematic "lesson cast" cutscene | LPC 9-frame walk row → 4-direction walk + idle |
| **Mine adventurer (Knight)** — playable / NPC character for the Bug-Mine | `baldric_walk_sheet.png` for idle + walk; `baldric_slash_sheet.png` for combat with bugs | Adds slash for boss bugs |
| Mage death scene | `mage_fall_sheet.png` | Use sparingly; emphasise resilience rather than failure |
| Knight death scene | `baldric_fall_sheet.png` | Bug-Mine respawn moment |

## Why this is the right "teacher" sprite

The Pythontopia narrative places a **Code Master / 老师** NPC who teaches Python topics from a podium near the school plaza. The Mage's robe + staff + spellcast animation maps to:

- "Spellcast" → teacher writing on the blackboard / projecting a slide.
- "Walk" → teacher patrolling the classroom.
- "Fall" → not used in the educational version (we will not animate teacher death).
