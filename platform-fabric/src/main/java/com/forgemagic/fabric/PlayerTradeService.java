package com.forgemagic.fabric;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared negotiated trades. Offers are immutable item-stack snapshots until a revision is finally confirmed. */
public final class PlayerTradeService {
    private static final long INVITE_TTL_MILLIS = 60_000L;
    private static final long IDLE_TTL_MILLIS = 120_000L;
    private static final String TRADE_WORLD = "forge_cornucopia";
    private final EconomyPriceRegistry catalog;
    private final TomatLedger ledger;
    private final Path completedLog;
    private final Map<UUID, Invitation> invitations = new HashMap<>();
    private final Map<UUID, TradeSession> sessionsByPlayer = new HashMap<>();

    public PlayerTradeService(EconomyPriceRegistry catalog, TomatLedger ledger, Path completedLog) {
        this.catalog = catalog;
        this.ledger = ledger;
        this.completedLog = completedLog;
    }

    public synchronized boolean invite(ServerPlayer sender, ServerPlayer receiver) {
        if (sender.getUUID().equals(receiver.getUUID())) return fail(sender, "You cannot trade with yourself.");
        if (sessionsByPlayer.containsKey(sender.getUUID()) || sessionsByPlayer.containsKey(receiver.getUUID()))
            return fail(sender, "One of you is already in a trade.");
        if (hasPendingInvitation(sender.getUUID()) || hasPendingInvitation(receiver.getUUID()))
            return fail(sender, "One of you already has a pending trade invitation.");
        long expires = System.currentTimeMillis() + INVITE_TTL_MILLIS;
        Invitation invitation = new Invitation(UUID.randomUUID(), sender.getUUID(), receiver.getUUID(), expires);
        invitations.put(receiver.getUUID(), invitation);
        sender.sendSystemMessage(Component.literal("§aTrade invitation sent to §f" + receiver.getName().getString() + "§a (expires in 60 seconds)."));
        receiver.sendSystemMessage(Component.literal("§e" + sender.getName().getString() + " invited you to trade. §a[ACCEPT]")
                .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/trade accept " + sender.getName().getString()))));
        return true;
    }

    public synchronized boolean accept(ServerPlayer receiver, String senderName, MinecraftServer server) {
        Invitation invitation = invitations.get(receiver.getUUID());
        if (invitation == null || invitation.expiresAtMillis() <= System.currentTimeMillis()) {
            invitations.remove(receiver.getUUID());
            return fail(receiver, "There is no live trade invitation to accept.");
        }
        ServerPlayer sender = server.getPlayerList().getPlayer(invitation.sender());
        if (sender == null || (!senderName.isBlank() && !sender.getName().getString().equalsIgnoreCase(senderName)))
            return fail(receiver, "That player is offline or the invitation does not match.");
        if (sessionsByPlayer.containsKey(receiver.getUUID()) || sessionsByPlayer.containsKey(sender.getUUID()))
            return fail(receiver, "One of you is already in a trade.");
        if (!inTradeWorld(sender) || !inTradeWorld(receiver) || !playersTogether(sender, receiver)) {
            receiver.sendSystemMessage(Component.literal("§cBoth players must be within 16 blocks of each other in Cornucopia to accept a trade."));
            sender.sendSystemMessage(Component.literal("§cTrade acceptance requires both players to be within 16 blocks in Cornucopia."));
            return false;
        }
        invitations.remove(receiver.getUUID());
        TradeSession session = new TradeSession(UUID.randomUUID(), sender.getUUID(), receiver.getUUID(), server, System.currentTimeMillis());
        sessionsByPlayer.put(sender.getUUID(), session);
        sessionsByPlayer.put(receiver.getUUID(), session);
        sender.sendSystemMessage(Component.literal("§aTrade accepted with §f" + receiver.getName().getString() + "§a."));
        receiver.sendSystemMessage(Component.literal("§aTrade accepted with §f" + sender.getName().getString() + "§a."));
        sendPanel(session, sender, receiver);
        sendPanel(session, receiver, sender);
        return true;
    }

    public synchronized boolean addHeld(ServerPlayer player, int amount) {
        TradeSession session = session(player);
        if (session == null) return fail(player, "No active trade. Start one with §f/trade <player>§c.");
        if (!checkRevision(session, player, session.revision)) return false;
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty() || amount < 1 || amount > held.getCount() || amount > 64)
            return fail(player, "Hold a catalogued tradable stack and choose a count within that stack (1–64).");
        EconomyPriceRegistry.Entry entry = catalog.find(held.getItem()).orElse(null);
        if (entry == null || !entry.tradeable() || !EconomyTradeRules.isTradableStack(held, player.getUUID(), inTradeWorld(player)))
            return fail(player, "That item is not player-tradable. Protected Fern items and unregistered IDs are excluded.");
        ItemStack offered = held.copy();
        offered.setCount(amount);
        List<ItemStack> side = session.items.get(player.getUUID());
        side.add(offered);
        if (!hasAllItems(player, side)) {
            side.remove(side.size() - 1);
            return fail(player, "You already offered more of that exact item stack than your inventory contains.");
        }
        edited(session);
        notifyEdit(session, player, "added " + amount + " × " + entry.itemId());
        return true;
    }

