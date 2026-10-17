package com.mdvcraft.mdvsocial.bedrock.forms;

import com.mdvcraft.mdvsocial.bedrock.port.SocialPort;
import com.mdvcraft.mdvsocial.bedrock.port.SocialPort.PartyView;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Native Bedrock forms for the MMOCore adventure party. */
public final class PartyForms {

    private final BedrockFormSupport forms;
    private final SocialPort social;
    private final FriendsForms friends;

    public PartyForms(BedrockFormSupport forms, SocialPort social, FriendsForms friends) {
        this.forms = forms;
        this.social = social;
        this.friends = friends;
    }

    public void open(Player player) {
        YamlConfiguration ui = forms.ui("party");
        PartyView party = social.party(player);
        List<Object> pending = social.pendingRequests(player, SocialPort.PARTY_INVITE);
        Map<String, String> tokens = Map.of(
                "member_count", String.valueOf(party.memberCount()),
                "max_members", String.valueOf(party.maxMembers()),
                "request_count", String.valueOf(pending.size()));

        String contentPath = party.exists() ? "content-party" : "content-no-party";
        List<String> defaults = party.exists()
                ? List.of("&7Integrantes: &f{member_count}&7/&f{max_members}")
                : List.of("&7No perteneces a un Grupo de Aventura.", "&7Puedes crear uno o aceptar una invitación.");
        StringBuilder content = new StringBuilder(forms.uiLines(ui, contentPath, defaults, player, tokens));
        if (party.exists()) {
            for (String member : party.members()) {
                Map<String, String> memberTokens = new HashMap<>(tokens);
                memberTokens.put("member_name", member);
                content.append('\n')
                        .append(forms.uiText(ui, "member-line", "&7• &f{member_name}", player, memberTokens));
            }
        }

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(ui, "title", "&d&lGrupo de Aventura", player, tokens))
                .content(content.toString());
        List<Runnable> actions = new ArrayList<>();

        if (!party.exists() && ui.getBoolean("buttons.create.enabled", true)) {
            forms.addButton(builder, ui, "buttons.create",
                    forms.uiText(ui, "buttons.create.text", "&aCrear Grupo de Aventura", player, tokens));
            actions.add(() -> {
                forms.host().message(player, social.createParty(player) ? "party-auto-created" : "party-error");
                open(player);
            });
        }
        if (ui.getBoolean("buttons.invite.enabled", true)) {
            forms.addButton(builder, ui, "buttons.invite",
                    forms.uiText(ui, "buttons.invite.text", "&d&lInvitar jugador", player, tokens));
            actions.add(() -> openInviteForm(player));
        }
        if (ui.getBoolean("buttons.invite-friends.enabled", true)) {
            forms.addButton(builder, ui, "buttons.invite-friends",
                    forms.uiText(ui, "buttons.invite-friends.text", "&aInvitar desde Amigos", player, tokens));
            actions.add(() -> friends.openList(player, 0));
        }
        if (ui.getBoolean("buttons.requests.enabled", true)) {
            forms.addButton(builder, ui, "buttons.requests",
                    forms.uiText(ui, "buttons.requests.text", "&eInvitaciones ({request_count})", player, tokens));
            actions.add(() -> openRequests(player));
        }
        if (party.exists() && ui.getBoolean("buttons.leave.enabled", true)) {
            forms.addButton(builder, ui, "buttons.leave",
                    forms.uiText(ui, "buttons.leave.text", "&c&lSalir del grupo", player, tokens));
            actions.add(() -> openLeaveConfirm(player));
        }
        forms.addButton(builder, ui, "buttons.back",
                forms.uiText(ui, "buttons.back.text", "&6Volver", player, tokens));
        actions.add(() -> forms.host().openMenu(player, "menuamigos", 1, "main", 1, null, "", false));

