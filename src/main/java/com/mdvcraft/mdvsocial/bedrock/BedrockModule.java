package com.mdvcraft.mdvsocial.bedrock;

import com.mdvcraft.mdvsocial.bedrock.action.BedrockActionRegistry;
import com.mdvcraft.mdvsocial.bedrock.action.BedrockNavigation;
import com.mdvcraft.mdvsocial.bedrock.action.BuiltinBedrockActions;
import com.mdvcraft.mdvsocial.bedrock.forms.BedrockFormSupport;
import com.mdvcraft.mdvsocial.bedrock.forms.BlockedPlayersForms;
import com.mdvcraft.mdvsocial.bedrock.forms.FriendsForms;
import com.mdvcraft.mdvsocial.bedrock.forms.LinkMessages;
import com.mdvcraft.mdvsocial.bedrock.forms.MMOCoreForms;
import com.mdvcraft.mdvsocial.bedrock.forms.MailForms;
import com.mdvcraft.mdvsocial.bedrock.forms.PartyForms;
import com.mdvcraft.mdvsocial.bedrock.forms.PlayerPickerForms;
import com.mdvcraft.mdvsocial.bedrock.forms.SocialCounters;
import com.mdvcraft.mdvsocial.bedrock.forms.TitlesForms;
import com.mdvcraft.mdvsocial.bedrock.forms.TpaForms;
import com.mdvcraft.mdvsocial.bedrock.menu.BedrockMenuParser;
import com.mdvcraft.mdvsocial.bedrock.menu.BedrockMenuRenderer;
import com.mdvcraft.mdvsocial.bedrock.menu.BedrockMenuRepository;
import com.mdvcraft.mdvsocial.bedrock.platform.FloodgateGateway;
import com.mdvcraft.mdvsocial.bedrock.port.BedrockHost;
import com.mdvcraft.mdvsocial.bedrock.port.BlocksPort;
import com.mdvcraft.mdvsocial.bedrock.port.MailPort;
import com.mdvcraft.mdvsocial.bedrock.port.SocialPort;
import com.mdvcraft.mdvsocial.bedrock.port.TeleportPort;
import com.mdvcraft.mdvsocial.bedrock.port.TitlesPort;
import com.mdvcraft.mdvsocial.bedrock.session.BedrockUiSessionManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Entry point of the Bedrock (Floodgate/Cumulus) UI. The host plugin builds
 * one instance with its port implementations and only talks to this facade.
 */
public final class BedrockModule {

    private final BedrockHost host;
    private final FloodgateGateway gateway;
    private final BedrockUiSessionManager sessions;
    private final BedrockActionRegistry actions;
    private final BedrockMenuRepository repository;
    private final BedrockMenuRenderer renderer;
    private final BedrockFormSupport forms;
    private final TitlesForms titles;
    private final MailForms mail;
    private final FriendsForms friends;
    private final PartyForms party;
    private final PlayerPickerForms picker;
    private final MMOCoreForms mmocore;
    private final BlockedPlayersForms blocked;
    private final TpaForms tpa;
    private final LinkMessages links;

    public BedrockModule(BedrockHost host, TitlesPort titlesPort, MailPort mailPort, SocialPort socialPort,
            BlocksPort blocksPort, TeleportPort teleportPort) {
        this.host = host;
        this.gateway = new FloodgateGateway(host.plugin());
        this.sessions = new BedrockUiSessionManager(host.plugin(), gateway);
        this.actions = new BedrockActionRegistry(host);
        this.repository = new BedrockMenuRepository(host.plugin(), new BedrockMenuParser(actions::normalize));
        this.forms = new BedrockFormSupport(host, gateway, sessions, repository,
                new SocialCounters(host.plugin(), socialPort, mailPort, teleportPort));
        this.titles = new TitlesForms(forms, titlesPort);
        this.mail = new MailForms(forms, mailPort);
        this.picker = new PlayerPickerForms(forms, socialPort);
        this.friends = new FriendsForms(forms, socialPort, picker);
        this.party = new PartyForms(forms, socialPort, picker);
        this.mmocore = new MMOCoreForms(forms, party);
        this.blocked = new BlockedPlayersForms(forms, blocksPort);
        this.tpa = new TpaForms(forms, teleportPort, picker);
        this.links = new LinkMessages(forms);

        BedrockNavigation navigation = new BedrockNavigation(host);
        this.renderer = new BedrockMenuRenderer(repository, forms, sessions, actions, navigation, socialPort);
        BuiltinBedrockActions.registerAll(actions, new BuiltinBedrockActions.Dependencies(
                host, navigation, titlesPort, socialPort, titles, mail, friends, party, mmocore, blocked, tpa, links));
    }

    public void enable() {
        reload();
    }

    public void reload() {
        gateway.refresh();
        repository.reload();
        host.plugin().getLogger().info("Menus Bedrock cargados: " + repository.size()
                + (gateway.isAvailable() ? " (Floodgate detectado)" : " (Floodgate no detectado)"));
    }

    public void shutdown() {
        sessions.clearAll();
    }

    /** Forgets the form session of a player who left. */
    public void forget(Player player) {
        sessions.clear(player);
    }

    public boolean isBedrock(Player player) {
        return gateway.isBedrock(player);
    }

    /** Opens a MenusBedrock menu. @return false so the caller can fall back to Java. */
    public boolean openMenu(Player player, String menuId, int page, String previousMenu, int previousPage,
            UUID targetUuid, String targetName, boolean targetOnline) {
        return renderer.open(player, menuId, page, previousMenu, previousPage, targetUuid, targetName,
                targetOnline);
    }

    /** Raw YAML of a MenusBedrock menu, or null when missing. */
    public YamlConfiguration menuConfig(String menuId) {
        return repository.rawMenu(menuId);
    }

    public FloodgateGateway gateway() {
        return gateway;
    }

    public BedrockUiSessionManager sessions() {
        return sessions;
    }

    /** Register custom MenusBedrock actions here. */
    public BedrockActionRegistry actions() {
        return actions;
    }

    public BedrockFormSupport forms() {
        return forms;
    }

    public TitlesForms titles() {
        return titles;
    }

    public MailForms mail() {
        return mail;
    }

    public FriendsForms friends() {
        return friends;
    }

    public PartyForms party() {
        return party;
    }

    /** Reusable "Escoger jugador" form. */
    public PlayerPickerForms picker() {
        return picker;
    }

    public MMOCoreForms mmocore() {
        return mmocore;
    }

    public BlockedPlayersForms blocked() {
        return blocked;
    }

    /** "Viajes (TPA)" forms. */
    public TpaForms tpa() {
        return tpa;
    }

    public LinkMessages links() {
        return links;
    }
}
