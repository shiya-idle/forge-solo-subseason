package com.forgemagic.fabric;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Vanilla music-disc tracks exposed by the opt-in Fern music command. */
public final class FernMusicTracks {
    private static final Map<String, Track> TRACKS = createTracks();

    private FernMusicTracks() { }

    public static Optional<Track> find(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TRACKS.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Collection<Track> all() {
        return TRACKS.values();
    }

    public static Collection<String> keys() {
        return TRACKS.keySet();
    }

    private static Map<String, Track> createTracks() {
        Map<String, Track> tracks = new LinkedHashMap<>();
        add(tracks, "13", SoundEvents.MUSIC_DISC_13);
        add(tracks, "cat", SoundEvents.MUSIC_DISC_CAT);
        add(tracks, "blocks", SoundEvents.MUSIC_DISC_BLOCKS);
        add(tracks, "chirp", SoundEvents.MUSIC_DISC_CHIRP);
        add(tracks, "far", SoundEvents.MUSIC_DISC_FAR);
        add(tracks, "mall", SoundEvents.MUSIC_DISC_MALL);
        add(tracks, "mellohi", SoundEvents.MUSIC_DISC_MELLOHI);
        add(tracks, "stal", SoundEvents.MUSIC_DISC_STAL);
        add(tracks, "strad", SoundEvents.MUSIC_DISC_STRAD);
        add(tracks, "ward", SoundEvents.MUSIC_DISC_WARD);
        add(tracks, "11", SoundEvents.MUSIC_DISC_11);
        add(tracks, "wait", SoundEvents.MUSIC_DISC_WAIT);
        add(tracks, "5", SoundEvents.MUSIC_DISC_5);
        add(tracks, "pigstep", SoundEvents.MUSIC_DISC_PIGSTEP);
        add(tracks, "otherside", SoundEvents.MUSIC_DISC_OTHERSIDE);
        add(tracks, "relic", SoundEvents.MUSIC_DISC_RELIC);
        add(tracks, "creator", SoundEvents.MUSIC_DISC_CREATOR);
        add(tracks, "creator_music_box", SoundEvents.MUSIC_DISC_CREATOR_MUSIC_BOX);
        add(tracks, "precipice", SoundEvents.MUSIC_DISC_PRECIPICE);
        add(tracks, "tears", SoundEvents.MUSIC_DISC_TEARS);
        add(tracks, "lava_chicken", SoundEvents.MUSIC_DISC_LAVA_CHICKEN);
        add(tracks, "bounce", SoundEvents.MUSIC_DISC_BOUNCE);
        return Collections.unmodifiableMap(tracks);
    }

    private static void add(Map<String, Track> tracks, String key, Holder.Reference<SoundEvent> sound) {
        tracks.put(key, new Track(key, sound));
    }

    public record Track(String key, Holder.Reference<SoundEvent> sound) { }
}
