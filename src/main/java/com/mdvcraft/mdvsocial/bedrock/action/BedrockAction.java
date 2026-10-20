package com.mdvcraft.mdvsocial.bedrock.action;

import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuButton;
import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuContext;
import org.bukkit.entity.Player;

/** Handler for one canonical action of a MenusBedrock button. */
@FunctionalInterface
public interface BedrockAction {

    void execute(Player player, BedrockMenuButton button, BedrockMenuContext context);
}
