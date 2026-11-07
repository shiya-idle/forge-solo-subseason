package com.forgemagic.claims;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Minimal server-authoritative claim store. Claims are append-only for normal players:
 * creation succeeds once, and there is deliberately no player delete/resize operation.
 */
public final class ImmutableClaimsService implements ClaimsService {
    private final ConcurrentMap<UUID, LandClaim> claims = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<ProtectedBuilding>> buildings = new ConcurrentHashMap<>();
    private final Set<String> usedConfirmations = ConcurrentHashMap.newKeySet();
    private final boolean purgeEffectsOnModBuildings;

    public ImmutableClaimsService(boolean purgeEffectsOnModBuildings) {
        this.purgeEffectsOnModBuildings = purgeEffectsOnModBuildings;
    }

    public synchronized ClaimResult createClaim(UUID owner, String world, Cuboid area, ThreeFactorConfirmation confirmation) {
        Objects.requireNonNull(owner); Objects.requireNonNull(world); Objects.requireNonNull(area);
        if (confirmation == null || !confirmation.validFor(owner, world, area)) return ClaimResult.REJECTED_CONFIRMATION;
        String confirmationKey = confirmation.replayKey();
        if (!usedConfirmations.add(confirmationKey)) return ClaimResult.REJECTED_CONFIRMATION_REPLAY;
        if (claims.values().stream().anyMatch(existing -> existing.world().equals(world) && existing.area().overlaps(area))) {
            usedConfirmations.remove(confirmationKey);
            return ClaimResult.REJECTED_OVERLAP;
        }
        LandClaim next = new LandClaim(UUID.randomUUID(), owner, world, area);
        claims.put(next.id(), next);
        return ClaimResult.CREATED;
    }

    public void registerProtectedBuilding(String world, ProtectedBuilding building) {
        buildings.computeIfAbsent(world, ignored -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(building);
    }

    /** Loads an already-created claim after validating its identity, bounds, and overlap. */
    public synchronized boolean restoreClaim(LandClaim claim) {
        if (claim == null || claim.id() == null || claim.owner() == null || claim.world() == null || claim.world().isBlank() || claim.area() == null) return false;
        if (claims.values().stream().anyMatch(existing -> existing.world().equals(claim.world()) && existing.area().overlaps(claim.area()))) return false;
        return claims.putIfAbsent(claim.id(), claim) == null;
    }

    public boolean canModify(UUID playerId, ClaimBlock block) {
        LandClaim claim = claimAt(block.world(), block.x(), block.y(), block.z());
        if (playerId != null && claim != null) return claim.owner().equals(playerId);
        // Unclaimed land remains normal Minecraft land. Only an existing claim
        // is protected; fail-closed behavior belongs to claim creation/storage,
        // not to every ordinary block in the world.
        return claim == null;
    }

    public boolean shouldPurgeEffect(ClaimBlock block, String effectId) {
        if (!purgeEffectsOnModBuildings) return false;
        if (block == null || effectId == null || effectId.isBlank()) return false;
        String normalizedEffect = effectId.toLowerCase(java.util.Locale.ROOT);
        for (ProtectedBuilding building : buildings.getOrDefault(block.world(), List.of())) {
            if (building.area().contains(block.x(), block.y(), block.z()) && building.effectIds().stream().map(id -> id.toLowerCase(java.util.Locale.ROOT)).anyMatch(normalizedEffect::equals)) return true;
        }
        return false;
    }

    public Collection<LandClaim> claims() { return List.copyOf(claims.values()); }

    @Override public String providerName() { return "IMMUTABLE_CLAIMS_FAIL_CLOSED"; }

    private LandClaim claimAt(String world, int x, int y, int z) {
        return claims.values().stream().filter(c -> c.world().equals(world) && c.area().contains(x, y, z)).findFirst().orElse(null);
    }

    public enum ClaimResult { CREATED, REJECTED_CONFIRMATION, REJECTED_CONFIRMATION_REPLAY, REJECTED_OVERLAP, REJECTED_CONFLICT }
    public record LandClaim(UUID id, UUID owner, String world, Cuboid area) { }
    public record ProtectedBuilding(String id, Cuboid area, List<String> effectIds) {
        public ProtectedBuilding { effectIds = List.copyOf(effectIds); }
    }
    public record Cuboid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Cuboid { if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Invalid cuboid"); }
        public boolean contains(int x, int y, int z) { return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ; }
        public boolean overlaps(Cuboid other) { return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY && minZ <= other.maxZ && maxZ >= other.minZ; }
    }
}
