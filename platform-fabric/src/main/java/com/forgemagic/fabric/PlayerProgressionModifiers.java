package com.forgemagic.fabric;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class PlayerProgressionModifiers {
    private static final Identifier HEALTH_MODIFIER = Identifier.fromNamespaceAndPath(ForgeSoloFabric.MOD_ID, "vendor_health_multiplier");
    private final Map<UUID, Double> cooldownReduction = new ConcurrentHashMap<>();
    private final Map<UUID, Double> xpMultiplier = new ConcurrentHashMap<>();
    private final Map<UUID, Double> healthMultiplier = new ConcurrentHashMap<>();
    public PlayerProgressionModifiers() { load(); }

    public void apply(ServerPlayer player, String offerId) {
        UUID id = player.getUUID();
        switch (offerId) {
            case "cooldown_scroll_small" -> cooldownReduction.merge(id, 0.05, (a, b) -> Math.min(0.30, a + b));
            case "cooldown_scroll_large" -> cooldownReduction.merge(id, 0.12, (a, b) -> Math.min(0.30, a + b));
            case "xp_multiplier_1_25" -> xpMultiplier.merge(id, 0.25, (a, b) -> Math.min(1.0, a + b));
            case "xp_multiplier_1_5" -> xpMultiplier.merge(id, 0.50, (a, b) -> Math.min(1.0, a + b));
            case "health_multiplier_1_25" -> { healthMultiplier.merge(id, 0.25, Math::max); setHealthMultiplier(player, healthMultiplier.get(id)); }
            case "health_multiplier_1_5" -> { healthMultiplier.merge(id, 0.50, Math::max); setHealthMultiplier(player, healthMultiplier.get(id)); }
            default -> { }
        }
        save();
    }

    public double cooldownReduction(UUID player) { return cooldownReduction.getOrDefault(player, 0.0); }
    public double xpMultiplier(UUID player) { return 1.0 + xpMultiplier.getOrDefault(player, 0.0); }

    public int scaledExperience(UUID player, int base) { return (int) Math.max(0, Math.round(base * xpMultiplier(player))); }
    public void restore(ServerPlayer player) { double health = healthMultiplier.getOrDefault(player.getUUID(), 0.0); if (health > 0) setHealthMultiplier(player, health); }

    private void setHealthMultiplier(ServerPlayer player, double amount) {
        var attribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) return;
        attribute.removeModifier(HEALTH_MODIFIER);
        attribute.addOrUpdateTransientModifier(new AttributeModifier(HEALTH_MODIFIER, Math.min(0.50, Math.max(0.0, amount)), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }
    private void save() { try { Files.createDirectories(Path.of("config")); Properties p = new Properties(); cooldownReduction.forEach((id, value) -> p.setProperty(id + ".cooldown", Double.toString(value))); xpMultiplier.forEach((id, value) -> p.setProperty(id + ".xp", Double.toString(value))); healthMultiplier.forEach((id, value) -> p.setProperty(id + ".health", Double.toString(value))); try (var out = Files.newOutputStream(Path.of("config", "forge-player-modifiers.properties"))) { p.store(out, "Forge vendor player modifiers"); } } catch (Exception error) { ForgeSoloFabric.LOGGER.warn("Could not persist vendor player modifiers", error); } }
    private void load() { Path path = Path.of("config", "forge-player-modifiers.properties"); if (!Files.exists(path)) return; try (var in = Files.newInputStream(path)) { Properties p = new Properties(); p.load(in); for (String key : p.stringPropertyNames()) { String[] parts = key.split("\\."); if (parts.length != 2) continue; UUID id; try { id = UUID.fromString(parts[0]); } catch (IllegalArgumentException ignored) { continue; } double value = Double.parseDouble(p.getProperty(key)); switch (parts[1]) { case "cooldown" -> cooldownReduction.put(id, value); case "xp" -> xpMultiplier.put(id, value); case "health" -> healthMultiplier.put(id, value); default -> { } } } } catch (Exception error) { ForgeSoloFabric.LOGGER.warn("Could not load vendor player modifiers", error); } }
}
