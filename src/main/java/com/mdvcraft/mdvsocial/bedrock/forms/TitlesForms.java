package com.mdvcraft.mdvsocial.bedrock.forms;

import com.mdvcraft.mdvsocial.bedrock.port.TitlesPort;
import com.mdvcraft.mdvsocial.bedrock.port.TitlesPort.RankView;
import com.mdvcraft.mdvsocial.bedrock.port.TitlesPort.TitleView;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native Bedrock forms for titles and ranks (titulos, titulos_lista, rangos). */
public final class TitlesForms {

    private final BedrockFormSupport forms;
    private final TitlesPort titles;

    public TitlesForms(BedrockFormSupport forms, TitlesPort titles) {
        this.forms = forms;
        this.titles = titles;
    }

    public void openHome(Player player) {
        YamlConfiguration ui = forms.ui("titulos");
        String activeId = titles.activeTitleId(player);
        String activeName = titles.activeTitleDisplay(player);
        Map<String, String> tokens = Map.of(
                "active_title", activeName == null ? "" : activeName,
                "active_title_id", activeId == null ? "" : activeId);

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(ui, "title", "&6&lTítulos y Rangos", player, tokens))
                .content(forms.uiLines(ui, "content",
                        List.of("&7Título equipado: &r{active_title}", "&7Elige una categoría."), player, tokens));
        List<Runnable> actions = new ArrayList<>();

        if (ui.getBoolean("buttons.my-titles.enabled", true)) {
            forms.addButton(builder, ui, "buttons.my-titles",
                    forms.uiText(ui, "buttons.my-titles.text", "&a&lMis títulos", player, tokens));
            actions.add(() -> openList(player, "MY_TITLES", 0));
        }
        if (ui.getBoolean("buttons.shop.enabled", true)) {
            forms.addButton(builder, ui, "buttons.shop",
                    forms.uiText(ui, "buttons.shop.text", "&6&lTienda de títulos", player, tokens));
            actions.add(() -> openList(player, "SHOP", 0));
        }
        if (ui.getBoolean("buttons.locked.enabled", true)) {
            forms.addButton(builder, ui, "buttons.locked",
                    forms.uiText(ui, "buttons.locked.text", "&c&lTítulos bloqueados", player, tokens));
            actions.add(() -> openList(player, "LOCKED", 0));
        }
        if (ui.getBoolean("buttons.ranks.enabled", true)) {
            forms.addButton(builder, ui, "buttons.ranks",
                    forms.uiText(ui, "buttons.ranks.text", "&b&lRangos", player, tokens));
            actions.add(() -> openRanks(player, 0));
        }
        if (ui.getBoolean("buttons.clear.enabled", true)) {
            forms.addButton(builder, ui, "buttons.clear",
                    forms.uiText(ui, "buttons.clear.text", "&eQuitar título", player, tokens));
            actions.add(() -> {
                titles.clear(player);
                openHome(player);
            });
        }
        forms.addButton(builder, ui, "buttons.back",
                forms.uiText(ui, "buttons.back.text", "&6Volver", player, tokens));
        actions.add(() -> forms.host().openStartMenu(player));

