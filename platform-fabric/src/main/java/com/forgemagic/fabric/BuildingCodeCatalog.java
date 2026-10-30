package com.forgemagic.fabric;

import com.forgemagic.claims.ImmutableClaimsService;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class BuildingCodeCatalog {
    private BuildingCodeCatalog() { }

    public static void loadInto(ImmutableClaimsService claims) {
        Path path = Path.of("config", "forge-buildings.yml");
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) Files.writeString(path, "# Add building codes here. Coordinates are inclusive.\n# buildings: []\n");
            Object parsed = new Yaml().load(Files.readString(path));
            if (!(parsed instanceof Map<?, ?> root) || !(root.get("buildings") instanceof List<?> rows)) return;
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> value)) continue;
                String world = text(value, "world", "minecraft:overworld");
                String id = text(value, "id", "unnamed-building");
                var area = new ImmutableClaimsService.Cuboid(integer(value, "min-x"), integer(value, "min-y"), integer(value, "min-z"),
                        integer(value, "max-x"), integer(value, "max-y"), integer(value, "max-z"));
                List<String> effects = value.get("purge-effects") instanceof List<?> list
                        ? list.stream().map(String::valueOf).toList() : List.of("tree-fell", "area-mine", "ore-pull");
                claims.registerProtectedBuilding(world, new ImmutableClaimsService.ProtectedBuilding(id, area, effects));
            }
        } catch (IOException | RuntimeException error) {
            ForgeSoloFabric.LOGGER.warn("Could not load forge-buildings.yml; no building codes were registered.");
        }
    }

    private static String text(Map<?, ?> value, String key, String fallback) { return value.get(key) == null ? fallback : String.valueOf(value.get(key)); }
    private static int integer(Map<?, ?> value, String key) { Object number = value.containsKey(key) ? value.get(key) : 0; return Integer.parseInt(String.valueOf(number)); }
}