    public synchronized boolean removeLastItem(ServerPlayer player) {
        TradeSession session = session(player);
        if (session == null) return fail(player, "No active trade.");
        List<ItemStack> side = session.items.get(player.getUUID());
        if (side.isEmpty()) return fail(player, "You have no staged item to remove.");
        ItemStack removed = side.remove(side.size() - 1);
        edited(session);
        notifyEdit(session, player, "removed " + removed.getCount() + " × " + BuiltInRegistries.ITEM.getKey(removed.getItem()));
        return true;
    }

    public synchronized boolean setTomats(ServerPlayer player, long centavos) {
        TradeSession session = session(player);
        if (session == null) return fail(player, "No active trade.");
        if (centavos < 0 || centavos > TomatLedger.MAX_BALANCE) return fail(player, "Tomat amount is out of range.");
        if (session.centavos.get(player.getUUID()) == centavos) { sendBoth(session); return true; }
        session.centavos.put(player.getUUID(), centavos);
        edited(session);
        notifyEdit(session, player, "changed their Tomat offer");
        return true;
    }

    public synchronized boolean ready(ServerPlayer player) {
        TradeSession session = session(player);
        if (session == null) return fail(player, "No active trade.");
        if (!validateSession(session, player.level().getServer())) return false;
        session.readyRevision.put(player.getUUID(), session.revision);
        session.lastActivityAt = System.currentTimeMillis();
        player.sendSystemMessage(Component.literal("§aReady set for offer revision " + session.revision + ". Any edit clears both Ready and Confirm."));
        sendBoth(session);
        return true;
    }

    public synchronized boolean confirm(ServerPlayer player) {
        TradeSession session = session(player);
        if (session == null) return fail(player, "No active trade.");
        if (!bothReady(session)) return fail(player, "Both players must Ready the same revision before final Confirm.");
        if (session.readyRevision.getOrDefault(player.getUUID(), -1) != session.revision)
            return fail(player, "Your Ready state is stale; Ready the current offer again.");
        if (!validateSession(session, player.level().getServer())) return false;
        session.confirmRevision.put(player.getUUID(), session.revision);
        session.lastActivityAt = System.currentTimeMillis();
        player.sendSystemMessage(Component.literal("§eFinal confirmation recorded for revision " + session.revision + "."));
        if (!bothConfirmed(session)) { sendBoth(session); return true; }
        return commit(session, player.level().getServer());
    }

    public synchronized boolean cancel(ServerPlayer player, String reason) {
        TradeSession session = session(player);
        if (session == null) {
            boolean removed = invitations.remove(player.getUUID()) != null;
            removed |= invitations.values().removeIf(invite -> invite.sender().equals(player.getUUID()));
            if (removed) {
                player.sendSystemMessage(Component.literal("§ePending trade invitation cancelled."));
                return true;
            }
            return fail(player, "No active trade or invitation to cancel.");
        }
        close(session, player.level().getServer(), reason, true);
        return true;
    }

