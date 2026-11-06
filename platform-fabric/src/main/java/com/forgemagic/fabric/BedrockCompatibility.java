package com.forgemagic.fabric;

import java.lang.reflect.Method;
import java.util.UUID;

/** Optional Floodgate integration without making the Fabric mod depend on Geyser/Floodgate jars. */
public final class BedrockCompatibility {
    private BedrockCompatibility() { }

    public static boolean isBedrockPlayer(UUID uuid) {
        return xuid(uuid) != null;
    }

    public static String xuid(UUID uuid) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            Object floodgatePlayer = apiClass.getMethod("getPlayer", UUID.class).invoke(api, uuid);
            if (floodgatePlayer == null) return null;
            Object xuid = floodgatePlayer.getClass().getMethod("getXuid").invoke(floodgatePlayer);
            return xuid == null ? null : String.valueOf(xuid);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }
}
