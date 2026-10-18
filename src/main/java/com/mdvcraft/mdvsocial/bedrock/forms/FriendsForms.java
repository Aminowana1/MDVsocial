package com.mdvcraft.mdvsocial.bedrock.forms;

import com.mdvcraft.mdvsocial.bedrock.forms.PlayerPickerForms.FriendsMode;
import com.mdvcraft.mdvsocial.bedrock.menu.model.BedrockMenuContext;
import com.mdvcraft.mdvsocial.bedrock.port.SocialPort;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Native Bedrock forms for friends: list, add, requests, removal and private messages. */
public final class FriendsForms {

    private final BedrockFormSupport forms;
    private final SocialPort social;
    private final PlayerPickerForms picker;

    public FriendsForms(BedrockFormSupport forms, SocialPort social, PlayerPickerForms picker) {
        this.forms = forms;
        this.social = social;
        this.picker = picker;
    }

    public void openList(Player player, int page) {
        YamlConfiguration ui = forms.ui("amigos_lista");
        List<UUID> friends = new ArrayList<>(social.friends(player));
        friends.sort(Comparator.comparing(uuid -> {
            OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
            return off.getName() == null ? uuid.toString() : off.getName().toLowerCase(Locale.ROOT);
        }));
        List<Object> pending = social.pendingRequests(player, SocialPort.FRIEND_REQUEST);
        int perPage = forms.pageSize(player);
        int maxPage = Math.max(0, (int) Math.ceil(friends.size() / (double) perPage) - 1);
        int safePage = Math.max(0, Math.min(page, maxPage));
        int start = safePage * perPage;
        int end = Math.min(friends.size(), start + perPage);
        Map<String, String> tokens = Map.of(
                "friend_count", String.valueOf(friends.size()),
                "request_count", String.valueOf(pending.size()),
                "page", String.valueOf(safePage + 1),
                "max_page", String.valueOf(maxPage + 1));

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(ui, "title", "&a&lAmigos y Solicitudes", player, tokens)
                        + forms.color(" &8(" + (safePage + 1) + "/" + (maxPage + 1) + ")"))
                .content(forms.uiLines(ui, "content",
                        List.of("&7Amigos: &f{friend_count}", "&7Solicitudes pendientes: &e{request_count}"),
                        player, tokens));
        List<Runnable> actions = new ArrayList<>();

        for (int i = start; i < end; i++) {
            UUID uuid = friends.get(i);
            Player online = Bukkit.getPlayer(uuid);
            OfflinePlayer off = online != null ? online : Bukkit.getOfflinePlayer(uuid);
            String name = off.getName() == null ? uuid.toString().substring(0, 8) : off.getName();
            boolean isOnline = online != null;
            String path = isOnline ? "friend.online" : "friend.offline";
            Map<String, String> friendTokens = new HashMap<>(tokens);
            friendTokens.put("friend_name", name);
            String text = forms.uiText(ui, path + ".text",
                    (isOnline ? "&a● &f" : "&8● &7") + "{friend_name}", player, friendTokens);
            forms.addButton(builder, ui, path, text);
            actions.add(() -> forms.host().openMenu(player, "amigo_opciones", 1, "menuamigos", 1,
                    uuid, name, isOnline));
        }

        if (ui.getBoolean("buttons.add.enabled", true)) {
            forms.addButton(builder, ui, "buttons.add",
                    forms.uiText(ui, "buttons.add.text", "&a&lAñadir amigo", player, tokens));
            actions.add(() -> openAddPicker(player, safePage));
        }
        if (ui.getBoolean("buttons.requests.enabled", true)) {
            forms.addButton(builder, ui, "buttons.requests",
                    forms.uiText(ui, "buttons.requests.text", "&eSolicitudes ({request_count})", player, tokens));
            actions.add(() -> openRequests(player, safePage));
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

    /** "Escoger jugador" for friend requests; current friends are not offered. */
    private void openAddPicker(Player player, int returnPage) {
        YamlConfiguration ui = forms.ui("amigos_lista");
        picker.open(player, new PlayerPickerForms.Request(
                ui.getString("add-picker.title", "&a&lAñadir amigo &8({page}/{max_page})"),
                ui.getString("add-picker.content", "&7Elige un jugador conectado o escribe su nombre."),
                FriendsMode.EXCLUDE,
                Set.of(),
                (viewer, target) -> {
                    social.inviteFriend(viewer, target.getUniqueId(), target.getName());
                    openList(viewer, returnPage);
                },
                viewer -> openAddForm(viewer, returnPage),
                viewer -> openList(viewer, returnPage)), 0);
    }

    private void openAddForm(Player player, int returnPage) {
        YamlConfiguration ui = forms.ui("amigos_lista");
        long session = forms.begin(player);
        CustomForm.Builder builder = CustomForm.builder()
                .title(forms.uiText(ui, "add-form.title", "&a&lAñadir amigo", player, Map.of()))
                .input(forms.uiText(ui, "add-form.input-label", "&eNombre del jugador", player, Map.of()),
                        forms.strip(ui.getString("add-form.input-placeholder", "Escribe el nombre...")), "");
        builder.validResultHandler(response -> {
            String rawName = response.asInput(0);
            forms.run(player, session, () -> {
                if (rawName == null || rawName.isBlank()) {
                    openAddPicker(player, returnPage);
                    return;
                }
                Player target = forms.findOnlinePlayer(rawName);
                if (target == null) {
                    forms.host().message(player, "friend-target-not-found");
                    openAddForm(player, returnPage);
                    return;
                }
                social.inviteFriend(player, target.getUniqueId(), target.getName());
                openList(player, returnPage);
            });
        });
        forms.send(player, builder.build());
    }

    private void openRequests(Player player, int returnPage) {
        YamlConfiguration ui = forms.ui("amigos_lista");
        List<Object> requests = social.pendingRequests(player, SocialPort.FRIEND_REQUEST);
        Map<String, String> tokens = Map.of("request_count", String.valueOf(requests.size()));
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(ui, "requests-menu.title", "&e&lSolicitudes de Amistad", player, tokens))
                .content(requests.isEmpty()
                        ? forms.uiText(ui, "requests-menu.empty", "&7No tienes solicitudes pendientes.", player,
                                tokens)
                        : forms.uiText(ui, "requests-menu.content", "&7Solicitudes pendientes: &f{request_count}",
                                player, tokens));
        List<Runnable> actions = new ArrayList<>();
        for (Object request : requests) {
            String creator = social.requestCreatorName(request);
            Map<String, String> requestTokens = Map.of("creator", creator, "request_count",
                    String.valueOf(requests.size()));
            forms.addButton(builder, ui, "requests-menu.request",
                    forms.uiText(ui, "requests-menu.request.text", "&e{creator}", player, requestTokens));
            actions.add(() -> openRequestDetail(player, request, returnPage));
        }
        forms.addButton(builder, ui, "requests-menu.back",
                forms.uiText(ui, "requests-menu.back", "&6Volver a Amigos", player, tokens));
        actions.add(() -> openList(player, returnPage));
        forms.show(player, builder, actions);
    }

