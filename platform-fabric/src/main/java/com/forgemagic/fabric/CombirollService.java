package com.forgemagic.fabric;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative two-material Combiroll crafting and casting. */
public final class CombirollService {
    private static final long DEFAULT_COOLDOWN_MILLIS = 45_000L;
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> owned = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Map<Material, net.minecraft.world.item.Item> items = new EnumMap<>(Material.class);

    public CombirollService() {
        items.put(Material.COPPER, Items.COPPER_INGOT);
        items.put(Material.IRON, Items.IRON_INGOT);
        items.put(Material.GOLD, Items.GOLD_INGOT);
        items.put(Material.AMETHYST, Items.AMETHYST_SHARD);
        items.put(Material.DIAMOND, Items.DIAMOND);
        items.put(Material.NETHERITE, Items.NETHERITE_INGOT);
        definition(Material.COPPER, Material.IRON, "Conductive Bulwark", Rarity.COMMON, Effect.BULWARK, 2.0);
        definition(Material.COPPER, Material.GOLD, "Fortune Current", Rarity.UNCOMMON, Effect.FORTUNE, 2.5);
        definition(Material.COPPER, Material.AMETHYST, "Resonant Coil", Rarity.RARE, Effect.RESONANCE, 3.0);
        definition(Material.COPPER, Material.DIAMOND, "Prismatic Circuit", Rarity.EPIC, Effect.PRISM, 4.0);
        definition(Material.COPPER, Material.NETHERITE, "Stormforged Core", Rarity.MYTHIC, Effect.STORM, 5.0);
        definition(Material.IRON, Material.GOLD, "Gilded Bastion", Rarity.UNCOMMON, Effect.BASTION, 3.0);
        definition(Material.IRON, Material.AMETHYST, "Crystal Guard", Rarity.RARE, Effect.CRYSTAL_GUARD, 3.5);
        definition(Material.IRON, Material.DIAMOND, "Diamond Bulwark", Rarity.EPIC, Effect.DIAMOND_GUARD, 4.5);
        definition(Material.IRON, Material.NETHERITE, "Infernal Bastion", Rarity.MYTHIC, Effect.INFERNAL, 6.0);
        definition(Material.GOLD, Material.AMETHYST, "Fortune Resonance", Rarity.RARE, Effect.FORTUNE_RESONANCE, 4.0);
        definition(Material.GOLD, Material.DIAMOND, "Solar Prism", Rarity.EPIC, Effect.SOLAR_PRISM, 5.0);
        definition(Material.GOLD, Material.NETHERITE, "Crown of Embers", Rarity.MYTHIC, Effect.EMBER_CROWN, 7.0);
        definition(Material.AMETHYST, Material.DIAMOND, "Arcane Prism", Rarity.EPIC, Effect.ARCANE_PRISM, 6.0);
        definition(Material.AMETHYST, Material.NETHERITE, "Void Resonator", Rarity.MYTHIC, Effect.VOID_RESONATOR, 8.0);
        definition(Material.DIAMOND, Material.NETHERITE, "Astral Breaker", Rarity.LEGENDARY, Effect.ASTRAL_BREAKER, 10.0);
        load();
    }

    public void list(ServerPlayer player) {
        Set<String> playerOwned = owned.getOrDefault(player.getUUID(), Set.of());
        player.sendSystemMessage(Component.literal("§6Combirolls §8— §f15 two-material combinations"));
        definitions.forEach((key, definition) -> player.sendSystemMessage(Component.literal(
                (playerOwned.contains(key) ? "§a✓ " : "§8• ") + "§e" + key + " §7— §f" + definition.name()
                        + " §8[" + definition.rarity().name().toLowerCase(Locale.ROOT) + "]")));
    }

