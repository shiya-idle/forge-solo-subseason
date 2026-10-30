package com.forgemagic.fabric;

import com.forgemagic.api.ClassId;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public record FabricSkillConfig(
        String version,
        boolean resourcePackRequired,
        boolean visualsEnabled,
        double particleDensity,
        int maxParticlesPerCast,
        boolean layeredParticles,
        boolean ringsEnabled,
        int ringPoints,
        double ringRadius,
        boolean combatHighlights,
        boolean castTrail,
        boolean impactWave,
        boolean damageFlash,
        int maxDurationTicks,
        int maxTargetRadius,
        double pvpScale,
        boolean pvpArenaEnabled,
        String pvpArenaWorld,
        int pvpArenaMaxTargetRange,
        double pvpArenaTargetRadius,
        double manaRegenPerSecond,
        boolean purgeEffectsOnModBuildings,
        ClassId defaultClass,
        boolean worldBorderEnabled,
        double worldBorderCenterX,
        double worldBorderCenterZ,
        double worldBorderDiameter
) {
    public static FabricSkillConfig load() {
        Path path = Path.of("config", "forge-solo-skills.yml");
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) try (InputStream in = FabricSkillConfig.class.getResourceAsStream("/forge-solo-skills.yml")) {
                if (in != null) Files.copy(in, path);
            }
            if (Files.exists(path)) {
                Object parsed = new Yaml().load(Files.readString(path));
                if (parsed instanceof Map<?, ?> root) return from(root);
            }
        } catch (IOException | RuntimeException ignored) {
            ForgeSoloFabric.LOGGER.warn("Could not read forge-solo-skills.yml; using safe defaults.");
        }
        return defaults();
    }

    private static FabricSkillConfig from(Map<?, ?> root) {
        Map<?, ?> resource = map(root.get("resource-pack"));
        Map<?, ?> visuals = map(root.get("visuals"));
        Map<?, ?> pvp = map(root.get("pvp"));
        Map<?, ?> arena = map(root.get("pvp-arena"));
        Map<?, ?> arenaVisuals = map(arena.get("visual-effects"));
        Map<?, ?> castIdentifier = map(arena.get("cast-identifier"));
        Map<?, ?> mana = map(root.get("mana"));
        Map<?, ?> worldBorder = map(root.get("world-border"));
        Map<?, ?> skills = map(root.get("skills"));
        Map<?, ?> effects = map(skills.get("effects"));
        return new FabricSkillConfig(
                string(root, "version", "1"),
                bool(resource, "required", true),
                bool(visuals, "enabled", true),
                bounded(number(visuals, "particle-density", 1.0), 0.1, 2.0),
                (int) bounded(number(visuals, "max-particles-per-cast", 160), 16, 400),
                bool(visuals, "layered-particles", true),
                bool(visuals, "rings-enabled", true),
                (int) bounded(number(visuals, "ring-points", 20), 8, 48),
                bounded(number(visuals, "ring-radius", 1.6), 0.5, 4.0),
                bool(visuals, "combat-highlights", true),
                bool(visuals, "cast-trail", true),
                bool(visuals, "impact-wave", true),
                bool(visuals, "damage-flash", true),
                (int) bounded(number(effects, "max-duration-ticks", 1200), 20, 1200),
                (int) bounded(number(effects, "max-target-radius", 12), 2, 32),
                bounded(number(pvp, "potency-scale", 0.6), 0.0, 1.0),
                bool(arena, "enabled", true),
                string(arena, "world", "forge_pvp_arena"),
                (int) bounded(number(castIdentifier, "max-target-range", 12), 2, 32),
                bounded(number(arenaVisuals, "ring-radius", 1.35), 0.35, 4.0),
                bounded(number(mana, "regen-per-second", 2.0), 0.0, 20.0),
                bool(map(root.get("claims")), "purge-effects-on-mod-buildings", true),
                parseClass(string(root, "default-class", "HEALER")),
                bool(worldBorder, "enabled", true),
                bounded(number(worldBorder, "center-x", 0.0), -29_999_984, 29_999_984),
                bounded(number(worldBorder, "center-z", 0.0), -29_999_984, 29_999_984),
                bounded(number(worldBorder, "diameter-blocks", 1_000_000.0), 1.0, 59_999_968.0)
        );
    }

    public static FabricSkillConfig defaults() { return new FabricSkillConfig("1", true, true, 1.0, 160, true, true, 20, 1.6, true, true, true, true, 1200, 12, 0.6, true, "forge_pvp_arena", 12, 1.35, 2.0, true, ClassId.HEALER, true, 0.0, 0.0, 1_000_000.0); }
    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> map ? map : Map.of(); }
    private static String string(Map<?, ?> map, String key, String fallback) { Object value = map.get(key); return value == null ? fallback : String.valueOf(value); }
    private static boolean bool(Map<?, ?> map, String key, boolean fallback) { Object value = map.get(key); return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value)); }
    private static double number(Map<?, ?> map, String key, double fallback) { try { Object value = map.containsKey(key) ? map.get(key) : fallback; return Double.parseDouble(String.valueOf(value)); } catch (RuntimeException e) { return fallback; } }
    private static double bounded(double value, double min, double max) { return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min; }
    private static ClassId parseClass(String value) { try { return ClassId.valueOf(value.toUpperCase()); } catch (RuntimeException e) { return ClassId.HEALER; } }
}
