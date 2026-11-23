package com.forgemagic.fabric.client;

import com.forgemagic.fabric.FernMusicTracks;
import com.forgemagic.fabric.ForgeSoloPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/** Client-local background playback, so music follows the player instead of a world position. */
public final class ForgeSoloMusicPlayer {
    private static Identifier activeSound;

    private ForgeSoloMusicPlayer() { }

    public static void handle(ForgeSoloPayloads.MusicControlPayload payload) {
        if (payload.stop()) {
            stop();
            return;
        }
        FernMusicTracks.Track track = FernMusicTracks.find(payload.track()).orElse(null);
        if (track == null) return;

        stop();
        var soundEvent = track.sound().value();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forMusic(soundEvent));
        activeSound = soundEvent.location();
    }

    public static void stop() {
        if (activeSound == null) return;
        Minecraft.getInstance().getSoundManager().stop(activeSound, SoundSource.MUSIC);
        activeSound = null;
    }
}
