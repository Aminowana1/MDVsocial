package com.mdvcraft.mdvsocial.bedrock.forms;

import com.mdvcraft.mdvsocial.bedrock.port.MailPort;
import com.mdvcraft.mdvsocial.bedrock.port.MailPort.MailView;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Native Bedrock forms for the mailbox: list, read, compose, reply and block. */
public final class MailForms {

    /** Pseudo menu id used to return to the native mailbox instead of a YAML menu. */
    public static final String RETURN_MAILBOX = "MAILBOX";

    private final BedrockFormSupport forms;
    private final MailPort mail;

    public MailForms(BedrockFormSupport forms, MailPort mail) {
        this.forms = forms;
        this.mail = mail;
    }

    public void openMailbox(Player player, int page) {
        if (!mail.enabled()) {
            forms.host().message(player, "mail-disabled");
            return;
        }
        if (!player.hasPermission("mdvsocial.mail.read")) {
            forms.host().message(player, "no-permission");
            return;
        }
        List<String> ids = mail.ids(player.getUniqueId());
        int perPage = forms.pageSize(player);
        int maxPage = Math.max(0, (int) Math.ceil(ids.size() / (double) perPage) - 1);
        int safePage = Math.max(0, Math.min(page, maxPage));
        int start = safePage * perPage;
        int end = Math.min(ids.size(), start + perPage);

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.color("&6&lBuzón") + " §8(" + (safePage + 1) + "/" + (maxPage + 1) + ")")
                .content(forms.color("&7Cartas: &f" + ids.size() + "&7/&f" + mail.mailboxLimit(player)
                        + (ids.isEmpty() ? "\n&8No tienes cartas guardadas." : "\n&7Toca una carta para leerla.")));
        List<Runnable> actions = new ArrayList<>();

        for (int i = start; i < end; i++) {
            String id = ids.get(i);
            MailView letter = mail.find(player.getUniqueId(), id);
            String sender = letter == null ? "Desconocido" : letter.fromName();
            String message = letter == null ? "" : letter.message();
            boolean read = letter != null && letter.read();
            String label = (read ? "&eCarta de &f" : "&aNueva carta de &f") + sender
                    + "\n&8" + mail.shorten(message, 38);
            builder.button(forms.color(label));
            actions.add(() -> openRead(player, id, safePage));
        }

        if (safePage > 0) {
            builder.button(forms.color("&ePágina anterior"));
            actions.add(() -> openMailbox(player, safePage - 1));
        }
        if (safePage < maxPage) {
            builder.button(forms.color("&ePágina siguiente"));
            actions.add(() -> openMailbox(player, safePage + 1));
        }
        builder.button(forms.color("&6Volver a Correo"));
        actions.add(() -> forms.host().openMenu(player, "correo", 1, "menuamigos", 1, null, "", false));

