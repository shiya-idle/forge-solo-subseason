package com.forgemagic.fabric;

import com.forgemagic.api.SkillAction;
import com.forgemagic.api.SkillDefinition;
import com.forgemagic.claims.ClaimsService;
import com.forgemagic.claims.ImmutableClaimsService;
import com.forgemagic.skills.SkillRuntime;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative effects with bounded visual compositions and exact PvP target resolution. */
public final class FabricSkillEffects {
    private final SkillRuntime runtime;
    private final FabricSkillConfig config;
    private final ImmutableClaimsService claims;
    private final PvpTargetingEngine targeting;

    public FabricSkillEffects(SkillRuntime runtime, FabricSkillConfig config, ImmutableClaimsService claims, PvpTargetingEngine targeting) {
        this.runtime = runtime; this.config = config; this.claims = claims; this.targeting = targeting;
    }

    public boolean cast(ServerPlayer player, SkillDefinition skill) {
        return cast(player, skill, 1, 1);
    }

    public boolean cast(ServerPlayer player, SkillDefinition skill, int playerLevel, int skillLevel) {
        ClaimsService.ClaimBlock location = new ClaimsService.ClaimBlock(player.level().dimension().identifier().toString(), player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ());
        String effectId = skill.id().toLowerCase(java.util.Locale.ROOT);
        if (claims.shouldPurgeEffect(location, effectId) || claims.shouldPurgeEffect(location, skill.action().name().toLowerCase(java.util.Locale.ROOT))) { player.sendSystemMessage(Component.literal("§cThis skill effect is purged inside a protected mod building.")); return false; }
        var ticket = runtime.beginCast(player.getUUID(), skill, playerLevel, skillLevel, 0, 0);
        if (ticket.isEmpty()) { player.sendSystemMessage(Component.literal("§cSkill unavailable: not enough mana or still cooling down.")); return false; }
        try {
            PvpTargetingEngine.Resolution resolution = targeting.resolve(player, skill);
            if (requiresTarget(skill.action()) && !resolution.hasTarget()) { runtime.discard(ticket.get()); player.sendSystemMessage(Component.literal("§cNo visible PvP target in range; cast cancelled.")); return false; }
            announceCast(player, skill, resolution);
            if (!apply(player, skill, resolution)) { runtime.discard(ticket.get()); player.sendSystemMessage(Component.literal("§cThis skill is unavailable until its protection provider is configured.")); return false; }
            if (!runtime.commit(ticket.get())) { player.sendSystemMessage(Component.literal("§cSkill state changed before commit; no mana or cooldown was consumed.")); return false; }
            castHighlight(player, skill.action()); player.sendSystemMessage(Component.literal("§a✦ §f" + skill.displayName() + " §7activated.")); return true;
        } catch (RuntimeException error) { runtime.discard(ticket.get()); ForgeSoloFabric.LOGGER.warn("Skill {} failed safely", skill.id(), error); player.sendSystemMessage(Component.literal("§cSkill failed safely; no mana or cooldown was consumed.")); return false; }
    }

    private boolean apply(ServerPlayer player, SkillDefinition skill, PvpTargetingEngine.Resolution resolution) {
        int duration = Math.max(20, Math.min(config.maxDurationTicks(), (int) Math.round(skill.baseCooldownSeconds() * 10)));
        int amplifier = Math.max(0, Math.min(2, (int) Math.round(skill.baseEffect()) - 1));
        return switch (skill.action()) {
            case SPEED -> effect(player, MobEffects.SPEED, duration, amplifier, ParticleTypes.HAPPY_VILLAGER, ParticleTypes.CLOUD);
            case HASTE -> effect(player, MobEffects.HASTE, duration, amplifier, ParticleTypes.CRIT, ParticleTypes.ELECTRIC_SPARK);
            case NIGHT_VISION -> effect(player, MobEffects.NIGHT_VISION, duration, 0, ParticleTypes.END_ROD, ParticleTypes.SCULK_SOUL);
            case REGENERATION -> effect(player, MobEffects.REGENERATION, duration, amplifier, ParticleTypes.HEART, ParticleTypes.HAPPY_VILLAGER);
            case ABSORPTION, ABSORB_DAMAGE -> effect(player, MobEffects.ABSORPTION, duration, Math.min(2, amplifier + 1), ParticleTypes.TOTEM_OF_UNDYING, ParticleTypes.ENCHANT);
            case DAMAGE_REDUCTION -> effect(player, MobEffects.RESISTANCE, duration, amplifier, ParticleTypes.ENCHANT, ParticleTypes.END_ROD);
            case HEAL -> heal(player, skill);
            case PURIFY -> purify(player);
            case GLOW, ORE_SENSE -> effect(player, MobEffects.GLOWING, duration, 0, ParticleTypes.END_ROD, ParticleTypes.ELECTRIC_SPARK);
            case SLOW -> targetSlow(resolution);
            case ROOT -> targetRoot(resolution);
            case KNOCKBACK -> targetKnockback(player, resolution);
            case PROJECTILE_DAMAGE -> targetDamage(player, skill, resolution);
            case ORE_PULL -> targetPull(player, resolution);
            case TREE_FELL, AREA_MINE -> false;
            default -> false;
        };
    }

