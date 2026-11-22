package com.forgemagic.fabric;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Minimal string payloads keep all offer and currency validation on the server. */
public final class TomatTradePayloads {
    private static final String CHANNEL = ForgeSoloFabric.MOD_ID;
    public static final CustomPacketPayload.Type<PanelPayload> PANEL_TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(CHANNEL, "tomat_trade_panel"));
    public static final CustomPacketPayload.Type<ActionPayload> ACTION_TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(CHANNEL, "tomat_trade_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PanelPayload> PANEL_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PanelPayload::data, PanelPayload::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> ACTION_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ActionPayload::data, ActionPayload::new);

    private TomatTradePayloads() { }
    public static void registerCommon() {
        PayloadTypeRegistry.clientboundPlay().register(PANEL_TYPE, PANEL_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ACTION_TYPE, ACTION_CODEC);
    }
    public record PanelPayload(String data) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return PANEL_TYPE; }
    }
    public record ActionPayload(String data) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return ACTION_TYPE; }
    }
}
