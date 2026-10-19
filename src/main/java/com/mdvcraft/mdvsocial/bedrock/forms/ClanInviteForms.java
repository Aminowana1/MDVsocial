package com.mdvcraft.mdvsocial.bedrock.forms;

import com.mdvcraft.mdvsocial.bedrock.port.ClanPort;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.CustomForm;

import java.util.Map;
import java.util.function.Consumer;

/**
 * "Invitar al clan" for Bedrock (MenusBedrock/clan_invitar.yml). MDVClans opens it
 * from its members form through MDVSocialAPI#openClanInvitePicker. Players already
 * in any clan are not offered: MDVClans would refuse them. After inviting, no form
 * is reopened so MDVClans' answer stays visible in the chat.
 */
public final class ClanInviteForms {

    private static final String MENU = "clan_invitar";

    private final BedrockFormSupport forms;
    private final ClanPort clans;
    private final PlayerPickerForms picker;

    public ClanInviteForms(BedrockFormSupport forms, ClanPort clans, PlayerPickerForms picker) {
        this.forms = forms;
        this.clans = clans;
        this.picker = picker;
    }

    /** @param onBack where "Volver" goes (MDVClans' members form) */
    public void open(Player player, Consumer<Player> onBack) {
        YamlConfiguration ui = forms.ui(MENU);
        picker.open(player, new PlayerPickerForms.Request(
                ui.getString("picker.title", "&3&lInvitar al clan &8({page}/{max_page})"),
                ui.getString("picker.content", "&7Elige a quién invitar o escribe su nombre."),
                clans::isInClan,
                (viewer, target) -> clans.invite(viewer, target.getName()),
                viewer -> openNameForm(viewer, onBack),
                onBack), 0);
    }

    /** Also for players who are offline: MDVClans keeps the invite and sends it by letter. */
    private void openNameForm(Player player, Consumer<Player> onBack) {
        YamlConfiguration ui = forms.ui(MENU);
        long session = forms.begin(player);
        CustomForm.Builder builder = CustomForm.builder()
                .title(forms.uiText(ui, "name-form.title", "&3&lInvitar al clan", player, Map.of()))
                .input(forms.uiText(ui, "name-form.input-label", "&eNombre del jugador", player, Map.of()),
                        forms.strip(ui.getString("name-form.input-placeholder", "Escribe el nombre...")), "");
        builder.validResultHandler(response -> {
            String name = response.asInput(0);
            forms.run(player, session, () -> {
                if (name == null || name.isBlank())
                    open(player, onBack);
                else
                    clans.invite(player, name.trim());
            });
        });
        forms.send(player, builder.build());
    }
}
