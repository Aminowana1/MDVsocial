package com.mdvcraft.mdvsocial;

import java.util.UUID;

/** Immutable navigation context for a Bedrock form screen. */
final class BedrockMenuContext {
    final String menuId;
    final int page;
    final String previousMenu;
    final int previousPage;
    final UUID targetUuid;
    final String targetName;
    final boolean targetOnline;

    BedrockMenuContext(String menuId, int page, String previousMenu, int previousPage,
            UUID targetUuid, String targetName, boolean targetOnline) {
        this.menuId = menuId == null ? "" : menuId;
        this.page = page;
        this.previousMenu = previousMenu == null ? "" : previousMenu;
        this.previousPage = previousPage;
        this.targetUuid = targetUuid;
        this.targetName = targetName == null ? "" : targetName;
        this.targetOnline = targetOnline;
    }
}
