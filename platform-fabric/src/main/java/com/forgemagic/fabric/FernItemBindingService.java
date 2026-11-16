package com.forgemagic.fabric;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-owned item identities: ordinary worlds are owner-locked; Cornucopia is the transfer zone. */
public final class FernItemBindingService {
    private static final String OWNER = "fern_owner_uuid";
    private static final String STACK_ID = "fern_stack_id";
    private static final String DROP_LOCK = "fern_death_drop";

    private FernItemBindingService() { }

    public static void bindInventory(ServerPlayer player) {
        boolean cornucopia = player.level().dimension().identifier().getPath().equals("forge_cornucopia");
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                if (cornucopia) transfer(stack, player.getUUID());
                else bind(stack, player.getUUID(), false);
            }
        }
    }

    public static void bind(ItemStack stack, UUID owner, boolean deathDrop) {
        if (stack == null || stack.isEmpty() || owner == null) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (!tag.contains(OWNER)) tag.putString(OWNER, owner.toString());
            // Same owner/type marker lets matching stacks merge; unique per-stack UUIDs would make all materials unstackable.
            if (!tag.contains(STACK_ID)) tag.putString(STACK_ID, owner + ":" + BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (deathDrop) tag.putString(DROP_LOCK, "true");
        });
    }

    private static void transfer(ItemStack stack, UUID newOwner) {
        if (stack == null || stack.isEmpty() || newOwner == null) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(OWNER, newOwner.toString());
            tag.putString(STACK_ID, newOwner + ":" + BuiltInRegistries.ITEM.getKey(stack.getItem()));
            tag.remove(DROP_LOCK);
        });
    }

    public static boolean isPermanentDeathDrop(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getStringOr(DROP_LOCK, "").equals("true");
    }

    public static UUID owner(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        try {
            String value = data.copyTag().getStringOr(OWNER, "");
            return value.isBlank() ? null : UUID.fromString(value);
        } catch (RuntimeException ignored) { return null; }
    }

    public static boolean isOwnedByOrUnbound(ItemStack stack, UUID player) {
        UUID bound = owner(stack);
        return bound == null || bound.equals(player);
    }

    public static boolean canUseOutsideTradeWorld(ItemStack stack, Player player) {
        return isOwnedByOrUnbound(stack, player.getUUID())
                || player.level().dimension().identifier().getPath().equals("forge_cornucopia");
    }

    public static boolean allowPickup(ItemEntity entity, Player picker) {
        ItemStack stack = entity.getItem();
        UUID boundOwner = owner(stack);
        boolean cornucopia = picker.level().dimension().identifier().getPath().equals("forge_cornucopia");
        if (boundOwner == null) {
            if (entity.getOwner() instanceof Player thrower) {
                boundOwner = thrower.getUUID();
                bind(stack, boundOwner, false);
            } else if (cornucopia) {
                transfer(stack, picker.getUUID());
            } else {
                bind(stack, picker.getUUID(), false);
                entity.setItem(stack);
                return true;
            }
            entity.setItem(stack);
        }
        if (cornucopia) {
            transfer(stack, picker.getUUID());
            entity.setItem(stack);
            return true;
        }
        return boundOwner.equals(picker.getUUID());
    }

    public static void protectDeathDrops(ServerPlayer player) {
        bindInventory(player);
        List<String> locations = new ArrayList<>();
        var bounds = player.getBoundingBox().inflate(8.0);
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, bounds,
                entity -> player.getUUID().equals(owner(entity.getItem())))) {
            bind(item.getItem(), player.getUUID(), true);
            item.setItem(item.getItem());
            item.setUnlimitedLifetime();
            item.setThrower(player);
            locations.add(String.format(java.util.Locale.ROOT, "%d %d %d", item.getBlockX(), item.getBlockY(), item.getBlockZ()));
        }
        if (locations.isEmpty()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eYour death items are owner-locked outside Cornucopia. Check the death location: §f"
                    + player.level().dimension().identifier() + " " + player.blockPosition().toShortString()));
        } else {
            String shown = String.join("; ", locations.subList(0, Math.min(5, locations.size())));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eYour drops are owner-locked outside Cornucopia and will not despawn. Coordinates: §f"
                    + player.level().dimension().identifier() + " — " + shown + (locations.size() > 5 ? " (and more)" : "")));
        }
    }
}
