package com.forgemagic.fabric;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stable item-id catalog; all monetary fields are integer centavos, never display names. */
public final class EconomyPriceRegistry {
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Path path;

    public EconomyPriceRegistry(Path path) { this.path = path; load(); }
    public Optional<Entry> find(String itemId) { return Optional.ofNullable(entries.get(itemId)); }
    public Optional<Entry> find(Item item) { return find(BuiltInRegistries.ITEM.getKey(item).toString()); }
    public Collection<Entry> entries() { return List.copyOf(entries.values()); }

    private void load() {
        try {
            Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                try (InputStream in = EconomyPriceRegistry.class.getResourceAsStream("/forge-market-catalog.csv")) {
                    if (in == null) throw new IOException("embedded market catalog is missing");
                    Files.copy(in, path);
                }
            }
            Map<String, Entry> loaded = new LinkedHashMap<>();
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    String row = line.trim();
                    if (row.isEmpty() || row.startsWith("#") || row.startsWith("item_id,")) continue;
                    String[] f = row.split(",", -1);
                    if (f.length < 11) throw new IllegalArgumentException("expected eleven columns at line " + lineNumber);
                    String itemId = f[0].trim();
                    Identifier identifier = Identifier.parse(itemId);
                    Item item = BuiltInRegistries.ITEM.getValue(identifier);
                    if (item == null || item == net.minecraft.world.item.Items.AIR)
                        throw new IllegalArgumentException("unknown item id '" + itemId + "' at line " + lineNumber);
                    long base = Long.parseLong(f[1].trim());
                    long npcBuy = Long.parseLong(f[2].trim());
                    long npcSell = Long.parseLong(f[3].trim());
                    boolean tradeable = Boolean.parseBoolean(f[4].trim());
                    int initialStock = Integer.parseInt(f[5].trim());
                    int stockCapacity = Integer.parseInt(f[6].trim());
                    int playerSellLimit = Integer.parseInt(f[7].trim());
                    int playerBuyLimit = Integer.parseInt(f[8].trim());
                    boolean npcBuys = Boolean.parseBoolean(f[9].trim());
                    boolean npcSells = Boolean.parseBoolean(f[10].trim());
                    if (base < 1 || base > 1_000_000_000L || npcBuy < 0 || npcSell < 0
                            || (npcBuys && npcBuy < 1) || (npcSells && npcSell < 1)
                            || (npcBuys && npcSells && npcBuy >= npcSell)
                            || initialStock < 0 || stockCapacity < 0 || initialStock > stockCapacity
                            || stockCapacity > 1_000_000 || playerSellLimit < 0 || playerBuyLimit < 0
                            || (npcBuys && playerSellLimit == 0) || (npcSells && playerBuyLimit == 0))
                        throw new IllegalArgumentException("out-of-range or inconsistent values at line " + lineNumber);
                    Entry entry = new Entry(itemId, item, base, npcBuy, npcSell, tradeable,
                            initialStock, stockCapacity, playerSellLimit, playerBuyLimit, npcBuys, npcSells);
                    if (loaded.putIfAbsent(itemId, entry) != null)
                        throw new IllegalArgumentException("duplicate stable item id '" + itemId + "'");
                }
            }
            entries.clear();
            entries.putAll(loaded);
            ForgeSoloFabric.LOGGER.info("Loaded {} item prices from {}", entries.size(), path);
        } catch (Exception error) {
            ForgeSoloFabric.LOGGER.error("Market catalog could not be loaded; NPC trading is fail-closed", error);
            entries.clear();
        }
    }

    public record Entry(String itemId, Item item, long baseCentavos, long npcBuyCentavos, long npcSellCentavos,
                        boolean tradeable, int initialStock, int stockCapacity,
                        int playerSellDailyLimit, int playerBuyDailyLimit, boolean npcBuys, boolean npcSells) { }
}
