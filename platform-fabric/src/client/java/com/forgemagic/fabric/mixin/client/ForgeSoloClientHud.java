package com.forgemagic.fabric.mixin.client;

import com.forgemagic.api.ClassId;
import com.forgemagic.api.SkillDefinition;
import com.forgemagic.fabric.client.ForgeSoloClientState;
import com.forgemagic.skills.ContentCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

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
    private static final Map<ClassId, Map<String, String>> ROUTE_SKILL_NAMES = createRouteSkillNames();

    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void forgeSolo$hideNativeHearts(GuiGraphicsExtractor graphics, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && !client.gui.hud.isHidden() && ForgeSoloClientState.fresh()
                && canDrawStatusHud(graphics.guiWidth(), graphics.guiHeight())) {
            callback.cancel();
        }
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("TAIL"))
    private void forgeSolo$drawHud(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo callback) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden() || !ForgeSoloClientState.fresh()) return;

        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        if (!canDrawStatusHud(guiWidth, guiHeight)) return;

        int statusWidth = Math.min(238, guiWidth - 20);
        // Reserve the lower-left chat transcript area below 800px tall; keep full-height layouts unchanged.
        int statusY = Math.max(10, guiHeight - (guiHeight < 800 ? 208 : 136));
        drawStatusPanel(graphics, client, 10, statusY, statusWidth);

        // The upper-right corner belongs to vanilla status-effect icons; start below that reserved row.
        if (guiWidth >= 530 && guiHeight >= 296) {
            drawSkillPanel(graphics, client, guiWidth - 262, guiHeight);
        }
    }

    private static boolean canDrawStatusHud(int guiWidth, int guiHeight) {
        // Keep native hearts when a tiny GUI cannot show the replacement legibly.
        return guiWidth >= 194 && guiHeight >= 150;
    }

    private static void drawStatusPanel(GuiGraphicsExtractor graphics, Minecraft client, int x, int y, int width) {
        int height = 84;
        drawCard(graphics, x, y, width, height, THIRST);

        graphics.text(client.font, Component.literal("FERN  /  STATUS"), x + 10, y + 7, TEXT);
        String className = fit(client, ForgeSoloClientState.className(), Math.max(48, width - 112));
        int classWidth = client.font.width(className);
        graphics.text(client.font, Component.literal(className), x + width - classWidth - 10, y + 7, 0xFFB7C8DA);

        float health = client.player.getHealth();
        float maxHealth = Math.max(1.0f, client.player.getMaxHealth());
        float healthRatio = health / maxHealth;
        drawMeter(graphics, client, x + 10, y + 24, width - 20, 14,
                healthRatio <= 0.20f ? "HP !" : "HP", String.format("%.0f / %.0f", health, maxHealth),
                healthRatio, HEALTH);

        drawMeter(graphics, client, x + 10, y + 43, width - 20, 14,
                "MANA", String.format("%.0f / %.0f", ForgeSoloClientState.mana(), ForgeSoloClientState.maxMana()),
                ForgeSoloClientState.mana() / ForgeSoloClientState.maxMana(), MANA);

        float thirstRatio = ForgeSoloClientState.thirst() / ForgeSoloClientState.maxThirst();
        int thirstColor = thirstRatio <= 0.20f ? HEALTH : thirstRatio <= 0.40f ? WARNING : THIRST;
        drawMeter(graphics, client, x + 10, y + 62, width - 20, 14,
                thirstRatio <= 0.20f ? "THIRST !" : "THIRST",
                String.format("%.0f / %.0f", ForgeSoloClientState.thirst(), ForgeSoloClientState.maxThirst()),
                thirstRatio, thirstColor);
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
        boolean urgent = ratio <= 0.20f && ("HP !".equals(label) || "THIRST !".equals(label));
        int edgeAlpha = urgent ? 0x40 + (int) ((Math.sin(System.currentTimeMillis() / 180.0) + 1.0) * 40.0) : 0x55;
        graphics.fill(x, y, x + width, y + 1, withAlpha(urgent ? color : 0xFF3A4D61, edgeAlpha));
        graphics.text(client.font, Component.literal(label), x + 7, y + 3, TEXT);
        String clippedValue = fit(client, value, Math.max(42, Math.min(76, width / 3)));
        int valueWidth = client.font.width(clippedValue);
        graphics.text(client.font, Component.literal(clippedValue), x + width - valueWidth - 6, y + 3, TEXT);
    }

    private static void drawSkillPanel(GuiGraphicsExtractor graphics, Minecraft client, int x, int guiHeight) {
        int width = 250;
        boolean compact = guiHeight < 380;
        int height = compact ? 188 : 250;
        int y = Math.max(72, guiHeight - height - 68);
        if (x < 0 || x + width > guiWidth(client) - 8 || y + height > guiHeight - 32) return;

        drawCard(graphics, x, y, width, height, MANA);
        graphics.text(client.font, Component.literal("SKILL LOADOUT"), x + 10, y + 8, TEXT);
        String currentClass = ForgeSoloClientState.className();
        String className = fit(client, currentClass, 86);
        int classWidth = client.font.width(className);
        graphics.text(client.font, Component.literal(className), x + width - classWidth - 10, y + 8, 0xFF9BB5FF);
        graphics.fill(x + 10, y + 21, x + width - 10, y + 22, 0xFF26394A);

        String[] bindings = ForgeSoloClientState.bindings();
        boolean novice = "NOVICE".equalsIgnoreCase(currentClass);
        if (novice || !hasUsableBinding(bindings)) {
            drawEmptySkillState(graphics, client, x, y, width, height, novice);
        } else {
            drawSkillBindings(graphics, client, x, y, compact, bindings, currentClass);
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

    private static void drawSkillBindings(GuiGraphicsExtractor graphics, Minecraft client, int x, int y,
                                          boolean compact, String[] bindings, String className) {
        int columns = 2;
        int cardWidth = 112;
        int cardHeight = compact ? 14 : 19;
        int gapX = 4;
        int gapY = compact ? 2 : 3;
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
            String text = fit(client, String.format("%02d  %s", i + 1, skillDisplayName(className, skill)), cardWidth - 8);
            graphics.text(client.font, Component.literal(text), slotX + 5,
                    slotY + (cardHeight - 9) / 2, TEXT);
        }
    }

    private static boolean hasUsableBinding(String[] bindings) {
        for (String binding : bindings) {
            if (binding != null && !binding.isBlank()) return true;
        }
        return false;
    }

    private static void drawEmptySkillState(GuiGraphicsExtractor graphics, Minecraft client, int x, int y,
                                            int width, int height, boolean novice) {
        int boxLeft = x + 12;
        int boxRight = x + width - 12;
        int boxTop = y + 30;
        int boxBottom = y + height - 22;
        graphics.fill(boxLeft, boxTop, boxRight, boxBottom, 0x88101B27);
        graphics.fill(boxLeft, boxTop, boxLeft + 2, boxBottom, novice ? WARNING : MUTED);

        int centerX = x + width / 2;
        int markY = y + 48;
        int markColor = novice ? WARNING : MUTED;
        graphics.fill(centerX - 1, markY - 7, centerX + 2, markY + 8, markColor);
        graphics.fill(centerX - 7, markY - 1, centerX + 8, markY + 2, markColor);

        String heading = novice ? "NO CLASS SELECTED" : "NO SKILLS BOUND";
        String explanation = novice
                ? "Select a class to unlock your skill deck."
                : "No abilities are assigned to this deck yet.";
        String nextStep = novice
                ? "Open /skills panel to choose a class."
                : "Your loadout will appear here when configured.";
        drawCenteredText(graphics, client, heading, centerX, y + 64, novice ? WARNING : MUTED, width - 28);
        drawCenteredText(graphics, client, explanation, centerX, y + 84, TEXT, width - 28);
        drawCenteredText(graphics, client, nextStep, centerX, y + 99, 0xFF9BB5FF, width - 28);
    }

    private static void drawCenteredText(GuiGraphicsExtractor graphics, Minecraft client, String value, int centerX,
                                         int y, int color, int maxWidth) {
        String text = fit(client, value, maxWidth);
        graphics.text(client.font, Component.literal(text), centerX - client.font.width(text) / 2, y, color);
    }

    private static int guiWidth(Minecraft client) {
        return client.getWindow().getGuiScaledWidth();
    }

    private static String skillDisplayName(String className, String skillId) {
        try {
            ClassId classId = ClassId.valueOf(className.toUpperCase(Locale.ROOT));
            return ROUTE_SKILL_NAMES.getOrDefault(classId, Map.of())
                    .getOrDefault(skillId, readableSkillId(skillId));
        } catch (IllegalArgumentException ignored) {
            return readableSkillId(skillId);
        }
    }

    private static Map<ClassId, Map<String, String>> createRouteSkillNames() {
        Map<ClassId, Map<String, String>> namesByClass = new EnumMap<>(ClassId.class);
        for (ClassId classId : ClassId.values()) {
            try {
                Map<String, String> names = new HashMap<>();
                for (SkillDefinition skill : ContentCatalog.routeSkills(classId)) {
                    names.put(skill.id(), skill.displayName());
                }
                namesByClass.put(classId, Map.copyOf(names));
            } catch (IllegalArgumentException ignored) {
                // Novice and other non-route classes have no route-skill catalog.
            }
        }
        return Map.copyOf(namesByClass);
    }

    private static String readableSkillId(String skillId) {
        String value = skillId == null ? "" : skillId;
        int firstDot = value.indexOf('.');
        if (firstDot >= 0 && firstDot + 1 < value.length()) value = value.substring(firstDot + 1);
        return value.replace('.', ' ').replace('_', ' ');
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
        if (client.font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        while (!text.isEmpty() && client.font.width(text + ellipsis) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text.isEmpty() ? "" : text + ellipsis;
    }
}
