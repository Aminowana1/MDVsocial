package com.mdvcraft.mdvsocial;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartyCommandTest {

    @Test
    void namesAreDedupedIgnoringCaseAndBlanks() {
        assertEquals(List.of("Player1234", "StevePro"),
                PartyCommand.distinctNames(new String[] { "Player1234", " ", "StevePro", "player1234" }));
        assertEquals(List.of(), PartyCommand.distinctNames(new String[0]));
    }
}
