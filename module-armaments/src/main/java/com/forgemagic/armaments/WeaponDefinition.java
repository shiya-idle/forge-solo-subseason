package com.forgemagic.armaments;

import com.forgemagic.api.ClassId;
import com.forgemagic.api.SkillDefinition;
import java.util.List;

public record WeaponDefinition(
        String id,
        ArmamentFamily family,
        ClassId classId,
        String baseItemKey,
        String displayName,
        String description,
        List<String> abilityIds,
        List<Integer> routeSlots,
        ArmamentWorldPolicy worldPolicy) {
    public WeaponDefinition {
        if (id == null || id.isBlank() || family == null || classId == null || classId == ClassId.NOVICE
                || baseItemKey == null || baseItemKey.isBlank() || displayName == null || displayName.isBlank()
                || description == null || description.isBlank() || abilityIds == null || abilityIds.isEmpty()
                || routeSlots == null || routeSlots.size() != abilityIds.size() || worldPolicy == null) {
            throw new IllegalArgumentException("invalid weapon definition");
        }
        if (routeSlots.stream().anyMatch(slot -> slot < 0 || slot >= 15)) {
            throw new IllegalArgumentException("weapon route slot must be between 0 and 14");
        }
        abilityIds = List.copyOf(abilityIds);
        routeSlots = List.copyOf(routeSlots);
    }

    public List<SkillDefinition> panelSkills(List<SkillDefinition> routeSkills) {
        return routeSlots.stream().map(routeSkills::get).toList();
    }

    public boolean allowedInWorld(String dimensionId) {
        String id = dimensionId == null ? "" : dimensionId.toLowerCase(java.util.Locale.ROOT);
        return switch (worldPolicy) {
            case ANY -> true;
            case OVERWORLD_ONLY -> id.equals("minecraft:overworld") || id.equals("overworld");
            case CLASS_ARENA_ONLY -> id.endsWith(":forge_class_arena") || id.equals("forge_class_arena");
            case PVP_ARENA_ONLY -> id.endsWith(":forge_pvp_arena") || id.equals("forge_pvp_arena");
            case FORGE_ARENA_ONLY -> id.endsWith(":forge_combiroll_arena") || id.equals("forge_combiroll_arena");
            case DUNGEON_ONLY -> id.equals("tid:layer_1") || id.endsWith(":layer_1");
        };
    }
}
