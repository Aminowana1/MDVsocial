package com.mdvcraft.mdvsocial.bedrock.forms;

import com.mdvcraft.mdvsocial.bedrock.port.SocialPort;
import com.mdvcraft.mdvsocial.players.PlayerRanking;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The one "Escoger jugador" form used wherever a Bedrock player chooses another
 * player: a "type a name" button followed by a paged list of online players,
 * ordered by {@link PlayerRanking} (friends, party, clan, others) and labelled by
 * tier. Players the viewer blocked are never listed. Texts live in
 * MenusBedrock/escoger_jugador.yml; each caller sets title, content, which
 * players would be redundant and what happens on pick.
 */
public final class PlayerPickerForms {

    /**
     * @param title      form title; may use {page}, {max_page} and {count}
     * @param content    text above the buttons
     * @param exclude    players redundant for this list (already friends, already in
     *                   the party...); null offers everyone
     * @param onPick     called with (viewer, chosen online player)
     * @param onTypeName called when the viewer prefers to type the name
     * @param onBack     called by the back button
     */
    public record Request(String title, String content, Predicate<UUID> exclude,
            BiConsumer<Player, Player> onPick, Consumer<Player> onTypeName, Consumer<Player> onBack) {
    }

    private static final Map<PlayerRanking.Tier, String> ENTRY_PATH = Map.of(
            PlayerRanking.Tier.FRIEND, "entry-friend",
            PlayerRanking.Tier.PARTY, "entry-party",
            PlayerRanking.Tier.CLAN, "entry-clan",
            PlayerRanking.Tier.ONLINE, "entry");
    private static final Map<PlayerRanking.Tier, String> ENTRY_DEFAULT = Map.of(
            PlayerRanking.Tier.FRIEND, "&2&l{player}\n&r&2Amigo",
            PlayerRanking.Tier.PARTY, "&5&l{player}\n&r&5Grupo de Aventura",
            PlayerRanking.Tier.CLAN, "&3&l{player}\n&r&3Clan",
            PlayerRanking.Tier.ONLINE, "&0&l{player}\n&r&8Conectado");

    private final BedrockFormSupport forms;
    private final SocialPort social;

    public PlayerPickerForms(BedrockFormSupport forms, SocialPort social) {
        this.forms = forms;
        this.social = social;
    }

    public void open(Player viewer, Request request, int page) {
        YamlConfiguration ui = forms.ui("escoger_jugador");
        List<PlayerRanking.Ranked> candidates = social.rankPlayers(viewer, request.exclude());

        int perPage = pageSize();
        int maxPage = Math.max(0, (int) Math.ceil(candidates.size() / (double) perPage) - 1);
        int safePage = Math.max(0, Math.min(page, maxPage));
        int start = safePage * perPage;
        int end = Math.min(candidates.size(), start + perPage);
        Map<String, String> tokens = Map.of(
                "page", String.valueOf(safePage + 1),
                "max_page", String.valueOf(maxPage + 1),
                "count", String.valueOf(candidates.size()));

        String content = forms.uiText(null, "", request.content(), viewer, tokens);
        if (candidates.isEmpty())
            content = content + "\n" + forms.uiText(ui, "empty", "&8No hay otros jugadores conectados.", viewer,
                    tokens);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(null, "", request.title(), viewer, tokens))
                .content(content);
        List<Runnable> actions = new ArrayList<>();

        forms.addButton(builder, ui, "type-name",
                forms.uiText(ui, "type-name.text", "&6&lEscribir nombre\n&r&8Busca a un jugador por su nombre.",
                        viewer, tokens));
        actions.add(() -> request.onTypeName().accept(viewer));

        for (int i = start; i < end; i++) {
            PlayerRanking.Ranked candidate = candidates.get(i);
            String path = ENTRY_PATH.get(candidate.tier());
            Map<String, String> entryTokens = new HashMap<>(tokens);
            entryTokens.put("player", candidate.name());
            forms.addButton(builder, ui, path,
                    forms.uiText(ui, path + ".text", ENTRY_DEFAULT.get(candidate.tier()), viewer, entryTokens));
            UUID chosen = candidate.uuid();
            actions.add(() -> {
                Player target = Bukkit.getPlayer(chosen);
                if (target == null || !target.isOnline()) {
                    forms.host().message(viewer, "party-target-not-found");
                    open(viewer, request, safePage);
                    return;
                }
                request.onPick().accept(viewer, target);
            });
        }

        if (safePage > 0) {
            forms.addButton(builder, ui, "buttons.previous",
                    forms.uiText(ui, "buttons.previous.text", "&6&lPágina anterior", viewer, tokens));
            actions.add(() -> open(viewer, request, safePage - 1));
        }
        if (safePage < maxPage) {
            forms.addButton(builder, ui, "buttons.next",
                    forms.uiText(ui, "buttons.next.text", "&6&lPágina siguiente", viewer, tokens));
            actions.add(() -> open(viewer, request, safePage + 1));
        }
        forms.addButton(builder, ui, "buttons.back",
                forms.uiText(ui, "buttons.back.text", "&6&lVolver", viewer, tokens));
        actions.add(() -> request.onBack().accept(viewer));

        forms.show(viewer, builder, actions);
    }

    private int pageSize() {
        int configured = forms.host().plugin().getConfig().getInt("bedrock.player-picker.page-size", 6);
        return Math.max(3, Math.min(20, configured));
    }
}
