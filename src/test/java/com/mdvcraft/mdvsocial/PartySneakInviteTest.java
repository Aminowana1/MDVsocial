package com.mdvcraft.mdvsocial;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.mdvcraft.mdvsocial.PartySneakInvite.Outcome.ALREADY_MEMBER;
import static com.mdvcraft.mdvsocial.PartySneakInvite.Outcome.CREATE_AND_INVITE;
import static com.mdvcraft.mdvsocial.PartySneakInvite.Outcome.FULL;
import static com.mdvcraft.mdvsocial.PartySneakInvite.Outcome.INVITE;
import static com.mdvcraft.mdvsocial.PartySneakInvite.Outcome.NOT_LEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartySneakInviteTest {

    @Test
    void withoutPartyCreatesOne() {
        assertEquals(CREATE_AND_INVITE, PartySneakInvite.decide(false, false, false, false));
    }

    @Test
    void onlyTheLeaderInvites() {
        assertEquals(INVITE, PartySneakInvite.decide(true, true, false, false));
        assertEquals(NOT_LEADER, PartySneakInvite.decide(true, false, false, false));
    }

    @Test
    void membersAndFullPartiesAreReported() {
        assertEquals(ALREADY_MEMBER, PartySneakInvite.decide(true, false, true, false));
        assertEquals(FULL, PartySneakInvite.decide(true, true, false, true));
    }

    @Test
    void cooldownLetsOneInviteThrough() {
        PartySneakInvite invite = new PartySneakInvite(null);
        UUID player = UUID.randomUUID();
        assertTrue(invite.cooledDown(player, 1_000, 1_500));
        assertFalse(invite.cooledDown(player, 2_000, 1_500));
        assertTrue(invite.cooledDown(player, 2_600, 1_500));
    }
}
