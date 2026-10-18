package com.mdvcraft.mdvsocial;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MMOCoreRequestClicksTest {

    private static final UUID ID = UUID.fromString("0b4f8f7e-3c1a-4a52-9a0e-6f3c2d1b7a90");

    @Test
    void parsesTheButtonsCommands() {
        assertEquals(new MMOCoreRequestClicks.Click(MMOCoreRequestClicks.Kind.PARTY, false, ID),
                MMOCoreRequestClicks.parse("/mmocore:party deny " + ID));
        assertEquals(new MMOCoreRequestClicks.Click(MMOCoreRequestClicks.Kind.FRIENDS, true, ID),
                MMOCoreRequestClicks.parse("/Friends  ACCEPT " + ID));
    }

    @Test
    void ignoresOtherCommands() {
        assertNull(MMOCoreRequestClicks.parse("/party deny"));
        assertNull(MMOCoreRequestClicks.parse("/party invite Bob"));
        assertNull(MMOCoreRequestClicks.parse("/party deny not-a-uuid"));
        assertNull(MMOCoreRequestClicks.parse("/tpa aceptarsolicitud " + ID));
        assertNull(MMOCoreRequestClicks.parse("party deny " + ID));
    }
}
