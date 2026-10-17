package com.mdvcraft.mdvsocial.bedrock.menu;

import com.mdvcraft.mdvsocial.bedrock.action.BedrockActionRegistry;
import com.mdvcraft.mdvsocial.bedrock.action.BedrockNavigation;
import com.mdvcraft.mdvsocial.bedrock.forms.BedrockFormSupport;
import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuButton;
import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuContext;
import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuDefinition;
import com.mdvcraft.mdvsocial.bedrock.port.SocialPort;
import com.mdvcraft.mdvsocial.bedrock.session.BedrockUiSessionManager;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Renders static MenusBedrock YAML files as native Cumulus SimpleForms.
 * Loading lives in BedrockMenuRepository and button actions in
 * BedrockActionRegistry.
 */
public final class BedrockMenuRenderer {

    private final BedrockMenuRepository repository;
    private final BedrockFormSupport forms;
    private final BedrockUiSessionManager sessions;
    private final BedrockActionRegistry actions;
    private final BedrockNavigation navigation;
    private final SocialPort social;

    public BedrockMenuRenderer(BedrockMenuRepository repository, BedrockFormSupport forms,
            BedrockUiSessionManager sessions, BedrockActionRegistry actions, BedrockNavigation navigation,
            SocialPort social) {
        this.repository = repository;
        this.forms = forms;
        this.sessions = sessions;
        this.actions = actions;
        this.navigation = navigation;
        this.social = social;
    }

    /**
     * @return false when the player is not Bedrock, the menu does not exist or the
     *         form could not be sent, so the caller can fall back to Java menus.
     */
    public boolean open(Player player, String rawMenuId, int page, String previousMenu, int previousPage,
            UUID targetUuid, String targetName, boolean targetOnline) {
        if (!forms.isBedrock(player))
            return false;

        String menuId = BedrockMenuParser.normalize(rawMenuId);
        BedrockMenuDefinition def = repository.menu(menuId);
        if (def == null)
            return false;

        if (!def.permission().isBlank() && !player.hasPermission(def.permission())) {
            forms.host().message(player, "no-permission");
            return true;
        }

        int maxPage = def.maxPage();
        int safePage = Math.max(1, Math.min(page, maxPage));
        BedrockMenuContext context = new BedrockMenuContext(
                menuId,
                safePage,
                BedrockMenuParser.normalize(previousMenu),
                Math.max(1, previousPage),
                targetUuid,
                targetName,
                targetOnline);

        List<BedrockMenuButton> visible = visibleButtons(player, def.page(safePage), context);

        sessions.trace(player, "open menu=" + menuId + " page=" + safePage
                + " previous=" + context.previousMenu() + " visible=" + visible.size());

        String title = forms.text(def.title()
                .replace("{page}", String.valueOf(safePage))
                .replace("{max_page}", String.valueOf(maxPage)),
                player, targetUuid, context.targetName(), targetOnline);

        StringBuilder content = new StringBuilder();
        for (String line : def.content()) {
            if (content.length() > 0)
                content.append('\n');
            content.append(forms.text(line
                    .replace("{page}", String.valueOf(safePage))
                    .replace("{max_page}", String.valueOf(maxPage)),
                    player, targetUuid, context.targetName(), targetOnline));
        }

        // Each form gets a unique token BEFORE its handler is registered. This is
        // important on mobile: a late result from the previous form can no longer
        // execute actions against the newly opened form/session.
        long session = sessions.begin(player);

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(title)
                .content(content.toString());

        for (BedrockMenuButton button : visible) {
            String text = forms.text(button.text(), player, targetUuid, context.targetName(), targetOnline);
            if (!button.hasImage()) {
                builder.button(text);
            } else {
                FormImage.Type type = button.imageType().equalsIgnoreCase("PATH")
                        ? FormImage.Type.PATH
                        : FormImage.Type.URL;
                builder.button(text, type, button.imageData());
            }
        }

        if (visible.isEmpty())
            builder.button(forms.text("&7Volver", player, targetUuid, context.targetName(), targetOnline));

        builder.closedResultHandler(() -> {
            // Native X closes the form. We intentionally do not reopen anything.
        });
        builder.validResultHandler(response -> {
            int index = response.clickedButtonId();
            if (visible.isEmpty()) {
                sessions.trace(player, "click menu=" + menuId + " index=" + index + " fallback=BACK");
            } else if (index >= 0 && index < visible.size()) {
                BedrockMenuButton selected = visible.get(index);
                sessions.trace(player, "click menu=" + menuId + " index=" + index
                        + " button=" + selected.id() + " action=" + selected.action()
                        + " target=" + selected.targetMenu());
            } else {
                sessions.trace(player, "click invalido menu=" + menuId + " index=" + index
                        + " visible=" + visible.size());
            }

            sessions.run(player, session, () -> {
                if (!player.isOnline())
                    return;
                if (visible.isEmpty()) {
                    navigation.back(player, context);
                    return;
                }
                if (index < 0 || index >= visible.size())
                    return;
                actions.dispatch(player, visible.get(index), context);
            });
        });

        return forms.send(player, builder.build());
    }

    private List<BedrockMenuButton> visibleButtons(Player player, List<BedrockMenuButton> buttons,
            BedrockMenuContext context) {
        UUID targetUuid = context.targetUuid();
        boolean hasTarget = targetUuid != null;
        boolean self = hasTarget && player.getUniqueId().equals(targetUuid);
        boolean friend = hasTarget && social.isFriend(player, targetUuid);

        List<BedrockMenuButton> visible = new ArrayList<>();
        for (BedrockMenuButton button : buttons) {
            if (!BedrockVisibility.isVisible(button.visibleWhen(), hasTarget, self, friend, context.targetOnline()))
                continue;
            if (!button.permission().isBlank() && !player.hasPermission(button.permission())
                    && button.hideWithoutPermission())
                continue;
            visible.add(button);
        }
        return visible;
    }
}
