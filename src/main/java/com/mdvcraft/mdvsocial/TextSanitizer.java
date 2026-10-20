package com.mdvcraft.mdvsocial;

import java.util.regex.Pattern;

/** Removes Minecraft formatting codes from player-written text. */
public final class TextSanitizer {

    /** &a, §l, &#RRGGBB and each &x&R&R... hex step. */
    private static final Pattern FORMATTING = Pattern.compile("(?i)[&§](?:#[0-9a-f]{6}|[0-9a-fk-orx])");

    private TextSanitizer() {
    }

    /**
     * Strips color and style codes so player text cannot imitate official
     * formatting. Repeats until stable, so nested inputs like "&&aa" do not
     * reassemble into a new code.
     */
    public static String stripFormatting(String text) {
        if (text == null)
            return "";
        String current = text;
        while (true) {
            String next = FORMATTING.matcher(current).replaceAll("");
            if (next.equals(current))
                return next;
            current = next;
        }
    }
}
