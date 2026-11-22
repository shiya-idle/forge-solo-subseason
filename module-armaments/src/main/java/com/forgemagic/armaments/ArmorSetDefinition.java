package com.forgemagic.armaments;

import com.forgemagic.api.ClassId;
import java.util.List;

public record ArmorSetDefinition(
        String id,
        ArmamentFamily family,
        ClassId classId,
        String displayName,
        List<String> setBonusIds) {
    public ArmorSetDefinition {
        if (id == null || id.isBlank() || family == null || classId == null || classId == ClassId.NOVICE
                || displayName == null || displayName.isBlank() || setBonusIds == null || setBonusIds.isEmpty()) {
            throw new IllegalArgumentException("invalid armor set definition");
        }
        setBonusIds = List.copyOf(setBonusIds);
    }
}