    public synchronized void open(ServerPlayer player) {
        TradeSession session = session(player);
        if (session == null) { player.sendSystemMessage(Component.literal("§7No active trade. Invite someone with §f/trade <player>§7.")); return; }
        ServerPlayer other = player.level().getServer().getPlayerList().getPlayer(session.other(player.getUUID()));
        if (other != null) sendPanel(session, player, other);
    }

    public synchronized void handleAction(ServerPlayer player, String payload) {
        String[] fields = payload.split("\\|", -1);
        if (fields.length != 5) return;
        TradeSession session = session(player);
        if (session == null || !session.id.toString().equals(fields[0])) return;
        int revision;
        int count;
        long amount;
        try { revision = Integer.parseInt(fields[1]); count = Integer.parseInt(fields[3]); amount = Long.parseLong(fields[4]); }
        catch (NumberFormatException invalid) { return; }
        if (revision != session.revision) { player.sendSystemMessage(Component.literal("§eThe offer changed; your panel has been refreshed.")); sendBoth(session); return; }
        switch (fields[2]) {
            case "ADD_HELD" -> addHeld(player, count);
            case "REMOVE_LAST_ITEM" -> removeLastItem(player);
            case "SET_TOMATS" -> setTomats(player, amount);
            case "READY" -> ready(player);
            case "CONFIRM" -> confirm(player);
            case "CANCEL" -> cancel(player, "cancelled by a player");
            default -> { }
        }
    }

    public synchronized void disconnect(UUID player, MinecraftServer server) {
        invitations.remove(player);
        invitations.values().removeIf(invite -> invite.sender().equals(player));
        TradeSession session = sessionsByPlayer.get(player);
        if (session != null) close(session, server, "player disconnected", true);
    }

    public synchronized void shutdown(MinecraftServer server) {
        for (TradeSession session : new ArrayList<>(sessionsByPlayer.values()).stream().distinct().toList())
            close(session, server, "server stopping", false);
        invitations.clear();
    }

    public synchronized void tick(MinecraftServer server, long now) {
        invitations.values().removeIf(invite -> invite.expiresAtMillis() <= now);
        for (TradeSession session : new ArrayList<>(sessionsByPlayer.values()).stream().distinct().toList()) {
            ServerPlayer a = server.getPlayerList().getPlayer(session.a);
            ServerPlayer b = server.getPlayerList().getPlayer(session.b);
            if (a == null || b == null) close(session, server, "player disconnected", true);
            else if (!inTradeWorld(a) || !inTradeWorld(b) || !playersTogether(a, b)) close(session, server, "players are no longer together in Cornucopia", true);
            else if (now - session.lastActivityAt >= IDLE_TTL_MILLIS) close(session, server, "two-minute idle timeout", true);
            else validateSession(session, server);
        }
    }