    public void menu(ServerPlayer player) {
        Set<String> playerOwned = owned.getOrDefault(player.getUUID(), Set.of());
        player.sendSystemMessage(Component.literal("§6§lCOMBIROLL MENU §8— §fClick to craft or cast"));
        definitions.forEach((key, definition) -> {
            boolean has = playerOwned.contains(key);
            String action = has ? "cast" : "craft";
            String label = has ? "§a[ CAST ] " : "§e[ CRAFT ] ";
            player.sendSystemMessage(Component.literal(label + definition.name() + " §8(" + key + ")")
                    .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/forge combiroll " + action + " " + key.replace('+', ' ')))));
        });
        player.sendSystemMessage(Component.literal("§7Crafting consumes one of each material. The server validates ownership and inventory."));
    }

    public void spawn(ServerPlayer player) {
        @SuppressWarnings("unchecked") EntityType<? extends Villager> villagerType = (EntityType<? extends Villager>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:villager"));
        Villager altar = new Villager(villagerType, player.level());
        altar.setPos(player.getX(), player.getY(), player.getZ());
        altar.setCustomName(Component.literal("§dCombiroll Altar"));
        altar.setCustomNameVisible(true);
        altar.setNoAi(true);
        altar.setPersistenceRequired();
        player.level().addFreshEntity(altar);
        player.sendSystemMessage(Component.literal("§dCombiroll Altar spawned. Use §f/forge combiroll list§d, then craft with the two materials."));
    }

    public boolean craft(ServerPlayer player, String first, String second) {
        Definition definition = find(first, second);
        if (definition == null) {
            player.sendSystemMessage(Component.literal("§cChoose two different materials: copper, iron, gold, amethyst, diamond, netherite."));
            return false;
        }
        Set<String> playerOwned = owned.computeIfAbsent(player.getUUID(), ignored -> ConcurrentHashMap.newKeySet());
        if (playerOwned.contains(definition.key())) {
            player.sendSystemMessage(Component.literal("§eYou already own §f" + definition.name() + "§e."));
            return false;
        }
        if (!takeItem(player, items.get(definition.first())) || !takeItem(player, items.get(definition.second()))) {
            if (items.get(definition.first()) != items.get(definition.second())) giveItem(player, items.get(definition.first()));
            player.sendSystemMessage(Component.literal("§cYou need one " + definition.first().displayName() + " and one " + definition.second().displayName() + "."));
            return false;
        }
        playerOwned.add(definition.key());
        save();
        ItemStack receipt = new ItemStack(Items.PAPER);
        receipt.set(DataComponents.CUSTOM_NAME, Component.literal(definition.name() + " Combiroll"));
        player.addItem(receipt);
        player.sendSystemMessage(Component.literal("§aCrafted §f" + definition.name() + " §7(" + definition.rarity().name().toLowerCase(Locale.ROOT) + ")§a. Use §f/forge combiroll cast " + definition.key().replace('+', ' ') + "§a."));
        return true;
    }

    public boolean cast(ServerPlayer player, String first, String second) {
        Definition definition = find(first, second);
        if (definition == null) { player.sendSystemMessage(Component.literal("§cUnknown Combiroll combination.")); return false; }
        if (!owned.getOrDefault(player.getUUID(), Set.of()).contains(definition.key())) {
            player.sendSystemMessage(Component.literal("§cCraft this Combiroll first with /forge combiroll craft " + definition.key().replace('+', ' ') + "."));
            return false;
        }
        long now = System.currentTimeMillis();
        long readyAt = cooldowns.getOrDefault(player.getUUID(), Map.of()).getOrDefault(definition.key(), 0L);
        if (readyAt > now) {
            player.sendSystemMessage(Component.literal("§cCombiroll cooling down for " + ((readyAt - now + 999) / 1000) + "s."));
            return false;
        }
        int level = Math.max(1, player.experienceLevel);
        double scale = Math.min(2.0, 1.0 + level / 100.0);
        if (!apply(player, definition, scale)) return false;
        cooldowns.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>()).put(definition.key(), now + DEFAULT_COOLDOWN_MILLIS);
        player.sendSystemMessage(Component.literal("§d✦ §f" + definition.name() + " §7[" + definition.rarity().name().toLowerCase(Locale.ROOT) + "] §dactivated."));
        return true;
    }

    public String hudStatus(ServerPlayer player) {
        Set<String> playerOwned = owned.getOrDefault(player.getUUID(), Set.of());
        if (playerOwned.isEmpty()) return "none~0";
        String key = playerOwned.stream().sorted().findFirst().orElse("none");
        long readyAt = cooldowns.getOrDefault(player.getUUID(), Map.of()).getOrDefault(key, 0L);
        return key + "~" + Math.max(0, (readyAt - System.currentTimeMillis() + 999) / 1000);
    }

    public void save() {
        try {
            Files.createDirectories(Path.of("config"));
            Properties properties = new Properties();
            owned.forEach((uuid, values) -> properties.setProperty(uuid.toString(), String.join(",", values)));
            try (OutputStream out = Files.newOutputStream(Path.of("config", "forge-combirolls.properties"))) {
                properties.store(out, "Fern Combiroll ownership; server authoritative");
            }
        } catch (Exception error) { ForgeSoloFabric.LOGGER.warn("Could not save Combiroll ownership", error); }
    }

    private void load() {
        Path path = Path.of("config", "forge-combirolls.properties");
        if (!Files.exists(path)) return;
        try (InputStream in = Files.newInputStream(path)) {
            Properties properties = new Properties(); properties.load(in);
            for (String name : properties.stringPropertyNames()) {
                try {
                    UUID uuid = UUID.fromString(name);
                    Set<String> values = ConcurrentHashMap.newKeySet();
                    for (String value : properties.getProperty(name, "").split(",")) {
                        String key = value.trim().toLowerCase(Locale.ROOT);
                        if (definitions.containsKey(key)) values.add(key);
                    }
                    if (!values.isEmpty()) owned.put(uuid, values);
                } catch (IllegalArgumentException ignored) { }
            }
        } catch (Exception error) { ForgeSoloFabric.LOGGER.warn("Could not load Combiroll ownership", error); }
    }

    private boolean apply(ServerPlayer player, Definition definition, double scale) {
        int duration = (int) Math.min(600, 160 + definition.rarity().ordinal() * 40);
        int amplifier = Math.max(0, Math.min(2, definition.rarity().ordinal() / 2));
        switch (definition.effect()) {
            case BULWARK, BASTION, CRYSTAL_GUARD, DIAMOND_GUARD, INFERNAL -> {
                player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, duration, amplifier));
                player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration, amplifier));
            }
            case FORTUNE, FORTUNE_RESONANCE, SOLAR_PRISM -> {
                player.addEffect(new MobEffectInstance(MobEffects.LUCK, duration, amplifier));
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, duration, amplifier));
            }
            case RESONANCE, PRISM, ARCANE_PRISM, VOID_RESONATOR -> {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, duration, amplifier));
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 0));
            }
            case STORM, EMBER_CROWN, ASTRAL_BREAKER -> {
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, duration, amplifier));
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, duration, 0));
                damageNearby(player, (float) Math.min(16.0, definition.damage() * scale));
            }
        }
        player.level().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 28 + definition.rarity().ordinal() * 8, 0.7, 0.9, 0.7, 0.08);
        player.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.4, 0.6, 0.4, 0.04);
        return true;
    }

    private void damageNearby(ServerPlayer player, float damage) {
        for (var entity : player.level().getEntities(player, player.getBoundingBox().inflate(4.5), entity -> entity instanceof LivingEntity && entity.isAlive())) {
            if (entity instanceof LivingEntity target) target.hurt(player.damageSources().playerAttack(player), damage);
        }
    }

    private boolean takeItem(ServerPlayer player, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() == item && !stack.isEmpty()) { stack.shrink(1); return true; }
        }
        return false;
    }

    private void giveItem(ServerPlayer player, net.minecraft.world.item.Item item) { player.addItem(new ItemStack(item)); }
    private void definition(Material first, Material second, String name, Rarity rarity, Effect effect, double damage) { Definition definition = new Definition(key(first, second), first, second, name, rarity, effect, damage); definitions.put(definition.key(), definition); }
    private Definition find(String first, String second) {
        try {
            return definitions.get(key(Material.parse(first), Material.parse(second)));
        } catch (IllegalArgumentException error) { return null; }
    }
    private static String key(Material first, Material second) { return first.ordinal() < second.ordinal() ? first.name().toLowerCase(Locale.ROOT) + "+" + second.name().toLowerCase(Locale.ROOT) : second.name().toLowerCase(Locale.ROOT) + "+" + first.name().toLowerCase(Locale.ROOT); }

    public enum Material {
        COPPER("copper"), IRON("iron"), GOLD("gold"), AMETHYST("amethyst"), DIAMOND("diamond"), NETHERITE("netherite");
        private final String displayName;
        Material(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
        static Material parse(String input) {
            String value = input == null ? "" : input.toLowerCase(Locale.ROOT).trim().replace('-', '_').replace(' ', '_');
            if (value.startsWith("minecraft:")) value = value.substring("minecraft:".length());
            value = switch (value) {
                case "copper_ingot" -> "copper";
                case "iron_ingot" -> "iron";
                case "gold_ingot" -> "gold";
                case "amethyst_shard" -> "amethyst";
                case "diamond_item" -> "diamond";
                case "netherite_ingot" -> "netherite";
                default -> value;
            };
            return Material.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }
    public enum Rarity { COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC }
    private enum Effect { BULWARK, FORTUNE, RESONANCE, PRISM, STORM, BASTION, CRYSTAL_GUARD, DIAMOND_GUARD, INFERNAL, FORTUNE_RESONANCE, SOLAR_PRISM, EMBER_CROWN, ARCANE_PRISM, VOID_RESONATOR, ASTRAL_BREAKER }
    private record Definition(String key, Material first, Material second, String name, Rarity rarity, Effect effect, double damage) { }
}
