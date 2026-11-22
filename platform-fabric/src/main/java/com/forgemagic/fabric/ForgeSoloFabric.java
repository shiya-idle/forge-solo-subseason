package com.forgemagic.fabric;

import com.forgemagic.api.ClassId;
import com.forgemagic.api.ManaState;
import com.forgemagic.api.SkillDefinition;
import com.forgemagic.armaments.ArmamentCatalog;
import com.forgemagic.armaments.WeaponDefinition;
import com.forgemagic.claims.ImmutableClaimsService;
import com.forgemagic.claims.ThreeFactorConfirmation;
import com.forgemagic.skills.ContentCatalog;
import com.forgemagic.skills.SkillRuntime;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class ForgeSoloFabric implements ModInitializer {
    public static final String MOD_ID = "forge_solo_subseason";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private final SkillRuntime skillRuntime = new SkillRuntime(Clock.systemUTC());
    private final Map<UUID, PlayerSettings> settings = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, String>> bindings = new ConcurrentHashMap<>();
    private final Map<UUID, PendingClaim> pendingClaims = new ConcurrentHashMap<>();
    private FabricSkillConfig config;
    private FabricSkillEffects effects;
    private PvpTargetingEngine targeting;
    private ImmutableClaimsService claims;
    private ClaimPersistence claimStorage;
    private boolean claimStorageReady;
    private final PlayerProgressionModifiers progression = new PlayerProgressionModifiers();
    private final ForgeVendorService vendor = new ForgeVendorService(progression);
    private final CombirollService combirolls = new CombirollService();
    private final CraftsmanWorkbench craftsman = new CraftsmanWorkbench(skillRuntime);
    private final FernEliteMobService eliteMobs = new FernEliteMobService();
    private final FernQuestBoardService questBoard = new FernQuestBoardService();
    private final FernWaypointService waypoints = new FernWaypointService();
    private final ThirstService thirst = new ThirstService();
    private FernAdminPolicy adminPolicy;
    private final PlayerClassService playerClasses = new PlayerClassService();
    private final Map<UUID, Long> classArenaMissingNoticeAt = new ConcurrentHashMap<>();
    private final Map<UUID, QualityProfile> quality = new ConcurrentHashMap<>();
    private final Map<UUID, PendingCompanion> pendingCompanions = new ConcurrentHashMap<>();
    private final Map<UUID, String> readyCompanions = new ConcurrentHashMap<>();
    private final Map<UUID, ReturnPoint> lastMainWorldPlaces = new ConcurrentHashMap<>();
    private ExtremeNightmareService difficulty;
    private long serverStartedAtMillis;

    @Override
    public void onInitialize() {
        ForgeSoloPayloads.registerCommon();
        ForgeSoloSounds.register();
        config = FabricSkillConfig.load();
        adminPolicy = FernAdminPolicy.load();
        FernWorldEditGuard.configure(adminPolicy);
        serverStartedAtMillis = loadServerStartedAtMillis();
        difficulty = new ExtremeNightmareService(nightmareUnlocked());
        claims = new ImmutableClaimsService(config.purgeEffectsOnModBuildings());
        claimStorage = new ClaimPersistence(Path.of("config", "forge-claims.yml"));
        int loadedClaims = claimStorage.loadInto(claims);
        claimStorageReady = loadedClaims >= 0;
        if (!claimStorageReady) LOGGER.error("Claims are fail-closed: persistent storage is unavailable; claim creation is disabled.");
        BuildingCodeCatalog.loadInto(claims);
        new FabricClaimsGuard(claims).register();
        targeting = new PvpTargetingEngine(config);
        effects = new FabricSkillEffects(skillRuntime, config, claims, targeting);
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player) FernItemBindingService.bindInventory(player);
            return true;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) FernItemBindingService.protectDeathDrops(player);
            else if (entity.getKillCredit() instanceof ServerPlayer killer) questBoard.onMobKill(killer, entity);
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && questBoard.isQuestNpc(entity)) {
                questBoard.show(serverPlayer);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        ServerPlayNetworking.registerGlobalReceiver(ForgeSoloPayloads.COMPANION_HELLO_TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            PendingCompanion pending = pendingCompanions.get(player.getUUID());
            if (pending == null || pending.expiresAtMillis() <= System.currentTimeMillis()
                    || payload.protocol() != ForgeSoloPayloads.PROTOCOL_VERSION
                    || !pending.nonce().equals(payload.nonce())) {
                player.connection.disconnect(Component.literal("Fern client verification failed. Please install the official Fern client companion."));
                return;
            }
            pendingCompanions.remove(player.getUUID());
            readyCompanions.put(player.getUUID(), payload.build());
            player.sendSystemMessage(Component.literal("§aFern client companion verified."));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            adminPolicy.apply(server, player);
            progression.restore(player);
            skillRuntime.setCooldownReduction(player.getUUID(), progression.cooldownReduction(player.getUUID()));
            ClassWeaponService.grantIfMissing(player, classFor(player));
            if (!BedrockCompatibility.isBedrockPlayer(player.getUUID()) && server.isDedicatedServer()) {
                pendingCompanions.put(player.getUUID(), new PendingCompanion(java.util.UUID.randomUUID().toString(), System.currentTimeMillis() + 15_000L));
            } else if (!server.isDedicatedServer()) {
                readyCompanions.put(player.getUUID(), "integrated-singleplayer");
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            pendingCompanions.remove(id);
            readyCompanions.remove(id);
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (!FernItemBindingService.canUseOutsideTradeWorld(serverPlayer.getItemInHand(hand), serverPlayer)) {
                serverPlayer.sendSystemMessage(Component.literal("§cThis item is bound to another player. Ownership can only change in Cornucopia."));
                return InteractionResult.FAIL;
            }
            if (craftsman.use(serverPlayer, serverPlayer.getItemInHand(hand))) return InteractionResult.SUCCESS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (thirst.drinkWaterBottle(serverPlayer)) return InteractionResult.SUCCESS;
            thirst.splashWaterPotion(serverPlayer);
            ClassId clazz = classFor(serverPlayer);
            if (serverPlayer.isShiftKeyDown() && ClassWeaponService.isClassWeapon(clazz, serverPlayer.getMainHandItem())) {
                return showPanel(serverPlayer) > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            if (serverPlayer.isShiftKeyDown() && serverPlayer.getMainHandItem().isEmpty()
                    && isClassArena(serverPlayer)) {
                return showClassChangePanel(serverPlayer) > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            if (!serverPlayer.isShiftKeyDown() && clazz == ClassId.CRAFTSMAN
                    && serverPlayer.getMainHandItem().is(Items.CRAFTING_TABLE)) {
                return showCraftingPanel(serverPlayer) > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            if (!serverPlayer.isShiftKeyDown() && serverPlayer.getMainHandItem().isEmpty()) {
                return showPlayerMenu(serverPlayer) > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("fern")
                    .executes(ctx -> showPlayerMenu(ctx.getSource().getPlayerOrException()))
                    .then(Commands.literal("return")
                            .executes(ctx -> showReturnPanel(ctx.getSource().getPlayerOrException()))
                            .then(Commands.literal("last").executes(ctx -> returnToLast(ctx.getSource().getPlayerOrException())))
                            .then(Commands.literal("spawn").executes(ctx -> returnToWorldSpawn(ctx.getSource().getPlayerOrException())))));
            var adminCommand = (java.util.function.Predicate<net.minecraft.commands.CommandSourceStack>) source -> source.isPlayer()
                    && (source.getServer().getPlayerList().isOp(((ServerPlayer) source.getEntity()).nameAndId())
                    || adminPolicy.isOperator((ServerPlayer) source.getEntity()));
            var mobCommand = Commands.literal("mob").requires(adminCommand)
                    .then(Commands.literal("spawn").then(Commands.argument("type", StringArgumentType.word())
                            .executes(ctx -> eliteMobs.spawn(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "type"), 1) ? 1 : 0)
                            .then(Commands.argument("tier", IntegerArgumentType.integer(1, 5))
                                    .executes(ctx -> eliteMobs.spawn(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "type"), IntegerArgumentType.getInteger(ctx, "tier")) ? 1 : 0))))
                    .then(Commands.literal("despawn").executes(ctx -> eliteMobs.despawnNear(ctx.getSource().getPlayerOrException(), 32) > 0 ? 1 : 0)
                            .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                    .executes(ctx -> eliteMobs.despawnNear(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "radius")) > 0 ? 1 : 0)))
                    .then(Commands.literal("despawn-all").executes(ctx -> eliteMobs.despawnAll(ctx.getSource().getPlayerOrException()) > 0 ? 1 : 0));
            var questNpcCommand = Commands.literal("questnpc").requires(adminCommand)
                    .then(Commands.literal("spawn").executes(ctx -> { questBoard.spawnNpc(ctx.getSource().getPlayerOrException()); return 1; }));
            dispatcher.register(Commands.literal("fern").then(mobCommand).then(questNpcCommand));
            dispatcher.register(Commands.literal("craftsman")
                    .executes(ctx -> { craftsman.show(ctx.getSource().getPlayerOrException(), classFor(ctx.getSource().getPlayerOrException())); return 1; })
                    .then(Commands.literal("craft")
                            .then(Commands.argument("recipe", StringArgumentType.word())
                                    .executes(ctx -> craftsman.craft(ctx.getSource().getPlayerOrException(), classFor(ctx.getSource().getPlayerOrException()), StringArgumentType.getString(ctx, "recipe")) ? 1 : 0))));
            dispatcher.register(Commands.literal("quests")
                    .executes(ctx -> { questBoard.show(ctx.getSource().getPlayerOrException()); return 1; })
                    .then(Commands.literal("claim")
                            .then(Commands.argument("quest", StringArgumentType.word())
                                    .executes(ctx -> questBoard.claim(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "quest")) ? 1 : 0))));
            dispatcher.register(Commands.literal("skills")
                    .executes(ctx -> showPanel(ctx.getSource().getPlayerOrException()))
                    .then(Commands.literal("panel").executes(ctx -> showPanel(ctx.getSource().getPlayerOrException())))
                    .then(Commands.literal("close").executes(ctx -> { ctx.getSource().getPlayerOrException().sendSystemMessage(Component.literal("§7Forge panel closed.")); return 1; }))
                    .then(Commands.literal("cast").then(Commands.argument("skill", StringArgumentType.word())
                            .executes(ctx -> cast(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "skill")))))
                    .then(Commands.literal("mana").then(Commands.argument("enabled", BoolArgumentType.bool())
                            .executes(ctx -> setManaHud(ctx.getSource().getPlayerOrException(), BoolArgumentType.getBool(ctx, "enabled")))))
                    .then(Commands.literal("bind").then(Commands.argument("slot", IntegerArgumentType.integer(1, 18))
                            .then(Commands.argument("skill", StringArgumentType.word())
                                    .executes(ctx -> bind(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot"), StringArgumentType.getString(ctx, "skill"))))))
                    .then(Commands.literal("unbind").then(Commands.argument("slot", IntegerArgumentType.integer(1, 18))
                                    .executes(ctx -> unbind(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "slot"))))
                    ));
            dispatcher.register(Commands.literal("class")
                    .executes(ctx -> showClassStatus(ctx.getSource().getPlayerOrException()))
                    .then(Commands.literal("choose").then(Commands.argument("class", StringArgumentType.word())
                            .executes(ctx -> chooseClass(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "class"))))));
            dispatcher.register(Commands.literal("quality")
                    .then(Commands.literal("list").executes(ctx -> listQuality(ctx.getSource().getPlayerOrException())))
                    .then(Commands.argument("preset", StringArgumentType.word())
                            .executes(ctx -> setQuality(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "preset")))));
            dispatcher.register(Commands.literal("waypoints")
                    .executes(ctx -> { waypoints.list(ctx.getSource().getPlayerOrException(), ""); return 1; })
                    .then(Commands.argument("category", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                String value = StringArgumentType.getString(ctx, "category");
                                if (value.equalsIgnoreCase("class") || value.equalsIgnoreCase("pvp") || value.equalsIgnoreCase("forge") || value.equalsIgnoreCase("cornucopia")
                                        || value.equalsIgnoreCase("dungeon") || value.equalsIgnoreCase("escher")
                                        || value.matches("(?i)(class|pvp|forge|cornucopia|dungeon)-\\d{3}")) {
                                    return teleportWaypoint(player, value) ? 1 : 0;
                                }
                                waypoints.list(player, value);
                                return 1;
                            })));
            dispatcher.register(Commands.literal("waypoint")
                    .then(Commands.argument("name", StringArgumentType.word())
                            .executes(ctx -> teleportWaypoint(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "name")) ? 1 : 0)));
            dispatcher.register(Commands.literal("claims")
                    .then(Commands.literal("request")
                            .then(Commands.argument("minX", IntegerArgumentType.integer(-29999984, 29999984))
                            .then(Commands.argument("minY", IntegerArgumentType.integer(-2048, 2048))
                            .then(Commands.argument("minZ", IntegerArgumentType.integer(-29999984, 29999984))
                            .then(Commands.argument("maxX", IntegerArgumentType.integer(-29999984, 29999984))
                            .then(Commands.argument("maxY", IntegerArgumentType.integer(-2048, 2048))
                            .then(Commands.argument("maxZ", IntegerArgumentType.integer(-29999984, 29999984))
                                    .executes(ctx -> requestClaim(ctx.getSource().getPlayerOrException(),
                                            IntegerArgumentType.getInteger(ctx, "minX"), IntegerArgumentType.getInteger(ctx, "minY"), IntegerArgumentType.getInteger(ctx, "minZ"),
                                            IntegerArgumentType.getInteger(ctx, "maxX"), IntegerArgumentType.getInteger(ctx, "maxY"), IntegerArgumentType.getInteger(ctx, "maxZ"))))))))))
                    .then(Commands.literal("confirm")
                            .then(Commands.argument("code", StringArgumentType.word())
                            .then(Commands.argument("phrase", StringArgumentType.word())
                            .then(Commands.argument("ack", StringArgumentType.word())
                                    .executes(ctx -> confirmClaim(ctx.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(ctx, "code"), StringArgumentType.getString(ctx, "phrase"), StringArgumentType.getString(ctx, "ack")))))))
                    .then(Commands.literal("cancel").executes(ctx -> cancelClaim(ctx.getSource().getPlayerOrException()))));
            dispatcher.register(Commands.literal("forge")
                    .then(Commands.literal("shop")
                            .then(Commands.literal("spawn")
                                    .requires(source -> source.isPlayer() && source.getServer().getPlayerList().isOp(((ServerPlayer) source.getEntity()).nameAndId()))
                                    .executes(ctx -> { vendor.spawn(ctx.getSource().getPlayerOrException()); return 1; }))
                            .then(Commands.literal("list").executes(ctx -> { vendor.list(ctx.getSource().getPlayerOrException()); return 1; }))
                            .then(Commands.literal("buy")
                                    .then(Commands.argument("item", StringArgumentType.word())
                                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                                            .executes(ctx -> vendor.buy(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "item"), IntegerArgumentType.getInteger(ctx, "amount")) ? 1 : 0)))))
                    .then(Commands.literal("combiroll")
                            .then(Commands.literal("spawn")
                                    .requires(source -> source.isPlayer() && source.getServer().getPlayerList().isOp(((ServerPlayer) source.getEntity()).nameAndId()))
                                    .executes(ctx -> { combirolls.spawn(ctx.getSource().getPlayerOrException()); return 1; }))
                            .then(Commands.literal("list").executes(ctx -> { combirolls.list(ctx.getSource().getPlayerOrException()); return 1; }))
                            .then(Commands.literal("menu").executes(ctx -> { combirolls.menu(ctx.getSource().getPlayerOrException()); return 1; }))
                            .then(Commands.literal("craft")
                                    .then(Commands.argument("materialA", StringArgumentType.word())
                                    .then(Commands.argument("materialB", StringArgumentType.word())
                                            .executes(ctx -> combirolls.craft(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "materialA"), StringArgumentType.getString(ctx, "materialB")) ? 1 : 0)))))
                            .then(Commands.literal("cast")
                                    .then(Commands.argument("materialA", StringArgumentType.word())
                                    .then(Commands.argument("materialB", StringArgumentType.word())
                                            .executes(ctx -> combirolls.cast(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "materialA"), StringArgumentType.getString(ctx, "materialB")) ? 1 : 0))))
                    .then(Commands.literal("drink").executes(ctx -> drink(ctx.getSource().getPlayerOrException())))
                    .then(Commands.literal("difficulty")
                            .requires(source -> source.isPlayer() && source.getServer().getPlayerList().isOp(((ServerPlayer) source.getEntity()).nameAndId()))
                            .then(Commands.argument("mode", StringArgumentType.word()).executes(ctx -> setDifficulty(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "mode")))))
                    .then(Commands.literal("notice")
                            .requires(source -> source.isPlayer() && source.getServer().getPlayerList().isOp(((ServerPlayer) source.getEntity()).nameAndId()))
                            .then(Commands.argument("title", StringArgumentType.word())
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> broadcastNotice(ctx.getSource().getServer(),
                                            StringArgumentType.getString(ctx, "title"), StringArgumentType.getString(ctx, "message")))))));
        });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(this::applyWorldBorder);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { if (claimStorage != null) claimStorage.save(claims); vendor.save(); combirolls.save(); questBoard.save(); });
        LOGGER.info("Forge Solo Subseason Fabric {} initialized; loaded claims={}, claim storage ready={}", config.version(), Math.max(0, loadedClaims), claimStorageReady);
    }

    private void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        List<ServerPlayer> tickPlayers = List.copyOf(server.getPlayerList().getPlayers());
        for (ServerPlayer player : tickPlayers) {
            if (isMainWorld(player)) {
                lastMainWorldPlaces.put(player.getUUID(), new ReturnPoint(
                        player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
            }
        }
        pendingClaims.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= System.currentTimeMillis());
        for (ServerPlayer player : tickPlayers) {
            UUID id = player.getUUID();
            playerClasses.tick(player, 10L);
            if (!adminPolicy.isGranted(player) && classFor(player) == ClassId.NOVICE && playerClasses.needsArenaArrival(id)) {
                if (waypoints.isWorldLoaded(server, "forge_class_arena")) {
                    classArenaMissingNoticeAt.remove(id);
                    if (teleportWaypoint(player, "class-001")) {
                        playerClasses.markArenaArrival(id);
                        player.sendSystemMessage(Component.literal("§dYou have arrived at the Class Change Arena. Choose your permanent class with §f/class choose <class>§d."));
                        showClassChangePanel(player);
                    }
                } else {
                    long now = System.currentTimeMillis();
                    long last = classArenaMissingNoticeAt.getOrDefault(id, 0L);
                    if (now - last >= 30_000L) {
                        classArenaMissingNoticeAt.put(id, now);
                        player.sendSystemMessage(Component.literal("§cThe Class Change Arena is not installed yet. An administrator must add the Fern arena datapack and restart the server."));
                    }
                }
            }
            if (server.getTickCount() % 20 == 0) ClassWeaponService.grantIfMissing(player, classFor(player));
            if (server.getTickCount() % 20 == 0) FernItemBindingService.bindInventory(player);
            skillRuntime.regenerate(id, config.manaRegenPerSecond() / 2.0);
            if (BedrockCompatibility.isBedrockPlayer(id)) {
                if (settings.getOrDefault(id, PlayerSettings.defaults()).manaHud()) sendLegacyHud(player);
            } else {
                PendingCompanion pending = pendingCompanions.get(id);
                if (pending != null && pending.expiresAtMillis() <= System.currentTimeMillis()) {
                    player.connection.disconnect(Component.literal("Fern client companion verification timed out. Install the official companion and reconnect."));
                } else if (pending != null && server.getTickCount() % 20 == 0) {
                    if (!ServerPlayNetworking.canSend(player, ForgeSoloPayloads.COMPANION_CHALLENGE_TYPE)) {
                        player.connection.disconnect(Component.literal("Fern requires the official client companion. Install it, then reconnect."));
                    } else {
                        ServerPlayNetworking.send(player, new ForgeSoloPayloads.CompanionChallengePayload(
                                ForgeSoloPayloads.PROTOCOL_VERSION, pending.nonce()));
                    }
                } else if (readyCompanions.containsKey(id) && settings.getOrDefault(id, PlayerSettings.defaults()).manaHud()) {
                    ServerPlayNetworking.send(player, new ForgeSoloPayloads.HudSnapshotPayload(hudSnapshot(player)));
                }
            }
            if (server.getTickCount() % 20 == 0) { thirst.tick(player); difficulty.tick(player); }
        }
        vendor.tick(System.currentTimeMillis());
    }

    private void applyWorldBorder(MinecraftServer server) {
        if (!config.worldBorderEnabled()) return;
        for (var level : server.getAllLevels()) {
            var border = level.getWorldBorder();
            border.setCenter(config.worldBorderCenterX(), config.worldBorderCenterZ());
            border.setSize(config.worldBorderDiameter());
        }
        LOGGER.info("Applied Fern world border: center=({}, {}), diameter={} blocks; chunks generate on demand inside the border.",
                config.worldBorderCenterX(), config.worldBorderCenterZ(), config.worldBorderDiameter());
    }

    private int requestClaim(ServerPlayer player, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (!claimStorageReady) { player.sendSystemMessage(Component.literal("§cClaims are disabled because persistent storage is unavailable.")); return 0; }
        var area = new ImmutableClaimsService.Cuboid(Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ), Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ));
        String code = Integer.toString(ThreadLocalRandom.current().nextInt(100000, 1000000));
        pendingClaims.put(player.getUUID(), new PendingClaim(area, code, System.currentTimeMillis() + 300_000));
        player.sendSystemMessage(Component.literal("§eClaim request staged for §f" + area + "§e."));
        player.sendSystemMessage(Component.literal("§6Three confirmations are required within 5 minutes."));
        player.sendSystemMessage(Component.literal("§7Run: §f/claims confirm " + code + " CLAIM_FOREVER I_UNDERSTAND"));
        player.sendSystemMessage(Component.literal("§7The area is permanent for normal players: no delete or resize command exists."));
        return 1;
    }

    private int confirmClaim(ServerPlayer player, String code, String phrase, String acknowledgement) {
        PendingClaim pending = pendingClaims.get(player.getUUID());
        if (pending == null || pending.expiresAtMillis() <= System.currentTimeMillis()) { pendingClaims.remove(player.getUUID()); player.sendSystemMessage(Component.literal("§cNo active claim request. Start with /claims request.")); return 0; }
        if (!pending.code().equals(code) || !"CLAIM_FOREVER".equals(phrase) || !"I_UNDERSTAND".equals(acknowledgement)) { player.sendSystemMessage(Component.literal("§cConfirmation failed. The code and both exact phrases must match.")); return 0; }
        String world = player.level().dimension().identifier().toString();
        var confirmation = new ThreeFactorConfirmation(player.getUUID(), world, pending.area(), "CLAIM FOREVER", code, acknowledgement, code, acknowledgement);
        var result = claims.createClaim(player.getUUID(), world, pending.area(), confirmation);
        if (result != ImmutableClaimsService.ClaimResult.CREATED) { player.sendSystemMessage(Component.literal("§cClaim rejected: §f" + result)); return 0; }
        pendingClaims.remove(player.getUUID());
        if (!claimStorage.save(claims)) LOGGER.error("Claim created in memory but could not be persisted immediately.");
        player.sendSystemMessage(Component.literal("§aClaim created permanently. Normal players cannot delete or resize it."));
        return 1;
    }

    private int cancelClaim(ServerPlayer player) { pendingClaims.remove(player.getUUID()); player.sendSystemMessage(Component.literal("§7Pending claim request cancelled.")); return 1; }
    private int broadcastNotice(MinecraftServer server, String title, String message) {
        Component titleText = Component.literal("◆ " + title.toUpperCase(java.util.Locale.ROOT))
                .withStyle(style -> style.withFont(new FontDescription.Resource(Identifier.parse("forge_solo:forge"))));
        Component subtitleText = Component.literal(message)
                .withStyle(style -> style.withFont(new FontDescription.Resource(Identifier.parse("forge_solo:forge"))));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playSound(ForgeSoloSounds.NOTICE, 0.75f, 1.0f);
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
            player.connection.send(new ClientboundSetTitleTextPacket(titleText));
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitleText));
        }
        LOGGER.info("Broadcast notice: {} — {}", title, message);
        return 1;
    }
    private int showPanel(ServerPlayer player) {
        ClassId clazz = classFor(player);
        if (clazz == ClassId.NOVICE) {
            return showClassChangePanel(player);
        }
        WeaponDefinition weapon = ClassWeaponService.weaponFor(player, clazz);
        if (weapon == null) {
            player.sendSystemMessage(Component.literal("§cYou need your Fern class weapon before opening its skill panel."));
            return 0;
        }
        if (!weapon.allowedInWorld(player.level().dimension().identifier().toString())) {
            player.sendSystemMessage(Component.literal("§cThis weapon's abilities are disabled in the current world."));
            return 0;
        }
        var skills = ArmamentCatalog.panelSkills(weapon, ContentCatalog.routeSkills(clazz));
        if (BedrockCompatibility.isBedrockPlayer(player.getUUID())
                && BedrockFormService.showSkillPanel(player, skills, skillId -> cast(player, skillId))) {
            return 1;
        }
        if (readyCompanions.containsKey(player.getUUID())
                && ServerPlayNetworking.canSend(player, ForgeSoloPayloads.PANEL_OPEN_TYPE)) {
            String skillIds = skills.stream().map(SkillDefinition::id).collect(Collectors.joining(","));
            String definitionId = weapon == null ? "fern:unknown" : weapon.id();
            ServerPlayNetworking.send(player, new ForgeSoloPayloads.PanelOpenPayload(clazz.name() + "|" + definitionId + "|" + skillIds));
            return 1;
        }
        player.sendSystemMessage(Component.literal("§2§l✦ FORGE / SKILL DECK")
                .withStyle(style -> style.withFont(new FontDescription.Resource(Identifier.parse("forge_solo:forge")))));
        player.sendSystemMessage(Component.literal("§8CLASS §f" + clazz.name() + "  §8•  §9MANA §f" + manaText(player)
                + "  §8•  §3THIRST §f" + thirst.bar(player.getUUID())));
        player.sendSystemMessage(Component.literal("§8────────────────────────────────────────"));
        player.sendSystemMessage(Component.literal("§7Select an ability below. Mana and cooldowns are validated server-side."));
        for (int i = 0; i < skills.size(); i++) {
            SkillDefinition skill = skills.get(i);
            player.sendSystemMessage(clickable("§8[ §b" + String.format(java.util.Locale.ROOT, "%02d", i + 1)
                    + " §8] §f" + skill.displayName() + " §a▶ CAST", "/skills cast " + skill.id(), skill.description()));
        }
        player.sendSystemMessage(Component.literal("§8────────────────────────────────────────"));
        player.sendSystemMessage(clickable("§8[ CLOSE DECK ]", "/skills close", "Close the skill deck."));
        return 1;
    }
    private int showClassChangePanel(ServerPlayer player) {
        ClassId current = classFor(player);
        if (BedrockCompatibility.isBedrockPlayer(player.getUUID())) {
            List<ClassId> options = List.of(ClassId.LUMBER, ClassId.MINER, ClassId.CRAFTSMAN, ClassId.MARKSMAN, ClassId.HEALER);
            if (BedrockFormService.showClassPanel(player, options, chosen -> chooseClass(player, chosen.name()))) return 1;
        }
        player.sendSystemMessage(Component.literal("§d§lCLASS CHANGE PANEL §8— §fClass Change Arena only"));
        if (current != ClassId.NOVICE) {
            player.sendSystemMessage(Component.literal("§aYour permanent class is §f" + current.name() + "§a. Class changes are locked after selection."));
        } else {
            player.sendSystemMessage(Component.literal("§7Click a class button when your three-day unlock is ready."));
            for (ClassId option : new ClassId[]{ClassId.HEALER, ClassId.MINER, ClassId.MARKSMAN, ClassId.LUMBER, ClassId.CRAFTSMAN}) {
                player.sendSystemMessage(clickable("§a[ " + option.name() + " ]", "/class choose " + option.name(), "Choose " + option.name()));
            }
        }
        player.sendSystemMessage(clickable("§8[ CLOSE ]", "/skills close", "Close class-change popup; crouch and right-click empty air here to reopen it."));
        return 1;
    }
    private MutableComponent clickable(String label, String command, String hover) {
        return Component.literal(label).withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal(hover))));
    }
    private boolean isClassArena(ServerPlayer player) {
        return player.level().dimension().identifier().getPath().equals("forge_class_arena");
    }
    private int showCraftingPanel(ServerPlayer player) { craftsman.show(player, classFor(player)); return 1; }
    private int showPlayerMenu(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§2§lFERN MENU §8— §fClick an option"));
        player.sendSystemMessage(clickable("§a[ SKILLS ]", "/skills panel", "Open your clickable skill panel"));
        player.sendSystemMessage(clickable("§b[ CORNUCOPIA / TRADE ]", "/waypoint cornucopia", "Go to the shared landing and trade world"));
        player.sendSystemMessage(clickable("§5[ ESCHER DUNGEON ]", "/waypoint dungeon", "Enter the Escher dungeon; use /fern return to return to the main world"));
        player.sendSystemMessage(clickable("§3[ QUEST BOARD ]", "/quests", "View your daily quest progress"));
        player.sendSystemMessage(clickable("§b[ CLASS CHANGE ]", "/waypoint class", "Travel to the Class Change Arena"));
        player.sendSystemMessage(clickable("§c[ PVP ARENA ]", "/waypoint pvp", "Travel to the PvP Arena"));
        player.sendSystemMessage(clickable("§d[ COMBIROLLS ]", "/forge combiroll menu", "Craft or cast Combirolls"));
        player.sendSystemMessage(clickable("§6[ VENDOR ]", "/forge shop list", "Open the Forge Vendor stock"));
        if (!isMainWorld(player)) player.sendSystemMessage(clickable("§e[ RETURN TO MAIN WORLD ]", "/fern return", "Choose Last Seen Place or World Spawn"));
        player.sendSystemMessage(clickable("§8[ CLOSE ]", "/skills close", "Close the Fern menu"));
        return 1;
    }
    private int showReturnPanel(ServerPlayer player) {
        if (isMainWorld(player)) {
            player.sendSystemMessage(Component.literal("§7You are already in the main world."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("§e§lRETURN TO MAIN WORLD §8— §fChoose a destination"));
        ReturnPoint point = lastMainWorldPlaces.get(player.getUUID());
        if (point != null) {
            player.sendSystemMessage(clickable("§a[ LAST SEEN PLACE ]", "/fern return last", "Return to your saved main-world location"));
        } else {
            player.sendSystemMessage(Component.literal("§8[ LAST SEEN PLACE ] §7No main-world location saved yet."));
        }
        player.sendSystemMessage(clickable("§b[ WORLD SPAWN ]", "/fern return spawn", "Return to the main-world spawn"));
        player.sendSystemMessage(clickable("§8[ CLOSE ]", "/skills close", "Close return menu"));
        return 1;
    }
    private int returnToLast(ServerPlayer player) {
        ReturnPoint point = lastMainWorldPlaces.get(player.getUUID());
        if (point == null) { player.sendSystemMessage(Component.literal("§cNo main-world Last Seen Place is saved.")); return 0; }
        MinecraftServer server = player.level().getServer();
        if (server == null || !isMainWorld(player)) {
            ServerLevel overworld = server == null ? null : server.overworld();
            if (overworld == null) return 0;
            if (!player.teleportTo(overworld, point.x(), point.y(), point.z(), Set.of(), point.yaw(), point.pitch(), false)) {
                player.sendSystemMessage(Component.literal("§cThe server rejected the Last Seen Place teleport."));
                return 0;
            }
            player.sendSystemMessage(Component.literal("§aReturned to your Last Seen Place in the main world."));
            return 1;
        }
        return 0;
    }
    private int returnToWorldSpawn(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) return 0;
        ServerLevel overworld = server.overworld();
        var respawn = overworld.getRespawnData();
        var spawn = respawn.pos();
        if (!player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                Set.of(), respawn.yaw(), respawn.pitch(), false)) {
            player.sendSystemMessage(Component.literal("§cThe server rejected the main-world spawn teleport."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("§aReturned to the main-world spawn."));
        return 1;
    }
    private boolean teleportWaypoint(ServerPlayer player, String requested) {
        if (isMainWorld(player)) {
            lastMainWorldPlaces.put(player.getUUID(), new ReturnPoint(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
        }
        return waypoints.teleport(player, requested);
    }
    private boolean isMainWorld(ServerPlayer player) {
        return player.level().dimension().identifier().toString().equals("minecraft:overworld");
    }
    private int showClassStatus(ServerPlayer player) { ClassId clazz = classFor(player); if (clazz != ClassId.NOVICE) { player.sendSystemMessage(Component.literal("§aYour class is §f" + clazz.name() + "§a.")); return 1; } long remain = playerClasses.remainingTicks(player.getUUID()); player.sendSystemMessage(Component.literal("§eClass selection unlocks after three connected Minecraft days. Remaining: §f" + remain + " ticks§e.")); return 1; }
    private int chooseClass(ServerPlayer player, String value) { ClassId chosen; try { chosen = ClassId.valueOf(value.toUpperCase(java.util.Locale.ROOT)); } catch (IllegalArgumentException error) { player.sendSystemMessage(Component.literal("§cUnknown class. Choose HEALER, MINER, MARKSMAN, LUMBER, or CRAFTSMAN.")); return 0; } if (!playerClasses.choose(player, chosen)) { player.sendSystemMessage(Component.literal("§cYou cannot choose that class yet, or you already have a permanent class.")); return 0; } ClassWeaponService.grantIfMissing(player, chosen); player.sendSystemMessage(Component.literal("§aClass selected: §f" + chosen.name() + "§a.")); return 1; }
    private int bind(ServerPlayer player, int slot, String skillId) { if (ContentCatalog.routeSkills(classFor(player)).stream().noneMatch(skill -> skill.id().equals(skillId))) { player.sendSystemMessage(Component.literal("§cUnknown skill id.")); return 0; } bindings.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>()).put(slot, skillId); player.sendSystemMessage(Component.literal("§aBound §f" + skillId + " §ato slot §f" + slot + "§a.")); return 1; }
    private int unbind(ServerPlayer player, int slot) { Map<Integer, String> playerBindings = bindings.get(player.getUUID()); if (playerBindings != null) playerBindings.remove(slot); player.sendSystemMessage(Component.literal("§7Cleared skill slot §f" + slot + "§7.")); return 1; }
    private int cast(ServerPlayer player, String skillId) { ClassId clazz = classFor(player); WeaponDefinition weapon = ClassWeaponService.weaponFor(player, clazz); if (weapon == null) { player.sendSystemMessage(Component.literal("§cYou need your Fern class weapon to cast class skills.")); return 0; } if (!weapon.allowedInWorld(player.level().dimension().identifier().toString())) { player.sendSystemMessage(Component.literal("§cThis weapon's abilities are disabled in the current world.")); return 0; } var routeSkills = ContentCatalog.routeSkills(clazz); SkillDefinition skill = routeSkills.stream().filter(candidate -> candidate.id().equals(skillId)).findFirst().orElse(null); if (skill == null || !ArmamentCatalog.panelSkills(weapon, routeSkills).contains(skill)) { player.sendSystemMessage(Component.literal("§cThat skill is not mapped to your Fern weapon.")); return 0; } skillRuntime.setCooldownReduction(player.getUUID(), progression.cooldownReduction(player.getUUID())); return effects.cast(player, skill, adminPolicy.playerLevel(player), adminPolicy.skillLevel(player)) ? 1 : 0; }
    private int setManaHud(ServerPlayer player, boolean enabled) { settings.put(player.getUUID(), new PlayerSettings(enabled)); player.sendSystemMessage(Component.literal(enabled ? "§aMana indicator enabled." : "§7Mana indicator disabled.")); return 1; }
    private void sendLegacyHud(ServerPlayer player) { player.sendSystemMessage(Component.literal("§c❤ " + healthText(player) + " §b✦ " + manaText(player) + " §3💧 " + thirst.bar(player.getUUID())), true); }
    private ClassId classFor(ServerPlayer player) { ClassId granted = adminPolicy.classFor(player, ClassId.NOVICE); return granted != ClassId.NOVICE ? granted : java.util.Optional.ofNullable(playerClasses.get(player.getUUID())).orElse(ClassId.NOVICE); }
    private String hudSnapshot(ServerPlayer player) {
        ManaState state = skillRuntime.mana(player.getUUID());
        double mana = state == null ? 100.0 : state.current();
        double maxMana = state == null ? 100.0 : state.maximum();
        StringBuilder binds = new StringBuilder();
        Map<Integer, String> playerBindings = bindings.getOrDefault(player.getUUID(), Map.of());
        for (int slot = 1; slot <= 18; slot++) {
            if (slot > 1) binds.append(',');
            binds.append(playerBindings.getOrDefault(slot, ""));
        }
        QualityProfile profile = quality.getOrDefault(player.getUUID(), QualityProfile.MEDIUM);
        return String.format(java.util.Locale.ROOT, "1|%.2f|%.2f|%.2f|100.0|%s|%s|%s|%s",
                mana, maxMana, thirst.value(player.getUUID()), classFor(player).name(),
                profile.name().toLowerCase(java.util.Locale.ROOT), binds, combirolls.hudStatus(player));
    }
    private String healthText(ServerPlayer player) { return String.format("%.0f/%.0f", player.getHealth(), player.getMaxHealth()); }
    private String manaText(ServerPlayer player) { ManaState state = skillRuntime.mana(player.getUUID()); if (state == null) return "100/100"; return String.format("%.0f/%.0f", state.current(), state.maximum()); }
    private int listQuality(ServerPlayer player) { QualityProfile.describe(player); return 1; }
    private int setQuality(ServerPlayer player, String value) { QualityProfile profile = QualityProfile.parse(value); quality.put(player.getUUID(), profile); QualityProfile.set(player.getUUID(), profile); player.sendSystemMessage(Component.literal("§aVisual quality set to §f" + profile.name().toLowerCase(java.util.Locale.ROOT) + "§a.")); return 1; }
    private int drink(ServerPlayer player) { if (!thirst.drinkWaterBottle(player)) { player.sendSystemMessage(Component.literal("§cHold a water bottle (glass bottle) to drink.")); return 0; } player.sendSystemMessage(Component.literal("§bYou drank water. Thirst restored.")); return 1; }
    private int setDifficulty(ServerPlayer player, String value) { boolean enable = value.equalsIgnoreCase("extreme-nightmare") || value.equalsIgnoreCase("nightmare"); if (enable && !nightmareUnlocked()) { player.sendSystemMessage(Component.literal("§cExtreme Nightmare unlocks after 21 days of server uptime. Remaining: " + Math.max(0, (21L * 24 * 60 * 60) - ((System.currentTimeMillis() - serverStartedAtMillis) / 1000)) + " seconds.")); return 0; } difficulty.setEnabled(enable); player.sendSystemMessage(Component.literal(enable ? "§4Extreme Nightmare enabled: hostile combat is 5x harder than Hard." : "§aExtreme Nightmare disabled.")); return 1; }
    private boolean nightmareUnlocked() { return System.currentTimeMillis() - serverStartedAtMillis >= 21L * 24 * 60 * 60 * 1000; }
    private long loadServerStartedAtMillis() { Path path = Path.of("config", "forge-server-started-at.txt"); try { Files.createDirectories(path.getParent()); if (Files.exists(path)) return Long.parseLong(Files.readString(path).trim()); long now = System.currentTimeMillis(); Files.writeString(path, Long.toString(now)); return now; } catch (Exception error) { LOGGER.warn("Could not read server activation clock; Nightmare remains locked.", error); return System.currentTimeMillis(); } }
    private record PlayerSettings(boolean manaHud) { static PlayerSettings defaults() { return new PlayerSettings(true); } }
    private record PendingCompanion(String nonce, long expiresAtMillis) { }
    private record PendingClaim(ImmutableClaimsService.Cuboid area, String code, long expiresAtMillis) { }
    private record ReturnPoint(double x, double y, double z, float yaw, float pitch) { }
}