    private boolean commit(TradeSession session, MinecraftServer server) {
        ServerPlayer a = server.getPlayerList().getPlayer(session.a);
        ServerPlayer b = server.getPlayerList().getPlayer(session.b);
        if (a == null || b == null || !validateSession(session, server)) return false;
        if (!hasAnyOffer(session)) { close(session, server, "empty trade rejected", true); return false; }
        List<ItemStack> toA = rebind(session.items.get(session.b), a.getUUID());
        List<ItemStack> toB = rebind(session.items.get(session.a), b.getUUID());
        List<ItemStack> planA = EconomyInventoryOps.planExchange(a.getInventory(), session.items.get(session.a), toA);
        List<ItemStack> planB = EconomyInventoryOps.planExchange(b.getInventory(), session.items.get(session.b), toB);
        if (planA == null || planB == null) { close(session, server, "invalid inventory offer", true); return false; }
        long moneyA = session.centavos.get(session.a);
        long moneyB = session.centavos.get(session.b);
        boolean hasMoney = moneyA + moneyB > 0;
        String tx = "TRADE:" + session.id;
        List<ItemStack> beforeA = EconomyInventoryOps.snapshot(a.getInventory());
        List<ItemStack> beforeB = EconomyInventoryOps.snapshot(b.getInventory());
        try {
            EconomyInventoryOps.applyPlan(a.getInventory(), planA);
            EconomyInventoryOps.applyPlan(b.getInventory(), planB);
        } catch (RuntimeException error) {
            EconomyInventoryOps.restore(a.getInventory(), beforeA);
            EconomyInventoryOps.restore(b.getInventory(), beforeB);
            ForgeSoloFabric.LOGGER.error("Player trade {} inventory application failed and was rolled back", session.id, error);
            close(session, server, "inventory commit failure", true);
            return false;
        }
        if (hasMoney) {
            Map<String, Long> debits = new LinkedHashMap<>();
            Map<String, Long> credits = new LinkedHashMap<>();
            if (moneyA > 0) { debits.put(TomatLedger.account(session.a), moneyA); credits.put(TomatLedger.account(session.b), moneyA); }
            if (moneyB > 0) { debits.put(TomatLedger.account(session.b), moneyB); credits.put(TomatLedger.account(session.a), moneyB); }
            if (!ledger.transfer(tx, "PLAYER_TRADE", debits, credits)) {
                EconomyInventoryOps.restore(a.getInventory(), beforeA);
                EconomyInventoryOps.restore(b.getInventory(), beforeB);
                close(session, server, "insufficient wallet balance or ledger commit failure", true);
                return false;
            }
        }
        appendCompleted(session);
        long now = System.currentTimeMillis();
        sessionsByPlayer.remove(session.a);
        sessionsByPlayer.remove(session.b);
        a.sendSystemMessage(Component.literal("§aTrade complete with §f" + b.getName().getString() + "§a."));
        b.sendSystemMessage(Component.literal("§aTrade complete with §f" + a.getName().getString() + "§a."));
        a.sendSystemMessage(Component.literal("§7Logged as trade " + session.id + " at " + Instant.ofEpochMilli(now) + "."));
        b.sendSystemMessage(Component.literal("§7Logged as trade " + session.id + " at " + Instant.ofEpochMilli(now) + "."));
        return true;
    }

    private boolean validateSession(TradeSession session, MinecraftServer server) {
        ServerPlayer a = server.getPlayerList().getPlayer(session.a);
        ServerPlayer b = server.getPlayerList().getPlayer(session.b);
        if (a == null || b == null || !inTradeWorld(a) || !inTradeWorld(b) || !playersTogether(a, b)) {
            close(session, server, "invalid trade location or offline participant", true);
            return false;
        }
        if (!hasAllItems(a, session.items.get(session.a)) || !hasAllItems(b, session.items.get(session.b))) {
            close(session, server, "invalid item offer; original inventories were unchanged", true);
            return false;
        }
        if (ledger.balance(a.getUUID()) < session.centavos.get(session.a)
                || ledger.balance(b.getUUID()) < session.centavos.get(session.b)) {
            close(session, server, "invalid Tomat offer; no money or items were transferred", true);
            return false;
        }
        if (!allTradable(session, a, b)) {
            close(session, server, "protected or unregistered item offer", true);
            return false;
        }
        return true;
    }

    private boolean allTradable(TradeSession session, ServerPlayer a, ServerPlayer b) {
        for (ItemStack stack : session.items.get(session.a)) if (!tradable(stack, a)) return false;
        for (ItemStack stack : session.items.get(session.b)) if (!tradable(stack, b)) return false;
        return true;
    }

    private boolean tradable(ItemStack stack, ServerPlayer player) {
        EconomyPriceRegistry.Entry entry = catalog.find(stack.getItem()).orElse(null);
        return entry != null && entry.tradeable() && EconomyTradeRules.isTradableStack(stack, player.getUUID(), inTradeWorld(player));
    }

