package com.mdvcraft.mdvsocial.bedrock.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockVisibilityTest {

    @Test
    void alwaysAndUnknownRulesAreVisible() {
        assertTrue(BedrockVisibility.isVisible("always", false, false, false, false));
        assertTrue(BedrockVisibility.isVisible("something_else", false, false, false, false));
        assertTrue(BedrockVisibility.isVisible(null, false, false, false, false));
    }

    @Test
    void targetRulesRequireATarget() {
        assertFalse(BedrockVisibility.isVisible("online", false, false, false, true));
        assertTrue(BedrockVisibility.isVisible("online", true, false, false, true));
        assertFalse(BedrockVisibility.isVisible("offline", true, false, false, true));
        assertTrue(BedrockVisibility.isVisible("has_target", true, false, false, false));
    }

    @Test
    void friendRulesNeverShowAddFriendToSelf() {
        assertTrue(BedrockVisibility.isVisible("friend", true, false, true, false));
        assertTrue(BedrockVisibility.isVisible("not_friend", true, false, false, false));
        assertFalse(BedrockVisibility.isVisible("not_friend", true, true, false, false));
        assertTrue(BedrockVisibility.isVisible("online_not_friend", true, false, false, true));
        assertFalse(BedrockVisibility.isVisible("online_not_friend", true, false, true, true));
        assertFalse(BedrockVisibility.isVisible("offline_not_friend", true, false, false, true));
    }

    @Test
    void selfRules() {
        assertTrue(BedrockVisibility.isVisible("self", true, true, false, true));
        assertFalse(BedrockVisibility.isVisible("other", true, true, false, true));
        assertTrue(BedrockVisibility.isVisible("online_other", true, false, false, true));
        assertFalse(BedrockVisibility.isVisible("online_other", true, false, false, false));
    }
}
