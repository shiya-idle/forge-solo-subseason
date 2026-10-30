package com.forgemagic.fabric.client;

import com.forgemagic.fabric.ForgeSoloPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Optional Java client companion initializer. */
public final class ForgeSoloClient implements ClientModInitializer {
    public static final String BUILD = "fern-client-1";

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ForgeSoloPayloads.COMPANION_CHALLENGE_TYPE,
                (payload, context) -> {
                    if (payload.protocol() != ForgeSoloPayloads.PROTOCOL_VERSION) return;
                    context.client().execute(() -> ClientPlayNetworking.send(
                            new ForgeSoloPayloads.CompanionHelloPayload(
                                    ForgeSoloPayloads.PROTOCOL_VERSION, BUILD, payload.nonce())));
                });
        ClientPlayNetworking.registerGlobalReceiver(ForgeSoloPayloads.HUD_SNAPSHOT_TYPE,
                (payload, context) -> context.client().execute(() -> ForgeSoloClientState.apply(payload.data())));
    }
}
