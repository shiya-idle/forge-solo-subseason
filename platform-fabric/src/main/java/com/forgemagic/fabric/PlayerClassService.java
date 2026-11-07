package com.forgemagic.fabric;

import com.forgemagic.api.ClassId;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent class eligibility based on connected server ticks, not wall-clock time. */
public final class PlayerClassService {
    public static final long TICKS_REQUIRED = 72_000L; // 3 x 24,000-tick Minecraft days
    private final Map<UUID, Long> onlineTicks = new ConcurrentHashMap<>();
    private final Map<UUID, ClassId> classes = new ConcurrentHashMap<>();
    private final Path path = Path.of("config", "forge-player-classes.properties");

    public PlayerClassService() { load(); }

    public void tick(ServerPlayer player, long ticks) {
        if (classes.containsKey(player.getUUID())) return;
        long next = Math.min(TICKS_REQUIRED, onlineTicks.getOrDefault(player.getUUID(), 0L) + Math.max(0L, ticks));
        onlineTicks.put(player.getUUID(), next);
        if (next == TICKS_REQUIRED) {
            player.sendSystemMessage(Component.literal("§eYou have completed three days of connected play. Choose your class with §f/class choose <class>§e."));
            save();
        } else if (next % 1200L == 0L) {
            save();
        }
    }

    public ClassId get(UUID player) { return classes.get(player); }
    public long onlineTicks(UUID player) { return onlineTicks.getOrDefault(player, 0L); }
    public boolean eligible(UUID player) { return onlineTicks(player) >= TICKS_REQUIRED; }
    public long remainingTicks(UUID player) { return Math.max(0L, TICKS_REQUIRED - onlineTicks(player)); }

    public boolean choose(ServerPlayer player, ClassId classId) {
        UUID id = player.getUUID();
        if (classId == null || classId == ClassId.NOVICE || !eligible(id) || classes.containsKey(id)) return false;
        classes.put(id, classId);
        save();
        return true;
    }

    private void load() {
        if (!Files.exists(path)) return;
        try (var in = Files.newInputStream(path)) {
            Properties p = new Properties(); p.load(in);
            for (String key : p.stringPropertyNames()) {
                String[] parts = key.split("\\.", 2); if (parts.length != 2) continue;
                UUID id; try { id = UUID.fromString(parts[0]); } catch (IllegalArgumentException ignored) { continue; }
                try {
                    if (parts[1].equals("ticks")) onlineTicks.put(id, Math.max(0L, Long.parseLong(p.getProperty(key))));
                    if (parts[1].equals("class")) classes.put(id, ClassId.valueOf(p.getProperty(key).toUpperCase()));
                } catch (RuntimeException ignored) { }
            }
        } catch (IOException error) { ForgeSoloFabric.LOGGER.warn("Could not load player class state", error); }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(path.getParent());
            Properties p = new Properties();
            onlineTicks.forEach((id, ticks) -> p.setProperty(id + ".ticks", Long.toString(ticks)));
            classes.forEach((id, clazz) -> p.setProperty(id + ".class", clazz.name()));
            try (var out = Files.newOutputStream(path)) { p.store(out, "Fern player class unlocks; three connected Minecraft days"); }
        } catch (IOException error) { ForgeSoloFabric.LOGGER.warn("Could not save player class state", error); }
    }
}
