package com.mdvcraft.mdvsocial.bedrock.menu.model;

import java.util.List;
import java.util.Locale;

/** One parsed button in a MenusBedrock SimpleForm. */
public record BedrockMenuButton(String id, String text, String action, String targetMenu, List<String> commands,
        boolean closeOnClick, String visibleWhen, String conditionPlaceholder,
        String conditionEquals, String trueMenu, String falseMenu, String clansMenu,
        String sound, String permission, boolean hideWithoutPermission,
        String imageType, String imageData) {

    public BedrockMenuButton {
        id = id == null ? "" : id;
        text = text == null ? "" : text;
        action = action == null ? "" : action;
        targetMenu = targetMenu == null ? "" : targetMenu;
        commands = commands == null ? List.of() : List.copyOf(commands);
        visibleWhen = visibleWhen == null ? "always"
                : visibleWhen.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        conditionPlaceholder = conditionPlaceholder == null ? "" : conditionPlaceholder;
        conditionEquals = conditionEquals == null ? "true" : conditionEquals;
        trueMenu = trueMenu == null ? "" : trueMenu;
        falseMenu = falseMenu == null ? "" : falseMenu;
        clansMenu = clansMenu == null ? "" : clansMenu;
        sound = sound == null ? "" : sound;
        permission = permission == null ? "" : permission.trim();
        imageType = imageType == null ? "" : imageType;
        imageData = imageData == null ? "" : imageData;
    }

    public boolean hasImage() {
        return !imageData.isBlank();
    }
}