    private void openRequestDetail(Player player, Object request, int returnPage) {
        YamlConfiguration ui = forms.ui("amigos_lista");
        String creator = social.requestCreatorName(request);
        Map<String, String> tokens = Map.of("creator", creator);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(ui, "requests-menu.detail.title", "&e&lSolicitud de {creator}", player, tokens))
                .content(forms.uiText(ui, "requests-menu.detail.content",
                        "&7¿Quieres aceptar la solicitud de amistad de &f{creator}&7?", player, tokens));
        forms.addButton(builder, ui, "requests-menu.detail.accept",
                forms.uiText(ui, "requests-menu.detail.accept", "&aAceptar solicitud", player, tokens));
        forms.addButton(builder, ui, "requests-menu.detail.deny",
                forms.uiText(ui, "requests-menu.detail.deny", "&cRechazar solicitud", player, tokens));
        forms.addButton(builder, ui, "requests-menu.detail.back",
                forms.uiText(ui, "requests-menu.detail.back", "&6Volver", player, tokens));
        forms.show(player, builder, List.of(
                () -> {
                    social.resolveRequest(player, request, true);
                    openList(player, returnPage);
                },
                () -> {
                    social.resolveRequest(player, request, false);
                    openRequests(player, returnPage);
                },
                () -> openRequests(player, returnPage)));
    }

    public void openRemoveConfirm(Player player, UUID targetUuid, String targetName, boolean targetOnline) {
        if (targetUuid == null) {
            forms.host().message(player, "social-target-not-found");
            return;
        }
        YamlConfiguration ui = forms.ui("amigo_opciones");
        String name = targetName == null || targetName.isBlank()
                ? Bukkit.getOfflinePlayer(targetUuid).getName()
                : targetName;
        if (name == null || name.isBlank())
            name = targetUuid.toString().substring(0, 8);
        Map<String, String> tokens = Map.of("target", name);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(ui, "remove-friend-confirm.title", "&c&lEliminar a {target}", player, tokens))
                .content(forms.uiText(ui, "remove-friend-confirm.content",
                        "&7¿Seguro que quieres eliminar a &f{target} &7de tus amigos?", player, tokens));
        forms.addButton(builder, ui, "remove-friend-confirm.confirm",
                forms.uiText(ui, "remove-friend-confirm.confirm.text", "&cEliminar amigo", player, tokens));
        forms.addButton(builder, ui, "remove-friend-confirm.back",
                forms.uiText(ui, "remove-friend-confirm.back.text", "&6Volver", player, tokens));
        String finalName = name;
        forms.show(player, builder, List.of(
                () -> {
                    social.removeFriend(player, targetUuid, finalName);
                    openList(player, 0);
                },
                () -> forms.host().openMenu(player, "amigo_opciones", 1, "menuamigos", 1,
                        targetUuid, finalName, targetOnline)));
    }

    public void openPrivateMessage(Player player, String targetName, BedrockMenuContext context) {
        String safeName = targetName == null ? "" : targetName.trim();
        if (safeName.isBlank()) {
            forms.host().message(player, "social-target-not-found");
            return;
        }
        long session = forms.begin(player);
        CustomForm.Builder builder = CustomForm.builder()
                .title(forms.color("&b&lMensaje para &f" + safeName))
                .input(forms.color("&eMensaje"), "Escribe tu mensaje privado...", "");
        builder.validResultHandler(response -> {
            String message = response.asInput(0);
            forms.run(player, session, () -> {
                if (message == null || message.isBlank()) {
                    forms.host().openMenu(player, context.menuId(), context.page(), context.previousMenu(),
                            context.previousPage(), context.targetUuid(), context.targetName(),
                            context.targetOnline());
                    return;
                }
                Bukkit.dispatchCommand(player, "msg " + safeName + " " + message.trim());
            });
        });
        forms.send(player, builder.build());
    }
}