    private boolean requiresTarget(SkillAction action) { return switch (action) { case SLOW, ROOT, KNOCKBACK, PROJECTILE_DAMAGE, ORE_PULL, TREE_FELL, AREA_MINE -> true; default -> false; }; }
    private boolean targetSlow(PvpTargetingEngine.Resolution resolution) { boolean applied = false; for (var target : resolution.targets()) { target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1)); applied = true; } return applied; }
    private boolean targetRoot(PvpTargetingEngine.Resolution resolution) { boolean applied = false; for (var target : resolution.targets()) { target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 4)); applied = true; } return applied; }
    private boolean targetKnockback(ServerPlayer player, PvpTargetingEngine.Resolution resolution) { if (resolution.primary() == null) return false; Vec3 direction = resolution.primary().position().subtract(player.position()).normalize(); resolution.primary().push(direction.x * 0.8, 0.25, direction.z * 0.8); return true; }
    private boolean targetDamage(ServerPlayer player, SkillDefinition skill, PvpTargetingEngine.Resolution resolution) { if (resolution.primary() == null) return false; float damage = (float) Math.max(0.5, Math.min(8.0, skill.baseEffect() * config.pvpScale())); float before = resolution.primary().getHealth(); resolution.primary().hurt(player.damageSources().playerAttack(player), damage); boolean hit = resolution.primary().getHealth() < before || !resolution.primary().isAlive(); if (hit) visualizeAt(resolution.predictedPoint(), player, ParticleTypes.CRIT, ParticleTypes.ELECTRIC_SPARK, 12); return hit; }
    private boolean targetPull(ServerPlayer player, PvpTargetingEngine.Resolution resolution) { if (resolution.primary() == null) return false; Vec3 direction = player.position().subtract(resolution.primary().position()).normalize(); resolution.primary().push(direction.x * 0.45, 0.1, direction.z * 0.45); return true; }

    private boolean effect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> type, int duration, int amplifier, ParticleOptions primary, ParticleOptions secondary) { player.addEffect(new MobEffectInstance(type, duration, amplifier, false, true, true)); visualize(player, primary, secondary, 22, true); return true; }
    private boolean heal(ServerPlayer player, SkillDefinition skill) { float amount = (float) Math.max(1.0, Math.min(12.0, skill.baseEffect() * 2.0)); if (player.getHealth() >= player.getMaxHealth()) return false; player.heal(amount); visualize(player, ParticleTypes.HEART, ParticleTypes.HAPPY_VILLAGER, 18, true); return true; }
    private boolean purify(ServerPlayer player) { boolean removed = player.removeEffect(MobEffects.POISON) | player.removeEffect(MobEffects.WITHER) | player.removeEffect(MobEffects.WEAKNESS) | player.removeEffect(MobEffects.SLOWNESS); if (removed) visualize(player, ParticleTypes.END_ROD, ParticleTypes.SOUL, 28, true); return removed; }

    private void announceCast(ServerPlayer player, SkillDefinition skill, PvpTargetingEngine.Resolution resolution) {
        String id = Integer.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextInt()).substring(0, 6).toUpperCase(java.util.Locale.ROOT);
        String target = switch (skill.action()) { case KNOCKBACK, PROJECTILE_DAMAGE, SLOW, ROOT, ORE_PULL -> "aimed direction / nearest target"; case AREA_MINE, TREE_FELL -> "area around target point"; case HEAL, PURIFY, REGENERATION, ABSORPTION, ABSORB_DAMAGE -> "self / friendly target"; default -> "self"; };
        int range = targeting.range(player);
        Component message = Component.literal("[CAST " + id + "] " + skill.displayName() + " • range " + range + " blocks • " + target + " • hit: " + resolution.targetLabel()).withStyle(style -> style.withFont(new FontDescription.Resource(Identifier.parse("forge_solo:forge"))));
        player.sendSystemMessage(message, true); ForgeSoloSounds.playCast(player, skill.action()); if (config.castTrail()) trail(player, ParticleTypes.END_ROD, range); if (resolution.predictedPoint() != null) visualizeAt(resolution.predictedPoint(), player, ParticleTypes.END_ROD, ParticleTypes.ELECTRIC_SPARK, 10);
    }
    private void trail(ServerPlayer player, ParticleOptions particle, int range) { if (!QualityProfile.forPlayer(player.getUUID()).trails()) return; Vec3 origin = player.getEyePosition(); Vec3 direction = player.getLookAngle().normalize(); int points = Math.min(16, Math.max(4, (int) Math.round(range * QualityProfile.forPlayer(player.getUUID()).visualDensity()))); for (int i = 1; i <= points; i++) { double distance = (double) range * i / points; Vec3 point = origin.add(direction.scale(distance)); player.level().sendParticles(particle, point.x, point.y, point.z, 1, 0, 0, 0, 0); } }
    private void castHighlight(ServerPlayer player, SkillAction action) { if (!config.combatHighlights()) return; if (action == SkillAction.KNOCKBACK || action == SkillAction.PROJECTILE_DAMAGE || action == SkillAction.DAMAGE_REDUCTION) visualize(player, ParticleTypes.CRIT, ParticleTypes.ELECTRIC_SPARK, 10, false); }
    private void visualize(ServerPlayer player, ParticleOptions primary, ParticleOptions secondary, int count, boolean ring) { if (!config.visualsEnabled()) return; burst(player, primary, count); if (config.layeredParticles()) burst(player, secondary, Math.max(3, count / 3)); if (ring && config.ringsEnabled()) ring(player, primary); }
    private void visualizeAt(Vec3 point, ServerPlayer player, ParticleOptions primary, ParticleOptions secondary, int count) { if (!config.visualsEnabled() || !QualityProfile.forPlayer(player.getUUID()).overlays()) return; int amount = particleAmount(player, count); player.level().sendParticles(primary, point.x, point.y, point.z, amount, 0.35, 0.35, 0.35, 0.02); if (config.layeredParticles() && QualityProfile.forPlayer(player.getUUID()).layered()) player.level().sendParticles(secondary, point.x, point.y, point.z, Math.max(2, amount / 3), 0.2, 0.2, 0.2, 0.01); }
    private void burst(ServerPlayer player, ParticleOptions particle, int count) { int amount = particleAmount(player, count); player.level().sendParticles(particle, player.getX(), player.getY() + 1.0, player.getZ(), amount, 0.55, 0.75, 0.55, 0.025); }
    private int particleAmount(ServerPlayer player, int count) { QualityProfile profile = QualityProfile.forPlayer(player.getUUID()); return Math.max(1, Math.min(Math.min(config.maxParticlesPerCast(), profile.particleCap()), (int) Math.round(count * config.particleDensity() * profile.visualDensity()))); }
    private void ring(ServerPlayer player, ParticleOptions particle) { int points = config.ringPoints(); double radius = config.ringRadius(); for (int i = 0; i < points; i++) { double angle = Math.PI * 2.0 * i / points; player.level().sendParticles(particle, player.getX() + Math.cos(angle) * radius, player.getY() + 0.08, player.getZ() + Math.sin(angle) * radius, 1, 0, 0.04, 0, 0); } }
}
