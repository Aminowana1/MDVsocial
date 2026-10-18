package com.mdvcraft.mdvsocial.bedrock.forms;

import com.mdvcraft.mdvsocial.bedrock.forms.PlayerPickerForms.FriendsMode;
import com.mdvcraft.mdvsocial.bedrock.port.TeleportPort;
import com.mdvcraft.mdvsocial.bedrock.port.TeleportPort.RequestView;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Native Bedrock forms for teleport requests (MenusBedrock/tpa.yml): ask to
 * travel to a player, answer received requests and cancel the one sent.
 */
public final class TpaForms {

    private static final String MENU = "tpa";

    private final BedrockFormSupport forms;
    private final TeleportPort teleport;
    private final PlayerPickerForms picker;

    public TpaForms(BedrockFormSupport forms, TeleportPort teleport, PlayerPickerForms picker) {
        this.forms = forms;
        this.teleport = teleport;
        this.picker = picker;
    }

    public void open(Player player) {
        if (!teleport.checkAccess(player))
            return;
        YamlConfiguration ui = forms.ui(MENU);
        List<RequestView> pending = teleport.pending(player);
        String outgoing = teleport.outgoingTarget(player);
        Map<String, String> tokens = Map.of(
                "request_count", String.valueOf(pending.size()),
                "target", outgoing == null ? "" : outgoing);

        String content = forms.uiLines(ui, "content",
                List.of("&7Pide viajar hasta otro jugador o responde a quien quiere venir.",
                        "&7Solicitudes recibidas: &e{request_count}"), player, tokens);
        if (outgoing != null)
            content += "\n" + forms.uiText(ui, "outgoing-line", "&7Solicitud enviada a &f{target}", player, tokens);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(ui, "title", "&a&lViajes (TPA)", player, tokens))
                .content(content);
        List<Runnable> actions = new ArrayList<>();

        forms.addButton(builder, ui, "buttons.request",
                forms.uiText(ui, "buttons.request.text", "&a&lSolicitar viaje\n&8Elige o escribe un jugador.", player,
                        tokens));
        actions.add(() -> openPicker(player, 0));
        forms.addButton(builder, ui, "buttons.requests",
                forms.uiText(ui, "buttons.requests.text", "&e&lSolicitudes ({request_count})", player, tokens));
        actions.add(() -> openRequests(player));
        if (outgoing != null) {
            forms.addButton(builder, ui, "buttons.cancel",
                    forms.uiText(ui, "buttons.cancel.text", "&c&lCancelar mi solicitud\n&8Enviada a {target}.", player,
                            tokens));
            actions.add(() -> {
                teleport.cancelOutgoing(player);
                open(player);
            });
        }
        forms.addButton(builder, ui, "buttons.back", forms.uiText(ui, "buttons.back.text", "&6Volver", player, tokens));
        actions.add(() -> forms.host().openMenu(player, "menuamigos", 1, "main", 1, null, "", false));

        forms.show(player, builder, actions);
    }

    /** Confirmation form for one received request (used by quick accept). */
    public void openRequest(Player player, UUID sender) {
        for (RequestView request : teleport.pending(player))
            if (request.sender().equals(sender)) {
                openRequestDetail(player, request, false);
                return;
            }
    }

    private void openPicker(Player player, int page) {
        YamlConfiguration ui = forms.ui(MENU);
        picker.open(player, new PlayerPickerForms.Request(
                ui.getString("picker.title", "&a&lSolicitar viaje &8({page}/{max_page})"),
                ui.getString("picker.content", "&7¿Hasta quién quieres viajar? Tus amigos aparecen primero."),
                FriendsMode.FIRST,
                Set.of(),
                (viewer, target) -> {
                    teleport.request(viewer, target);
                    open(viewer);
                },
                this::openNameForm,
                this::open), page);
    }

    private void openNameForm(Player player) {
        YamlConfiguration ui = forms.ui(MENU);
        long session = forms.begin(player);
        CustomForm.Builder builder = CustomForm.builder()
                .title(forms.uiText(ui, "name-form.title", "&a&lSolicitar viaje", player, Map.of()))
                .input(forms.uiText(ui, "name-form.input-label", "&eNombre del jugador", player, Map.of()),
                        forms.strip(ui.getString("name-form.input-placeholder", "Escribe el nombre...")), "");
        builder.validResultHandler(response -> {
            String rawName = response.asInput(0);
            forms.run(player, session, () -> {
                if (rawName == null || rawName.isBlank()) {
                    openPicker(player, 0);
                    return;
                }
                teleport.request(player, forms.findOnlinePlayer(rawName.trim()));
                open(player);
            });
        });
        forms.send(player, builder.build());
    }

    private void openRequests(Player player) {
        YamlConfiguration ui = forms.ui(MENU);
        List<RequestView> requests = teleport.pending(player);
        Map<String, String> tokens = Map.of("request_count", String.valueOf(requests.size()));
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(ui, "requests-menu.title", "&e&lSolicitudes de viaje", player, tokens))
                .content(requests.isEmpty()
                        ? forms.uiText(ui, "requests-menu.empty", "&7No tienes solicitudes pendientes.", player, tokens)
                        : forms.uiText(ui, "requests-menu.content", "&7Quieren viajar hasta ti: &f{request_count}",
                                player, tokens));
        List<Runnable> actions = new ArrayList<>();
        for (RequestView request : requests) {
            Map<String, String> requestTokens = requestTokens(request);
            forms.addButton(builder, ui, "requests-menu.request",
                    forms.uiText(ui, "requests-menu.request.text", "&a{player}\n&8Expira en {seconds}s", player,
                            requestTokens));
            actions.add(() -> openRequestDetail(player, request, true));
        }
        forms.addButton(builder, ui, "requests-menu.back",
                forms.uiText(ui, "requests-menu.back", "&6Volver", player, tokens));
        actions.add(() -> open(player));
        forms.show(player, builder, actions);
    }

    /** @param fromList whether "back" returns to the request list (false: "later" just closes). */
    private void openRequestDetail(Player player, RequestView request, boolean fromList) {
        YamlConfiguration ui = forms.ui(MENU);
        Map<String, String> tokens = requestTokens(request);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(ui, "requests-menu.detail.title", "&a&lViaje de {player}", player, tokens))
                .content(forms.uiText(ui, "requests-menu.detail.content",
                        "&f{player} &7quiere teletransportarse hasta ti.", player, tokens));
        forms.addButton(builder, ui, "requests-menu.detail.accept",
                forms.uiText(ui, "requests-menu.detail.accept", "&aAceptar", player, tokens));
        forms.addButton(builder, ui, "requests-menu.detail.deny",
                forms.uiText(ui, "requests-menu.detail.deny", "&cRechazar", player, tokens));
        forms.addButton(builder, ui, "requests-menu.detail.back",
                forms.uiText(ui, fromList ? "requests-menu.detail.back" : "requests-menu.detail.later",
                        fromList ? "&6Volver" : "&7Más tarde", player, tokens));
        Runnable afterwards = fromList ? () -> openRequests(player) : () -> { };
        forms.show(player, builder, List.of(
                () -> {
                    if (!teleport.accept(player, request.sender()))
                        afterwards.run();
                },
                () -> {
                    teleport.deny(player, request.sender());
                    afterwards.run();
                },
                afterwards));
    }

    private static Map<String, String> requestTokens(RequestView request) {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("player", request.senderName());
        tokens.put("seconds", String.valueOf(request.remainingSeconds()));
        return tokens;
    }
}
