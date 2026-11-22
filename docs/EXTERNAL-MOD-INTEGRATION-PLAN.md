# External Mod Integration Plan

## Scope

This document maps the four referenced repositories onto the current `forge-solo-subseason` Fabric 26.3 architecture. The goal is to reuse gameplay ideas and, where legally and technically safe, selected data/assets without importing incompatible loader code, version-specific mixins, or unvalidated client behavior.

Referenced repositories:

- [Plenty Of Armors](https://github.com/aleganza/Plenty-Of-Armors) — Fabric, Minecraft 1.21.1, LGPL-2.1-or-later metadata.
- [EnhancedCraft](https://github.com/onlyspxctre/enhancedcraft) — Fabric, Minecraft 1.20.4, GPL-3.0 metadata, Trinkets/Fabric ASM dependencies.
- [Awesome Armaments](https://github.com/TheV123/AwesomeArmaments) — Fabric, Minecraft 1.20.1, MIT metadata.
- [WeaponMaster](https://github.com/greatericontop/weaponmaster) — Paper/Spigot plugin, Minecraft 1.21+, GPL-3.0.

The local review was performed against the current repository head `82b876a` and shallow reference checkouts captured during this task.

## Current architecture to preserve

The current project has a good separation that should remain intact:

- `core-api` contains loader-neutral concepts such as `ClassId`, `SkillDefinition`, `SkillAction`, `ManaState`, route tiers, and economy contracts.
- `module-skills` owns the class route catalog and server-side skill runtime/math.
- `module-classes`, `module-routes`, `module-leveling`, `module-quests`, and `module-claims` own portable rules and persistence contracts.
- `platform-fabric` owns Minecraft registries, item identity, event hooks, world lookup, client payloads, and Fabric-specific effects.
- The Java client companion renders the HUD and now has a real class skill screen. The server remains authoritative for class, item identity, mana, cooldown, and casts.

External content should therefore enter through definitions and registries, not through direct calls from `module-skills` into Minecraft classes.

## Repository-by-repository assessment

### 1. Plenty Of Armors

Plenty Of Armors is the cleanest source of armor-content patterns. It has a relatively small Java surface, armor material/item registration, custom armor effects, item groups, and a large resource/data footprint. It does not provide a screen, class route, dimension, or world-connection system.

**Useful concepts:** armor-set identity, material tiers, set bonuses, custom effects such as fire/frost walking, bouncing, and thorns-like retaliation; recipe/model/lang organization.

**Recommended integration:** reimplement the set definitions as data-backed Fern armor archetypes under a new `module-armaments` module. Register the actual `ArmorItem` instances in `platform-fabric`, and apply set bonuses through a single `FernArmorEffectService` called from the existing server tick/equipment path. Use tags for set membership rather than checking individual item constants throughout the code.

**Do not import directly:** the old registration classes, version-specific material APIs, or mixins. Port the behavior to the current 26.3 mappings and current `FernItemBindingService` ownership rules.

**License note:** the repository advertises LGPL-2.1. Copying code or assets requires preserving the applicable license/notice obligations. Because the current Fern repository is marked All Rights Reserved, a clean-room reimplementation is the safer default unless licensing is explicitly reconciled.

### 2. EnhancedCraft

EnhancedCraft is the broadest technical reference. It contains many tools/armor/items, roughly 90 recipes, custom entities, particles, enchantments, status effects, mixins, networking, and data-driven ore/world-generation files. It targets Minecraft 1.20.4 and depends on APIs that do not match the current 26.3 environment.

**Useful concepts:** tiered material progression, ore generation, item tags, enchantment-like bonuses, projectile/teleport entities, client particles, server/client payload separation, and world-generation JSON organization.

**Recommended integration:** select individual mechanics and rebuild them against current APIs. Put portable progression and effect contracts in `module-armaments` or `module-skills`; put Fabric registrations/entities/worldgen in `platform-fabric`. Reuse the shape of the data layout, not the old mixin implementation. For ores, prefer current datapack JSON plus a narrow registry bootstrap rather than porting the old world mixins wholesale.

**High-risk areas:** the source includes many mixins into vanilla entities/items/world classes, an access widener, Fabric ASM, and optional Trinkets/BetterNether/BCLib integration. These are high collision risks with Fern's own claims, item-binding, skill effects, HUD, and world-route guards. They should be excluded from the first integration.

**License note:** GPL-3.0 metadata means copied implementation code cannot be treated as proprietary Fern code without a compatible licensing decision. Reimplement behavior from feature descriptions and retain no copied source/assets unless the whole distribution strategy is changed accordingly.

### 3. Awesome Armaments

Awesome Armaments is the best candidate for a first content port. It targets 1.20.1, has a compact Fabric structure, armor/weapon materials, custom archetype effects, loot-table modifiers, recipes, and data generation. It has no custom panel or dimension system.

**Useful concepts:** archetypes such as Plated, Wise, Aqua, Berserk, Bulwark, Infinite Sight, Neptune, Bloodlust, Windcaller, Stormcaller, Venomous, and Withering; recipe/data generation; set-bonus tradeoffs.

**Recommended integration:** convert these archetypes into Fern `WeaponDefinition`/`ArmorSetDefinition` records. Map each active ability to a `SkillAction` or a new bounded action enum. Example mappings:

- Windcaller/Stormcaller -> bounded speed/levitation/lightning skill actions.
- Venomous/Withering -> server-side hit effect with cooldown and target validation.
- Aqua/Neptune -> water traversal and underwater bonuses through equipment-state evaluation.
- Berserk/Bloodlust -> health-threshold modifiers through `PlayerProgressionModifiers`.
- Wise/Infinite Sight -> mana/cooldown or perception effects, but keep the cost visible in the new panel.

Register each physical weapon as a Fern class weapon with a stable definition ID. The class panel should display only the abilities allowed by the player's class and owned weapon definition. Do not use the old generic BOOK/BOW/AXE matching path.

**License note:** MIT is the most permissive of the four reviewed licenses, but attribution and license inclusion are still required for copied code/assets. Prefer copying only clearly identified MIT-licensed data or reimplementing the small effect rules with attribution in `NOTICE-THIRD-PARTY.md`.

### 4. WeaponMaster

WeaponMaster is not a Fabric mod. It is a large Paper/Spigot plugin with Bukkit command executors, event listeners, inventory GUI listeners, per-item classes, cooldown utilities, target selection, dragon/descent progression, and many command routes.

**Useful concepts:** each weapon having a narrow ability service, cooldown and target-selection utilities, progression/reward trees, challenge encounters, recipe registration, and GUI state machines.

**Recommended integration:** use it only as a gameplay design reference. Rebuild the useful parts around existing Fern services:

- Weapon listeners -> `WeaponAbilityService` plus `SkillRuntime`.
- Cooldowns -> existing `SkillRuntime`/mana model, not Bukkit scheduler state.
- Target selectors -> a Fabric service using `PvpTargetingEngine` and server-side line-of-sight checks.
- Inventory GUI listeners -> the current real client panel payload/screen, with command fallback for Bedrock.
- Dragon's Descent -> a new portable progression module, then a Fabric encounter service.
- Bukkit commands -> Brigadier commands registered in `ForgeSoloFabric`.

**Do not port directly:** Bukkit `InventoryClickEvent`, `PlayerInteractEvent`, `JavaPlugin`, `ItemMeta`, Bukkit scheduler logic, or plugin command classes. They cannot be loaded by Fabric and would conflict with the current server-authoritative item and route model.

**License note:** GPL-3.0. Treat the source as a reference for behavior and architecture, not as code to copy into the current All Rights Reserved project.

## Proposed target structure

Add a new portable module:

```text
module-armaments/
  src/main/java/com/forgemagic/armaments/
    ArmorSetDefinition.java
    WeaponDefinition.java
    Archetype.java
    WeaponAbility.java
    ArmamentCatalog.java
    ArmamentProgression.java
```

Keep Minecraft-independent records here. A `WeaponDefinition` should contain at least:

```text
id
classId
archetype
baseItemKey
abilityIds
requiredTier
cooldownPolicy
worldPolicy
```

Add Fabric-only integration under:

```text
platform-fabric/src/main/java/com/forgemagic/fabric/armaments/
  FernArmamentRegistry.java
  FernWeaponItemService.java
  FernArmorEffectService.java
  FernWeaponAbilityService.java
  FernArmamentWorldgen.java
```

The service boundaries should be:

1. `FernArmamentRegistry` registers items, armor, effects, sounds, and tags.
2. `FernWeaponItemService` creates and validates server-owned item identity using definition ID, class ID, owner UUID, and version.
3. `FernWeaponAbilityService` translates a definition's ability into existing `SkillAction`/`SkillRuntime` operations.
4. `FernArmorEffectService` evaluates complete-set bonuses without scattering armor checks through unrelated event handlers.
5. `FernArmamentWorldgen` owns only new ores/structures that are explicitly enabled; it must not create or reset Fern arena dimensions.

## Class and weapon route integration

The current class route catalog has five permanent classes and 15 route skills per class. External weapons should not create an independent second class system.

Recommended rules:

- A player has one permanent `ClassId` from `PlayerClassService`.
- A physical weapon has one stable Fern definition ID and one compatible class, stored in custom data.
- The panel opens only for a valid class weapon owned by the player.
- `ContentCatalog.routeSkills(classId)` remains the source of class-route permissions.
- A weapon may expose a subset or specialization of the class route, but it cannot grant an unrelated class route.
- Every cast goes through the existing server method that checks class, skill ID, mana, cooldown, level, and world policy.
- Generic vanilla tools are never treated as class weapons.

A suitable item identity payload is:

```text
fern_class_weapon: true
fern_class_id: MARKSMAN
fern_definition_id: awesome_armaments.stormcaller_bow
fern_owner_uuid: <uuid>
fern_item_version: 1
```

This composes the new armament system with the current ownership protection instead of bypassing it.

## World and dimension integration

None of Plenty Of Armors, Awesome Armaments, or WeaponMaster supplies a compatible Fern world-route system. EnhancedCraft has ore/worldgen examples, but not the dedicated arena routing Fern needs.

Keep all destination resolution in `FernWaypointService` and make it definition/config driven:

- `minecraft:overworld` for return points and spawn.
- `forge_class_arena` for class selection.
- `forge_pvp_arena` for PvP-only abilities.
- `forge_combiroll_arena` for forging.
- `forge_cornucopia` for transfer/trade ownership changes.
- `tid:layer_1` for the Escher dungeon route.

New external-derived content should declare a world policy such as `ANY`, `OVERWORLD_ONLY`, `PVP_ARENA_ONLY`, `FORGE_ARENA_ONLY`, or `DUNGEON_ONLY`. The ability service checks this policy before applying effects. A missing destination must produce a route error and never silently generate or replace a world.

## Integration order

### Phase 0 — foundation

- Add `module-armaments` and its tests.
- Add stable definition IDs and a versioned custom-data schema.
- Keep the existing class weapon migration path: old untagged vanilla items no longer open the panel; newly granted items are tagged.
- Add route-policy tests for every external-derived ability.

### Phase 1 — Awesome Armaments archetypes

- Port a small vertical slice: one armor set and two weapons, preferably Windcaller and Venomous.
- Register recipes, tags, models, translations, and loot only after the server behavior passes.
- Connect the two weapons to the existing real panel and `SkillRuntime`.
- Test ownership, class mismatch, cooldown, mana, death drops, and arena restrictions.

### Phase 2 — Plenty Of Armors set effects

- Port two armor sets with bounded effects.
- Use the shared armor effect service and set tags.
- Verify interaction with claims, thirst, progression modifiers, and item binding.

### Phase 3 — selected EnhancedCraft systems

- Add one material tier and one ore as data-driven content.
- Add only one projectile/entity mechanic if it cannot be represented by existing `SkillAction`.
- Avoid the large mixin/access-widener/ASM surface until a specific mechanic proves necessary.

### Phase 4 — WeaponMaster-inspired progression

- Reimplement one progression tree or challenge encounter in Fabric.
- Use Brigadier, Fabric lifecycle events, and Fern persistence contracts.
- Do not copy Bukkit GUI or listener code.

## Compatibility and legal gate

Before merging external code/assets:

1. Record the exact upstream commit and file paths.
2. Confirm the file's license and whether it is code, data, texture, sound, or documentation.
3. Preserve required notices and attribution.
4. Do not combine GPL-derived implementation code into the current All Rights Reserved distribution without an explicit licensing decision.
5. Prefer clean-room reimplementation of mechanics and retain only an attribution note for inspiration where no code/assets are copied.
6. Re-run the Fabric 26.3 Java 25 build and client compilation.

## Verification matrix

For each integrated item/armor/ability:

- `/class` returns the expected permanent class.
- Only the tagged compatible class weapon opens the real client panel.
- Every panel button reaches the server and is rejected safely when invalid.
- Wrong class, wrong owner, missing mana, cooldown, and invalid world all fail closed.
- Main-world -> arena -> main-world return coordinates survive the route.
- Missing arena dimensions produce a clear error without generating/resetting a world.
- Java companion clients receive the panel; Bedrock clients retain a usable command/action-bar fallback.
- `./gradlew --no-daemon build` passes under Java 25.
