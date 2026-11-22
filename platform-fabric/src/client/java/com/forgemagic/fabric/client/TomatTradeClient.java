package com.forgemagic.fabric.client;

import com.forgemagic.fabric.TomatTradePayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Client-only receiver for the optional native trade panel. */
public final class TomatTradeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(TomatTradePayloads.PANEL_TYPE,
                (payload, context) -> context.client().execute(() ->
                        context.client().setScreenAndShow(new TomatTradeScreen(payload.data()))));
    }
}
