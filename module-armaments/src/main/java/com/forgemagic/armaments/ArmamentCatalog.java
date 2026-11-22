package com.forgemagic.armaments;

import com.forgemagic.api.ClassId;
import com.forgemagic.api.SkillDefinition;
import java.util.List;
import java.util.Map;

/** Fern-owned definitions; external projects are represented as design families, not runtime dependencies. */
public final class ArmamentCatalog {
    private static final List<WeaponDefinition> WEAPONS = List.of(
            new WeaponDefinition("fern:heartwood_cleaver", ArmamentFamily.PLENTY_OF_ARMORS, ClassId.LUMBER,
                    "minecraft:iron_axe", "Heartwood Cleaver", "A durable set-bonus axe inspired by armor-set progression.",
                    List.of("fern:tree_fell", "fern:barkweave", "fern:firewalker"), List.of(0, 4, 9), ArmamentWorldPolicy.OVERWORLD_ONLY),
            new WeaponDefinition("fern:etherium_pick", ArmamentFamily.ENHANCEDCRAFT, ClassId.MINER,
                    "minecraft:iron_pickaxe", "Etherium Pick", "A tiered mining weapon with bounded ore-sense and material bonuses.",
                    List.of("fern:ore_sense", "fern:area_mine", "fern:gem_sight"), List.of(0, 6, 7), ArmamentWorldPolicy.ANY),
            new WeaponDefinition("fern:masterwork_table", ArmamentFamily.WEAPONMASTER, ClassId.CRAFTSMAN,
                    "minecraft:crafting_table", "Masterwork Table", "A crafting progression weapon with cooldown-based refunds and repair.",
                    List.of("fern:craft_refund", "fern:tool_repair", "fern:overdrive"), List.of(1, 6, 10), ArmamentWorldPolicy.FORGE_ARENA_ONLY),
            new WeaponDefinition("fern:stormcaller_bow", ArmamentFamily.AWESOME_ARMAMENTS, ClassId.MARKSMAN,
                    "minecraft:bow", "Stormcaller Bow", "A projectile archetype with bounded accuracy, recovery, and storm effects.",
                    List.of("fern:arrow_accuracy", "fern:arrow_recovery", "fern:stormcaller"), List.of(0, 1, 6), ArmamentWorldPolicy.PVP_ARENA_ONLY),
            new WeaponDefinition("fern:wise_tome", ArmamentFamily.AWESOME_ARMAMENTS, ClassId.HEALER,
                    "minecraft:book", "Wise Tome", "A support weapon for healing, purification, and defensive set bonuses.",
                    List.of("fern:healing_word", "fern:purify", "fern:warding_light"), List.of(0, 2, 4), ArmamentWorldPolicy.ANY)
    );

    private static final List<ArmorSetDefinition> ARMOR_SETS = List.of(
            new ArmorSetDefinition("fern:plenty_firewalker", ArmamentFamily.PLENTY_OF_ARMORS, ClassId.LUMBER,
                    "Firewalker Heartwood", List.of("firewalker", "frostwalker", "thorns")),
            new ArmorSetDefinition("fern:enhancedcraft_godslayer", ArmamentFamily.ENHANCEDCRAFT, ClassId.MINER,
                    "Godslayer Etherium", List.of("bulwark", "purified", "soulbound")),
            new ArmorSetDefinition("fern:awesome_aqua", ArmamentFamily.AWESOME_ARMAMENTS, ClassId.MARKSMAN,
                    "Aqua Armament", List.of("waterborn", "infinite_sight", "bloodlust")),
            new ArmorSetDefinition("fern:weaponmaster_descent", ArmamentFamily.WEAPONMASTER, ClassId.CRAFTSMAN,
                    "Descent Masterwork", List.of("progression", "challenge_reward"))
    );

    private static final Map<ClassId, WeaponDefinition> DEFAULT_BY_CLASS = WEAPONS.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(WeaponDefinition::classId, definition -> definition));

    private ArmamentCatalog() { }

    public static List<WeaponDefinition> weapons() { return WEAPONS; }
    public static List<ArmorSetDefinition> armorSets() { return ARMOR_SETS; }
    public static WeaponDefinition weaponFor(ClassId classId) { return DEFAULT_BY_CLASS.get(classId); }
    public static WeaponDefinition weapon(String id) {
        return WEAPONS.stream().filter(definition -> definition.id().equals(id)).findFirst().orElse(null);
    }
    public static List<SkillDefinition> panelSkills(WeaponDefinition weapon, List<SkillDefinition> routeSkills) {
        if (weapon == null || routeSkills == null || routeSkills.size() < 15) return List.of();
        return weapon.panelSkills(routeSkills);
    }
}
