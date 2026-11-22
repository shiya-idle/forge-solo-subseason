# Forge Solo Bedrock pack

This resource pack is for Bedrock Edition players joining the Minecraft 26.3 Fabric server through Geyser/Floodgate. The manifest minimum is Bedrock 1.21.0; it is not a claim that every device or current client version has been runtime-tested.

## Open the class skill deck

Hold the matching class weapon, crouch, then tap/use it to open the native Bedrock skill form. You can also run `/skills panel`. Tap an ability to cast; the server checks the class weapon, world rules, mana, and cooldown. The class-selection form warns that the class choice is permanent.

Every ability button includes its text label; Cumulus adds a pack icon when the image API and texture path are available. The server does not detect a client-side image load failure. If the server cannot use the Cumulus image-button API, it falls back to text-only buttons. Bedrock players can turn the action-bar health/mana/thirst readout on or off with `/skills mana true` and `/skills mana false`.

## Pack contents and compatibility

Class/skill form icons are Bedrock texture paths under `textures/ui/forge_solo/icons/`; the server presents them through Geyser's Cumulus forms API. The other panel/overlay textures are assets only and do not, by themselves, create a Bedrock HUD. The server-authoritative action-bar display and Geyser-translated particles are the live Bedrock visuals; Java client HUD mixins do not run on Bedrock.

Build the `.mcpack` with `python3 tools/build_bedrock_pack.py`. Put `forge-solo-bedrock-26.3.mcpack` in Geyser's `packs` directory and restart/reload Geyser. Verify it on the actual target Bedrock version and device families before making the pack mandatory.
