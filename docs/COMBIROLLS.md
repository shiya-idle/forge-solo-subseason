# Fern Combirolls

Fern now defines 15 two-material Combirolls from Copper, Iron, Gold, Amethyst, Diamond, and Netherite. In vanilla Minecraft, the Amethyst ingredient is represented by an Amethyst Shard because there is no vanilla Amethyst Ingot item.

## Commands

Players can inspect, craft, and cast through the server-authoritative commands:

```text
/forge combiroll list
/forge combiroll craft <materialA> <materialB>
/forge combiroll cast <materialA> <materialB>
```

An operator can spawn the persistent named altar NPC at their position:

```text
/forge combiroll spawn
```

The altar currently acts as a visible in-world anchor for the same validated command flow. A future screen can call these same service methods, but recipe validation must remain on the server.

Crafting consumes one of each material and records ownership in `config/forge-combirolls.properties`. It also gives the player a named paper receipt. Ownership is server-authoritative and survives restarts. Casting checks ownership, applies the rarity-based cooldown, scales the effect from player level up to a bounded 2x multiplier, and emits particles.

The combinations are:

- Copper + Iron — Conductive Bulwark — Common
- Copper + Gold — Fortune Current — Uncommon
- Copper + Amethyst — Resonant Coil — Rare
- Copper + Diamond — Prismatic Circuit — Epic
- Copper + Netherite — Stormforged Core — Mythic
- Iron + Gold — Gilded Bastion — Uncommon
- Iron + Amethyst — Crystal Guard — Rare
- Iron + Diamond — Diamond Bulwark — Epic
- Iron + Netherite — Infernal Bastion — Mythic
- Gold + Amethyst — Fortune Resonance — Rare
- Gold + Diamond — Solar Prism — Epic
- Gold + Netherite — Crown of Embers — Mythic
- Amethyst + Diamond — Arcane Prism — Epic
- Amethyst + Netherite — Void Resonator — Mythic
- Diamond + Netherite — Astral Breaker — Legendary

Java clients receive the first owned Combiroll and its remaining cooldown in the synchronized HUD payload. Bedrock clients continue to receive the server-authoritative state through the action-bar fallback. The generated visual pack contains one icon for each combination under the Fern GUI textures.
