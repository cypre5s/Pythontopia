# Reborn world maps

The retained editable Tiled map is town_hub.json (50 x 40, 16px tiles).
Source layout and atlas logic: scripts/generate-maps.mjs and build_srw_assets.py.
Generate explicitly with node scripts/generate-maps.mjs; it is not a build hook.
The library is library.json (18 x 14), authored by scripts/generate-library.mjs.
Use node scripts/generate-library.mjs --check to verify its reproducible layout.
Run node scripts/validate-world.mjs and node scripts/audit-assets.mjs after edits.

The original town terrain/building/decoration/collision layers are retained.
Added negative-coordinate landscape layers extend visible scenery to render_bounds
without extending the 50 x 40 walk_bounds. Library walls, wood floor, rug and
furniture use the retained original farming_source.png and explicit source frames.
Both the Phaser world and Vue map preview consume the same art layers and props.
Building object names identify static art. Names, purposes, approach anchors,
portal destinations and gates are defined in data/world/world.json, imported by
the frontend and loaded by the backend. Legacy interaction properties are not
the runtime authority, and never dispatch retired Vue routes.
Runtime NPCs, dialogue, activity bindings and quests come from the authenticated API.
Library interior transitions preserve the town world session and saved position.
All 11 houses have configured Chinese names. Closed interiors show their purpose
instead of opening an invented room. Cross-map travel uses the authorized portal
API rather than arbitrary position writes; small entrance/exit artwork uses the same config.
Building signs show names only; unopened buildings have no E prompts or closed-state toasts.
The bottom map workspace shows the current logical map, cropped away from scenic extensions,
with a large left preview and a separate right target card containing icon filters.
There is no map chooser or WASD/arrow selection handling. Only explicit navigation moves the player.
All ten real library bookcases bind to the same library_shelf semantic through distinct points;
the central service aisle, two complete wide reading tables and four separate short stools replace
the former oversized rug, lone table and incorrect long seating slices. Table footprints cover
their surface area and stools have distinct collision tiles.
The player marker uses the current in-memory Sprite position and remains visible under all filters.
The map workspace has a transparent outer shell and separate map/paper target surfaces.
Library cameras remain centered on the player; outdoor cameras use the scenic bounds.

Source art and authorization limitations: ../pixel/LICENSE.md and root LICENSE-ART.md.
Selected original PNGs were copied from the user-confirmed purchased local source;
source paths and provenance are recorded in assets/inbox/super-retro-world/SOURCES.md.
The purchase terms archive is not part of this repository.
