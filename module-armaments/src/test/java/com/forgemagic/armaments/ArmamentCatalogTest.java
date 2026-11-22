package com.forgemagic.armaments;

import com.forgemagic.api.ClassId;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmamentCatalogTest {
    @Test
    void everyPlayableClassHasOneDefaultWeapon() {
        for (ClassId classId : new ClassId[]{ClassId.LUMBER, ClassId.MINER, ClassId.CRAFTSMAN, ClassId.MARKSMAN, ClassId.HEALER}) {
            WeaponDefinition definition = ArmamentCatalog.weaponFor(classId);
            assertNotNull(definition);
            assertEquals(classId, definition.classId());
            assertTrue(definition.id().startsWith("fern:"));
            assertTrue(!definition.abilityIds().isEmpty());
            assertEquals(definition.abilityIds().size(), definition.routeSlots().size());
        }
    }

    @Test
    void allReferenceFamiliesHaveFernOwnedContent() {
        EnumSet<ArmamentFamily> families = EnumSet.noneOf(ArmamentFamily.class);
        ArmamentCatalog.weapons().forEach(definition -> families.add(definition.family()));
        ArmamentCatalog.armorSets().forEach(definition -> families.add(definition.family()));
        assertEquals(EnumSet.allOf(ArmamentFamily.class), families);
    }

    @Test
    void worldPoliciesFailClosed() {
        assertTrue(ArmamentCatalog.weapon("fern:stormcaller_bow").allowedInWorld("forge_pvp_arena"));
        assertTrue(!ArmamentCatalog.weapon("fern:stormcaller_bow").allowedInWorld("minecraft:overworld"));
        assertTrue(ArmamentCatalog.weapon("fern:masterwork_table").allowedInWorld("forge_combiroll_arena"));
    }
}
