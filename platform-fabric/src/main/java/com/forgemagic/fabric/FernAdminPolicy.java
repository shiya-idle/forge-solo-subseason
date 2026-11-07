package com.forgemagic.fabric;

import com.forgemagic.api.ClassId;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigInteger;
import java.util.Map;

/** Server-side grants keyed by Floodgate XUID, never by a mutable username. */
public final class FernAdminPolicy {
    private final Map<String, Object> grants;

    private FernAdminPolicy(Map<String, Object> grants) { this.grants = grants; }

    public static FernAdminPolicy load() {
        Path path = Path.of("config", "forge-admins.yml");
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) Files.writeString(path, "admins: {}\n");
            Object value = new Yaml().load(Files.readString(path));
            if (value instanceof Map<?, ?> root && root.get("admins") instanceof Map<?, ?> admins) {
                @SuppressWarnings("unchecked") Map<String, Object> cast = (Map<String, Object>) admins;
                return new FernAdminPolicy(cast);
            }
        } catch (IOException | RuntimeException error) {
            ForgeSoloFabric.LOGGER.warn("Could not load forge-admins.yml; no automatic admin grants will be applied.", error);
        }
        return new FernAdminPolicy(Map.of());
    }

    private Map<?, ?> grant(ServerPlayer player) {
        String xuid = BedrockCompatibility.xuid(player.getUUID());
        if (xuid == null) return Map.of();
        for (Map.Entry<String, Object> entry : grants.entrySet()) {
            if (xuidMatches(entry.getKey(), xuid) && entry.getValue() instanceof Map<?, ?> map) return map;
        }
        return Map.of();
    }

    /** Floodgate may expose an XUID as decimal while the deployment file commonly uses hexadecimal. */
    private static boolean xuidMatches(String configured, String runtime) {
        if (configured == null || runtime == null) return false;
        if (configured.equalsIgnoreCase(runtime)) return true;
        try {
            BigInteger configuredValue = new BigInteger(configured.replaceFirst("^0[xX]", ""), 16);
            BigInteger runtimeValue = runtime.matches("[0-9]+") ? new BigInteger(runtime, 10) : new BigInteger(runtime, 16);
            return configuredValue.equals(runtimeValue);
        } catch (RuntimeException ignored) { return false; }
    }

    public boolean isGranted(ServerPlayer player) { return !grant(player).isEmpty(); }
    public boolean isOperator(ServerPlayer player) { return bool(grant(player), "operator", false); }
    public ClassId classFor(ServerPlayer player, ClassId fallback) {
        try { return ClassId.valueOf(string(grant(player), "class", fallback.name()).toUpperCase()); }
        catch (RuntimeException ignored) { return fallback; }
    }
    public int playerLevel(ServerPlayer player) { return integer(grant(player), "level", 1, 1, 1000); }
    public int skillLevel(ServerPlayer player) { return integer(grant(player), "skill-level", 1, 1, 25); }

    public void apply(MinecraftServer server, ServerPlayer player) {
        if (isOperator(player) && !server.getPlayerList().isOp(player.nameAndId())) {
            server.getPlayerList().op(player.nameAndId());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6Fern admin access enabled for your verified Bedrock XUID."));
        }
    }

    private static String string(Map<?, ?> map, String key, String fallback) { Object value = map.get(key); return value == null ? fallback : String.valueOf(value); }
    private static boolean bool(Map<?, ?> map, String key, boolean fallback) { Object value = map.get(key); return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value)); }
    private static int integer(Map<?, ?> map, String key, int fallback, int min, int max) { try { Object value = map.containsKey(key) ? map.get(key) : fallback; return Math.max(min, Math.min(max, Integer.parseInt(String.valueOf(value)))); } catch (RuntimeException ignored) { return fallback; } }
}
