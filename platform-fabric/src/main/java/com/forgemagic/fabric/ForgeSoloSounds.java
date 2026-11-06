package com.forgemagic.fabric;

import com.forgemagic.api.SkillAction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class ForgeSoloSounds {
    // Do not register custom SoundEvents here. Fabric sync treats custom
    // registry entries as required and rejects vanilla/Geyser clients before
    // login. Vanilla sounds preserve feedback without a forge_solo registry.
    public static final SoundEvent CAST_NATURE = SoundEvents.PLAYER_ATTACK_SWEEP;
    public static final SoundEvent CAST_ARCANE = SoundEvents.ENCHANTMENT_TABLE_USE;
    public static final SoundEvent CAST_RADIANT = SoundEvents.EXPERIENCE_ORB_PICKUP;
    public static final SoundEvent CAST_SHADOW = SoundEvents.ENDERMAN_TELEPORT;
    public static final SoundEvent IMPACT = SoundEvents.ANVIL_LAND;
    public static final SoundEvent NOTICE = SoundEvents.BELL_BLOCK;

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
