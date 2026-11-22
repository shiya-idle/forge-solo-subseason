package com.forgemagic.fabric.client;

import com.forgemagic.fabric.TomatMoney;
import com.forgemagic.fabric.TomatTradePayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Shared client view; every action and item/balance check is still verified by the server. */
public final class TomatTradeScreen extends Screen {
    private final String data;
    private final String[] fields;
    private final String tradeId;
    private final int revision;
    private final String ownItems;
    private final String otherItems;
    private final long ownCentavos;
    private final long otherCentavos;
    private final boolean ownReady;
    private final boolean otherReady;
    private final boolean bothReady;
    private final String ownName;
    private final String otherName;
    private EditBox countInput;
    private EditBox tomatInput;
    private String inputError = "";

    public TomatTradeScreen(String data) {
        super(titleFor(data));
        this.data = data == null ? "" : data;
        fields = this.data.split("\\|", -1);
        tradeId = at(0, "");
        revision = parseInt(at(1, "0"), 0);
        ownItems = at(2, "—");
        otherItems = at(3, "—");
        ownCentavos = parseLong(at(4, "0"), 0);
        otherCentavos = parseLong(at(5, "0"), 0);
        ownReady = Boolean.parseBoolean(at(6, "false"));
        otherReady = Boolean.parseBoolean(at(7, "false"));
        bothReady = Boolean.parseBoolean(at(8, "false"));
        ownName = at(9, "You");
        otherName = at(10, "Other player");
    }

    private static Component titleFor(String data) {
        String[] parts = data == null ? new String[0] : data.split("\\|", -1);
        String other = parts.length > 10 ? parts[10] : "Other player";
        return Component.literal("TRADE  /  " + other);
    }

    @Override
    protected void init() {
        int middle = this.width / 2;
        int margin = Math.min(14, Math.max(6, this.width / 24));
        int fieldWidth = Math.max(80, Math.min(132, (this.width - margin * 3) / 2));
        int fieldGap = 8;
        int fieldsLeft = middle - fieldWidth - fieldGap / 2;
        int inputY = Math.max(78, Math.min(98, this.height / 2 - 18));
        countInput = new EditBox(font, fieldsLeft, inputY, fieldWidth, 20, Component.literal("Count"));
        countInput.setMaxLength(3);
        countInput.setValue("1");
        countInput.setHint(Component.literal("Count"));
        addRenderableWidget(countInput);
        tomatInput = new EditBox(font, middle + fieldGap / 2, inputY, fieldWidth, 20, Component.literal("TMT"));
        tomatInput.setMaxLength(20);
        tomatInput.setValue(TomatMoney.format(ownCentavos).substring("TMT ".length()));
        tomatInput.setHint(Component.literal("TMT amount"));
        addRenderableWidget(tomatInput);

        int buttonWidth = Math.max(78, Math.min(132, (this.width - margin * 3) / 2));
        int xLeft = middle - buttonWidth - fieldGap / 2;
        int xRight = middle + fieldGap / 2;
        int buttonY = Math.max(inputY + 28, Math.min(this.height - 78, inputY + 28));
        addRenderableWidget(Button.builder(Component.literal("Add held item"), button -> send("ADD_HELD", parseCount(), 0))
                .bounds(xLeft, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Set TMT offer"), button -> setTomats())
                .bounds(xRight, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal(ownReady ? "Ready ✓" : "Ready"), button -> send("READY", 0, 0))
                .bounds(xLeft, buttonY + 25, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Final Confirm"), button -> send("CONFIRM", 0, 0))
                .bounds(xRight, buttonY + 25, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Remove last"), button -> send("REMOVE_LAST_ITEM", 0, 0))
                .bounds(xLeft, Math.min(this.height - 26, buttonY + 51), buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel trade"), button -> send("CANCEL", 0, 0))
                .bounds(xRight, Math.min(this.height - 26, buttonY + 51), buttonWidth, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractBackground(graphics, mouseX, mouseY, partialTick);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        centerText(graphics, "Offer revision " + revision + "   ·   Cornucopia", width / 2, 18, 0xFFE2B85B);
        graphics.text(font, "You  ·  " + TomatMoney.format(ownCentavos), 12, 35, 0xFFFFFFFF);
        graphics.text(font, fit(ownItems, width - 24), 12, 48, 0xFFBFC8D4);
        graphics.text(font, otherName + "  ·  " + TomatMoney.format(otherCentavos), 12, 62, 0xFFFFFFFF);
        graphics.text(font, fit(otherItems, width - 24), 12, 75, 0xFFBFC8D4);
        graphics.text(font, "Item count", Math.max(8, width / 2 - 142), Math.max(63, height / 2 - 31), 0xFFBFC8D4);
        graphics.text(font, "Tomat offer", Math.min(width - 88, width / 2 + 5), Math.max(63, height / 2 - 31), 0xFFBFC8D4);
        String status = bothReady ? "Both Ready — final Confirm is active" : "Ready: you " + yes(ownReady) + "  /  " + otherName + " " + yes(otherReady);
        centerText(graphics, status, width / 2, Math.max(55, height - 17), bothReady ? 0xFF68D391 : 0xFFBFC8D4);
        if (!inputError.isBlank()) centerText(graphics, inputError, width / 2, Math.max(52, height - 32), 0xFFFF6B6B);
    }

    private void setTomats() {
        try {
            BigDecimal amount = new BigDecimal(tomatInput.getValue());
            if (amount.scale() > 2 || amount.signum() < 0) throw new ArithmeticException();
            long centavos = amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            send("SET_TOMATS", 0, centavos);
        } catch (RuntimeException invalid) {
            inputError = "Enter a non-negative TMT amount with at most 2 decimals.";
        }
    }

    private int parseCount() {
        try { return Integer.parseInt(countInput.getValue()); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private void send(String action, int count, long amount) {
        ClientPlayNetworking.send(new TomatTradePayloads.ActionPayload(
                tradeId + "|" + revision + "|" + action + "|" + count + "|" + amount));
    }

    private String at(int index, String fallback) { return index < fields.length && !fields[index].isBlank() ? fields[index] : fallback; }
    private static int parseInt(String text, int fallback) { try { return Integer.parseInt(text); } catch (NumberFormatException e) { return fallback; } }
    private static long parseLong(String text, long fallback) { try { return Long.parseLong(text); } catch (NumberFormatException e) { return fallback; } }
    private static String yes(boolean value) { return value ? "ready" : "not ready"; }
    private void centerText(GuiGraphicsExtractor graphics, String value, int centerX, int y, int color) {
        graphics.text(font, value, centerX - font.width(value) / 2, y, color);
    }
    private String fit(String text, int maxWidth) {
        String value = text == null ? "" : text;
        if (font.width(value) <= maxWidth) return value;
        while (!value.isEmpty() && font.width(value + "…") > maxWidth) value = value.substring(0, value.length() - 1);
        return value + "…";
    }

    @Override public boolean isPauseScreen() { return false; }
}