        forms.show(player, builder, actions);
    }

    private void openLeaveConfirm(Player player) {
        YamlConfiguration ui = forms.ui("party");
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(ui, "leave-confirm.title", "&c&lSalir del Grupo", player, Map.of()))
                .content(forms.uiText(ui, "leave-confirm.content",
                        "&7¿Seguro que quieres abandonar tu Grupo de Aventura?", player, Map.of()));
        forms.addButton(builder, ui, "leave-confirm.confirm",
                forms.uiText(ui, "leave-confirm.confirm.text", "&cSí, salir del grupo", player, Map.of()));
        forms.addButton(builder, ui, "leave-confirm.back",
                forms.uiText(ui, "leave-confirm.back.text", "&6Volver", player, Map.of()));
        forms.show(player, builder, List.of(
                () -> {
                    social.leaveParty(player);
                    open(player);
                },
                () -> open(player)));
    }

    private void openInviteForm(Player player) {
        YamlConfiguration ui = forms.ui("party");
        long session = forms.begin(player);
        CustomForm.Builder builder = CustomForm.builder()
                .title(forms.uiText(ui, "invite-form.title", "&d&lInvitar al Grupo", player, Map.of()))
                .input(forms.uiText(ui, "invite-form.input-label", "&eNombre del jugador", player, Map.of()),
                        forms.strip(ui.getString("invite-form.input-placeholder", "Escribe el nombre...")),
                        "");
        builder.validResultHandler(response -> {
            String rawName = response.asInput(0);
            forms.run(player, session, () -> {
                Player target = forms.findOnlinePlayer(rawName);
                if (target == null) {
                    forms.host().message(player, "party-target-not-found");
                    openInviteForm(player);
                    return;
                }
                social.invitePlayerToParty(player, target);
                open(player);
            });
        });
        forms.send(player, builder.build());
    }

    private void openRequests(Player player) {
        YamlConfiguration ui = forms.ui("party");
        List<Object> requests = social.pendingRequests(player, SocialPort.PARTY_INVITE);
        Map<String, String> tokens = Map.of("request_count", String.valueOf(requests.size()));
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(ui, "requests-menu.title", "&e&lInvitaciones a Grupos", player, tokens))
                .content(requests.isEmpty()
                        ? forms.uiText(ui, "requests-menu.empty", "&7No tienes invitaciones pendientes.", player,
                                tokens)
                        : forms.uiText(ui, "requests-menu.content", "&7Invitaciones pendientes: &f{request_count}",
                                player, tokens));
        List<Runnable> actions = new ArrayList<>();
        for (Object request : requests) {
            String creator = social.requestCreatorName(request);
            Map<String, String> requestTokens = Map.of("creator", creator, "request_count",
                    String.valueOf(requests.size()));
            forms.addButton(builder, ui, "requests-menu.request",
                    forms.uiText(ui, "requests-menu.request.text", "&d{creator}", player, requestTokens));
            actions.add(() -> openRequestDetail(player, request));
        }
        forms.addButton(builder, ui, "requests-menu.back",
                forms.uiText(ui, "requests-menu.back", "&6Volver al Grupo", player, tokens));
        actions.add(() -> open(player));
        forms.show(player, builder, actions);
    }

    private void openRequestDetail(Player player, Object request) {
        YamlConfiguration ui = forms.ui("party");
        String creator = social.requestCreatorName(request);
        Map<String, String> tokens = Map.of("creator", creator);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.uiText(ui, "requests-menu.detail.title", "&d&lInvitación de {creator}", player, tokens))
                .content(forms.uiText(ui, "requests-menu.detail.content",
                        "&7¿Quieres unirte al Grupo de Aventura de &f{creator}&7?", player, tokens));
        forms.addButton(builder, ui, "requests-menu.detail.accept",
                forms.uiText(ui, "requests-menu.detail.accept", "&aAceptar invitación", player, tokens));
        forms.addButton(builder, ui, "requests-menu.detail.deny",
                forms.uiText(ui, "requests-menu.detail.deny", "&cRechazar invitación", player, tokens));
        forms.addButton(builder, ui, "requests-menu.detail.back",
                forms.uiText(ui, "requests-menu.detail.back", "&6Volver", player, tokens));
        forms.show(player, builder, List.of(
                () -> {
                    if (social.resolveRequest(player, request, true)) {
                        Bukkit.getScheduler().runTaskLater(forms.host().plugin(), () -> {
                            if (!player.isOnline())
                                return;
                            player.closeInventory();
                            social.syncPartyPermission(player);
                            open(player);
                        }, 2L);
                    } else {
                        openRequests(player);
                    }
                },
                () -> {
                    if (social.resolveRequest(player, request, false))
                        forms.host().message(player, "party-request-denied", Map.of("target", creator));
                    openRequests(player);
                },
                () -> openRequests(player)));
    }
}
