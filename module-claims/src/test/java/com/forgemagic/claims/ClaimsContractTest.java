package com.forgemagic.claims;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;

class ClaimsContractTest {
    @Test void defaultProviderIsNamedFailClosed() { assertEquals("UNCONFIGURED_FAIL_CLOSED", new FailClosedClaimsService().providerName()); }

    @Test void ordinaryPlayersNeedThreeFactorsAndCannotDeleteClaims() {
        UUID player = UUID.randomUUID();
        var area = new ImmutableClaimsService.Cuboid(0, 0, 0, 10, 10, 10);
        var service = new ImmutableClaimsService(true);
        var missing = new ThreeFactorConfirmation(player, "world", area, "CLAIM FOREVER", "", "proof", "123456", "proof");
        assertEquals(ImmutableClaimsService.ClaimResult.REJECTED_CONFIRMATION, service.createClaim(player, "world", area, missing));
        var valid = new ThreeFactorConfirmation(player, "world", area, "CLAIM FOREVER", "123456", "proof", "123456", "proof");
        assertEquals(ImmutableClaimsService.ClaimResult.CREATED, service.createClaim(player, "world", area, valid));
        assertEquals(ImmutableClaimsService.ClaimResult.REJECTED_CONFIRMATION_REPLAY, service.createClaim(player, "world", area, valid));
        assertTrue(service.canModify(player, new ClaimsService.ClaimBlock("world", 1, 1, 1)));
        assertFalse(service.canModify(UUID.randomUUID(), new ClaimsService.ClaimBlock("world", 1, 1, 1)));
        assertFalse(service.canModify(null, new ClaimsService.ClaimBlock("world", 1, 1, 1)));
        assertEquals(1, service.claims().size());
    }

    @Test void overlappingClaimsAreRejectedBeforeTheyCanBecomePermanent() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var firstArea = new ImmutableClaimsService.Cuboid(0, 0, 0, 10, 10, 10);
        var secondArea = new ImmutableClaimsService.Cuboid(10, 5, 10, 20, 15, 20);
        var service = new ImmutableClaimsService(true);
        var one = new ThreeFactorConfirmation(first, "world", firstArea, "CLAIM FOREVER", "111111", "proof-one", "111111", "proof-one");
        var two = new ThreeFactorConfirmation(second, "world", secondArea, "CLAIM FOREVER", "222222", "proof-two", "222222", "proof-two");
        assertEquals(ImmutableClaimsService.ClaimResult.CREATED, service.createClaim(first, "world", firstArea, one));
        assertEquals(ImmutableClaimsService.ClaimResult.REJECTED_OVERLAP, service.createClaim(second, "world", secondArea, two));
    }

    @Test void modBuildingEffectsPurgeByDefaultWhenRegistered() {
        var service = new ImmutableClaimsService(true);
        service.registerProtectedBuilding("world", new ImmutableClaimsService.ProtectedBuilding(
                "town-hall", new ImmutableClaimsService.Cuboid(0, 0, 0, 10, 10, 10), java.util.List.of("tree-fell", "area-mine")));
        assertTrue(service.shouldPurgeEffect(new ClaimsService.ClaimBlock("world", 4, 4, 4), "TREE-FELL"));
        assertFalse(service.shouldPurgeEffect(new ClaimsService.ClaimBlock("world", 4, 4, 4), "healing"));
    }
}
