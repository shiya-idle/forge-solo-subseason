# Configured Skills, Classes, and Routes

## Counts

The current implementation contains:

- **6 class identifiers:** `NOVICE`, `LUMBER`, `MINER`, `CRAFTSMAN`, `MARKSMAN`, and `HEALER`.
- **5 playable catalog classes:** every class except `NOVICE` currently has a route table.
- **15 route skills per playable class:** 75 named route skills total.
- **54 generated subclass skills per playable class:** 270 generated subclass skills total.
- **345 catalog skills total across the five playable classes.**

Route tiers are `COMMON`, `RARE`, `EPIC`, `LEGENDARY`, and `MYTHIC`, with three route skills at each tier.

## Class identifiers

| Class | Catalog route status | YAML role |
|---|---|---|
| `NOVICE` | No catalog route table; starter/configuration class | Available in class-change arena and has two starter panel bindings |
| `LUMBER` | 15 route skills | Wood/nature-oriented route |
| `MINER` | 15 route skills | Ore/stone-oriented route |
| `CRAFTSMAN` | 15 route skills | Crafting/tool/furnace-oriented route |
| `MARKSMAN` | 15 route skills | Arrow/projectile-oriented route |
| `HEALER` | 15 route skills | Healing/defensive route; current default class |

## LUMBER route

| Tier | Skill ID suffix | Skill | Action |
|---|---:|---|---|
| Common | `0` | Heartwood Fall | `TREE_FELL` |
| Common | `1` | Seedling Blessing | `MATERIAL_BONUS` |
| Common | `2` | Featherblade | `PASSIVE_DURABILITY` |
| Rare | `0` | Verdant Stride | `SPEED` |
| Rare | `1` | Barkweave Aegis | `DAMAGE_REDUCTION` |
| Rare | `2` | Amber Tears | `MATERIAL_BONUS` |
| Epic | `0` | Veil of Mist | `GLOW` |
| Epic | `1` | Grasping Roots | `ROOT` |
| Epic | `2` | Titan's Cleave | `KNOCKBACK` |
| Legendary | `0` | Grove's Blessing | `REGENERATION` |
| Legendary | `1` | Living Bulwark | `ABSORB_DAMAGE` |
| Legendary | `2` | Phoenix Heartwood | `ABSORB_DAMAGE` |
| Mythic | `0` | Canopy of Eternity | `SPEED` |
| Mythic | `1` | Wrath of the Ancients | `KNOCKBACK` |
| Mythic | `2` | Endless Harvest | `MATERIAL_BONUS` |

Runtime IDs use the pattern `lumber.<tier>.0`, for example `lumber.common.0`.

## MINER route

| Tier | Skill ID suffix | Skill | Action |
|---|---:|---|---|
| Common | `0` | Oresight | `ORE_SENSE` |
| Common | `1` | Everlasting Edge | `PASSIVE_DURABILITY` |
| Common | `2` | Lanternlight | `NIGHT_VISION` |
| Rare | `0` | Granite Skin | `DAMAGE_REDUCTION` |
| Rare | `1` | Lodestone Pull | `ORE_PULL` |
| Rare | `2` | Echo Compass | `ORE_SENSE` |
| Epic | `0` | Runic Blast | `AREA_MINE` |
| Epic | `1` | Gemsight | `ORE_SENSE` |
| Epic | `2` | Surge of the Deep | `HASTE` |
| Legendary | `0` | Midas Whisper | `MATERIAL_BONUS` |
| Legendary | `1` | Magma Walker | `ABSORB_DAMAGE` |
| Legendary | `2` | Earthshaker Stride | `KNOCKBACK` |
| Mythic | `0` | Worldcore Tap | `ORE_SENSE` |
| Mythic | `1` | Heart of the Mountain | `ABSORPTION` |
| Mythic | `2` | Oracle of the Deep | `ORE_SENSE` |

Runtime IDs use `miner.<tier>.<slot>`.

## CRAFTSMAN route

