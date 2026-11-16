package com.forgemagic.fabric.mixin;

import com.forgemagic.fabric.FernItemBindingService;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class FernBoundItemEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void fern$keepDeathDropsForever(CallbackInfo callback) {
        ItemEntity item = (ItemEntity) (Object) this;
        if (FernItemBindingService.isPermanentDeathDrop(item.getItem())) item.setUnlimitedLifetime();
    }

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void fern$enforceItemOwner(Player player, CallbackInfo callback) {
        ItemEntity item = (ItemEntity) (Object) this;
        if (!FernItemBindingService.allowPickup(item, player)) callback.cancel();
    }
}
