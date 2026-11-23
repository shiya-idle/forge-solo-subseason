package com.forgemagic.fabric;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** Opt-in, per-player playback of Minecraft's own music-disc tracks. */
public final class FernMusicService {
    private final Map<UUID, Playback> activePlayback = new ConcurrentHashMap<>();

    public int list(ServerPlayer player) {
        String choices = FernMusicTracks.all().stream()
                .map(FernMusicTracks.Track::key)
                .collect(Collectors.joining(", "));
        player.sendSystemMessage(Component.literal("§6Fern music tracks: §f" + choices));
        player.sendSystemMessage(Component.literal("§7Play one with §f/fern music play <track>§7; stop it with §f/fern music stop§7."));
        return 1;
    }

    public int play(ServerPlayer player, String key) {
        FernMusicTracks.Track track = FernMusicTracks.find(key).orElse(null);
        if (track == null) {
            player.sendSystemMessage(Component.literal("§cUnknown music track. Use /fern music list to see available tracks."));
            return 0;
        }

        stopCurrent(player);
        boolean bedrock = BedrockCompatibility.isBedrockPlayer(player.getUUID());
        if (bedrock) {
            Holder<SoundEvent> sound = track.sound();
            player.connection.send(new ClientboundSoundPacket(sound, SoundSource.MUSIC,
                    player.getX(), player.getY(), player.getZ(), 0.85f, 1.0f,
                    player.getRandom().nextLong()));
        } else {
            ServerPlayNetworking.send(player, new ForgeSoloPayloads.MusicControlPayload(track.key(), false));
        }
        activePlayback.put(player.getUUID(), new Playback(track.sound().value().location(), bedrock));
        player.sendSystemMessage(Component.literal("§aNow playing " + track.key() + " for you. Use /fern music stop to stop it."));
        return 1;
    }

    public int stop(ServerPlayer player) {
        if (!stopCurrent(player)) {
            player.sendSystemMessage(Component.literal("§7No Fern music track is currently playing."));
        } else {
            player.sendSystemMessage(Component.literal("§aStopped your Fern music."));
        }
        return 1;
    }

    public void clear(UUID playerId) {
        activePlayback.remove(playerId);
    }

    private boolean stopCurrent(ServerPlayer player) {
        Playback playback = activePlayback.remove(player.getUUID());
        if (playback == null) return false;

        if (playback.bedrock()) {
            player.connection.send(new ClientboundStopSoundPacket(playback.soundId(), SoundSource.MUSIC));
        } else {
            ServerPlayNetworking.send(player, new ForgeSoloPayloads.MusicControlPayload("", true));
        }
        return true;
    }

    private record Playback(Identifier soundId, boolean bedrock) { }
}
