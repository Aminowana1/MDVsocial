package com.mdvcraft.mdvsocial.bedrock.port;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/** Title and rank operations used by the Bedrock title forms. */
public interface TitlesPort {

    record TitleView(String id, String display, double price, String unlockPermission) {
    }

    record RankView(String id, String display, String permission) {
    }

    String activeTitleId(Player player);

    /** Display of the active title, or the configured default title. */
    String activeTitleDisplay(Player player);

    /** Titles of the given type (MY_TITLES, SHOP, LOCKED), sorted for display. */
    List<TitleView> titles(Player player, String type);

    void equip(Player player, String titleId);

    void buy(Player player, String titleId);

    void clear(Player player);

    /** Rank pages (0-based) with their ranks ordered by slot. */
    Map<Integer, List<RankView>> rankPages();

    String formatPrice(double price);
}
