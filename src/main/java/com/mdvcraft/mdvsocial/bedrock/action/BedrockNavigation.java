package com.mdvcraft.mdvsocial.bedrock.action;

import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuContext;
import com.mdvcraft.mdvsocial.bedrock.port.BedrockHost;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Navigation helpers between MenusBedrock screens, shared by actions and the renderer. */
public final class BedrockNavigation {

    private final BedrockHost host;

    public BedrockNavigation(BedrockHost host) {
        this.host = host;
    }

    public void back(Player player, BedrockMenuContext context) {
        if (context != null && !context.previousMenu().isBlank()) {
            host.openMenu(player, context.previousMenu(), context.previousPage(), "", 1,
                    context.targetUuid(), context.targetName(), context.targetOnline());
        } else {
            host.openStartMenu(player);
        }
    }

    /** Reopens the current menu at the given page, keeping history and target. */
    public void reopen(Player player, BedrockMenuContext context, int page) {
        host.openMenu(player, context.menuId(), page, context.previousMenu(), context.previousPage(),
                context.targetUuid(), context.targetName(), context.targetOnline());
    }

    /** Opens a child menu whose BACK returns to the current one. */
    public void openChild(Player player, BedrockMenuContext context, String menuId) {
        host.openMenu(player, menuId, 1, context.menuId(), context.page(),
                context.targetUuid(), context.targetName(), context.targetOnline());
    }

    public void runPlayerCommands(Player player, List<String> commands, BedrockMenuContext context) {
        if (commands == null || commands.isEmpty())
            return;
        UUID targetUuid = context.targetUuid();
        for (String line : commands) {
            String cmd = host.applyTargetPlaceholders(line, player, targetUuid, context.targetName(),
                    context.targetOnline()).trim();
            if (cmd.isBlank())
                continue;
            if (cmd.startsWith("/"))
                cmd = cmd.substring(1);
            Bukkit.dispatchCommand(player, cmd);
        }
    }
}
