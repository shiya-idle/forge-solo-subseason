package com.forgemagic.fabric;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Small persistent daily quest board; rewards are tangible items until the server ledger adapter exists. */
public final class FernQuestBoardService {
    private static final String NPC_TAG = "fern_quest_board_npc";
    private static final String QUEST_ID = "daily_zombie_hunt";
    private static final int REQUIRED = 5;
    private static final int REWARD_EMERALDS = 12;
    private static final String ESCHER_QUEST_ID = "daily_escher_delver";
    private static final int ESCHER_REQUIRED = 10;
    private static final int ESCHER_REWARD_EMERALDS = 16;
    private final ConcurrentHashMap<UUID, Progress> progress = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Progress> escherProgress = new ConcurrentHashMap<>();
    private final Path file = Path.of("config", "forge-quests.properties");

    public FernQuestBoardService() { load(); }

    public void spawnNpc(ServerPlayer admin) {
        @SuppressWarnings("unchecked") EntityType<? extends Villager> type = (EntityType<? extends Villager>) BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:villager"));
        Villager npc = new Villager(type, admin.level());
        npc.setPos(admin.getX(), admin.getY(), admin.getZ());
        npc.setYRot(admin.getYRot());
        npc.setCustomName(Component.literal("§b§lFern Questkeeper"));
        npc.setCustomNameVisible(true);
        npc.setNoAi(true);
        npc.setPersistenceRequired();
        npc.addTag(NPC_TAG);
        admin.level().addFreshEntity(npc);
        admin.sendSystemMessage(Component.literal("§aQuestkeeper spawned. Players can right-click it or use §f/quests§a."));
    }

    public boolean isQuestNpc(net.minecraft.world.entity.Entity entity) { return entity.entityTags().contains(NPC_TAG); }

    public void onMobKill(ServerPlayer player, LivingEntity killed) {
        if (killed instanceof ServerPlayer) return;
        Identifier killedId = BuiltInRegistries.ENTITY_TYPE.getKey(killed.getType());
        UUID playerId = player.getUUID();
        boolean changed = false;
        if (Identifier.parse("minecraft:zombie").equals(killedId)) {
            Progress current = current(playerId);
            if (!current.claimed() && current.completed() < REQUIRED) {
                progress.put(playerId, new Progress(current.day(), current.completed() + 1, false));
                changed = true;
            }
        }
        if ("tid".equals(killed.level().dimension().identifier().getNamespace())
                && killed.getType().getCategory() == MobCategory.MONSTER) {
            Progress current = currentEscher(playerId);
            if (!current.claimed() && current.completed() < ESCHER_REQUIRED) {
                escherProgress.put(playerId, new Progress(current.day(), current.completed() + 1, false));
                changed = true;
            }
        }
        if (changed) save();
    }

