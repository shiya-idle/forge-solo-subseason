package com.forgemagic.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client companion HUD driven by server-authoritative snapshots. */
@Mixin(Hud.class)
public abstract class ForgeSoloClientHud {
    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void forgeSolo$hideNativeHearts(GuiGraphicsExtractor graphics, CallbackInfo callback) {
        callback.cancel();
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("TAIL"))
    private void forgeSolo$drawHud(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        int width = 170;
        int height = 8;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 72;
        drawBar(graphics, x, y, width, height, client.player.getHealth() / client.player.getMaxHealth(), 0xFFE04455,
                String.format("❤ %.0f/%.0f", client.player.getHealth(), client.player.getMaxHealth()), 0xFFFFB4BC);

        if (ForgeSoloClientState.fresh()) {
            drawBar(graphics, x, y + 15, width, height, ForgeSoloClientState.mana() / ForgeSoloClientState.maxMana(), 0xFF4E9BFF,
                    String.format("✦ %.0f/%.0f", ForgeSoloClientState.mana(), ForgeSoloClientState.maxMana()), 0xFF9CCBFF);
            drawBar(graphics, x, y + 30, width, height, ForgeSoloClientState.thirst() / ForgeSoloClientState.maxThirst(), 0xFF39C6D8,
                    String.format("💧 %.0f/%.0f", ForgeSoloClientState.thirst(), ForgeSoloClientState.maxThirst()), 0xFF8EEAF2);
            graphics.text(client.font, Component.literal(ForgeSoloClientState.className()), x + width - 52, y - 12, 0xFFFFFFFF);
            if (!"none".equals(ForgeSoloClientState.combiroll())) {
                String cooldown = ForgeSoloClientState.combirollCooldown() > 0 ? " · " + ForgeSoloClientState.combirollCooldown() + "s" : " · READY";
                graphics.text(client.font, Component.literal("◈ " + ForgeSoloClientState.combiroll() + cooldown), x, y + 45, 0xFFE7C8FF);
            }
            drawSkillSlots(graphics, client);
        }
    }

    private static void drawBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                               float ratio, int color, String label, int labelColor) {
        int fill = Math.round(width * Math.max(0.0f, Math.min(1.0f, ratio)));
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xCC10151E);
        graphics.fill(x, y, x + fill, y + height, color);
        graphics.text(Minecraft.getInstance().font, Component.literal(label), x, y - 12, labelColor);
    }

    private static void drawSkillSlots(GuiGraphicsExtractor graphics, Minecraft client) {
        String[] bindings = ForgeSoloClientState.bindings();
        int slotWidth = 78;
        int slotHeight = 18;
        int startX = graphics.guiWidth() - (slotWidth * 3) - 18;
        int startY = 38;
        for (int i = 0; i < bindings.length; i++) {
            if (bindings[i] == null || bindings[i].isBlank()) continue;
            int row = i / 3;
            int col = i % 3;
            int sx = startX + col * slotWidth;
            int sy = startY + row * slotHeight;
            graphics.fill(sx, sy, sx + slotWidth - 4, sy + slotHeight - 3, 0xCC10151E);
            graphics.text(client.font, Component.literal((i + 1) + "  " + bindings[i]), sx + 4, sy + 3, 0xFFE7ECF4);
        }
    }
}
