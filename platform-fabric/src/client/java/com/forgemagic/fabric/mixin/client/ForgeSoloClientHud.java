package com.forgemagic.fabric.mixin.client;

import com.forgemagic.fabric.client.ForgeSoloClientState;
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
    private static final int PANEL = 0xD90B1420;
    private static final int PANEL_EDGE = 0xFF294052;
    private static final int MUTED = 0xFF9AAEC0;
    private static final int TEXT = 0xFFF1F6FC;
    private static final int HEALTH = 0xFFE45B70;
    private static final int MANA = 0xFF6D8EFF;
    private static final int THIRST = 0xFF35C6D0;
    private static final int WARNING = 0xFFFFB84D;

    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void forgeSolo$hideNativeHearts(GuiGraphicsExtractor graphics, CallbackInfo callback) {
        callback.cancel();
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("TAIL"))
    private void forgeSolo$drawHud(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        boolean fresh = ForgeSoloClientState.fresh();
        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        drawStatusPanel(graphics, client, 14, Math.max(10, guiHeight - 102), fresh);
        if (fresh && guiWidth >= 540) {
            drawSkillPanel(graphics, client, guiWidth - 264, 34, guiWidth);
        }
    }

    private static void drawStatusPanel(GuiGraphicsExtractor graphics, Minecraft client, int x, int y, boolean fresh) {
        int width = 238;
        int height = fresh ? 84 : 48;
        drawCard(graphics, x, y, width, height, THIRST);

        graphics.text(client.font, Component.literal("FERN  /  STATUS"), x + 10, y + 7, TEXT);
        if (fresh) {
            String className = fit(client, ForgeSoloClientState.className(), 106);
            int classWidth = client.font.width(className);
            graphics.text(client.font, Component.literal(className), x + width - classWidth - 10, y + 7, 0xFFB7C8DA);
        }

        float health = client.player.getHealth();
        float maxHealth = Math.max(1.0f, client.player.getMaxHealth());
        drawMeter(graphics, client, x + 10, y + 24, width - 20, 14,
                "HP", String.format("%.0f / %.0f", health, maxHealth), health / maxHealth, HEALTH);

        if (fresh) {
            drawMeter(graphics, client, x + 10, y + 43, width - 20, 14,
                    "MANA", String.format("%.0f / %.0f", ForgeSoloClientState.mana(), ForgeSoloClientState.maxMana()),
                    ForgeSoloClientState.mana() / ForgeSoloClientState.maxMana(), MANA);
            float thirstRatio = ForgeSoloClientState.thirst() / ForgeSoloClientState.maxThirst();
            int thirstColor = thirstRatio <= 0.20f ? HEALTH : thirstRatio <= 0.40f ? WARNING : THIRST;
            drawMeter(graphics, client, x + 10, y + 62, width - 20, 14,
                    "THIRST", String.format("%.0f / %.0f", ForgeSoloClientState.thirst(), ForgeSoloClientState.maxThirst()),
                    thirstRatio, thirstColor);
        }
    }

    private static void drawMeter(GuiGraphicsExtractor graphics, Minecraft client, int x, int y, int width, int height,
                                  String label, String value, float ratio, int color) {
        graphics.fill(x, y, x + width, y + height, 0xFF101B27);
        graphics.fill(x, y, x + 3, y + height, color);
        int innerX = x + 4;
        int innerWidth = width - 8;
        int fill = Math.round(innerWidth * Math.max(0.0f, Math.min(1.0f, ratio)));
        if (fill > 0) {
            graphics.fill(innerX, y + 1, innerX + fill, y + height - 1, withAlpha(color, 0xD8));
            graphics.fill(innerX, y + 1, innerX + fill, y + 3, withAlpha(0xFFFFFFFF, 0x30));
        }
        for (int mark = 1; mark < 4; mark++) {
            int markX = innerX + innerWidth * mark / 4;
            graphics.fill(markX, y + 2, markX + 1, y + height - 2, 0x50202C3A);
        }
        graphics.fill(x, y, x + width, y + 1, 0x553A4D61);
        graphics.text(client.font, Component.literal(label), x + 7, y + 3, TEXT);
        String clippedValue = fit(client, value, 76);
        int valueWidth = client.font.width(clippedValue);
        graphics.text(client.font, Component.literal(clippedValue), x + width - valueWidth - 6, y + 3, TEXT);
    }

    private static void drawSkillPanel(GuiGraphicsExtractor graphics, Minecraft client, int x, int y, int guiWidth) {
        int width = 250;
        int height = 184;
        if (x < 0 || x + width > guiWidth - 8) return;
        drawCard(graphics, x, y, width, height, MANA);
        graphics.text(client.font, Component.literal("SKILL LOADOUT"), x + 10, y + 8, TEXT);
        String className = fit(client, ForgeSoloClientState.className(), 86);
        int classWidth = client.font.width(className);
        graphics.text(client.font, Component.literal(className), x + width - classWidth - 10, y + 8, 0xFF9BB5FF);
        graphics.fill(x + 10, y + 21, x + width - 10, y + 22, 0xFF26394A);

        String[] bindings = ForgeSoloClientState.bindings();
        int columns = 3;
        int cardWidth = 72;
        int cardHeight = 19;
        int gapX = 4;
        int gapY = 3;
        int startX = x + 11;
        int startY = y + 28;
        for (int i = 0; i < bindings.length; i++) {
            String skill = bindings[i];
            if (skill == null || skill.isBlank()) continue;
            int row = i / columns;
            int col = i % columns;
            int slotX = startX + col * (cardWidth + gapX);
            int slotY = startY + row * (cardHeight + gapY);
            graphics.fill(slotX, slotY, slotX + cardWidth, slotY + cardHeight, 0xCC142230);
            graphics.fill(slotX, slotY, slotX + 2, slotY + cardHeight, MANA);
            graphics.fill(slotX + 2, slotY, slotX + cardWidth, slotY + 1, 0xFF34495A);
            String text = fit(client, String.format("%02d  %s", i + 1, skill), cardWidth - 8);
            graphics.text(client.font, Component.literal(text), slotX + 5, slotY + 5, TEXT);
        }

        String combiroll = ForgeSoloClientState.combiroll();
        if (!"none".equals(combiroll)) {
            String status = ForgeSoloClientState.combirollCooldown() > 0
                    ? "COOLDOWN " + ForgeSoloClientState.combirollCooldown() + "s"
                    : "READY";
            String footer = fit(client, "COMBIROLL  /  " + combiroll + "  ·  " + status, width - 20);
            graphics.text(client.font, Component.literal(footer), x + 10, y + height - 14, 0xFFD9C5FF);
        }
    }

    private static void drawCard(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int accent) {
        graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0x50000000);
        graphics.fill(x, y, x + width, y + height, PANEL_EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, PANEL);
        graphics.fill(x + 1, y + 1, x + 4, y + height - 1, accent);
        graphics.fill(x + 5, y + 1, x + width - 1, y + 2, 0x553A5064);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static String fit(Minecraft client, String value, int maxWidth) {
        String text = value == null ? "" : value;
        while (!text.isEmpty() && client.font.width(text) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
