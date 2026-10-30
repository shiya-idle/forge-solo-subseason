package com.forgemagic.fabric;

import java.lang.reflect.Method;
import java.util.UUID;

/** Optional Floodgate integration without making the Fabric mod depend on Geyser/Floodgate jars. */
public final class BedrockCompatibility {
    private BedrockCompatibility() { }

    public static boolean isBedrockPlayer(UUID uuid) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            Method isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            return Boolean.TRUE.equals(isFloodgatePlayer.invoke(api, uuid));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
