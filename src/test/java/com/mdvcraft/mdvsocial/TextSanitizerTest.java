package com.mdvcraft.mdvsocial;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextSanitizerTest {

    @Test
    void removesLegacyColorAndStyleCodes() {
        assertEquals("Hola mundo", TextSanitizer.stripFormatting("&4&lHola &kmundo"));
        assertEquals("Oficial", TextSanitizer.stripFormatting("§6§lOficial§r"));
    }

    @Test
    void removesHexCodes() {
        assertEquals("rojo", TextSanitizer.stripFormatting("&#FF0000rojo"));
        assertEquals("azul", TextSanitizer.stripFormatting("&x&0&0&0&0&F&Fazul"));
    }

    @Test
    void codesCannotBeReassembled() {
        assertEquals("texto", TextSanitizer.stripFormatting("&&aatexto"));
    }

    @Test
    void keepsPlainAmpersands() {
        assertEquals("Tom & Jerry & co", TextSanitizer.stripFormatting("Tom & Jerry & co"));
        assertEquals("", TextSanitizer.stripFormatting(null));
    }
}
