package com.forgemagic.fabric;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.Set;
import java.util.UUID;

/** Shared eligibility checks; custom Fern items stay distinct from their vanilla lookalikes. */
final class EconomyTradeRules {
    private static final Set<String> RESERVED_NAMES = Set.of(
            "Mana Tonic", "Ember Flare", "Cooldown Scroll I", "Cooldown Scroll II",
            "Mana Regeneration Potion", "Anti-Bottleneck Scroll I", "Anti-Bottleneck Scroll II",
            "Anti-Bottleneck Scroll III", "XP Multiplier 1.25x", "XP Multiplier 1.5x",
            "Health Multiplier 1.25x", "Health Multiplier 1.5x");
    private EconomyTradeRules() { }

    static boolean isTradableStack(ItemStack stack, UUID owner, boolean inTradeWorld) {
        if (stack == null || stack.isEmpty() || owner == null || FernItemBindingService.isPermanentDeathDrop(stack)) return false;
        if (!FernItemBindingService.isOwnedByOrUnbound(stack, owner)) return false;
        UUID boundOwner = FernItemBindingService.owner(stack);
        if (boundOwner != null && !inTradeWorld) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            var tag = data.copyTag();
            if (tag.getBooleanOr("fern_class_weapon", false)
                    || !tag.getStringOr("fern_craft_item", "").isBlank()
                    || !tag.getStringOr("fern_non_tradeable", "").isBlank()
                    || !tag.getStringOr("fern_vendor_offer", "").isBlank()
                    || tag.getBooleanOr("fern_combiroll_receipt", false)) return false;
        }
        var customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            String normalized = customName.getString().replaceAll("§[0-9a-fk-or]", "").trim();
            if (RESERVED_NAMES.contains(normalized) || normalized.endsWith(" Combiroll")) return false;
        }
        return true;
    }
}
