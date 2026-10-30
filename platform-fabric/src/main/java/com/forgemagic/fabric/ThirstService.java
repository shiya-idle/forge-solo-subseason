package com.forgemagic.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ThirstService {
    private static final double MAX = 100.0;
    private final Map<UUID, Double> thirst = new ConcurrentHashMap<>();

    public double value(UUID player) { return thirst.getOrDefault(player, MAX); }
    public void restore(UUID player, double amount) { thirst.put(player, Math.min(MAX, value(player) + Math.max(0, amount))); }
    public boolean drinkWaterBottle(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.getItem() != Items.GLASS_BOTTLE && held.getItem() != Items.POTION) return false;
        if (held.getCount() == 1) player.setItemInHand(player.getUsedItemHand(), new ItemStack(Items.GLASS_BOTTLE));
        else held.shrink(1);
        restore(player.getUUID(), 35.0);
        return true;
    }

    /** Called once per second. Empty thirst costs exactly 2% max health per second. */
    public void tick(ServerPlayer player) {
        double current = Math.max(0, value(player.getUUID()) - 0.75);
        thirst.put(player.getUUID(), current);
        if (current <= 0.0 && player.isAlive()) player.hurt(player.damageSources().generic(), player.getMaxHealth() * 0.02f);
    }

    public String bar(UUID player) { return bar(value(player), MAX); }
    public static String bar(double current, double maximum) {
        int filled = (int) Math.round(Math.max(0, Math.min(10, current / maximum * 10)));
        return "§b" + "▰".repeat(filled) + "§8" + "▱".repeat(10 - filled);
    }
}
