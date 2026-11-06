package com.forgemagic.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ThirstService {
    private static final double MAX = 100.0;
    private static final double DRAIN_PER_SECOND = 0.35;
    private static final double WATER_CONTACT_RESTORE_PER_SECOND = 4.0;
    private static final double WATER_DRINK_RESTORE = 35.0;
    private final Map<UUID, Double> thirst = new ConcurrentHashMap<>();

    public double value(UUID player) { return thirst.getOrDefault(player, MAX); }
    public void restore(UUID player, double amount) { thirst.put(player, Math.min(MAX, value(player) + Math.max(0, amount))); }
    public boolean drinkWaterBottle(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.getItem() == Items.GLASS_BOTTLE) {
            if (!player.isInWater()) return false;
            restore(player.getUUID(), WATER_DRINK_RESTORE);
            return true;
        }
        if (held.getItem() != Items.POTION || !isPlainWater(held)) return false;
        if (held.getCount() == 1) player.setItemInHand(player.getUsedItemHand(), new ItemStack(Items.GLASS_BOTTLE));
        else held.shrink(1);
        restore(player.getUUID(), WATER_DRINK_RESTORE);
        return true;
    }

    /** Called when a splash potion is thrown; only a plain water potion counts. */
    public boolean splashWaterPotion(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.getItem() != Items.SPLASH_POTION || !isPlainWater(held)) return false;
        restore(player.getUUID(), WATER_DRINK_RESTORE);
        return true;
    }

    private boolean isPlainWater(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && contents.is(Potions.WATER);
    }

    /** Called once per second. Water contact restores thirst; dry players drain slowly. */
    public void tick(ServerPlayer player) {
        if (player.isInWater()) {
            restore(player.getUUID(), WATER_CONTACT_RESTORE_PER_SECOND);
            return;
        }
        double current = Math.max(0, value(player.getUUID()) - DRAIN_PER_SECOND);
        thirst.put(player.getUUID(), current);
        if (current <= 0.0 && player.isAlive()) player.hurt(player.damageSources().generic(), player.getMaxHealth() * 0.02f);
    }

    public String bar(UUID player) { return bar(value(player), MAX); }
    public static String bar(double current, double maximum) {
        int filled = (int) Math.round(Math.max(0, Math.min(10, current / maximum * 10)));
        return "§b" + "▰".repeat(filled) + "§8" + "▱".repeat(10 - filled);
    }
}
