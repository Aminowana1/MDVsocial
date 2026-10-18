package com.mdvcraft.mdvsocial.bedrock.action;

import com.mdvcraft.mdvsocial.bedrock.forms.BlockedPlayersForms;
import com.mdvcraft.mdvsocial.bedrock.forms.FriendsForms;
import com.mdvcraft.mdvsocial.bedrock.forms.LinkMessages;
import com.mdvcraft.mdvsocial.bedrock.forms.MMOCoreForms;
import com.mdvcraft.mdvsocial.bedrock.forms.MailForms;
import com.mdvcraft.mdvsocial.bedrock.forms.PartyForms;
import com.mdvcraft.mdvsocial.bedrock.forms.TitlesForms;
import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuContext;
import com.mdvcraft.mdvsocial.bedrock.port.BedrockHost;
import com.mdvcraft.mdvsocial.bedrock.port.SocialPort;
import com.mdvcraft.mdvsocial.bedrock.port.TitlesPort;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

/** Registers every action supported out of the box by MenusBedrock buttons. */
public final class BuiltinBedrockActions {

    private BuiltinBedrockActions() {
    }

    public record Dependencies(BedrockHost host, BedrockNavigation navigation, TitlesPort titlesPort,
            SocialPort social, TitlesForms titles, MailForms mail, FriendsForms friends, PartyForms party,
            MMOCoreForms mmocore, BlockedPlayersForms blocked, LinkMessages links) {
    }

    public static void registerAll(BedrockActionRegistry registry, Dependencies d) {
        BedrockNavigation nav = d.navigation();
        BedrockHost host = d.host();

        // Navigation between YAML menus.
        registry.register((p, b, c) -> nav.reopen(p, c, c.page()), "", "NONE");
        registry.register((p, b, c) -> {
            // The form already closed when the button was chosen.
        }, "CLOSE");
        registry.register((p, b, c) -> host.openStartMenu(p), "OPEN_MAIN");
        registry.register((p, b, c) -> nav.openChild(p, c, b.targetMenu()), "OPEN_MENU");
        registry.register((p, b, c) -> {
            boolean result = host.evaluateCondition(p, b.conditionPlaceholder(), b.conditionEquals());
            nav.openChild(p, c, result ? b.trueMenu() : b.falseMenu());
        }, "OPEN_CONDITIONAL_MENU");
        registry.register((p, b, c) -> nav.back(p, c), "BACK");
        registry.register((p, b, c) -> nav.reopen(p, c, Math.max(1, c.page() - 1)), "PREVIOUS_PAGE", "PREV_PAGE");
        registry.register((p, b, c) -> nav.reopen(p, c, c.page() + 1), "NEXT_PAGE");
        registry.register((p, b, c) -> nav.runPlayerCommands(p, b.commands(), c), "COMMAND_PLAYER");
        registry.register((p, b, c) -> {
            String clansMenu = b.clansMenu();
            if (clansMenu.isBlank())
                clansMenu = b.targetMenu();
            if (clansMenu.isBlank())
                clansMenu = "auto";
            Bukkit.dispatchCommand(p, "clan abrir " + clansMenu);
        }, "MDVCLANS_OPEN");

        // Titles and ranks.
        registry.register((p, b, c) -> d.titles().openHome(p), "OPEN_TITLES", "OPEN_TITLES_HOME");
        registry.register((p, b, c) -> d.titles().openList(p, "MY_TITLES", 0), "OPEN_MY_TITLES");
        registry.register((p, b, c) -> d.titles().openList(p, "SHOP", 0), "OPEN_SHOP");
        registry.register((p, b, c) -> d.titles().openList(p, "LOCKED", 0), "OPEN_LOCKED");
        registry.register((p, b, c) -> d.titles().openRanks(p, 0), "OPEN_RANKS");
        registry.register((p, b, c) -> {
            d.titlesPort().clear(p);
            d.titles().openHome(p);
        }, "CLEAR_TITLE");

        // Mail.
        registry.register((p, b, c) -> d.mail().openMailbox(p, 0), "OPEN_MAILBOX");
        registry.register((p, b, c) -> d.mail().openCompose(p, returnMenu(c), c.page()), "START_MAIL_SEND");
        registry.register((p, b, c) -> d.mail().openMessageToTarget(p, c.targetUuid(), c.targetName(),
                returnMenu(c), c.page()), "START_MAIL_SEND_TARGET");

        // Friends and party.
        registry.register((p, b, c) -> d.friends().openList(p, 0), "OPEN_BEDROCK_FRIENDS");
        registry.register((p, b, c) -> d.party().open(p), "OPEN_BEDROCK_PARTY");
        registry.register((p, b, c) -> d.social().inviteFriendToParty(p, c.targetUuid(), c.targetName()),
                "INVITE_PARTY_TARGET");
        registry.register((p, b, c) -> d.social().inviteFriend(p, c.targetUuid(), c.targetName()),
                "INVITE_FRIEND_TARGET");
        registry.register((p, b, c) -> d.friends().openRemoveConfirm(p, c.targetUuid(), c.targetName(),
                c.targetOnline()), "REMOVE_FRIEND_TARGET");
        registry.register((p, b, c) -> d.friends().openPrivateMessage(p, c.targetName(), c), "SUGGEST_MSG_TARGET");

        // General block list (old START_MAIL_BLOCK/UNBLOCK buttons are aliases of these).
        registry.register((p, b, c) -> d.blocked().openList(p, 0), "OPEN_BLOCKED_PLAYERS");
        registry.register((p, b, c) -> d.blocked().openBlockForm(p), "START_BLOCK_PLAYER");
        registry.register((p, b, c) -> d.blocked().openUnblockForm(p), "START_UNBLOCK_PLAYER");

        // MMOCore: native form, or the button commands when MMOCore cannot open it.
        registry.register(mmocore(nav, d.mmocore()::openProfile), "OPEN_MMOCORE_PROFILE");
        registry.register(mmocore(nav, d.mmocore()::openAttributes), "OPEN_MMOCORE_ATTRIBUTES");
        registry.register(mmocore(nav, d.mmocore()::openClasses), "OPEN_MMOCORE_CLASSES");

        // External links and other features.
        registry.register((p, b, c) -> host.openItemBrowser(p), "OPEN_MMOITEMS_BROWSER");
        registry.register((p, b, c) -> d.links().sendShop(p), "OPEN_STORE", "STORE_LINK", "OPEN_ONLINE_STORE");
        registry.register((p, b, c) -> d.links().sendDiscord(p), "OPEN_DISCORD", "DISCORD_LINK");
    }

    private static BedrockAction mmocore(BedrockNavigation nav, Predicate<Player> opener) {
        return (p, b, c) -> {
            if (!opener.test(p))
                nav.runPlayerCommands(p, b.commands(), c);
        };
    }

    private static String returnMenu(BedrockMenuContext context) {
        return context.menuId().isBlank() ? "correo" : context.menuId();
    }
}
