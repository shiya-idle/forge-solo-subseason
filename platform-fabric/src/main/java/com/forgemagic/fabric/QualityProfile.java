package com.forgemagic.fabric;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public enum QualityProfile {
    POWER_SAVING(0.35, 32, false, false, false),
    MEDIUM(0.65, 64, true, false, true),
    HIGH(1.0, 128, true, true, true),
    ULTRA(1.35, 192, true, true, true);

    private final double visualDensity;
    private final int particleCap;
    private final boolean trails;
    private final boolean layered;
    private final boolean overlays;
    private static final Map<UUID, QualityProfile> PLAYER_PROFILES = new ConcurrentHashMap<>();
    QualityProfile(double visualDensity, int particleCap, boolean trails, boolean layered, boolean overlays) { this.visualDensity = visualDensity; this.particleCap = particleCap; this.trails = trails; this.layered = layered; this.overlays = overlays; }
    public double visualDensity() { return visualDensity; }
    public int particleCap() { return particleCap; }
    public boolean trails() { return trails; }
    public boolean layered() { return layered; }
    public boolean overlays() { return overlays; }
    public static QualityProfile forPlayer(UUID player) { return PLAYER_PROFILES.getOrDefault(player, MEDIUM); }
    public static void set(UUID player, QualityProfile profile) { PLAYER_PROFILES.put(player, profile); }
    public static QualityProfile parse(String value) { try { return valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_')); } catch (RuntimeException e) { return MEDIUM; } }
    public static void describe(ServerPlayer player) { for (QualityProfile p : values()) player.sendSystemMessage(Component.literal("§e" + p.name().toLowerCase(Locale.ROOT) + " §7density=" + p.visualDensity + ", particles=" + p.particleCap + ", trails=" + p.trails + ", layered=" + p.layered + ", overlays=" + p.overlays)); }
    public static Map<String, Object> configShape() { return Map.of("power-saving", POWER_SAVING, "medium", MEDIUM, "high", HIGH, "ultra", ULTRA); }
}
