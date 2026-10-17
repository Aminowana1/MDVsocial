package com.mdvcraft.mdvsocial.bedrock.menu.model;

import java.util.UUID;

/** Immutable navigation context for a Bedrock form screen. */
public record BedrockMenuContext(String menuId, int page, String previousMenu, int previousPage,
        UUID targetUuid, String targetName, boolean targetOnline) {

    public BedrockMenuContext {
        menuId = menuId == null ? "" : menuId;
        previousMenu = previousMenu == null ? "" : previousMenu;
        targetName = targetName == null ? "" : targetName;
    }
}
