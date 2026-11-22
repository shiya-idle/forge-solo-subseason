package com.forgemagic.fabric;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

/** NPC market with stable item IDs, treasury-funded buybacks, finite stock, and bounded reference movement. */
public final class TomatMarketService {
    private static final long REFRESH_MILLIS = 15 * 60_000L;
    private static final long QUOTE_LIFETIME_MILLIS = 5 * 60_000L;
    private final EconomyPriceRegistry catalog;
    private final TomatLedger ledger;
    private final Path statePath;
    private final Path tradeLog;
    private final Map<String, MarketState> states = new LinkedHashMap<>();
    private final Map<String, PlayerUsage> usage = new HashMap<>();
    private final Map<UUID, Map<String, IssuedQuote>> issuedQuotes = new HashMap<>();

    public TomatMarketService(EconomyPriceRegistry catalog, TomatLedger ledger, Path statePath, Path tradeLog) {
        this.catalog = catalog;
        this.ledger = ledger;
        this.statePath = statePath;
        this.tradeLog = tradeLog;
        load();
    }

    public static boolean isMarketNpc(net.minecraft.world.entity.Entity entity) {
        return entity.getCustomName() != null && entity.getCustomName().getString().contains("Tomat Market");
    }

    public void spawn(ServerPlayer player) {
        @SuppressWarnings("unchecked") EntityType<? extends Villager> villagerType =
                (EntityType<? extends Villager>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:villager"));
        Villager npc = new Villager(villagerType, player.level());
        npc.setPos(player.getX(), player.getY(), player.getZ());
        npc.setCustomName(Component.literal("§6§lTomat Market"));
        npc.setCustomNameVisible(true);
        npc.setNoAi(true);
        npc.setPersistenceRequired();
        player.level().addFreshEntity(npc);
        player.sendSystemMessage(Component.literal("§aTomat Market NPC spawned. Players can right-click it or use §f/market§a."));
    }

    public synchronized Optional<MarketQuote> quote(String itemId, long now) {
        EconomyPriceRegistry.Entry entry = catalog.find(itemId).orElse(null);
        MarketState state = states.get(itemId);
        if (entry == null || state == null) return Optional.empty();
        long bid = entry.npcBuys() ? Math.max(1, Math.round(entry.npcBuyCentavos() + state.shiftCentavos)) : 0;
        long ask = entry.npcSells() ? Math.max(1, Math.round(entry.npcSellCentavos() + state.shiftCentavos)) : 0;
        if (entry.npcBuys() && entry.npcSells() && ask <= bid) ask = bid + 1;
        return Optional.of(new MarketQuote(entry.itemId(), entry.baseCentavos(), bid, ask,
                state.stock, entry.stockCapacity(), state.priceVersion, state.nextUpdateAt,
                state.pricedAt, now + QUOTE_LIFETIME_MILLIS, state.todayBought, state.todaySold,
                entry.playerBuyDailyLimit(), entry.playerSellDailyLimit(), entry.tradeable(), entry.npcBuys(), entry.npcSells()));
    }

    public synchronized void show(ServerPlayer player) {
        if (catalog.entries().isEmpty()) {
            player.sendSystemMessage(Component.literal("§cMarket catalog is unavailable; NPC trading is disabled."));
            return;
        }
        long now = System.currentTimeMillis();
        player.sendSystemMessage(Component.literal("§6§lTOMAT MARKET §8— §7prices are centavo quotes, stock is finite"));
        player.sendSystemMessage(Component.literal("§7Wallet: §f" + TomatMoney.format(ledger.balance(player.getUUID()))
                + " §8| §7NPC treasury: §f" + TomatMoney.format(ledger.balance(TomatLedger.MARKET_ACCOUNT))));
        for (EconomyPriceRegistry.Entry entry : catalog.entries()) {
            MarketQuote q = quote(entry.itemId(), now).orElse(null);
            if (q == null) continue;
            issueQuote(player, q);
            String name = new ItemStack(entry.item()).getHoverName().getString();
            String stock = "§bstock " + q.stock() + "/" + q.stock_capacity();
            String buy = q.npc_sells() && q.stock() > 0
                    ? "§a[BUY x1 · " + TomatMoney.format(q.npc_sell_unit_price()) + "]"
                    : "§8[BUY paused]";
            String sell = q.npc_buys()
                    ? "§e[SELL x1 · " + TomatMoney.format(q.npc_buy_unit_price()) + "]"
                    : "§8[SELL paused]";
            MutableComponent line = Component.literal("§f" + name + " §8(" + entry.itemId() + ") §7base "
                    + TomatMoney.format(entry.baseCentavos()) + " " + stock + " §8" + buy + " " + sell);
            if (q.npc_sells() && q.stock() > 0) {
                line = line.withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(
                        "/market buy " + entry.itemId() + " 1 " + q.price_version())));
            }
            player.sendSystemMessage(line);
            if (q.npc_buys() && entry.tradeable()) {
                player.sendSystemMessage(Component.literal("   §e[SELL ONE] §7limit " + q.player_sell_daily_limit() + "/day")
                        .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(
                                "/market sell " + entry.itemId() + " 1 " + q.price_version()))));
            }
        }
        player.sendSystemMessage(Component.literal("§7Quote refreshes every 15 minutes; valid for 5 minutes. Use §f/market quote <item_id>§7 to refresh."));
    }

    public synchronized boolean showQuote(ServerPlayer player, String itemId) {
        MarketQuote q = quote(itemId, System.currentTimeMillis()).orElse(null);
        if (q == null) { player.sendSystemMessage(Component.literal("§cUnknown item id. Use §f/market§c.")); return false; }
        issueQuote(player, q);
        player.sendSystemMessage(Component.literal("§6" + q.item_id() + " §8| §7base " + TomatMoney.format(q.base_centavos())
                + " §8| §eNPC buys " + (q.npc_buys() ? TomatMoney.format(q.npc_buy_unit_price()) : "disabled")
                + " §8| §aNPC sells " + (q.npc_sells() ? TomatMoney.format(q.npc_sell_unit_price()) : "disabled")
                + " §8| §bstock " + q.stock() + "/" + q.stock_capacity() + " §8| §7version " + q.price_version()
                + " · expires " + Instant.ofEpochMilli(q.quote_expires_at())));
        return true;
    }

    public synchronized void disconnect(UUID player) { issuedQuotes.remove(player); }

    public synchronized boolean buy(ServerPlayer player, String itemId, int amount, int version) {
        EconomyPriceRegistry.Entry entry = catalog.find(itemId).orElse(null);
        MarketQuote q = quote(itemId, System.currentTimeMillis()).orElse(null);
        if (entry == null || !entry.tradeable() || !entry.npcSells() || q == null) return fail(player, "That item is not sold by the market.");
        if (!validQuote(player, itemId, q, version)) return false;
        if (amount < 1 || amount > 64) return fail(player, "Choose an amount from 1 to 64.");
        if (q.stock() < amount) return fail(player, "Market stock is too low; refresh §f/market§c for current stock.");
        if (!dailyAllowed(player, entry, true, amount)) return false;
        long total;
        try { total = Math.multiplyExact(q.npc_sell_unit_price(), amount); }
        catch (ArithmeticException error) { return fail(player, "That purchase is too large."); }
        ItemStack bought = new ItemStack(entry.item(), amount);
        if (!EconomyInventoryOps.canFit(player.getInventory(), java.util.List.of(bought))) return fail(player, "Your inventory does not have room.");
        String tx = "MARKET:BUY:" + UUID.randomUUID();
        if (!ledger.transfer(tx, "NPC_MARKET_BUY", Map.of(TomatLedger.account(player.getUUID()), total), Map.of(TomatLedger.MARKET_ACCOUNT, total)))
            return fail(player, "Not enough centavos, or the market transaction could not be committed.");
        if (!EconomyInventoryOps.addAll(player.getInventory(), java.util.List.of(bought))) {
            ledger.transfer(tx + ":ROLLBACK", "MARKET_BUY_ROLLBACK", Map.of(TomatLedger.MARKET_ACCOUNT, total), Map.of(TomatLedger.account(player.getUUID()), total));
            return fail(player, "Inventory changed during purchase; the charge was rolled back.");
        }
        MarketState state = states.get(itemId);
        resetDaily(state);
        state.stock -= amount;
        state.flowUnits = Math.min(1_000_000, state.flowUnits + amount);
        state.todayBought += amount;
        recordUsage(player, itemId, true, amount);
        save();
        appendTrade("BUY", player.getUUID(), itemId, amount, total);
        player.sendSystemMessage(Component.literal("§aBought " + amount + " × " + itemId + " for " + TomatMoney.format(total) + ". Wallet: " + TomatMoney.format(ledger.balance(player.getUUID()))));
        return true;
    }

    public synchronized boolean sell(ServerPlayer player, String itemId, int amount, int version) {
        EconomyPriceRegistry.Entry entry = catalog.find(itemId).orElse(null);
        MarketQuote q = quote(itemId, System.currentTimeMillis()).orElse(null);
        if (entry == null || !entry.tradeable() || !entry.npcBuys() || q == null) return fail(player, "That item is not bought by the market.");
        if (!validQuote(player, itemId, q, version)) return false;
        if (amount < 1 || amount > 64) return fail(player, "Choose an amount from 1 to 64.");
        if (!dailyAllowed(player, entry, false, amount)) return false;
        MarketState state = states.get(itemId);
        if (state.stock + amount > entry.stockCapacity()) return fail(player, "The market cannot store more of that item right now.");
        if (EconomyInventoryOps.countItem(player.getInventory(), entry.item()) < amount) return fail(player, "You do not have enough of that item.");
        if (!hasSellableItemStacks(player, entry.item(), amount)) return fail(player, "Protected or marked items cannot be sold to the NPC market.");
        long total;
        try { total = Math.multiplyExact(q.npc_buy_unit_price(), amount); }
        catch (ArithmeticException error) { return fail(player, "That sale is too large."); }
        ListWithItems removed = removeSellable(player, entry.item(), amount);
        if (removed == null) return fail(player, "Your inventory changed before the sale could be completed.");
        String tx = "MARKET:SELL:" + UUID.randomUUID();
        if (!ledger.transfer(tx, "NPC_MARKET_SELL", Map.of(TomatLedger.MARKET_ACCOUNT, total), Map.of(TomatLedger.account(player.getUUID()), total))) {
            EconomyInventoryOps.addAll(player.getInventory(), removed.items);
            return fail(player, "Market buybacks are paused: the treasury is low or the transaction could not be committed. Treasury does not auto-refill.");
        }
        state.stock += amount;
        state.flowUnits = Math.max(-1_000_000, state.flowUnits - amount);
        resetDaily(state);
        state.todaySold += amount;
        recordUsage(player, itemId, false, amount);
        save();
        appendTrade("SELL", player.getUUID(), itemId, amount, total);
        player.sendSystemMessage(Component.literal("§aSold " + amount + " × " + itemId + " for " + TomatMoney.format(total) + ". Wallet: " + TomatMoney.format(ledger.balance(player.getUUID()))));
        return true;
    }

    public synchronized void tick(long now) {
        boolean changed = false;
        for (var entry : catalog.entries()) {
            MarketState state = states.get(entry.itemId());
            if (state == null) continue;
            if (now >= state.nextUpdateAt) {
                double divisor = Math.max(1.0, entry.stockCapacity() / 10.0);
                double requested = (state.flowUnits / divisor) * entry.baseCentavos() * 0.005;
                double maximumMove = entry.baseCentavos() * 0.005;
                double move = Math.max(-maximumMove, Math.min(maximumMove, requested));
                state.shiftCentavos = clamp(state.shiftCentavos + move, -entry.baseCentavos() * 0.20, entry.baseCentavos() * 0.20);
                state.flowUnits = 0;
                state.nextUpdateAt = now - (now % REFRESH_MILLIS) + REFRESH_MILLIS;
                state.pricedAt = now;
                state.priceVersion++;
                changed = true;
            }
            while (now - state.lastDriftAt >= 24 * 60 * 60_000L) {
                state.shiftCentavos *= 0.90;
                state.lastDriftAt += 24 * 60 * 60_000L;
                state.priceVersion++;
                state.pricedAt = now;
                changed = true;
            }
            if (!state.day.equals(utcDay())) {
                state.day = utcDay();
                state.todayBought = 0;
                state.todaySold = 0;
                changed = true;
            }
        }
        if (changed) save();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(statePath.toAbsolutePath().getParent());
            Properties p = new Properties();
            for (var row : states.entrySet()) {
                String prefix = "state." + enc(row.getKey()) + ".";
                MarketState s = row.getValue();
                p.setProperty(prefix + "stock", Integer.toString(s.stock));
                p.setProperty(prefix + "shift", Double.toString(s.shiftCentavos));
                p.setProperty(prefix + "flow", Integer.toString(s.flowUnits));
                p.setProperty(prefix + "next", Long.toString(s.nextUpdateAt));
                p.setProperty(prefix + "drift", Long.toString(s.lastDriftAt));
                p.setProperty(prefix + "version", Integer.toString(s.priceVersion));
                p.setProperty(prefix + "priced", Long.toString(s.pricedAt));
                p.setProperty(prefix + "day", s.day);
                p.setProperty(prefix + "bought", Integer.toString(s.todayBought));
                p.setProperty(prefix + "sold", Integer.toString(s.todaySold));
            }
            usage.forEach((key, value) -> {
                String prefix = "usage." + enc(key) + ".";
                p.setProperty(prefix + "day", value.day);
                p.setProperty(prefix + "bought", Integer.toString(value.bought));
                p.setProperty(prefix + "sold", Integer.toString(value.sold));
            });
            Path temp = statePath.resolveSibling(statePath.getFileName() + ".tmp");
            try (var out = Files.newOutputStream(temp)) { p.store(out, "Tomat market stock, centavo quotes and limits; do not edit while server is running"); }
            try { Files.move(temp, statePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(temp, statePath, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException error) { ForgeSoloFabric.LOGGER.error("Could not persist Tomat market state", error); }
    }

    public MarketQuote quoteFor(String itemId) { return quote(itemId, System.currentTimeMillis()).orElse(null); }

    private void issueQuote(ServerPlayer player, MarketQuote quote) {
        issuedQuotes.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
                .put(quote.item_id(), new IssuedQuote(quote.price_version(), quote.quote_expires_at()));
    }

    private boolean validQuote(ServerPlayer player, String itemId, MarketQuote quote, int version) {
        IssuedQuote issued = issuedQuotes.getOrDefault(player.getUUID(), Map.of()).get(itemId);
        if (issued == null || version != quote.price_version() || issued.version != version
                || System.currentTimeMillis() >= issued.expiresAt) {
            player.sendSystemMessage(Component.literal("§eThat quote expired or the market moved. Open §f/market§e again before trading."));
            return false;
        }
        return true;
    }

    private boolean dailyAllowed(ServerPlayer player, EconomyPriceRegistry.Entry entry, boolean buy, int amount) {
        PlayerUsage current = usageFor(player.getUUID(), entry.itemId());
        int used = buy ? current.bought : current.sold;
        int limit = buy ? entry.playerBuyDailyLimit() : entry.playerSellDailyLimit();
        if (used + amount > limit) {
            player.sendSystemMessage(Component.literal("§cDaily market limit reached for " + entry.itemId() + " (" + limit + ")."));
            return false;
        }
        return true;
    }

    private PlayerUsage usageFor(UUID player, String itemId) {
        String key = usageKey(player, itemId);
        PlayerUsage current = usage.computeIfAbsent(key, ignored -> new PlayerUsage(utcDay(), 0, 0));
        if (!current.day.equals(utcDay())) {
            current.day = utcDay();
            current.bought = 0;
            current.sold = 0;
        }
        return current;
    }

    private void recordUsage(ServerPlayer player, String itemId, boolean buy, int amount) {
        PlayerUsage current = usageFor(player.getUUID(), itemId);
        if (buy) current.bought += amount;
        else current.sold += amount;
    }

    private static String usageKey(UUID player, String itemId) { return player + "|" + itemId; }

    private static void resetDaily(MarketState state) {
        if (!state.day.equals(utcDay())) {
            state.day = utcDay();
            state.todayBought = 0;
            state.todaySold = 0;
        }
    }

    private boolean hasSellableItemStacks(ServerPlayer player, net.minecraft.world.item.Item item, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || stack.getItem() != item) continue;
            if (!EconomyTradeRules.isTradableStack(stack, player.getUUID(), false)) return false;
            remaining -= Math.min(remaining, stack.getCount());
        }
        return remaining == 0;
    }

    private ListWithItems removeSellable(ServerPlayer player, net.minecraft.world.item.Item item, int amount) {
        java.util.ArrayList<ItemStack> removed = new java.util.ArrayList<>();
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || stack.getItem() != item || !EconomyTradeRules.isTradableStack(stack, player.getUUID(), false)) continue;
            int take = Math.min(remaining, stack.getCount());
            ItemStack part = stack.copy();
            part.setCount(take);
            removed.add(part);
            stack.shrink(take);
            remaining -= take;
            if (stack.isEmpty()) player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        return remaining == 0 ? new ListWithItems(removed) : null;
    }

    private boolean fail(ServerPlayer player, String message) { player.sendSystemMessage(Component.literal("§c" + message)); return false; }

    private void appendTrade(String action, UUID player, String itemId, int amount, long totalCentavos) {
        try {
            Files.createDirectories(tradeLog.toAbsolutePath().getParent());
            if (!Files.exists(tradeLog)) Files.writeString(tradeLog, "timestamp_utc,action,player_uuid,item_id,amount,total_centavos\n");
            Files.writeString(tradeLog, Instant.now() + "," + action + "," + player + "," + itemId + "," + amount + "," + totalCentavos + "\n", java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException error) { ForgeSoloFabric.LOGGER.error("Market trade committed but could not append its audit row", error); }
    }

    private void load() {
        for (EconomyPriceRegistry.Entry entry : catalog.entries()) {
            long now = System.currentTimeMillis();
            states.put(entry.itemId(), new MarketState(entry.initialStock(), 0.0, 0, nextQuarter(now), now, 1, now, utcDay(), 0, 0));
        }
        if (!Files.exists(statePath)) { save(); return; }
        try {
            Properties p = new Properties();
            try (var in = Files.newInputStream(statePath)) { p.load(in); }
            for (var row : states.entrySet()) {
                String prefix = "state." + enc(row.getKey()) + ".";
                MarketState old = row.getValue();
                EconomyPriceRegistry.Entry entry = catalog.find(row.getKey()).orElseThrow();
                int stock = clampInt(Integer.parseInt(p.getProperty(prefix + "stock", Integer.toString(old.stock))), 0, entry.stockCapacity());
                double shift = Double.parseDouble(p.getProperty(prefix + "shift", "0"));
                shift = clamp(shift, -entry.baseCentavos() * .20, entry.baseCentavos() * .20);
                states.put(row.getKey(), new MarketState(stock, shift,
                        Integer.parseInt(p.getProperty(prefix + "flow", "0")),
                        Long.parseLong(p.getProperty(prefix + "next", Long.toString(old.nextUpdateAt))),
                        Long.parseLong(p.getProperty(prefix + "drift", Long.toString(old.lastDriftAt))),
                        Integer.parseInt(p.getProperty(prefix + "version", "1")),
                        Long.parseLong(p.getProperty(prefix + "priced", Long.toString(old.pricedAt))),
                        p.getProperty(prefix + "day", utcDay()),
                        Integer.parseInt(p.getProperty(prefix + "bought", "0")),
                        Integer.parseInt(p.getProperty(prefix + "sold", "0"))));
            }
            for (String property : p.stringPropertyNames()) {
                if (!property.startsWith("usage.") || !property.endsWith(".day")) continue;
                String encodedKey = property.substring("usage.".length(), property.length() - ".day".length());
                String key = dec(encodedKey);
                String[] parts = key.split("\\|", 2);
                if (parts.length != 2 || catalog.find(parts[1]).isEmpty()) continue;
                UUID.fromString(parts[0]);
                String day = p.getProperty("usage." + encodedKey + ".day", utcDay());
                if (!day.equals(utcDay())) continue;
                int bought = clampInt(Integer.parseInt(p.getProperty("usage." + encodedKey + ".bought", "0")), 0, 1_000_000);
                int sold = clampInt(Integer.parseInt(p.getProperty("usage." + encodedKey + ".sold", "0")), 0, 1_000_000);
                usage.put(key, new PlayerUsage(day, bought, sold));
            }
        } catch (Exception error) {
            ForgeSoloFabric.LOGGER.error("Could not load Tomat market state; using safe catalog defaults", error);
        }
    }

    private static int clampInt(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static long nextQuarter(long now) { return now - (now % REFRESH_MILLIS) + REFRESH_MILLIS; }
    private static String utcDay() { return LocalDate.now(ZoneOffset.UTC).toString(); }
    private static String enc(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private static String dec(String value) { return new String(Base64.getUrlDecoder().decode(value), java.nio.charset.StandardCharsets.UTF_8); }

    public record MarketQuote(String item_id, long base_centavos, long npc_buy_unit_price, long npc_sell_unit_price,
                              int stock, int stock_capacity, int price_version, long next_update_at,
                              long priced_at, long quote_expires_at, int daily_bought, int daily_sold,
                              int player_buy_daily_limit, int player_sell_daily_limit,
                              boolean tradeable, boolean npc_buys, boolean npc_sells) { }
    private static final class MarketState {
        private int stock;
        private double shiftCentavos;
        private int flowUnits;
        private long nextUpdateAt;
        private long lastDriftAt;
        private int priceVersion;
        private long pricedAt;
        private String day;
        private int todayBought;
        private int todaySold;
        private MarketState(int stock, double shift, int flow, long next, long drift, int version, long priced,
                            String day, int bought, int sold) {
            this.stock = stock; this.shiftCentavos = shift; this.flowUnits = flow; this.nextUpdateAt = next;
            this.lastDriftAt = drift; this.priceVersion = version; this.pricedAt = priced; this.day = day;
            this.todayBought = bought; this.todaySold = sold;
        }
    }
    private record ListWithItems(java.util.List<ItemStack> items) { }
    private record IssuedQuote(int version, long expiresAt) { }
    private static final class PlayerUsage {
        private String day;
        private int bought;
        private int sold;
        private PlayerUsage(String day, int bought, int sold) { this.day = day; this.bought = bought; this.sold = sold; }
    }
}
