package com.forgemagic.fabric;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

import java.util.Locale;

/** Admin-spawned elite mobs are tagged, persistent, and removable without affecting natural mobs. */
public final class FernEliteMobService {
    private static final String TAG = "fern_elite_mob";

    public boolean spawn(ServerPlayer admin, String requestedType, int tier) {
        int boundedTier = Math.max(1, Math.min(5, tier));
        String id = requestedType == null ? "" : requestedType.toLowerCase(Locale.ROOT);
        if (!id.contains(":")) id = "minecraft:" + id;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
        if (type == null || !BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("minecraft")) {
            admin.sendSystemMessage(Component.literal("§cChoose a vanilla mob id, for example zombie, skeleton, or vindicator."));
            return false;
        }
        if (!(type.create(admin.level(), EntitySpawnReason.COMMAND) instanceof Mob mob)) {
            admin.sendSystemMessage(Component.literal("§cThat entity is not a living mob."));
            return false;
        }
        mob.setPos(admin.getX(), admin.getY(), admin.getZ());
        mob.setYRot(admin.getYRot());
        mob.addTag(TAG);
        mob.addTag(TAG + "_tier_" + boundedTier);
        mob.setPersistenceRequired();
        mob.setCustomName(Component.literal("§c§lFern Elite §7[T" + boundedTier + "] §f" + mob.getType().getDescription().getString()));
        mob.setCustomNameVisible(true);
        double healthScale = 1.5 + boundedTier * 0.5;
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(Math.min(200.0, health.getBaseValue() * healthScale));
            mob.setHealth((float) health.getBaseValue());
        }
        AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(Math.min(40.0, attack.getBaseValue() * (1.25 + boundedTier * 0.25)));
        mob.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, Integer.MAX_VALUE, Math.min(2, boundedTier / 2), false, false, true));
        mob.addEffect(new MobEffectInstance(MobEffects.STRENGTH, Integer.MAX_VALUE, Math.min(3, boundedTier / 2), false, false, true));
        if (!admin.level().addFreshEntity(mob)) {
            admin.sendSystemMessage(Component.literal("§cThe elite mob could not be spawned here."));
            return false;
        }
        admin.sendSystemMessage(Component.literal("§aSpawned Fern Elite tier " + boundedTier + " §f" + id + "§a."));
        return true;
    }

    public int despawnNear(ServerPlayer admin, double radius) {
        double safeRadius = Math.max(1.0, Math.min(128.0, radius));
        var found = admin.level().getEntitiesOfClass(Mob.class, new AABB(
                admin.getX() - safeRadius, admin.getY() - safeRadius, admin.getZ() - safeRadius,
                admin.getX() + safeRadius, admin.getY() + safeRadius, admin.getZ() + safeRadius), mob -> mob.entityTags().contains(TAG));
        found.forEach(mob -> mob.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED));
        admin.sendSystemMessage(Component.literal(found.isEmpty() ? "§7No tagged Fern elite mobs within " + (int) safeRadius + " blocks."
                : "§aDespawned §f" + found.size() + "§a Fern elite mob(s)."));
        return found.size();
    }

    public int despawnAll(ServerPlayer admin) {
        int removed = 0;
        for (Mob mob : admin.level().getEntitiesOfClass(Mob.class, new AABB(
                admin.getX() - 128, admin.getY() - 128, admin.getZ() - 128,
                admin.getX() + 128, admin.getY() + 128, admin.getZ() + 128), entity -> entity.entityTags().contains(TAG))) {
            mob.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            removed++;
        }
        admin.sendSystemMessage(Component.literal("§aDespawned §f" + removed + "§a Fern elite mob(s) within 128 blocks."));
        return removed;
    }
}
