package com.mdvcraft.mdvsocial.bedrock.forms;

import com.mdvcraft.mdvsocial.bedrock.port.SocialPort;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Reusable "Escoger jugador" form: a "type a name" button followed by a paged
 * list of online players, friends first and labelled as such. Texts live in
 * MenusBedrock/escoger_jugador.yml; each caller sets title, content and what
 * happens on pick.
 */
public final class PlayerPickerForms {

    /**
     * @param title          form title; may use {page}, {max_page} and {count}
     * @param content        text above the buttons
     * @param friendsMode    whether friends are listed first, hidden or treated like anyone else
     * @param excludedNames  lower-case names never offered (e.g. current party members)
     * @param onPick         called with (viewer, chosen online player)
     * @param onTypeName     called when the viewer prefers to type the name
     * @param onBack         called by the back button
     */
    public record Request(String title, String content, FriendsMode friendsMode, Set<String> excludedNames,
            BiConsumer<Player, Player> onPick, Consumer<Player> onTypeName, Consumer<Player> onBack) {

        public Request {
            excludedNames = excludedNames == null ? Set.of() : Set.copyOf(excludedNames);
        }
    }

    public enum FriendsMode {
        /** Friends first, with the friend label. */
        FIRST,
        /** Friends are not offered at all (e.g. "Añadir amigo"). */
        EXCLUDE
    }

    private final BedrockFormSupport forms;
    private final SocialPort social;

    public PlayerPickerForms(BedrockFormSupport forms, SocialPort social) {
        this.forms = forms;
        this.social = social;
    }

    public void open(Player viewer, Request request, int page) {
        YamlConfiguration ui = forms.ui("escoger_jugador");
        Set<UUID> friends = new HashSet<>(social.friends(viewer));
        List<Candidate> candidates = candidates(viewer, request, friends);

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
            Candidate candidate = candidates.get(i);
            String path = candidate.friend() ? "entry-friend" : "entry";
            Map<String, String> entryTokens = new HashMap<>(tokens);
            entryTokens.put("player", candidate.player().getName());
            String def = candidate.friend() ? "&2&l{player}\n&r&2Amigo" : "&0&l{player}\n&r&8Conectado";
            forms.addButton(builder, ui, path, forms.uiText(ui, path + ".text", def, viewer, entryTokens));
            UUID chosen = candidate.player().getUniqueId();
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

    /** Online players the viewer can see, friends first (or hidden), then alphabetically. */
    private List<Candidate> candidates(Player viewer, Request request, Set<UUID> friends) {
        List<Candidate> out = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(viewer) || !viewer.canSee(online)
                    || request.excludedNames().contains(online.getName().toLowerCase(Locale.ROOT)))
                continue;
            boolean friend = friends.contains(online.getUniqueId());
            if (friend && request.friendsMode() == FriendsMode.EXCLUDE)
                continue;
            out.add(new Candidate(online, friend));
        }
        out.sort(ORDER);
        return out;
    }

    static final Comparator<Candidate> ORDER = Comparator.comparing((Candidate c) -> !c.friend())
            .thenComparing(c -> c.player().getName(), String.CASE_INSENSITIVE_ORDER);

    private int pageSize() {
        int configured = forms.host().plugin().getConfig().getInt("bedrock.player-picker.page-size", 6);
        return Math.max(3, Math.min(20, configured));
    }

    record Candidate(Player player, boolean friend) {
    }
}