| Tier | Skill ID suffix | Skill | Action |
|---|---:|---|---|
| Common | `0` | Swiftwork | `HASTE` |
| Common | `1` | Thrifty Spark | `CRAFT_REFUND` |
| Common | `2` | Mending Touch | `TOOL_REPAIR` |
| Rare | `0` | Whetstone Blessing | `PROJECTILE_DAMAGE` |
| Rare | `1` | Tempered Soul | `PASSIVE_DURABILITY` |
| Rare | `2` | Emberflow | `FURNACE_SPEED` |
| Epic | `0` | Blueprint Sigil | `CRAFT_REFUND` |
| Epic | `1` | Overdrive Rune | `HASTE` |
| Epic | `2` | Enchanter's Bargain | `CRAFT_REFUND` |
| Legendary | `0` | Masterwork Spark | `MATERIAL_BONUS` |
| Legendary | `1` | Spectral Assembly | `CRAFT_REFUND` |
| Legendary | `2` | Aegis of the Maker | `ABSORB_DAMAGE` |
| Mythic | `0` | Hammer of Creation | `TOOL_REPAIR` |
| Mythic | `1` | Flawless Creation | `CRAFT_REFUND` |
| Mythic | `2` | Sanctum of the Forge | `FURNACE_SPEED` |

Runtime IDs use `craftsman.<tier>.<slot>`.

## MARKSMAN route

| Tier | Skill ID suffix | Skill | Action |
|---|---:|---|---|
| Common | `0` | Eagle's Focus | `ARROW_ACCURACY` |
| Common | `1` | Swiftstring | `ARROW_RECOVERY` |
| Common | `2` | Arrow's Return | `PASSIVE_DURABILITY` |
| Rare | `0` | Hunter's Brand | `PROJECTILE_DAMAGE` |
| Rare | `1` | Spirit Tracker | `GLOW` |
| Rare | `2` | Snaring Bolt | `SLOW` |
| Epic | `0` | Piercing Gale | `PROJECTILE_DAMAGE` |
| Epic | `1` | Arcing Tempest | `PROJECTILE_DAMAGE` |
| Epic | `2` | Zephyr Step | `SPEED` |
| Legendary | `0` | Seeking Star | `PROJECTILE_DAMAGE` |
| Legendary | `1` | Eye of the Roc | `GLOW` |
| Legendary | `2` | Phantom Quiver | `ARROW_RECOVERY` |
| Mythic | `0` | Starfall Volley | `PROJECTILE_DAMAGE` |
| Mythic | `1` | Spectral Shaft | `PROJECTILE_DAMAGE` |
| Mythic | `2` | Moonlit Mark | `GLOW` |

Runtime IDs use `marksman.<tier>.<slot>`.

## HEALER route

| Tier | Skill ID suffix | Skill | Action |
|---|---:|---|---|
| Common | `0` | Wildroot Poultice | `REGENERATION` |
| Common | `1` | Gentle Radiance | `REGENERATION` |
| Common | `2` | Purifying Breath | `PURIFY` |
| Rare | `0` | Pulse of Life | `HEAL` |
| Rare | `1` | Warding Light | `ABSORPTION` |
| Rare | `2` | Mercy's Eye | `HEAL` |
| Epic | `0` | Soul Tether | `REGENERATION` |
| Epic | `1` | Serenity Blessing | `DAMAGE_REDUCTION` |
| Epic | `2` | Phoenix Rite | `HEAL` |
| Legendary | `0` | Circle of Renewal | `HEAL` |
| Legendary | `1` | Cleansing Nova | `PURIFY` |
| Legendary | `2` | Fate's Thread | `DAMAGE_REDUCTION` |
| Mythic | `0` | Celestial Rebirth | `HEAL` |
| Mythic | `1` | Sanctuary of Dawn | `REGENERATION` |
| Mythic | `2` | Eternal Grace | `REGENERATION` |

Runtime IDs use `healer.<tier>.<slot>`.

## Generated subclass skills

Subclass skills are generated rather than individually named in the source catalog.

For each playable class, the generator creates:

- Domains `1` through `9`.
- Two skills per subclass tier.
- Three subclass tiers:
  - `MYTHICAL_SUBCLASS`
  - `PRISMATIC_SUBCLASS`
  - `TRANSCENDENT_SUBCLASS`

