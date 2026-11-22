package com.forgemagic.fabric;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Server-side inventory primitives that compare full item components, not just registry IDs. */
final class EconomyInventoryOps {
    private EconomyInventoryOps() { }

    static List<ItemStack> snapshot(Inventory inventory) {
        List<ItemStack> result = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) result.add(inventory.getItem(slot).copy());
        return result;
    }

    static void restore(Inventory inventory, List<ItemStack> snapshot) {
        for (int slot = 0; slot < Math.min(inventory.getContainerSize(), snapshot.size()); slot++)
            inventory.setItem(slot, snapshot.get(slot).copy());
    }

    static boolean canFit(Inventory inventory, List<ItemStack> incoming) {
        List<ItemStack> simulated = snapshot(inventory);
        for (ItemStack stack : incoming) if (!insert(simulated, stack.copy())) return false;
        return true;
    }

    static boolean addAll(Inventory inventory, List<ItemStack> incoming) {
        List<ItemStack> work = snapshot(inventory);
        for (ItemStack stack : incoming) if (!insert(work, stack.copy())) return false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) inventory.setItem(slot, work.get(slot));
        return true;
    }

    static List<ItemStack> planExchange(Inventory inventory, List<ItemStack> outgoing, List<ItemStack> incoming) {
        List<ItemStack> work = snapshot(inventory);
        for (ItemStack stack : outgoing) if (!remove(work, stack)) return null;
        for (ItemStack stack : incoming) if (!insert(work, stack.copy())) return null;
        return work;
    }

    static void applyPlan(Inventory inventory, List<ItemStack> plan) {
        for (int slot = 0; slot < Math.min(inventory.getContainerSize(), plan.size()); slot++)
            inventory.setItem(slot, plan.get(slot).copy());
    }

    static boolean removeExact(Inventory inventory, ItemStack requested) {
        int remaining = requested.getCount();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (!sameComponents(current, requested)) continue;
            int removed = Math.min(remaining, current.getCount());
            current.shrink(removed);
            remaining -= removed;
            if (current.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
        }
        return remaining == 0;
    }

    static int countItem(Inventory inventory, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) count += stack.getCount();
        }
        return count;
    }

    static List<ItemStack> removeByItem(Inventory inventory, net.minecraft.world.item.Item item, int amount) {
        if (amount < 1 || countItem(inventory, item) < amount) return null;
        List<ItemStack> removed = new ArrayList<>();
        int remaining = amount;
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current.isEmpty() || current.getItem() != item) continue;
            int take = Math.min(remaining, current.getCount());
            ItemStack part = current.copy();
            part.setCount(take);
            removed.add(part);
            current.shrink(take);
            remaining -= take;
            if (current.isEmpty()) inventory.setItem(slot, ItemStack.EMPTY);
        }
        return remaining == 0 ? removed : null;
    }

    static boolean addToPlayer(ServerPlayer player, List<ItemStack> items) {
        return addAll(player.getInventory(), items);
    }

    static boolean sameComponents(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameComponents(a, b);
    }

    private static boolean remove(List<ItemStack> inventory, ItemStack requested) {
        int remaining = requested.getCount();
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            ItemStack current = inventory.get(slot);
            if (!sameComponents(current, requested)) continue;
            int moved = Math.min(remaining, current.getCount());
            current.shrink(moved);
            remaining -= moved;
            if (current.isEmpty()) inventory.set(slot, ItemStack.EMPTY);
        }
        return remaining == 0;
    }

    private static boolean insert(List<ItemStack> inventory, ItemStack incoming) {
        if (incoming.isEmpty()) return true;
        for (ItemStack current : inventory) {
            if (!sameComponents(current, incoming)) continue;
            int room = Math.max(0, current.getMaxStackSize() - current.getCount());
            int moved = Math.min(room, incoming.getCount());
            if (moved > 0) { current.grow(moved); incoming.shrink(moved); }
            if (incoming.isEmpty()) return true;
        }
        for (int slot = 0; slot < inventory.size() && !incoming.isEmpty(); slot++) {
            if (!inventory.get(slot).isEmpty()) continue;
            int moved = Math.min(incoming.getMaxStackSize(), incoming.getCount());
            ItemStack part = incoming.copy();
            part.setCount(moved);
            inventory.set(slot, part);
            incoming.shrink(moved);
        }
        return incoming.isEmpty();
    }
}
