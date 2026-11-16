package com.forgemagic.fabric;

import com.forgemagic.api.ClassId;
import com.forgemagic.api.ManaState;
import com.forgemagic.skills.SkillRuntime;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Small, working cross-platform chat workbench. All ingredient checks run on the server. */
public final class CraftsmanWorkbench {
    private static final String KIND = "fern_craft_item";
    private static final Map<String, Recipe> RECIPES = recipes();
    private final SkillRuntime skills;

    public CraftsmanWorkbench(SkillRuntime skills) { this.skills = skills; }

    public void show(ServerPlayer player, ClassId playerClass) {
        if (playerClass != ClassId.CRAFTSMAN) {
            player.sendSystemMessage(Component.literal("§cOnly Craftsmen can use the Fern workbench."));
            return;
        }
        player.sendSystemMessage(Component.literal("§6§lCRAFTSMAN WORKBENCH §8| §7Server-checked recipes"));
        player.sendSystemMessage(Component.literal("§8────────────────────────────────"));
        RECIPES.forEach((id, recipe) -> {
            boolean canCraft = has(player, recipe.ingredients());
            String color = canCraft ? "§a" : "§7";
            player.sendSystemMessage(Component.literal(color + "[ CRAFT ] §f" + recipe.name()
                    + " §8— " + recipe.summary() + (canCraft ? " §a✓ ready" : " §cneed ingredients"))
                    .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand("/craftsman craft " + id))));
        });
        player.sendSystemMessage(Component.literal("§7Click a line or run §f/craftsman§7. Inventory changes are validated on the server."));
    }

    public boolean craft(ServerPlayer player, ClassId playerClass, String requested) {
        if (playerClass != ClassId.CRAFTSMAN) {
            player.sendSystemMessage(Component.literal("§cOnly Craftsmen can craft at the Fern workbench."));
            return false;
        }
        Recipe recipe = RECIPES.get(requested == null ? "" : requested.toLowerCase(java.util.Locale.ROOT));
        if (recipe == null) {
            player.sendSystemMessage(Component.literal("§cUnknown recipe. Open §f/craftsman§c."));
            return false;
        }
        if (!has(player, recipe.ingredients())) {
            player.sendSystemMessage(Component.literal("§cMissing ingredients for §f" + recipe.name() + "§c."));
            return false;
        }
        for (var ingredient : recipe.ingredients().entrySet()) consume(player, ingredient.getKey(), ingredient.getValue());
        ItemStack output = new ItemStack(recipe.output());
        output.set(DataComponents.CUSTOM_NAME, Component.literal("§d" + recipe.name()));
        CustomData.update(DataComponents.CUSTOM_DATA, output, tag -> {
            tag.putString(KIND, recipe.id());
            tag.putString("fern_stack_id", UUID.randomUUID().toString());
        });
        FernItemBindingService.bind(output, player.getUUID(), false);
        if (!player.getInventory().add(output)) player.drop(output, false, net.minecraft.util.Prediction.SERVER_ONLY);
        player.sendSystemMessage(Component.literal("§aCrafted §f" + recipe.name() + "§a."));
        return true;
    }

    /** Returns true only when Fern consumed a signed-by-data tonic/flare item. */
    public boolean use(ServerPlayer player, ItemStack stack) {
        String kind = kind(stack);
        if (kind == null) return false;
        if (kind.equals("mana_tonic")) {
            ManaState current = skills.mana(player.getUUID());
            if (current == null) current = new ManaState(100, 100);
            skills.setMana(player.getUUID(), current.regenerate(Math.max(20.0, current.maximum() * 0.35)));
            stack.shrink(1);
            player.sendSystemMessage(Component.literal("§b✦ Mana restored."), true);
            player.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.35, 0.55, 0.35, 0.05);
            return true;
        }
        if (kind.equals("ember_flare")) {
            stack.shrink(1);
            player.level().sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 36, 0.55, 0.8, 0.55, 0.035);
            player.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.4, 0.65, 0.4, 0.025);
            player.sendSystemMessage(Component.literal("§6The Ember Flare blooms around you."), true);
            return true;
        }
        return false;
    }

    private static String kind(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag().getStringOr(KIND, "");
    }

    private static boolean has(ServerPlayer player, Map<net.minecraft.world.item.Item, Integer> ingredients) {
        for (var ingredient : ingredients.entrySet()) {
            int count = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.getItem() == ingredient.getKey() && FernItemBindingService.isOwnedByOrUnbound(stack, player.getUUID())) count += stack.getCount();
            }
            if (count < ingredient.getValue()) return false;
        }
        return true;
    }

    private static void consume(ServerPlayer player, net.minecraft.world.item.Item item, int amount) {
        for (int slot = 0; slot < player.getInventory().getContainerSize() && amount > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() != item || !FernItemBindingService.isOwnedByOrUnbound(stack, player.getUUID())) continue;
            int take = Math.min(amount, stack.getCount());
            stack.shrink(take);
            amount -= take;
        }
    }

    private static Map<String, Recipe> recipes() {
        Map<String, Recipe> result = new LinkedHashMap<>();
        result.put("mana_tonic", new Recipe("mana_tonic", "Mana Tonic", Items.HONEY_BOTTLE,
                Map.of(Items.GLASS_BOTTLE, 1, Items.SUGAR, 2, Items.AMETHYST_SHARD, 1), "1 glass bottle + 2 sugar + 1 amethyst shard"));
        result.put("ember_flare", new Recipe("ember_flare", "Ember Flare", Items.FIREWORK_ROCKET,
                Map.of(Items.PAPER, 1, Items.GLOWSTONE_DUST, 2, Items.QUARTZ, 1), "1 paper + 2 glowstone dust + 1 quartz"));
        return Map.copyOf(result);
    }

    private record Recipe(String id, String name, net.minecraft.world.item.Item output,
                          Map<net.minecraft.world.item.Item, Integer> ingredients, String summary) { }
}
