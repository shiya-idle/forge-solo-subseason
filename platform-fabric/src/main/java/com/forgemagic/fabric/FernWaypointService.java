package com.forgemagic.fabric;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Public server-side access points for Fern's dedicated arenas. */
public final class FernWaypointService {
    public static final int MAX_PUBLIC_WAYPOINTS = 200;
    private final Map<String, Waypoint> waypoints = new LinkedHashMap<>();

    public FernWaypointService() {
        generate("pvp", "PvP Arena", "forge_pvp_arena", 70, -128, 128, 40, 160);
        generate("class", "Class Change", "forge_class_arena", 65, -32, 32, 50, 120);
        generate("forge", "Combiroll Forging", "forge_combiroll_arena", 65, -64, 64, 50, 140);
    }

    public void list(ServerPlayer player, String filter) {
        String normalized = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        player.sendSystemMessage(Component.literal("§dFern Waypoints §8— §f200 public access points"));
        int shown = 0;
        for (Waypoint waypoint : waypoints.values()) {
            if (!normalized.isEmpty() && !waypoint.id().startsWith(normalized)) continue;
            player.sendSystemMessage(Component.literal("§e" + waypoint.id() + " §7— §f" + waypoint.label() + " §8(" + waypoint.worldId() + ")"));
            if (++shown >= 40) {
                player.sendSystemMessage(Component.literal("§7Showing 40 results. Use §f/waypoints pvp§7, §f/waypoints class§7, or §f/waypoints forge§7 to narrow the list."));
                break;
            }
        }
    }

    public boolean teleport(ServerPlayer player, String requested) {
        String id = requested.toLowerCase(Locale.ROOT);
        Waypoint waypoint = waypoints.get(id);
        if (waypoint == null) {
            player.sendSystemMessage(Component.literal("§cUnknown waypoint. Try §f/waypoints§c."));
            return false;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) return false;
        ServerLevel target = findLevel(server, waypoint.worldId());
        if (target == null) {
            player.sendSystemMessage(Component.literal("§cThe destination world §f" + waypoint.worldId() + "§c is not loaded. Create or import it before using this waypoint."));
            return false;
        }
        player.teleportTo(target, waypoint.x() + 0.5, waypoint.y(), waypoint.z() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        player.sendSystemMessage(Component.literal("§aTeleported to §f" + waypoint.label() + " §7(" + waypoint.id() + ")§a."));
        return true;
    }

    private ServerLevel findLevel(MinecraftServer server, String worldId) {
        for (ServerLevel level : server.getAllLevels()) {
            String location = level.dimension().identifier().toString();
            String path = level.dimension().identifier().getPath();
            if (location.equals(worldId) || location.endsWith(":" + worldId) || path.equals(worldId)) return level;
        }
        return null;
    }

    public int size() { return waypoints.size(); }

    private void generate(String prefix, String label, String worldId, int count, int min, int max, int minY, int maxY) {
        int columns = (int) Math.ceil(Math.sqrt(count));
        int span = Math.max(1, max - min);
        for (int index = 0; index < count; index++) {
            int row = index / columns;
            int column = index % columns;
            int x = min + (int) Math.round((double) column * span / Math.max(1, columns - 1));
            int z = min + (int) Math.round((double) row * span / Math.max(1, (count + columns - 1) / columns - 1));
            int y = Math.min(maxY - 1, Math.max(minY + 1, minY + 30));
            String id = String.format(Locale.ROOT, "%s-%03d", prefix, index + 1);
            waypoints.put(id, new Waypoint(id, label + " Waypoint " + (index + 1), worldId, x, y, z));
        }
    }

    public record Waypoint(String id, String label, String worldId, int x, int y, int z) { }
}
