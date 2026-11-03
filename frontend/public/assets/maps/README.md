# Reborn town map

The retained editable Tiled map is town_hub.json (50 x 40, 16px tiles).
Source layout and atlas logic: scripts/generate-maps.mjs and build_srw_assets.py.
Generate explicitly with node scripts/generate-maps.mjs; it is not a build hook.
Run node scripts/validate-world.mjs and node scripts/audit-assets.mjs after edits.

Terrain/buildings/decoration/collision layers remain from the original town.
Building interaction metadata is a scene/object key, never a retired Vue route.
Runtime NPCs, dialogue, activity bindings and quests come from the authenticated API.
Library interior transitions preserve the town world session and saved position.

Source art and authorization limitations: ../pixel/LICENSE.md and root LICENSE-ART.md.
The original SRW source package/terms archive was not in the tracked repository;
do not claim it was newly audited or silently replace the original world.
