package com.forgemagic.fabric;

import com.forgemagic.api.SkillAction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;

public final class ForgeSoloSounds {
    public static final SoundEvent CAST_NATURE = register("cast.nature");
    public static final SoundEvent CAST_ARCANE = register("cast.arcane");
    public static final SoundEvent CAST_RADIANT = register("cast.radiant");
    public static final SoundEvent CAST_SHADOW = register("cast.shadow");
    public static final SoundEvent IMPACT = register("impact");
    public static final SoundEvent NOTICE = register("notice");

    private static SoundEvent register(String path) {
        return Registry.register(BuiltInRegistries.SOUND_EVENT, Identifier.fromNamespaceAndPath(ForgeSoloFabric.MOD_ID, path),
                SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(ForgeSoloFabric.MOD_ID, path)));
    }

    public static void playCast(ServerPlayer player, SkillAction action) {
        SoundEvent sound = switch (action) {
            case HEAL, PURIFY, REGENERATION, ABSORPTION, ABSORB_DAMAGE -> CAST_RADIANT;
            case GLOW, ORE_SENSE, NIGHT_VISION, HASTE -> CAST_ARCANE;
            case ROOT, TREE_FELL, SPEED -> CAST_NATURE;
            case KNOCKBACK, PROJECTILE_DAMAGE, SLOW -> IMPACT;
            default -> CAST_SHADOW;
        };
        player.playSound(sound, 0.65f, 0.95f + (player.getRandom().nextFloat() * 0.1f));
    }

    public static void register() { }
    private ForgeSoloSounds() { }
}
