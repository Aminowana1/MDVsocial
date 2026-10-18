package com.mdvcraft.mdvsocial.bedrock.forms;

import com.mdvcraft.mdvsocial.bedrock.port.BlocksPort;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Native Bedrock forms for the general block list (MenusBedrock/bloqueados.yml). */
public final class BlockedPlayersForms {

    private final BedrockFormSupport forms;
    private final BlocksPort blocks;

    public BlockedPlayersForms(BedrockFormSupport forms, BlocksPort blocks) {
        this.forms = forms;
        this.blocks = blocks;
    }

    public void openList(Player player, int page) {
        YamlConfiguration ui = forms.ui("bloqueados");
        List<UUID> blocked = blocks.blocked(player);
        int perPage = forms.pageSize(player);
        int maxPage = Math.max(0, (int) Math.ceil(blocked.size() / (double) perPage) - 1);
        int safePage = Math.max(0, Math.min(page, maxPage));
        int start = safePage * perPage;
        int end = Math.min(blocked.size(), start + perPage);
        Map<String, String> tokens = Map.of(
                "count", String.valueOf(blocked.size()),
                "page", String.valueOf(safePage + 1),
                "max_page", String.valueOf(maxPage + 1));

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(ui, "title", "&c&lJugadores Bloqueados &8({page}/{max_page})", player, tokens))
                .content(blocked.isEmpty()
                        ? forms.uiLines(ui, "content-empty",
                                List.of("&7No tienes jugadores bloqueados.",
                                        "&7Los jugadores bloqueados no pueden enviarte cartas."),
                                player, tokens)
                        : forms.uiLines(ui, "content",
                                List.of("&7Bloqueados: &f{count}", "&7Toca un jugador para desbloquearlo."),
                                player, tokens));
        List<Runnable> actions = new ArrayList<>();

        for (int i = start; i < end; i++) {
            UUID uuid = blocked.get(i);
            String name = displayName(uuid);
            Map<String, String> entryTokens = new HashMap<>(tokens);
            entryTokens.put("target", name);
            forms.addButton(builder, ui, "entry",
                    forms.uiText(ui, "entry.text", "&f{target}\n&7Toca para desbloquear", player, entryTokens));
            int returnPage = safePage;
            actions.add(() -> openUnblockConfirm(player, uuid, name, returnPage));
        }

        if (ui.getBoolean("buttons.block.enabled", true)) {
            forms.addButton(builder, ui, "buttons.block",
                    forms.uiText(ui, "buttons.block.text", "&c&lBloquear jugador\n&7Escribe su nombre", player, tokens));
            actions.add(() -> openBlockForm(player));
        }
        if (safePage > 0) {
            forms.addButton(builder, ui, "buttons.previous",
                    forms.uiText(ui, "buttons.previous.text", "&ePágina anterior", player, tokens));
            actions.add(() -> openList(player, safePage - 1));
        }
        if (safePage < maxPage) {
            forms.addButton(builder, ui, "buttons.next",
                    forms.uiText(ui, "buttons.next.text", "&ePágina siguiente", player, tokens));
            actions.add(() -> openList(player, safePage + 1));
        }
        forms.addButton(builder, ui, "buttons.back",
                forms.uiText(ui, "buttons.back.text", "&6Volver", player, tokens));
        actions.add(() -> forms.host().openMenu(player, "menuamigos", 1, "main", 1, null, "", false));

        forms.show(player, builder, actions);
    }

    private void openUnblockConfirm(Player player, UUID target, String name, int returnPage) {
        YamlConfiguration ui = forms.ui("bloqueados");
        Map<String, String> tokens = Map.of("target", name);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(ui, "unblock-confirm.title", "&a&lDesbloquear a {target}", player, tokens))
                .content(forms.uiText(ui, "unblock-confirm.content",
                        "&7¿Quieres desbloquear a &f{target}&7? Podrá volver a enviarte cartas.", player, tokens));
        forms.addButton(builder, ui, "unblock-confirm.confirm",
                forms.uiText(ui, "unblock-confirm.confirm.text", "&aSí, desbloquear", player, tokens));
        forms.addButton(builder, ui, "unblock-confirm.back",
                forms.uiText(ui, "unblock-confirm.back.text", "&6Volver", player, tokens));
        forms.show(player, builder, List.of(
                () -> {
                    blocks.unblock(player, target);
                    openList(player, returnPage);
                },
                () -> openList(player, returnPage)));
    }

    public void openBlockForm(Player player) {
        openNameForm(player, true);
    }

    public void openUnblockForm(Player player) {
        openNameForm(player, false);
    }

    private void openNameForm(Player player, boolean block) {
        YamlConfiguration ui = forms.ui("bloqueados");
        String path = block ? "block-form" : "unblock-form";
        long session = forms.begin(player);
        CustomForm.Builder builder = CustomForm.builder()
                .title(forms.uiText(ui, path + ".title",
                        block ? "&c&lBloquear jugador" : "&a&lDesbloquear jugador", player, Map.of()))
                .input(forms.uiText(ui, path + ".input-label", "&eNombre del jugador", player, Map.of()),
                        forms.strip(ui.getString(path + ".input-placeholder", "Escribe el nombre...")), "");
        builder.validResultHandler(response -> {
            String rawName = response.asInput(0);
            forms.run(player, session, () -> {
                if (rawName != null && !rawName.isBlank()) {
                    if (block)
                        blocks.blockByName(player, rawName.trim());
                    else
                        blocks.unblockByName(player, rawName.trim());
                }
                openList(player, 0);
            });
        });
        forms.send(player, builder.build());
    }

    private static String displayName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null || name.isBlank() ? uuid.toString().substring(0, 8) : name;
    }
}