    private static boolean hasAllItems(ServerPlayer player, List<ItemStack> offers) {
        List<ItemStack> unique = new ArrayList<>();
        List<Integer> amounts = new ArrayList<>();
        for (ItemStack offered : offers) {
            int found = -1;
            for (int i = 0; i < unique.size(); i++) if (EconomyInventoryOps.sameComponents(unique.get(i), offered)) { found = i; break; }
            if (found < 0) { unique.add(offered); amounts.add(offered.getCount()); }
            else amounts.set(found, amounts.get(found) + offered.getCount());
        }
        for (int i = 0; i < unique.size(); i++) {
            int count = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (EconomyInventoryOps.sameComponents(stack, unique.get(i))) count += stack.getCount();
            }
            if (count < amounts.get(i)) return false;
        }
        return true;
    }

    private static List<ItemStack> rebind(List<ItemStack> source, UUID recipient) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack offered : source) {
            ItemStack copy = offered.copy();
            FernItemBindingService.transferForTrade(copy, recipient);
            result.add(copy);
        }
        return result;
    }

    private void edited(TradeSession session) {
        session.revision++;
        session.readyRevision.clear();
        session.confirmRevision.clear();
        session.lastActivityAt = System.currentTimeMillis();
        sendBoth(session);
    }

    private void notifyEdit(TradeSession session, ServerPlayer editor, String description) {
        ServerPlayer other = editor.level().getServer().getPlayerList().getPlayer(session.other(editor.getUUID()));
        if (other != null) other.sendSystemMessage(Component.literal("§e" + editor.getName().getString() + " " + description + "; both Ready/Confirm states were cleared."));
    }

    private void sendBoth(TradeSession session) {
        ServerPlayer a = session.server.getPlayerList().getPlayer(session.a);
        ServerPlayer b = session.server.getPlayerList().getPlayer(session.b);
        if (a != null && b != null) { sendPanel(session, a, b); sendPanel(session, b, a); }
    }

    private void sendPanel(TradeSession session, ServerPlayer player, ServerPlayer other) {
        String data = String.join("|", session.id.toString(), Integer.toString(session.revision),
                safeSummary(session.items.get(player.getUUID())), safeSummary(session.items.get(other.getUUID())),
                Long.toString(session.centavos.get(player.getUUID())), Long.toString(session.centavos.get(other.getUUID())),
                Boolean.toString(session.readyRevision.getOrDefault(player.getUUID(), -1) == session.revision),
                Boolean.toString(session.readyRevision.getOrDefault(other.getUUID(), -1) == session.revision),
                Boolean.toString(bothReady(session)), player.getName().getString(), other.getName().getString());
        if (ServerPlayNetworking.canSend(player, TomatTradePayloads.PANEL_TYPE)) {
            ServerPlayNetworking.send(player, new TomatTradePayloads.PanelPayload(data));
        } else {
            player.sendSystemMessage(Component.literal("§6§lTRADE PANEL §8— §7revision " + session.revision));
            player.sendSystemMessage(Component.literal("§fYou offer: §7" + safeSummary(session.items.get(player.getUUID())) + " §8+ §e" + TomatMoney.format(session.centavos.get(player.getUUID()))));
            player.sendSystemMessage(Component.literal("§f" + other.getName().getString() + " offers: §7" + safeSummary(session.items.get(other.getUUID())) + " §8+ §e" + TomatMoney.format(session.centavos.get(other.getUUID()))));
            player.sendSystemMessage(Component.literal("§a[READY]").withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/trade ready"))));
            player.sendSystemMessage(Component.literal("§e[FINAL CONFIRM]").withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/trade confirm"))));
            player.sendSystemMessage(Component.literal("§c[CANCEL]").withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/trade cancel"))));
            player.sendSystemMessage(Component.literal("§7Hold an item, use §f/trade add <count>§7, remove the last item with §f/trade remove§7, or set §f/trade tomats <TMT amount>§7."));
        }
    }

    private static String safeSummary(List<ItemStack> items) {
        if (items.isEmpty()) return "—";
        List<String> parts = new ArrayList<>();
        for (ItemStack stack : items) {
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            String name = stack.getHoverName().getString().replace('|', '/').replace('\n', ' ');
            parts.add(stack.getCount() + "× " + name + " [" + id + "]");
        }
        return String.join("; ", parts);
    }

    private void close(TradeSession session, MinecraftServer server, String reason, boolean notify) {
        sessionsByPlayer.remove(session.a);
        sessionsByPlayer.remove(session.b);
        if (!notify) return;
        for (UUID id : List.of(session.a, session.b)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) player.sendSystemMessage(Component.literal("§eTrade closed (" + reason + "). Offers were only staged: no items or centavos were removed."));
        }
    }

    private static boolean bothReady(TradeSession session) {
        return session.readyRevision.getOrDefault(session.a, -1) == session.revision
                && session.readyRevision.getOrDefault(session.b, -1) == session.revision;
    }
    private static boolean bothConfirmed(TradeSession session) {
        return session.confirmRevision.getOrDefault(session.a, -1) == session.revision
                && session.confirmRevision.getOrDefault(session.b, -1) == session.revision;
    }
    private TradeSession session(ServerPlayer player) { return sessionsByPlayer.get(player.getUUID()); }
    private boolean checkRevision(TradeSession session, ServerPlayer player, int revision) {
        if (session.revision != revision) { sendBoth(session); return fail(player, "Offer revision changed; the panel was refreshed."); }
        return true;
    }
    private static boolean inTradeWorld(ServerPlayer player) { return player.level().dimension().identifier().getPath().equals(TRADE_WORLD); }
    private static boolean sameWorld(ServerPlayer a, ServerPlayer b) { return a.level().dimension().identifier().equals(b.level().dimension().identifier()); }
    private static boolean playersTogether(ServerPlayer a, ServerPlayer b) { return sameWorld(a, b) && a.distanceToSqr(b) <= 256.0; }
    private boolean hasPendingInvitation(UUID player) {
        return invitations.containsKey(player) || invitations.values().stream().anyMatch(invite -> invite.sender().equals(player));
    }
    private static boolean hasAnyOffer(TradeSession session) {
        return session.centavos.get(session.a) > 0 || session.centavos.get(session.b) > 0
                || !session.items.get(session.a).isEmpty() || !session.items.get(session.b).isEmpty();
    }
    private boolean fail(ServerPlayer player, String text) { player.sendSystemMessage(Component.literal("§c" + text)); return false; }

    private void appendCompleted(TradeSession session) {
        try {
            Files.createDirectories(completedLog.toAbsolutePath().getParent());
            if (!Files.exists(completedLog)) Files.writeString(completedLog, "timestamp_utc,trade_id,player_a,player_b,a_centavos,b_centavos,a_items,b_items\n");
            String aItems = csv(safeSummary(session.items.get(session.a)));
            String bItems = csv(safeSummary(session.items.get(session.b)));
            Files.writeString(completedLog, Instant.now() + "," + session.id + "," + session.a + "," + session.b + ","
                    + session.centavos.get(session.a) + "," + session.centavos.get(session.b) + "," + aItems + "," + bItems + "\n",
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException error) { ForgeSoloFabric.LOGGER.error("Trade {} committed but its audit row could not be written", session.id, error); }
    }
    private static String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }

    private record Invitation(UUID id, UUID sender, UUID receiver, long expiresAtMillis) { }
    private static final class TradeSession {
        private final UUID id;
        private final UUID a;
        private final UUID b;
        private final MinecraftServer server;
        private int revision = 1;
        private long lastActivityAt;
        private final Map<UUID, List<ItemStack>> items = new HashMap<>();
        private final Map<UUID, Long> centavos = new HashMap<>();
        private final Map<UUID, Integer> readyRevision = new HashMap<>();
        private final Map<UUID, Integer> confirmRevision = new HashMap<>();
        private TradeSession(UUID id, UUID a, UUID b, MinecraftServer server, long now) {
            this.id = id; this.a = a; this.b = b; this.lastActivityAt = now;
            this.items.put(a, new ArrayList<>()); this.items.put(b, new ArrayList<>());
            this.centavos.put(a, 0L); this.centavos.put(b, 0L);
            this.server = server;
        }
        private UUID other(UUID id) { return a.equals(id) ? b : a; }
    }
}
