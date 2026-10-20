package com.mdvcraft.mdvsocial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyJoinMenuGuardTest {

    @Test
    void recognisesPartyAcceptCommands() {
        assertTrue(PartyJoinMenuGuard.isAcceptCommand("/mmocore:party accept 1234"));
        assertTrue(PartyJoinMenuGuard.isAcceptCommand("/Party  Accept"));
        assertFalse(PartyJoinMenuGuard.isAcceptCommand("/party deny 1234"));
        assertFalse(PartyJoinMenuGuard.isAcceptCommand("/partyaccept"));
        assertFalse(PartyJoinMenuGuard.isAcceptCommand("party accept"));
    }
}