        forms.show(player, builder, actions);
    }

    public void openRead(Player player, String id, int page) {
        MailView letter = id == null || id.isBlank() ? null : mail.find(player.getUniqueId(), id);
        if (letter == null) {
            forms.host().message(player, "mail-not-found");
            openMailbox(player, page);
            return;
        }
        mail.markRead(player.getUniqueId(), id);

        String fromName = letter.fromName();
        String fromUuid = letter.fromUuid();
        boolean clanInvite = "MDVCLANS_INVITE".equalsIgnoreCase(letter.type());

        String content = forms.color("&7De: &f" + fromName
                + "\n&7Enviada: &e" + mail.formatTime(letter.sentAt())
                + "\n&7Expira: &c" + mail.daysLeft(letter.expiresAt())
                + "\n\n&f" + letter.message());
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.color((clanInvite ? "&d&lInvitación de clan" : "&6&lCarta") + " &8- &f" + fromName))
                .content(content);
        List<Runnable> actions = new ArrayList<>();

        if (clanInvite) {
            builder.button(forms.color("&aAceptar invitación"));
            actions.add(() -> mail.handleClanInvite(player, id, true, page));
            builder.button(forms.color("&cRechazar invitación"));
            actions.add(() -> mail.handleClanInvite(player, id, false, page));
        } else {
            builder.button(forms.color("&eResponder"));
            actions.add(() -> openReply(player, id, page));
            builder.button(forms.color("&cEliminar carta"));
            actions.add(() -> confirmDelete(player, id, page));
            if (fromUuid != null && !fromUuid.isBlank()) {
                builder.button(forms.color("&4Bloquear remitente"));
                actions.add(() -> {
                    mail.blockSender(player, fromUuid);
                    openMailbox(player, page);
                });
            }
        }
        builder.button(forms.color("&6Volver al buzón"));
        actions.add(() -> openMailbox(player, page));

        forms.show(player, builder, actions);
    }

    private void confirmDelete(Player player, String id, int page) {
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(forms.color("&c&lEliminar carta"))
                .content(forms.color("&7¿Seguro que quieres eliminar esta carta?"))
                .button(forms.color("&cSí, eliminar"))
                .button(forms.color("&aNo, volver"));
        forms.show(player, builder, List.of(
                () -> {
                    mail.delete(player, id);
                    openMailbox(player, page);
                },
                () -> openRead(player, id, page)));
    }

    public void openCompose(Player player, String returnMenu, int returnPage) {
        if (!mail.enabled()) {
            forms.host().message(player, "mail-disabled");
            return;
        }
        if (!player.hasPermission("mdvsocial.mail.send")) {
            forms.host().message(player, "no-permission");
            return;
        }
        long session = forms.begin(player);
        CustomForm.Builder builder = CustomForm.builder()
                .title(forms.color("&6&lEnviar carta"))
                .input(forms.color("&eDestinatario"), "Nombre del jugador", "")
                .input(forms.color("&eMensaje"), "Escribe tu carta...", "");
        builder.validResultHandler(response -> {
            String target = response.asInput(0);
            String message = response.asInput(1);
            forms.run(player, session, () -> {
                if (target == null || target.isBlank()) {
                    forms.host().message(player, "mail-player-not-found");
                    openCompose(player, returnMenu, returnPage);
                    return;
                }
                mail.sendByName(player, target.trim(), message == null ? "" : message.trim());
                returnFrom(player, returnMenu, returnPage);
            });
        });
        forms.send(player, builder.build());
    }

    public void openMessageToTarget(Player player, UUID targetUuid, String fallbackName,
            String returnMenu, int returnPage) {
        if (!mail.enabled()) {
            forms.host().message(player, "mail-disabled");
            return;
        }
        if (!player.hasPermission("mdvsocial.mail.send")) {
            forms.host().message(player, "no-permission");
            return;
        }
        if (targetUuid == null) {
            forms.host().message(player, "social-target-not-found");
            return;
        }
        if (targetUuid.equals(player.getUniqueId())) {
            forms.host().message(player, "mail-self");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        String targetName = target.getName() == null || target.getName().isBlank() ? fallbackName : target.getName();
        if (targetName == null || targetName.isBlank())
            targetName = "jugador";
        final String safeTargetName = targetName;

        long session = forms.begin(player);
        CustomForm.Builder builder = CustomForm.builder()
                .title(forms.color("&6&lCarta para &f" + safeTargetName))
                .input(forms.color("&eMensaje &7(máx. " + mail.maxLength() + ")"), "Escribe tu carta...", "");
        builder.validResultHandler(response -> {
            String message = response.asInput(0);
            forms.run(player, session, () -> {
                mail.sendByUuid(player, targetUuid, safeTargetName, message == null ? "" : message.trim());
                returnFrom(player, returnMenu, returnPage);
            });
        });
        forms.send(player, builder.build());
    }

    public void openBlockForm(Player player, boolean block, String returnMenu, int returnPage) {
        long session = forms.begin(player);
        CustomForm.Builder builder = CustomForm.builder()
                .title(forms.color(block ? "&c&lBloquear cartas" : "&a&lDesbloquear cartas"))
                .input(forms.color("&eJugador"), "Nombre del jugador", "");
        builder.validResultHandler(response -> {
            String target = response.asInput(0);
            forms.run(player, session, () -> {
                if (target == null || target.isBlank()) {
                    returnFrom(player, returnMenu, returnPage);
                    return;
                }
                if (block)
                    mail.blockByName(player, target.trim());
                else
                    mail.unblockByName(player, target.trim());
                returnFrom(player, returnMenu, returnPage);
            });
        });
        forms.send(player, builder.build());
    }

    public void openReply(Player player, String mailId, int returnPage) {
        MailView letter = mailId == null || mailId.isBlank() ? null : mail.find(player.getUniqueId(), mailId);
        if (letter == null) {
            forms.host().message(player, "mail-not-found");
            openMailbox(player, returnPage);
            return;
        }
        String fromUuidText = letter.fromUuid();
        if (fromUuidText == null || fromUuidText.isBlank()) {
            forms.host().message(player, "mail-cannot-reply-server");
            openRead(player, mailId, returnPage);
            return;
        }
        try {
            UUID targetUuid = UUID.fromString(fromUuidText);
            openMessageToTarget(player, targetUuid, letter.fromName(), RETURN_MAILBOX, returnPage);
        } catch (Exception ignored) {
            forms.host().message(player, "mail-cannot-reply-server");
            openRead(player, mailId, returnPage);
        }
    }

    private void returnFrom(Player player, String returnMenu, int returnPage) {
        if (player == null || !player.isOnline())
            return;
        if (returnMenu != null && returnMenu.equalsIgnoreCase(RETURN_MAILBOX)) {
            openMailbox(player, Math.max(0, returnPage));
            return;
        }
        String menu = returnMenu == null || returnMenu.isBlank() ? "correo" : returnMenu;
        forms.host().openMenu(player, menu, Math.max(1, returnPage), "", 1, null, "", false);
    }
}