    public void show(ServerPlayer player) {
        Progress state = current(player.getUUID());
        player.sendSystemMessage(Component.literal("§b§lFERN QUEST BOARD §8— §7Daily"));
        player.sendSystemMessage(Component.literal("§3Daily First Hunt §8| §fDefeat 5 zombies §8| §eReward: 12 emeralds"));
        player.sendSystemMessage(Component.literal("§7Progress: §f" + Math.min(REQUIRED, state.completed()) + "/" + REQUIRED));
        if (state.claimed()) {
            player.sendSystemMessage(Component.literal("§aReward claimed today. New daily quest arrives at 00:00 UTC."));
        } else if (state.completed() >= REQUIRED) {
            player.sendSystemMessage(Component.literal("§a[ CLAIM 12 EMERALDS ]")
                    .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/quests claim " + QUEST_ID))));
        } else {
            player.sendSystemMessage(Component.literal("§7Keep hunting. Progress is saved across restarts."));
        }
        Progress dungeon = currentEscher(player.getUUID());
        player.sendSystemMessage(Component.literal("§5Escher Delver §8| §fDefeat 10 dungeon monsters §8| §eReward: 16 emeralds"));
        player.sendSystemMessage(Component.literal("§7Progress: §f" + Math.min(ESCHER_REQUIRED, dungeon.completed()) + "/" + ESCHER_REQUIRED));
        if (dungeon.claimed()) {
            player.sendSystemMessage(Component.literal("§aEscher Delver reward claimed today."));
        } else if (dungeon.completed() >= ESCHER_REQUIRED) {
            player.sendSystemMessage(Component.literal("§a[ CLAIM 16 EMERALDS ]")
                    .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/quests claim " + ESCHER_QUEST_ID))));
        } else {
            player.sendSystemMessage(Component.literal("§7Only monster kills inside an Escher `tid:` dimension count."));
        }
    }

    public boolean claim(ServerPlayer player, String questId) {
        if (QUEST_ID.equals(questId)) return claim(player, progress, REQUIRED, REWARD_EMERALDS, "Daily First Hunt");
        if (ESCHER_QUEST_ID.equals(questId)) return claim(player, escherProgress, ESCHER_REQUIRED, ESCHER_REWARD_EMERALDS, "Escher Delver");
        {
            player.sendSystemMessage(Component.literal("§cUnknown quest. Open §f/quests§c."));
            return false;
        }
    }

    private boolean claim(ServerPlayer player, ConcurrentHashMap<UUID, Progress> tracker, int required, int rewardCount, String label) {
        Progress state = current(player.getUUID(), tracker);
        if (state.claimed() || state.completed() < required) {
            player.sendSystemMessage(Component.literal("§cQuest is incomplete or already claimed."));
            return false;
        }
        state = new Progress(state.day(), state.completed(), true);
        tracker.put(player.getUUID(), state);
        save();
        ItemStack reward = new ItemStack(Items.EMERALD, rewardCount);
        FernItemBindingService.bind(reward, player.getUUID(), false);
        if (!player.getInventory().add(reward)) player.drop(reward, false, net.minecraft.util.Prediction.SERVER_ONLY);
        player.sendSystemMessage(Component.literal("§aQuest complete: §f" + label + "§a — received §f" + rewardCount + " emeralds§a."));
        return true;
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            Properties properties = new Properties();
            progress.forEach((uuid, value) -> properties.setProperty(uuid.toString(), value.day() + "," + value.completed() + "," + value.claimed()));
            escherProgress.forEach((uuid, value) -> properties.setProperty("escher." + uuid, value.day() + "," + value.completed() + "," + value.claimed()));
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) { properties.store(out, "Fern daily quest progress"); }
            try { Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception error) { ForgeSoloFabric.LOGGER.warn("Could not save Fern quest progress", error); }
    }

    private Progress current(UUID id) {
        return current(id, progress);
    }

    private Progress currentEscher(UUID id) {
        return current(id, escherProgress);
    }

    private Progress current(UUID id, ConcurrentHashMap<UUID, Progress> tracker) {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        return tracker.compute(id, (key, old) -> old == null || !old.day().equals(today) ? new Progress(today, 0, false) : old);
    }

    private void load() {
        if (!Files.exists(file)) return;
        try (InputStream in = Files.newInputStream(file)) {
            Properties p = new Properties(); p.load(in);
            for (String key : p.stringPropertyNames()) {
                try {
                    String[] parts = p.getProperty(key, "").split(",");
                    if (parts.length == 3) {
                        boolean escher = key.startsWith("escher.");
                        UUID id = UUID.fromString(escher ? key.substring("escher.".length()) : key);
                        Progress value = new Progress(parts[0], Math.max(0, Integer.parseInt(parts[1])), Boolean.parseBoolean(parts[2]));
                        (escher ? escherProgress : progress).put(id, value);
                    }
                } catch (RuntimeException ignored) { }
            }
        } catch (Exception error) { ForgeSoloFabric.LOGGER.warn("Could not load Fern quest progress", error); }
    }

    private record Progress(String day, int completed, boolean claimed) { }
}
