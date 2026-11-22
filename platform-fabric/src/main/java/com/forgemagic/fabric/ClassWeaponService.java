package com.forgemagic.fabric;

import com.forgemagic.api.ClassId;
import com.forgemagic.armaments.ArmamentCatalog;
import com.forgemagic.armaments.WeaponDefinition;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Server-authoritative identity and lifecycle for Fern class weapons. */
public final class ClassWeaponService {
    private static final String CLASS_WEAPON = "fern_class_weapon";
    private static final String CLASS_ID = "fern_class_id";
    private static final String DEFINITION_ID = "fern_definition_id";
    private static final String ITEM_VERSION = "fern_item_version";

    private ClassWeaponService() { }

    public static boolean isClassWeapon(ClassId clazz, ItemStack stack) {
        WeaponDefinition definition = definition(stack);
        return clazz != null && definition != null && definition.classId() == clazz;
    }

    public static WeaponDefinition definition(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        var tag = data.copyTag();
        if (!tag.getBooleanOr(CLASS_WEAPON, false)) return null;
        WeaponDefinition definition = ArmamentCatalog.weapon(tag.getStringOr(DEFINITION_ID, ""));
        if (definition == null || !definition.classId().name().equalsIgnoreCase(tag.getStringOr(CLASS_ID, ""))) return null;
        return definition;
    }

    public static String definitionId(ItemStack stack) {
        WeaponDefinition definition = definition(stack);
        return definition == null ? "fern:unknown" : definition.id();
    }

    public static boolean hasWeapon(ServerPlayer player, ClassId clazz) {
        return weaponFor(player, clazz) != null;
    }

    public static WeaponDefinition weaponFor(ServerPlayer player, ClassId clazz) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isClassWeapon(clazz, stack)) return definition(stack);
        }
        return null;
    }

    public static void grantIfMissing(ServerPlayer player, ClassId clazz) {
        if (clazz == null || clazz == ClassId.NOVICE || hasWeapon(player, clazz)) return;
        WeaponDefinition definition = ArmamentCatalog.weaponFor(clazz);
        if (definition == null) return;

        ItemStack stack = new ItemStack(baseItem(definition));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putBoolean(CLASS_WEAPON, true);
            tag.putString(CLASS_ID, clazz.name());
            tag.putString(DEFINITION_ID, definition.id());
            tag.putInt(ITEM_VERSION, 1);
        });
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(definition.displayName()));
        if (!player.getInventory().add(stack)) {
            player.sendSystemMessage(Component.literal("§cYour inventory is full; class weapon was not granted."));
            return;
        }
        player.sendSystemMessage(Component.literal("§aClass weapon granted: §f" + stack.getHoverName().getString()));
    }

    private static Item baseItem(WeaponDefinition definition) {
        return switch (definition.baseItemKey()) {
            case "minecraft:book" -> Items.BOOK;
            case "minecraft:iron_pickaxe" -> Items.IRON_PICKAXE;
            case "minecraft:bow" -> Items.BOW;
            case "minecraft:iron_axe" -> Items.IRON_AXE;
            case "minecraft:crafting_table" -> Items.CRAFTING_TABLE;
            default -> throw new IllegalArgumentException("Unsupported Fern armament base item: " + definition.baseItemKey());
        };
    }
}
