package com.forgemagic.fabric;

import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Spawnable Forge vendor with finite, rarity-based stock. */
public final class ForgeVendorService {
    private final Map<String, Offer> offers = new LinkedHashMap<>();
    private final Map<String, Stock> stock = new LinkedHashMap<>();
    private final PlayerProgressionModifiers modifiers;

    public ForgeVendorService(PlayerProgressionModifiers modifiers) {
        this.modifiers = modifiers;
        offer("cooldown_scroll_small", "Cooldown Scroll I", Items.PAPER, 8, Rarity.COMMON, "Reduces one skill cooldown by 5%.");
        offer("cooldown_scroll_large", "Cooldown Scroll II", Items.PAPER, 20, Rarity.UNCOMMON, "Reduces one skill cooldown by 12%.");
        offer("mana_regen_potion", "Mana Regeneration Potion", Items.POTION, 12, Rarity.COMMON, "Restores 35 mana over time.");
        offer("anti_bottleneck_i", "Anti-Bottleneck Scroll I", Items.PAPER, 10, Rarity.COMMON, "Level-scaled progression protection, levels 1-20.");
        offer("anti_bottleneck_ii", "Anti-Bottleneck Scroll II", Items.PAPER, 24, Rarity.UNCOMMON, "Level-scaled progression protection, levels 21-50.");
        offer("anti_bottleneck_iii", "Anti-Bottleneck Scroll III", Items.PAPER, 48, Rarity.RARE, "Level-scaled progression protection, levels 51+.");
        offer("xp_multiplier_1_25", "XP Multiplier 1.25x", Items.EXPERIENCE_BOTTLE, 18, Rarity.UNCOMMON, "Adds a bounded 1.25x XP multiplier.");
        offer("xp_multiplier_1_5", "XP Multiplier 1.5x", Items.EXPERIENCE_BOTTLE, 36, Rarity.RARE, "Adds a bounded 1.5x XP multiplier.");
        offer("health_multiplier_1_25", "Health Multiplier 1.25x", Items.GOLDEN_APPLE, 30, Rarity.UNCOMMON, "Adds a bounded 1.25x health multiplier.");
        offer("health_multiplier_1_5", "Health Multiplier 1.5x", Items.ENCHANTED_GOLDEN_APPLE, 80, Rarity.LEGENDARY, "Adds a bounded 1.5x health multiplier.");
        load();
    }

