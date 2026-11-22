package com.forgemagic.fabric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A real interactive screen; gameplay remains validated by the server command handlers. */
public final class ForgeSoloPanelScreen extends Screen {
    private final String className;
    private final String weaponId;
    private final String[] skillIds;

    public ForgeSoloPanelScreen(String data) {
        super(titleFor(data));
        String[] parts = data == null ? new String[0] : data.split("\\|", 3);
        className = parts.length == 0 || parts[0].isBlank() ? "NOVICE" : parts[0];
        weaponId = parts.length < 2 || parts[1].isBlank() ? "fern:unknown" : parts[1];
        skillIds = parts.length < 3 || parts[2].isBlank() ? new String[0] : parts[2].split(",", -1);
    }

    private static Component titleFor(String data) {
        String[] parts = data == null ? new String[0] : data.split("\\|", 3);
        String clazz = parts.length == 0 || parts[0].isBlank() ? "NOVICE" : parts[0];
        String weapon = parts.length < 2 || parts[1].isBlank() ? "fern:unknown" : parts[1];
        return Component.literal("FERN / " + clazz + " / " + weapon);
    }

    @Override
    protected void init() {
        int columns = 3;
        int buttonWidth = 132;
        int buttonHeight = 24;
        int gap = 6;
        int totalWidth = columns * buttonWidth + (columns - 1) * gap;
        int startX = (this.width - totalWidth) / 2;
        int startY = Math.max(32, (this.height - 190) / 2);

        for (int index = 0; index < skillIds.length; index++) {
            String id = skillIds[index];
            int row = index / columns;
            int column = index % columns;
            int x = startX + column * (buttonWidth + gap);
            int y = startY + row * (buttonHeight + gap);
            String label = String.format("%02d  %s", index + 1, shortName(id));
            addRenderableWidget(Button.builder(Component.literal(label), button -> cast(id))
                    .bounds(x, y, buttonWidth, buttonHeight).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(startX + totalWidth - buttonWidth, startY + 5 * (buttonHeight + gap), buttonWidth, buttonHeight).build());
    }

    private void cast(String skillId) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && !skillId.isBlank()) {
            client.player.connection.sendCommand("skills cast " + skillId);
        }
    }

    private static String shortName(String id) {
        String value = id == null ? "" : id;
        int lastDot = value.lastIndexOf('.');
        if (lastDot >= 0) value = value.substring(0, lastDot);
        int previousDot = value.lastIndexOf('.');
        if (previousDot >= 0) value = value.substring(previousDot + 1);
        return value.length() > 16 ? value.substring(0, 16) : value;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
