package com.forgemagic.fabric;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;

/** Fabric entrypoint for Tomats, the NPC market and guarded player-to-player trades. */
public final class TomatEconomyEntrypoint implements ModInitializer {
    private volatile EconomyPriceRegistry catalog;
    private volatile TomatLedger ledger;
    private volatile TomatMarketService market;
    private volatile PlayerTradeService trades;

    @Override
    public void onInitialize() {
        TomatTradePayloads.registerCommon();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && market != null && TomatMarketService.isMarketNpc(entity)) {
                market.show(serverPlayer);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        ServerPlayNetworking.registerGlobalReceiver(TomatTradePayloads.ACTION_TYPE, (payload, context) ->
                context.server().execute(() -> {
                    PlayerTradeService service = trades;
                    if (service != null) service.handleAction(context.player(), payload.data());
                }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PlayerTradeService service = trades;
            if (service != null) service.disconnect(handler.getPlayer().getUUID(), server);
            TomatMarketService marketService = market;
            if (marketService != null) marketService.disconnect(handler.getPlayer().getUUID());
        });
        ServerLifecycleEvents.SERVER_STARTED.register(this::startServices);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            PlayerTradeService tradeService = trades;
            if (tradeService != null) tradeService.shutdown(server);
            TomatMarketService marketService = market;
            if (marketService != null) marketService.save();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            long now = System.currentTimeMillis();
            TomatMarketService marketService = market;
            if (marketService != null) marketService.tick(now);
            PlayerTradeService tradeService = trades;
            if (tradeService != null) tradeService.tick(server, now);
        });
    }

    private void startServices(MinecraftServer server) {
        catalog = new EconomyPriceRegistry(Path.of("config", "forge-market-catalog.csv"));
        ledger = new TomatLedger(Path.of("config", "forge-tomat-ledger.properties"));
        market = new TomatMarketService(catalog, ledger,
                Path.of("config", "forge-market-state.properties"),
                Path.of("config", "forge-market-trades.csv"));
        trades = new PlayerTradeService(catalog, ledger, Path.of("config", "forge-player-trades.csv"));
        ForgeSoloFabric.LOGGER.info("Tomat economy ready: {} catalog IDs, centavo ledger, market and shared trade service",
                catalog.entries().size());
    }

    private void registerCommands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wallet").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            TomatLedger current = ledger;
            if (current == null) { player.sendSystemMessage(Component.literal("§cTomat wallet is not ready yet.")); return 0; }
            player.sendSystemMessage(Component.literal("§6Tomat Wallet §8— §f" + TomatMoney.format(current.balance(player.getUUID()))
                    + " §7(centavo balances are stored as whole integers)"));
            return 1;
        }));

        dispatcher.register(Commands.literal("market")
                .executes(ctx -> { TomatMarketService current = market; if (current == null) return 0; current.show(ctx.getSource().getPlayerOrException()); return 1; })
                .then(Commands.literal("list").executes(ctx -> { market.show(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("quote").then(Commands.argument("item_id", itemIdArgument())
                        .executes(ctx -> market.showQuote(ctx.getSource().getPlayerOrException(), itemId(ctx)) ? 1 : 0)))
                .then(Commands.literal("buy")
                        .then(Commands.argument("item_id", itemIdArgument())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                        .then(Commands.argument("price_version", IntegerArgumentType.integer(1))
                                .executes(ctx -> market.buy(ctx.getSource().getPlayerOrException(),
                                        itemId(ctx),
                                        IntegerArgumentType.getInteger(ctx, "count"),
                                        IntegerArgumentType.getInteger(ctx, "price_version")) ? 1 : 0)))))
                .then(Commands.literal("sell")
                        .then(Commands.argument("item_id", itemIdArgument())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                        .then(Commands.argument("price_version", IntegerArgumentType.integer(1))
                                .executes(ctx -> market.sell(ctx.getSource().getPlayerOrException(),
                                        itemId(ctx),
                                        IntegerArgumentType.getInteger(ctx, "count"),
                                        IntegerArgumentType.getInteger(ctx, "price_version")) ? 1 : 0)))))
                .then(Commands.literal("spawn").requires(source -> source.isPlayer()
                                && source.getServer().getPlayerList().isOp(((ServerPlayer) source.getEntity()).nameAndId()))
                        .executes(ctx -> { market.spawn(ctx.getSource().getPlayerOrException()); return 1; })));

        dispatcher.register(Commands.literal("trade")
                .executes(ctx -> { trades.open(ctx.getSource().getPlayerOrException()); return 1; })
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> invite(ctx.getSource().getPlayerOrException(),
                                StringArgumentType.getString(ctx, "player"), ctx.getSource().getServer())))
                .then(Commands.literal("accept").executes(ctx -> trades.accept(
                                ctx.getSource().getPlayerOrException(), "", ctx.getSource().getServer()) ? 1 : 0)
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(ctx -> trades.accept(ctx.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(ctx, "player"), ctx.getSource().getServer()) ? 1 : 0)))
                .then(Commands.literal("add").executes(ctx -> trades.addHeld(ctx.getSource().getPlayerOrException(), 1) ? 1 : 0)
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> trades.addHeld(ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "count")) ? 1 : 0)))
                .then(Commands.literal("remove").executes(ctx -> trades.removeLastItem(ctx.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("tomats").then(Commands.argument("amount", StringArgumentType.word())
                        .executes(ctx -> setTomatOffer(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "amount")))))
                .then(Commands.literal("ready").executes(ctx -> trades.ready(ctx.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("confirm").executes(ctx -> trades.confirm(ctx.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("cancel").executes(ctx -> trades.cancel(ctx.getSource().getPlayerOrException(), "cancelled by a player") ? 1 : 0)));

        dispatcher.register(Commands.literal("tomats").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            TomatLedger current = ledger;
            if (current == null) return 0;
            player.sendSystemMessage(Component.literal("§6" + TomatMoney.format(current.balance(player.getUUID()))));
            return 1;
        }));
    }

    static IdentifierArgument itemIdArgument() {
        return IdentifierArgument.id();
    }

    private static String itemId(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        return IdentifierArgument.getId(context, "item_id").toString();
    }

    private int invite(ServerPlayer sender, String targetName, MinecraftServer server) {
        ServerPlayer target = server.getPlayerList().getPlayers().stream()
                .filter(candidate -> candidate.getName().getString().equalsIgnoreCase(targetName))
                .findFirst().orElse(null);
        if (target == null) {
            sender.sendSystemMessage(Component.literal("§cThat player is not online."));
            return 0;
        }
        return trades.invite(sender, target) ? 1 : 0;
    }

    private int setTomatOffer(ServerPlayer player, String amount) {
        try {
            BigDecimal tmt = new BigDecimal(amount);
            if (tmt.scale() > 2 || tmt.signum() < 0) throw new NumberFormatException("up to two decimal places only");
            long centavos = tmt.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            return trades.setTomats(player, centavos) ? 1 : 0;
        } catch (ArithmeticException | NumberFormatException error) {
            player.sendSystemMessage(Component.literal("§cEnter a non-negative TMT value with at most two decimal places, e.g. 1.25."));
            return 0;
        }
    }
}
