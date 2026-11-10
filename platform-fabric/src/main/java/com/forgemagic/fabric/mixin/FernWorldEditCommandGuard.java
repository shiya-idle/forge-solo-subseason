package com.forgemagic.fabric.mixin;

import com.forgemagic.fabric.FernWorldEditGuard;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class FernWorldEditCommandGuard {
    @Inject(method = "performCommand", at = @At("HEAD"), cancellable = true)
    private void fern$restrictWorldEdit(ParseResults<CommandSourceStack> parseResults, String command, CallbackInfo callback) {
        CommandSourceStack source = parseResults.getContext().getSource();
        if (!FernWorldEditGuard.allow(source, command)) callback.cancel();
    }
}