This creates `9 × 3 × 2 = 54` subclass skills per class and `270` total.

Their runtime ID format is:

```text
<class>.subclass.<domain>.<tier-index>.<skill-index>
```

The generated display name is:

```text
Domain <domain> <ACTION>
```

The action is selected from that class’s 15-action route array using the deterministic expression `(domain + skill-index + tier-index) % 15`. Therefore subclass names and actions are deterministic but are not separate hand-authored entries in YAML.

## Configured class-change route

The class-change arena is configured as:

```text
World: forge_class_arena
Environment: NORMAL
Difficulty: PEACEFUL
PvP: disabled
Skill damage: disabled
Building: disabled
Block breaking: disabled
Claim creation: disabled
Keep inventory: enabled
Class-change cooldown: 300 seconds
Combat lockout: 10 seconds
Confirmation: required
Confirmation phrase: CONFIRM CLASS
```

Available class selections:

```text
NOVICE
HEALER
MARKSMAN
LUMBER
MINER
CRAFTSMAN
```

## Configured PvP arena route

The PvP arena is configured as:

```text
World: forge_pvp_arena
Environment: NORMAL
Difficulty: NORMAL
PvP: enabled
Skill damage: enabled
Friendly fire: enabled
Building: disabled
Block breaking: disabled
Claim creation: disabled
Teleport skills: disabled
Instant-kill effects: disabled
Damage scaling: 0.6
Maximum target range: 12 blocks
Target radius: 1.35 blocks
Minimum hit delay: 10 ticks
Combat tag: 15 seconds
Combat logout protection: enabled
Respawn in arena: enabled
Keep inventory: enabled
```

The arena also has bounded visual effects, including cast trails, rings, impact waves, damage flashes, cast identifiers, target class display, range display, and predicted target guidance.

## PvP class loadouts

The configured default PvP bindings are:

- `NOVICE`: `verdant-stride`, `whetstone-blessing`
- `HEALER`: `pulse-of-life`, `cleansing-nova`, `wildroot-poultice`, `verdant-stride`
- `MARKSMAN`: `hunters-brand`, `snaring-bolt`, `oresight`
- `LUMBER`: `heartwood-fall`, `runic-blast`, `whetstone-blessing`
- `MINER`: `oresight`, `runic-blast`, `heartwood-fall`
- `CRAFTSMAN`: `whetstone-blessing`, `wildroot-poultice`, `pulse-of-life`

The same binding sets are used by the custom two-row panel configuration.

## Initial visual-profile skill set

The YAML assigns these visual profiles:

| Skill slug | Visual profile |
|---|---|
| `heartwood-fall` | Nature |
| `verdant-stride` | Nature |
| `oresight` | Arcane |
| `runic-blast` | Arcane |
| `whetstone-blessing` | Impact |
| `hunters-brand` | Shadow |
| `snaring-bolt` | Impact |
| `wildroot-poultice` | Radiant |
| `pulse-of-life` | Radiant |
| `cleansing-nova` | Radiant |

The initial batch is the same ten configured slugs.

## Important implementation mismatch

The YAML uses human-friendly slugs such as `pulse-of-life` and `hunters-brand`, while the current Java catalog generates IDs such as:

```text
healer.rare.0
marksman.rare.0
```

The current Fabric command path validates against `ContentCatalog.routeSkills(...)`, so YAML slugs are not automatically equivalent to catalog IDs. The next cleanup should add an explicit alias registry or change the YAML bindings to the generated runtime IDs. `NOVICE` also has YAML bindings but intentionally has no route table in `ContentCatalog` at present.

## Source of truth

- Route and subclass generation: `module-skills/src/main/java/com/forgemagic/skills/ContentCatalog.java`
- Class identifiers: `core-api/src/main/java/com/forgemagic/api/ClassId.java`
- Route tiers: `core-api/src/main/java/com/forgemagic/api/RouteTier.java`
- Skill tiers: `core-api/src/main/java/com/forgemagic/api/SkillTier.java`
- Arena, panel, loadout, and visual configuration: `platform-fabric/src/main/resources/forge-solo-skills.yml`
