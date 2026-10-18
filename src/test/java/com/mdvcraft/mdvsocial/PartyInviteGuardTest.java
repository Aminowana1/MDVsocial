package com.mdvcraft.mdvsocial;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PartyInviteGuardTest {

    private static final List<String> COMMANDS = List.of("party invite", "/mmocore:party invite");

    @Test
    void extractsInvitedName() {
        assertEquals("Bob", PartyInviteGuard.invitedName("/party invite Bob", COMMANDS));
        assertEquals("Bob", PartyInviteGuard.invitedName("/PARTY   Invite   Bob extra", COMMANDS));
        assertEquals("Ana", PartyInviteGuard.invitedName("/mmocore:party invite Ana", COMMANDS));
    }

    @Test
    void ignoresOtherCommands() {
        assertNull(PartyInviteGuard.invitedName("/party invite", COMMANDS));
        assertNull(PartyInviteGuard.invitedName("/party accept Bob", COMMANDS));
        assertNull(PartyInviteGuard.invitedName("/partyinvite Bob", COMMANDS));
        assertNull(PartyInviteGuard.invitedName("party invite Bob", COMMANDS));
    }
}
