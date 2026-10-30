package com.forgemagic.fabric;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Common payload definitions shared by the server and the optional Java client companion. */
public final class ForgeSoloPayloads {
    public static final int PROTOCOL_VERSION = 1;
    private static final String CHANNEL = ForgeSoloFabric.MOD_ID;

    public static final CustomPacketPayload.Type<CompanionChallengePayload> COMPANION_CHALLENGE_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CHANNEL, "companion_challenge"));
    public static final CustomPacketPayload.Type<CompanionHelloPayload> COMPANION_HELLO_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CHANNEL, "companion_hello"));
    public static final CustomPacketPayload.Type<HudSnapshotPayload> HUD_SNAPSHOT_TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(CHANNEL, "hud_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionChallengePayload> COMPANION_CHALLENGE_CODEC =
            StreamCodec.composite(ByteBufCodecs.INT, CompanionChallengePayload::protocol,
                    ByteBufCodecs.STRING_UTF8, CompanionChallengePayload::nonce,
                    CompanionChallengePayload::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionHelloPayload> COMPANION_HELLO_CODEC =
            StreamCodec.composite(ByteBufCodecs.INT, CompanionHelloPayload::protocol,
                    ByteBufCodecs.STRING_UTF8, CompanionHelloPayload::build,
                    ByteBufCodecs.STRING_UTF8, CompanionHelloPayload::nonce,
                    CompanionHelloPayload::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, HudSnapshotPayload> HUD_SNAPSHOT_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, HudSnapshotPayload::data,
                    HudSnapshotPayload::new);

    private ForgeSoloPayloads() { }

    public static void registerCommon() {
        PayloadTypeRegistry.clientboundPlay().register(COMPANION_CHALLENGE_TYPE, COMPANION_CHALLENGE_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(HUD_SNAPSHOT_TYPE, HUD_SNAPSHOT_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(COMPANION_HELLO_TYPE, COMPANION_HELLO_CODEC);
    }

    public record CompanionChallengePayload(int protocol, String nonce) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return COMPANION_CHALLENGE_TYPE; }
    }

    public record CompanionHelloPayload(int protocol, String build, String nonce) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return COMPANION_HELLO_TYPE; }
    }

    public record HudSnapshotPayload(String data) implements CustomPacketPayload {
        @Override public Type<? extends CustomPacketPayload> type() { return HUD_SNAPSHOT_TYPE; }
    }
}