    public void spawn(ServerPlayer player) {
        @SuppressWarnings("unchecked") EntityType<? extends Villager> villagerType = (EntityType<? extends Villager>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:villager"));
        Villager npc = new Villager(villagerType, player.level());
        npc.setPos(player.getX(), player.getY(), player.getZ());
        npc.setCustomName(Component.literal("§6Forge Progression Vendor"));
        npc.setCustomNameVisible(true);
        npc.setNoAi(true);
        npc.setPersistenceRequired();
        player.level().addFreshEntity(npc);
        player.sendSystemMessage(Component.literal("§aForge Progression Vendor spawned."));
    }

    public void tick(long now) {
        boolean changed = false;
        for (Stock entry : stock.values()) {
            if (entry.available() >= entry.maxStock() || now < entry.nextRestockMillis()) continue;
            entry.restock(now);
            changed = true;
        }
        if (changed) save();
    }

    public void list(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6Forge Vendor §8— §fFinite stock; rare goods return slowly"));
        long now = System.currentTimeMillis();
        offers.forEach((id, offer) -> {
            Stock s = stock.get(id);
            long wait = Math.max(0, (s.nextRestockMillis() - now) / 1000);
            player.sendSystemMessage(Component.literal("§e" + id + " §7— §f" + offer.name() + " §8[" + offer.price() + " emeralds] §bstock " + s.available() + "/" + s.maxStock() + " §7" + offer.rarity().name().toLowerCase(Locale.ROOT) + (wait > 0 ? " · restock " + wait + "s" : "")));
        });
    }

    public boolean buy(ServerPlayer player, String id, int amount) {
        String key = id.toLowerCase(Locale.ROOT);
        Offer offer = offers.get(key);
        Stock available = stock.get(key);
        if (offer == null || available == null || amount < 1 || amount > 64) { player.sendSystemMessage(Component.literal("§cUnknown offer or invalid amount.")); return false; }
        if (available.available() < amount) { player.sendSystemMessage(Component.literal("§cThat item is out of stock. Check /forge shop list.")); return false; }
        long total = (long) offer.price() * amount;
        if (total > Integer.MAX_VALUE || !takeEmeralds(player, (int) total)) { player.sendSystemMessage(Component.literal("§cYou need " + total + " emeralds.")); return false; }
        available.consume(amount);
        save();
        player.addItem(new ItemStack(offer.item(), amount));
        for (int i = 0; i < amount; i++) modifiers.apply(player, key);
        player.sendSystemMessage(Component.literal("§aPurchased §f" + offer.name() + " §ax" + amount + "§a. Stock remaining: §f" + available.available()));
        return true;
    }

    public Map<String, Offer> offers() { return Map.copyOf(offers); }
    public void save() { try { Properties properties = new Properties(); stock.forEach((id, value) -> { properties.setProperty(id + ".available", Integer.toString(value.available())); properties.setProperty(id + ".next", Long.toString(value.nextRestockMillis())); }); Files.createDirectories(Path.of("config")); try (var out = Files.newOutputStream(Path.of("config", "forge-vendor-stock.properties"))) { properties.store(out, "Forge vendor stock; do not edit while the server is running"); } } catch (Exception error) { ForgeSoloFabric.LOGGER.warn("Could not persist vendor stock", error); } }
    private void load() { Path path = Path.of("config", "forge-vendor-stock.properties"); if (!Files.exists(path)) return; try (var in = Files.newInputStream(path)) { Properties properties = new Properties(); properties.load(in); stock.forEach((id, value) -> { value.available = Math.max(0, Math.min(value.maxStock, Integer.parseInt(properties.getProperty(id + ".available", Integer.toString(value.available))))); value.nextRestockMillis = Long.parseLong(properties.getProperty(id + ".next", Long.toString(value.nextRestockMillis))); }); } catch (Exception error) { ForgeSoloFabric.LOGGER.warn("Could not load vendor stock; safe defaults retained", error); } }
    private void offer(String id, String name, Item item, int price, Rarity rarity, String description) { offers.put(id, new Offer(name, item, price, rarity, description)); stock.put(id, new Stock(rarity.maxStock(), rarity.maxStock(), rarity.intervalMillis(), System.currentTimeMillis() + rarity.intervalMillis())); }
    private boolean takeEmeralds(ServerPlayer player, int amount) {
        int available = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) { ItemStack stack = player.getInventory().getItem(slot); if (stack.getItem() == Items.EMERALD) available += stack.getCount(); }
        if (available < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) { ItemStack stack = player.getInventory().getItem(slot); if (stack.getItem() != Items.EMERALD) continue; int remove = Math.min(remaining, stack.getCount()); stack.shrink(remove); remaining -= remove; }
        return remaining == 0;
    }

    public record Offer(String name, Item item, int price, Rarity rarity, String description) { }
    public enum Rarity { COMMON(32, 10 * 60_000L), UNCOMMON(16, 20 * 60_000L), RARE(8, 45 * 60_000L), LEGENDARY(2, 90 * 60_000L); private final int maxStock; private final long intervalMillis; Rarity(int maxStock, long intervalMillis) { this.maxStock = maxStock; this.intervalMillis = intervalMillis; } public int maxStock() { return maxStock; } public long intervalMillis() { return intervalMillis; } }
    private static final class Stock {
        private final int maxStock; private int available; private final long intervalMillis; private long nextRestockMillis;
        private Stock(int maxStock, int available, long intervalMillis, long nextRestockMillis) { this.maxStock = maxStock; this.available = available; this.intervalMillis = intervalMillis; this.nextRestockMillis = nextRestockMillis; }
        int maxStock() { return maxStock; } int available() { return available; }
        void consume(int amount) { available -= amount; }
        void restock(long now) { available = maxStock; nextRestockMillis = now + intervalMillis; }
        long nextRestockMillis() { return nextRestockMillis; }
    }
}
