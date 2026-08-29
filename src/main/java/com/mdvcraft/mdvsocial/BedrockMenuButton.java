package com.mdvcraft.mdvsocial;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** One parsed button in a MenusBedrock SimpleForm. */
final class BedrockMenuButton {
    final String id;
    final String text;
    final String action;
    final String targetMenu;
    final List<String> commands;
    final boolean closeOnClick;
    final String visibleWhen;
    final String conditionPlaceholder;
    final String conditionEquals;
    final String trueMenu;
    final String falseMenu;
    final String clansMenu;
    final String sound;
    final String permission;
    final boolean hideWithoutPermission;
    final String imageType;
    final String imageData;

    BedrockMenuButton(String id, String text, String action, String targetMenu, List<String> commands,
            boolean closeOnClick, String visibleWhen, String conditionPlaceholder,
            String conditionEquals, String trueMenu, String falseMenu, String clansMenu,
            String sound, String permission, boolean hideWithoutPermission,
            String imageType, String imageData) {
        this.id = id == null ? "" : id;
        this.text = text == null ? "" : text;
        this.action = action == null ? "" : action;
        this.targetMenu = targetMenu == null ? "" : targetMenu;
        this.commands = commands == null ? List.of() : commands;
        this.closeOnClick = closeOnClick;
        this.visibleWhen = visibleWhen == null ? "always"
                : visibleWhen.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        this.conditionPlaceholder = conditionPlaceholder == null ? "" : conditionPlaceholder;
        this.conditionEquals = conditionEquals == null ? "true" : conditionEquals;
        this.trueMenu = trueMenu == null ? "" : trueMenu;
        this.falseMenu = falseMenu == null ? "" : falseMenu;
        this.clansMenu = clansMenu == null ? "" : clansMenu;
        this.sound = sound == null ? "" : sound;
        this.permission = permission == null ? "" : permission.trim();
        this.hideWithoutPermission = hideWithoutPermission;
        this.imageType = imageType == null ? "" : imageType;
        this.imageData = imageData == null ? "" : imageData;
    }

    boolean isVisible(MDVSocialPlugin plugin, Player viewer, UUID targetUuid, boolean targetOnline) {
        boolean hasTarget = targetUuid != null;
        boolean self = hasTarget && viewer.getUniqueId().equals(targetUuid);
        boolean friend = hasTarget && plugin.isBedrockFriend(viewer, targetUuid);
        return switch (visibleWhen) {
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
