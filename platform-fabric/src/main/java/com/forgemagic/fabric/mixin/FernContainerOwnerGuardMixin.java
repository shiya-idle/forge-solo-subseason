package com.forgemagic.fabric.mixin;

import com.forgemagic.fabric.FernItemBindingService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class FernContainerOwnerGuardMixin {
    @Shadow public abstract ItemStack getCarried();
    @Shadow @Final public net.minecraft.core.NonNullList<net.minecraft.world.inventory.Slot> slots;

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void fern$guardForeignBoundItems(int slotIndex, int button, ContainerInput input, Player player, CallbackInfo callback) {
        if (!(player instanceof ServerPlayer) || player.level().dimension().identifier().getPath().equals("forge_cornucopia")) return;
        ItemStack carried = getCarried();
        if (!FernItemBindingService.isOwnedByOrUnbound(carried, player.getUUID())) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cThis item belongs to another player. Item transfers are only allowed in Cornucopia."));
            callback.cancel();
            return;
        }
        if (slotIndex < 0 || slotIndex >= slots.size()) return;
        ItemStack clicked = slots.get(slotIndex).getItem();
        if (!FernItemBindingService.isOwnedByOrUnbound(clicked, player.getUUID())) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cThis item belongs to another player. Item transfers are only allowed in Cornucopia."));
            callback.cancel();
        }
    }
}