        forms.show(player, builder, actions);
    }

    public void openList(Player player, String type, int page) {
        if (type.equals("SHOP") && titles.shopClosed()) {
            forms.host().message(player, "title-shop-closed");
            return;
        }
        YamlConfiguration ui = forms.ui("titulos_lista");
        String section = switch (type) {
            case "SHOP" -> "shop";
            case "LOCKED" -> "locked";
            default -> "my-titles";
        };
        List<TitleView> list = titles.titles(player, type);

        int perPage = forms.pageSize(player);
        int maxPage = Math.max(0, (int) Math.ceil(list.size() / (double) perPage) - 1);
        int safePage = Math.max(0, Math.min(page, maxPage));
        int start = safePage * perPage;
        int end = Math.min(list.size(), start + perPage);
        Map<String, String> pageTokens = Map.of(
                "page", String.valueOf(safePage + 1),
                "max_page", String.valueOf(maxPage + 1),
                "count", String.valueOf(list.size()));

        String base = "sections." + section;
        String defaultTitle = switch (type) {
            case "SHOP" -> "&6&lTienda de Títulos &8({page}/{max_page})";
            case "LOCKED" -> "&c&lTítulos Bloqueados &8({page}/{max_page})";
            default -> "&a&lMis Títulos &8({page}/{max_page})";
        };
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(ui, base + ".title", defaultTitle, player, pageTokens))
                .content(list.isEmpty()
                        ? forms.uiText(ui, base + ".empty", "&7No hay títulos en esta categoría.", player, pageTokens)
                        : forms.uiLines(ui, base + ".content", List.of("&7Selecciona un título."), player,
                                pageTokens));
        List<Runnable> actions = new ArrayList<>();

        String activeId = titles.activeTitleId(player);
        for (int i = start; i < end; i++) {
            TitleView title = list.get(i);
            boolean active = title.id().equals(activeId);
            String status;
            String requirement = title.unlockPermission() == null || title.unlockPermission().isBlank()
                    ? "&cBloqueado"
                    : "&cRequiere: &7" + title.unlockPermission();
            if (type.equals("MY_TITLES"))
                status = active ? "&aEquipado" : "&eToca para equipar";
            else if (type.equals("SHOP"))
                status = "&6" + titles.formatPrice(title.price()) + " monedas";
            else
                status = requirement;

            Map<String, String> tokens = new HashMap<>(pageTokens);
            tokens.put("title_id", title.id());
            tokens.put("title_display", title.display());
            tokens.put("price", titles.formatPrice(title.price()));
            tokens.put("status", status);
            tokens.put("requirement", requirement);
            tokens.put("active", active ? "true" : "false");
            String genericEntryPath = base + ".entry";
            String specificEntryPath = "titles." + title.id();
            String entryPath = ui.isConfigurationSection(specificEntryPath) ? specificEntryPath : genericEntryPath;
            String genericTemplate = ui.getString(genericEntryPath + ".text", "{title_display}\n{status}");
            String text = forms.uiText(ui, entryPath + ".text", genericTemplate, player, tokens);
            forms.addButton(builder, ui, entryPath, text);

            if (type.equals("MY_TITLES")) {
                actions.add(() -> {
                    titles.equip(player, title.id());
                    openList(player, type, safePage);
                });
            } else if (type.equals("SHOP")) {
                actions.add(() -> {
                    titles.buy(player, title.id());
                    openList(player, type, safePage);
                });
            } else {
                actions.add(() -> {
                    forms.host().message(player, "title-locked");
                    openList(player, type, safePage);
                });
            }
        }

        if (safePage > 0) {
            forms.addButton(builder, ui, "buttons.previous",
                    forms.uiText(ui, "buttons.previous.text", "&ePágina anterior", player, pageTokens));
            actions.add(() -> openList(player, type, safePage - 1));
        }
        if (safePage < maxPage) {
            forms.addButton(builder, ui, "buttons.next",
                    forms.uiText(ui, "buttons.next.text", "&ePágina siguiente", player, pageTokens));
            actions.add(() -> openList(player, type, safePage + 1));
        }
        forms.addButton(builder, ui, "buttons.back",
                forms.uiText(ui, "buttons.back.text", "&6Volver a Títulos", player, pageTokens));
        actions.add(() -> openHome(player));

        forms.show(player, builder, actions);
    }

    public void openRanks(Player player, int page) {
        YamlConfiguration ui = forms.ui("rangos");
        Map<Integer, List<RankView>> layout = titles.rankPages();
        int maxPage = Math.max(0, layout.keySet().stream().max(Integer::compareTo).orElse(0));
        int safePage = Math.max(0, Math.min(page, maxPage));
        List<RankView> current = layout.getOrDefault(safePage, List.of());
        Map<String, String> pageTokens = Map.of(
                "page", String.valueOf(safePage + 1),
                "max_page", String.valueOf(maxPage + 1));

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(ui, "title", "&b&lRangos &8({page}/{max_page})", player, pageTokens))
                .content(forms.uiLines(ui, "content", List.of("&7Tus rangos y requisitos actuales."), player,
                        pageTokens));
        List<Runnable> actions = new ArrayList<>();

        for (RankView rank : current) {
            boolean owned = rank.permission() == null || rank.permission().isBlank()
                    || player.hasPermission(rank.permission());
            Map<String, String> tokens = new HashMap<>(pageTokens);
            tokens.put("rank_id", rank.id());
            tokens.put("rank_display", rank.display());
            tokens.put("permission", rank.permission() == null ? "" : rank.permission());
            tokens.put("status", owned ? "&aObtenido" : "&cNo obtenido");
            String path = owned ? "rank.owned" : "rank.locked";
            String specificPath = "ranks." + rank.id();
            String entryPath = ui.isConfigurationSection(specificPath) ? specificPath : path;
            String genericTemplate = ui.getString(path + ".text", "{rank_display}\n{status}");
            forms.addButton(builder, ui, entryPath,
                    forms.uiText(ui, entryPath + ".text", genericTemplate, player, tokens));
            actions.add(() -> openRanks(player, safePage));
        }

        if (safePage > 0) {
            forms.addButton(builder, ui, "buttons.previous",
                    forms.uiText(ui, "buttons.previous.text", "&ePágina anterior", player, pageTokens));
            actions.add(() -> openRanks(player, safePage - 1));
        }
        if (safePage < maxPage) {
            forms.addButton(builder, ui, "buttons.next",
                    forms.uiText(ui, "buttons.next.text", "&ePágina siguiente", player, pageTokens));
            actions.add(() -> openRanks(player, safePage + 1));
        }
        forms.addButton(builder, ui, "buttons.back",
                forms.uiText(ui, "buttons.back.text", "&6Volver a Títulos", player, pageTokens));
        actions.add(() -> openHome(player));

        forms.show(player, builder, actions);
    }
}
