package com.forgemagic.fabric;

import com.forgemagic.claims.ImmutableClaimsService;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Durable append-only claim storage. Writes are atomic so a crash cannot truncate the live file. */
public final class ClaimPersistence {
    private final Path path;
    private final Yaml yaml;

    public ClaimPersistence(Path path) {
        this.path = path;
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        this.yaml = new Yaml(options);
    }

    public int loadInto(ImmutableClaimsService claims) {
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) { save(claims); return 0; }
            Object parsed = new Yaml().load(Files.readString(path));
            if (!(parsed instanceof Map<?, ?> root) || !(root.get("claims") instanceof List<?> rows)) return 0;
            int loaded = 0;
            for (Object row : rows) if (row instanceof Map<?, ?> value) {
                try {
                    UUID id = UUID.fromString(text(value, "id"));
                    UUID owner = UUID.fromString(text(value, "owner"));
                    String world = text(value, "world");
                    ImmutableClaimsService.Cuboid area = new ImmutableClaimsService.Cuboid(
                            integer(value, "min-x"), integer(value, "min-y"), integer(value, "min-z"),
                            integer(value, "max-x"), integer(value, "max-y"), integer(value, "max-z"));
                    if (claims.restoreClaim(new ImmutableClaimsService.LandClaim(id, owner, world, area))) loaded++;
                } catch (RuntimeException invalid) {
                    ForgeSoloFabric.LOGGER.warn("Skipped malformed persistent claim entry.");
                }
            }
            return loaded;
        } catch (IOException | RuntimeException error) {
            ForgeSoloFabric.LOGGER.error("Claim storage could not be read; refusing to create new claims until storage is writable.", error);
            return -1;
        }
    }

    public synchronized boolean save(ImmutableClaimsService claims) {
        try {
            Files.createDirectories(path.getParent());
            List<Map<String, Object>> rows = new ArrayList<>();
            for (ImmutableClaimsService.LandClaim claim : claims.claims()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", claim.id().toString()); row.put("owner", claim.owner().toString()); row.put("world", claim.world());
                row.put("min-x", claim.area().minX()); row.put("min-y", claim.area().minY()); row.put("min-z", claim.area().minZ());
                row.put("max-x", claim.area().maxX()); row.put("max-y", claim.area().maxY()); row.put("max-z", claim.area().maxZ());
                rows.add(row);
            }
            Map<String, Object> root = Map.of("format", 1, "claims", rows);
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, yaml.dump(root));
            try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException | RuntimeException error) {
            ForgeSoloFabric.LOGGER.error("Claim storage could not be written; claim creation must remain disabled.", error);
            return false;
        }
    }

    private static String text(Map<?, ?> value, String key) { Object result = value.get(key); if (result == null || String.valueOf(result).isBlank()) throw new IllegalArgumentException(key); return String.valueOf(result); }
    private static int integer(Map<?, ?> value, String key) { Object result = value.get(key); return Integer.parseInt(String.valueOf(result)); }
}
