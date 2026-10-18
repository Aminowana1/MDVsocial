package com.mdvcraft.mdvsocial;

import com.mdvcraft.mdvsocial.MDVSocialPlugin.RankDef;
import com.mdvcraft.mdvsocial.MDVSocialPlugin.TitleDef;
import com.mdvcraft.mdvsocial.bedrock.port.TitlesPort;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Title and rank data of MDVSocialPlugin for the Bedrock title forms. */
final class BedrockTitlesAdapter implements TitlesPort {

    private final MDVSocialPlugin plugin;

    BedrockTitlesAdapter(MDVSocialPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String activeTitleId(Player player) {
        return plugin.getActiveTitleId(player.getUniqueId());
    }

    @Override
    public String activeTitleDisplay(Player player) {
        TitleDef active = plugin.findTitle(activeTitleId(player));
        return active == null
                ? plugin.getConfig().getString("settings.default-title", "forastero")
                : active.display;
    }

    @Override
    public List<TitleView> titles(Player player, String type) {
        return plugin.filteredTitles(player, type).stream()
                .sorted(Comparator.comparing(t -> plugin.stripColor(t.display)))
                .map(t -> new TitleView(t.id, t.display, t.price, t.unlockPermission))
                .toList();
    }

    @Override
    public void equip(Player player, String titleId) {
        plugin.equipTitle(player, titleId);
    }

    @Override
    public void buy(Player player, String titleId) {
        plugin.buyTitle(player, titleId);
    }

    @Override
    public boolean shopClosed() {
        return plugin.isTitleShopClosed();
    }

    @Override
    public void clear(Player player) {
        plugin.clearActiveTitle(player);
    }

    @Override
    public Map<Integer, List<RankView>> rankPages() {
        Map<Integer, List<RankView>> pages = new LinkedHashMap<>();
        plugin.buildRankLayout().forEach((page, slots) -> pages.put(page, slots.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .map(BedrockTitlesAdapter::view)
                .toList()));
        return pages;
    }

    @Override
    public String formatPrice(double price) {
        return plugin.formatPrice(price);
    }

    private static RankView view(RankDef rank) {
        return new RankView(rank.id, rank.display, rank.permission);
    }
}
