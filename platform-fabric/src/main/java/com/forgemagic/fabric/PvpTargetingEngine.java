package com.forgemagic.fabric;

import com.forgemagic.api.SkillAction;
import com.forgemagic.api.SkillDefinition;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Server-authoritative PvP target selection. It never trusts client target packets.
 * The resolver uses the caster view ray, entity bounds, line of sight, and a small
 * movement lead so fast targets are selected consistently on the server.
 */
public final class PvpTargetingEngine {
    private final FabricSkillConfig config;

    public PvpTargetingEngine(FabricSkillConfig config) { this.config = config; }

    public Resolution resolve(ServerPlayer caster, SkillDefinition skill) {
        if (!isArena(caster) || selfTarget(skill.action())) return Resolution.self();
        Vec3 origin = caster.getEyePosition();
        Vec3 direction = caster.getLookAngle().normalize();
        Vec3 endpoint = origin.add(direction.scale(range(caster)));
        AABB search = new AABB(origin, endpoint).inflate(1.25);
        List<Candidate> candidates = caster.level().getEntitiesOfClass(LivingEntity.class, search, entity -> entity != caster && entity.isAlive() && caster.hasLineOfSight(entity))
                .stream().map(entity -> score(caster, entity, origin, direction)).filter(candidate -> candidate != null)
                .sorted(Comparator.comparingDouble(Candidate::score)).limit(8).toList();
        if (candidates.isEmpty()) return new Resolution(List.of(), null, endpoint, "no visible entity in range");
        Candidate selected = candidates.getFirst();
        if (skill.action() == SkillAction.AREA_MINE || skill.action() == SkillAction.TREE_FELL) {
            Vec3 center = selected.predictedPoint();
            List<LivingEntity> area = caster.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(config.pvpArenaTargetRadius()), entity -> entity != caster && entity.isAlive() && caster.hasLineOfSight(entity)).stream().limit(4).toList();
            return new Resolution(area, selected.entity(), center, "predicted area target");
        }
        return new Resolution(List.of(selected.entity()), selected.entity(), selected.predictedPoint(), "visible ray target");
    }

    private Candidate score(ServerPlayer caster, LivingEntity entity, Vec3 origin, Vec3 direction) {
        Vec3 current = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
        double distance = current.distanceTo(origin);
        if (distance > range(caster) + 1.0) return null;
        double leadTicks = Math.max(1.0, Math.min(6.0, distance * 0.18));
        Vec3 predicted = current.add(entity.getDeltaMovement().scale(leadTicks));
        double along = predicted.subtract(origin).dot(direction);
        if (along < 0 || along > range(caster) + 0.5) return null;
        Vec3 closest = origin.add(direction.scale(along));
        double radius = Math.max(0.35, entity.getBbWidth() * 0.55);
        double distanceFromRay = predicted.distanceToSqr(closest);
        if (distanceFromRay > radius * radius) return null;
        return new Candidate(entity, predicted, along + distanceFromRay);
    }

    public int range(ServerPlayer player) { return isArena(player) ? config.pvpArenaMaxTargetRange() : config.maxTargetRadius(); }
    private boolean isArena(ServerPlayer player) { if (!config.pvpArenaEnabled()) return false; String id = player.level().dimension().identifier().toString(); return id.equals(config.pvpArenaWorld()) || id.equals("minecraft:" + config.pvpArenaWorld()); }
    private boolean selfTarget(SkillAction action) { return switch (action) { case SPEED, HASTE, NIGHT_VISION, REGENERATION, ABSORPTION, ABSORB_DAMAGE, DAMAGE_REDUCTION, HEAL, PURIFY, GLOW, ORE_SENSE -> true; default -> false; }; }

    public record Resolution(List<LivingEntity> targets, LivingEntity primary, Vec3 predictedPoint, String reason) {
        public Resolution { targets = List.copyOf(targets); }
        public static Resolution self() { return new Resolution(List.of(), null, Vec3.ZERO, "self target"); }
        public boolean hasTarget() { return !targets.isEmpty(); }
        public String targetLabel() { return primary == null ? "self" : primary.getName().getString(); }
    }
    private record Candidate(LivingEntity entity, Vec3 predictedPoint, double score) { }
}
