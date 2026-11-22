package com.forgemagic.fabric.client;

import com.forgemagic.api.ClassId;
import com.forgemagic.api.SkillDefinition;
import com.forgemagic.skills.ContentCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

/** A real interactive screen; gameplay remains validated by the server command handlers. */
public final class ForgeSoloPanelScreen extends Screen {
    private final String data;
    private final String className;
    private final String[] skillIds;
    private final int page;

    public ForgeSoloPanelScreen(String data) {
        this(data, 0);
    }

    private ForgeSoloPanelScreen(String data, int page) {
        super(titleFor(data));
        this.data = data == null ? "" : data;
        this.page = Math.max(0, page);
        String[] parts = this.data.split("\\|", 3);
        className = parts.length == 0 || parts[0].isBlank() ? "NOVICE" : parts[0];
        skillIds = parts.length < 3 || parts[2].isBlank() ? new String[0] : parts[2].split(",", -1);
    }

    private static Component titleFor(String data) {
        String[] parts = data == null ? new String[0] : data.split("\\|", 3);
        String clazz = parts.length == 0 || parts[0].isBlank() ? "NOVICE" : parts[0];
        return Component.literal("FERN  /  " + clazz + " SKILL DECK");
    }

    @Override
    protected void init() {
        int margin = Math.min(16, Math.max(8, this.width / 20));
        int usableWidth = Math.max(1, this.width - margin * 2);
        int columns = usableWidth >= 560 ? 3 : usableWidth >= 280 ? 2 : 1;
        int gapX = 8;
        int buttonWidth = Math.max(1, (usableWidth - gapX * (columns - 1)) / columns);
        int buttonHeight = this.height < 220 ? 24 : 32;
        int gapY = 6;
        int startY = Math.max(26, Math.min(52, this.height / 4));
        int footerReserve = this.height < 220 ? 64 : 72;
        int availableGridHeight = Math.max(buttonHeight, this.height - startY - footerReserve);
        int rows = Math.max(1, Math.min(5, (availableGridHeight + gapY) / (buttonHeight + gapY)));
        int pageSize = columns * rows;
        int pageCount = Math.max(1, (skillIds.length + pageSize - 1) / pageSize);
        int currentPage = Math.min(page, pageCount - 1);
        int startIndex = currentPage * pageSize;
        int endIndex = Math.min(skillIds.length, startIndex + pageSize);
        int contentWidth = columns * buttonWidth + (columns - 1) * gapX;
        int startX = (this.width - contentWidth) / 2;

        for (int index = startIndex; index < endIndex; index++) {
            String id = skillIds[index];
            int localIndex = index - startIndex;
            int row = localIndex / columns;
            int column = localIndex % columns;
            int x = startX + column * (buttonWidth + gapX);
            int y = startY + row * (buttonHeight + gapY);
            String fullLabel = String.format("%02d  %s", index + 1, displayName(id));
            String label = fitText(fullLabel, Math.max(1, buttonWidth - 16));
            addRenderableWidget(Button.builder(Component.literal(label), button -> cast(id))
                    .bounds(x, y, buttonWidth, buttonHeight).build());
        }

        int footerHeight = Math.min(buttonHeight, Math.max(20, this.height - 8));
        int closeY = Math.max(0, this.height - footerHeight - 5);
        int closeWidth = Math.min(170, Math.max(80, this.width - 24));
        if (pageCount > 1) {
            int navY = Math.max(startY + buttonHeight + 4, closeY - footerHeight - 5);
            int navWidth = Math.min(124, Math.max(58, (this.width - 32) / 2));
            int navGap = 8;
            int navStartX = (this.width - (navWidth * 2 + navGap)) / 2;
            if (currentPage > 0) {
                addRenderableWidget(Button.builder(Component.literal("‹ PREVIOUS"), button ->
                                Minecraft.getInstance().setScreenAndShow(new ForgeSoloPanelScreen(data, currentPage - 1)))
                        .bounds(navStartX, navY, navWidth, footerHeight).build());
            }
            if (currentPage + 1 < pageCount) {
                addRenderableWidget(Button.builder(Component.literal("NEXT ›"), button ->
                                Minecraft.getInstance().setScreenAndShow(new ForgeSoloPanelScreen(data, currentPage + 1)))
                        .bounds(navStartX + navWidth + navGap, navY, navWidth, footerHeight).build());
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds((this.width - closeWidth) / 2, closeY, closeWidth, footerHeight).build());
    }

    private String fitText(String value, int maxWidth) {
        String text = value == null ? "" : value;
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "…";
        while (!text.isEmpty() && font.width(text + ellipsis) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text.isEmpty() ? "" : text + ellipsis;
    }

    private void cast(String skillId) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && !skillId.isBlank()) {
            client.player.connection.sendCommand("skills cast " + skillId);
        }
    }

    private String displayName(String skillId) {
        try {
            ClassId classId = ClassId.valueOf(className.toUpperCase(java.util.Locale.ROOT));
            return ContentCatalog.routeSkills(classId).stream()
                    .filter(skill -> skill.id().equals(skillId))
                    .map(SkillDefinition::displayName)
                    .findFirst()
                    .orElseGet(() -> shortName(skillId));
        } catch (IllegalArgumentException ignored) {
            return shortName(skillId);
        }
    }

    private static String shortName(String id) {
        String value = id == null ? "" : id;
        String[] parts = value.split("\\.");
        if (parts.length > 1) value = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));
        value = value.replace('_', ' ');
        return value.length() > 28 ? value.substring(0, 27) + "…" : value;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
