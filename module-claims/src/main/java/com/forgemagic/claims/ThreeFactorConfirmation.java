package com.forgemagic.claims;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Three independent confirmations are required before an ordinary player claim is created:
 * exact intent phrase, one-time server code, and a recent session/email proof.
 */
public record ThreeFactorConfirmation(
        UUID player,
        String world,
        ImmutableClaimsService.Cuboid area,
        String intentPhrase,
        String oneTimeCode,
        String recentProof,
        String expectedOneTimeCode,
        String expectedRecentProof
) {
    public boolean validFor(UUID expectedPlayer, String expectedWorld, ImmutableClaimsService.Cuboid expectedArea) {
        return expectedPlayer.equals(player)
                && expectedWorld.equals(world)
                && expectedArea.equals(area)
                && constant("CLAIM FOREVER", intentPhrase)
                && nonBlank(oneTimeCode) && constant(expectedOneTimeCode, oneTimeCode)
                && nonBlank(recentProof) && constant(expectedRecentProof, recentProof);
    }

    public String replayKey() {
        return player + "|" + world + "|" + area + "|" + oneTimeCode + "|" + recentProof;
    }

    private static boolean nonBlank(String value) { return value != null && !value.isBlank(); }
    private static boolean constant(String a, String b) {
        if (!nonBlank(a) || !nonBlank(b)) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
