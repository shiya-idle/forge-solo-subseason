package com.forgemagic.fabric;

import com.forgemagic.api.ClassId;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.tags.ItemTags;

public final class ClassWeaponService {
    private ClassWeaponService() { }

    public static boolean isClassWeapon(ClassId clazz, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return switch (clazz) {
            case HEALER -> item == Items.BOOK;
            case MINER -> stack.is(ItemTags.PICKAXES);
            case MARKSMAN -> item instanceof BowItem || item instanceof CrossbowItem;
            case LUMBER -> stack.is(ItemTags.AXES);
            case CRAFTSMAN -> item == Items.CRAFTING_TABLE;
            default -> false;
        };
    }

    public static boolean hasWeapon(ServerPlayer player, ClassId clazz) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isClassWeapon(clazz, player.getInventory().getItem(slot))) return true;
        }
        return false;
    }

    public static void grantIfMissing(ServerPlayer player, ClassId clazz) {
        if (clazz == null || clazz == ClassId.NOVICE || hasWeapon(player, clazz)) return;
        Item item = switch (clazz) {
            case HEALER -> Items.BOOK;
            case MINER -> Items.IRON_PICKAXE;
            case MARKSMAN -> Items.BOW;
            case LUMBER -> Items.IRON_AXE;
            case CRAFTSMAN -> Items.CRAFTING_TABLE;
            default -> null;
        };
        if (item != null) {
            ItemStack stack = new ItemStack(item);
            if (!player.getInventory().add(stack)) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYour inventory is full; class weapon was not granted."));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aClass weapon granted: §f" + stack.getHoverName().getString()));
        }
    }
}
