# Arena and Bedrock pack bugfix

The previous build did not create custom dimensions. It only searched for already-loaded worlds, causing repeated `forge_class_arena is not loaded` messages. This build rate-limits the missing-world notice to once every 30 seconds and does not skip the player's mana, HUD, thirst, or companion processing.

Players are only sent to the class arena when the world is actually loaded. A player who selects a class before the arena is installed is not later teleported into the novice flow.

The Bedrock `.mcpack` now includes `ui/hud_screen.json`, which references the Fern health and thirst artwork and places static frames above the Bedrock hotbar. Live values remain server-sent action-bar text. Install it through Geyser's local `packs` directory; it is not a Java resource pack.

The arena worlds still require a real server dimension/datapack or imported world. This bundle does not silently invent or reset world data. Do not enable the automatic class-arena flow until `forge_class_arena` is loaded.
