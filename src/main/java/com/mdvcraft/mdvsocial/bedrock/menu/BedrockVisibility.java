package com.mdvcraft.mdvsocial.bedrock.menu;

/** Evaluates the {@code visible-when} rule of a MenusBedrock button. */
public final class BedrockVisibility {

    private BedrockVisibility() {
    }

    /**
     * @param rule         normalized visible-when value (lowercase, underscores)
     * @param hasTarget    whether the menu was opened for a target player
     * @param self         whether the target is the viewer
     * @param friend       whether the target is a friend of the viewer
     * @param targetOnline whether the target is online
     */
    public static boolean isVisible(String rule, boolean hasTarget, boolean self, boolean friend,
            boolean targetOnline) {
        return switch (rule == null ? "always" : rule) {
            case "online", "target_online", "friend_online" -> hasTarget && targetOnline;
            case "offline", "target_offline", "friend_offline" -> hasTarget && !targetOnline;
            case "target", "has_target" -> hasTarget;
            case "friend", "is_friend", "already_friend" -> hasTarget && friend;
            case "not_friend", "non_friend" -> hasTarget && !friend && !self;
            case "online_friend" -> hasTarget && targetOnline && friend;
            case "online_not_friend", "online_non_friend" -> hasTarget && targetOnline && !friend && !self;
            case "offline_not_friend", "offline_non_friend" -> hasTarget && !targetOnline && !friend && !self;
            case "self" -> self;
            case "not_self", "other" -> hasTarget && !self;
            case "online_not_self", "online_other" -> hasTarget && targetOnline && !self;
            default -> true;
        };
    }
}
