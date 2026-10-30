package com.forgemagic.fabric.client;

import java.util.Arrays;

/** Client cache populated only by the server HUD snapshot payload. */
public final class ForgeSoloClientState {
    private static volatile float mana;
    private static volatile float maxMana = 100.0f;
    private static volatile float thirst = 100.0f;
    private static volatile float maxThirst = 100.0f;
    private static volatile String className = "NOVICE";
    private static volatile String quality = "medium";
    private static volatile String[] bindings = new String[18];
    private static volatile String combiroll = "none";
    private static volatile int combirollCooldown;
    private static volatile long lastSnapshotMillis;

    private ForgeSoloClientState() { }

    public static void apply(String data) {
        try {
            String[] parts = data.split("\\|", -1);
            if (parts.length < 7 || !"1".equals(parts[0])) return;
            mana = Float.parseFloat(parts[1]);
            maxMana = Math.max(1.0f, Float.parseFloat(parts[2]));
            thirst = Math.max(0.0f, Float.parseFloat(parts[3]));
            maxThirst = Math.max(1.0f, Float.parseFloat(parts[4]));
            className = parts[5];
            quality = parts[6];
            String[] nextBindings = new String[18];
            if (parts.length >= 8 && !parts[7].isEmpty()) {
                String[] values = parts[7].split(",", -1);
                for (int i = 0; i < Math.min(values.length, nextBindings.length); i++) {
                    nextBindings[i] = values[i].isEmpty() ? null : values[i];
                }
            }
            bindings = nextBindings;
            if (parts.length >= 9 && !parts[8].isEmpty()) {
                String[] combirollParts = parts[8].split("~", -1);
                combiroll = combirollParts[0];
                combirollCooldown = combirollParts.length > 1 ? Math.max(0, Integer.parseInt(combirollParts[1])) : 0;
            }
            lastSnapshotMillis = System.currentTimeMillis();
        } catch (RuntimeException ignored) {
            // A malformed snapshot must never break the client render loop.
        }
    }

    public static float mana() { return mana; }
    public static float maxMana() { return maxMana; }
    public static float thirst() { return thirst; }
    public static float maxThirst() { return maxThirst; }
    public static String className() { return className; }
    public static String quality() { return quality; }
    public static String[] bindings() { return Arrays.copyOf(bindings, bindings.length); }
    public static String combiroll() { return combiroll; }
    public static int combirollCooldown() { return combirollCooldown; }
    public static boolean fresh() { return System.currentTimeMillis() - lastSnapshotMillis < 5000L; }
}
