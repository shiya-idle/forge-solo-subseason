package com.forgemagic.fabric;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Blocks WorldEdit command execution unless the verified admin grant explicitly enables it. */
public final class FernWorldEditGuard {
    private static volatile FernAdminPolicy policy = FernAdminPolicy.empty();

    private FernWorldEditGuard() { }

    public static void configure(FernAdminPolicy value) {
        policy = value == null ? FernAdminPolicy.empty() : value;
    }

    public static boolean allow(CommandSourceStack source, String command) {
        if (!isWorldEditCommand(command)) return true;
        ServerPlayer player = source.getPlayer();
        if (player != null && policy.isWorldEditAllowed(player)) return true;
        source.sendFailure(Component.literal("WorldEdit is restricted to the verified Fern builder account."));
        return false;
    }

    private static boolean isWorldEditCommand(String command) {
        if (command == null) return false;
        String value = command.trim().toLowerCase(java.util.Locale.ROOT);
        return value.startsWith("//") || value.startsWith("/worldedit") || value.equals("worldedit")
                || value.startsWith("/wea ") || value.equals("/wea")
                || value.startsWith("wea ") || value.equals("wea");
    }
}
