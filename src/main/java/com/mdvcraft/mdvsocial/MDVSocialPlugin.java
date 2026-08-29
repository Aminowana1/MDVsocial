package com.mdvcraft.mdvsocial;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.economy.Economy;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class MDVSocialPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final Map<String, TitleDef> titles = new HashMap<>();
    private final Map<String, RankDef> ranks = new HashMap<>();
    private final Map<String, CustomMenuDef> customMenus = new HashMap<>();
    private final List<ExternalGuiAction> externalGuiActions = new ArrayList<>();
    private final List<Integer> listSlots = new ArrayList<>();
    private final Map<UUID, MailComposeSession> mailSessions = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> scoreboardPartyAttachments = new ConcurrentHashMap<>();
    private final Map<UUID, ChatProfileSnapshot> interactiveChatProfiles = new ConcurrentHashMap<>();
    private final Map<UUID, Long> bedrockUiLastAction = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bedrockUiActionGeneration = new ConcurrentHashMap<>();
    private final Map<UUID, Long> bedrockUiSession = new ConcurrentHashMap<>();
    private final LegacyComponentSerializer legacyAmpersand = LegacyComponentSerializer.legacyAmpersand();
    private BukkitTask interactiveChatProfileTask;
    private volatile boolean interactiveChatEnabled;
    private volatile List<String> interactiveHoverTemplate = List.of();

    private File dataFile;
    private PlayerDataStore data;
    private File mailFile;
    private YamlConfiguration mailData;
    private Economy economy;
    private SocialMenuItemManager socialMenuItemManager;
    private PlayerHomesMenuManager playerHomesMenuManager;
    private MMOItemsBrowserManager mmoItemsBrowserManager;
    private BedrockMenuManager bedrockMenuManager;

    private org.bukkit.NamespacedKey keyAction;
    private org.bukkit.NamespacedKey keyTitle;
    private org.bukkit.NamespacedKey keyMenu;
    private org.bukkit.NamespacedKey keyTargetMenu;
    private org.bukkit.NamespacedKey keyCommands;
    private org.bukkit.NamespacedKey keySound;
    private org.bukkit.NamespacedKey keyCloseOnClick;
    private org.bukkit.NamespacedKey keyConditionPlaceholder;
    private org.bukkit.NamespacedKey keyConditionEquals;
    private org.bukkit.NamespacedKey keyTrueMenu;
    private org.bukkit.NamespacedKey keyFalseMenu;
    private org.bukkit.NamespacedKey keyClansMenu;
    private org.bukkit.NamespacedKey keyMailId;
    private org.bukkit.NamespacedKey keyMailSender;
    private org.bukkit.NamespacedKey keyFriendTargetUuid;
    private org.bukkit.NamespacedKey keyFriendTargetName;
    private org.bukkit.NamespacedKey keyFriendTargetOnline;
    private org.bukkit.NamespacedKey keyRequiredPermission;
    private org.bukkit.NamespacedKey keyRightAction;
    private org.bukkit.NamespacedKey keyRightCommands;

    @Override
    public void onEnable() {
        keyAction = new org.bukkit.NamespacedKey(this, "action");
        keyTitle = new org.bukkit.NamespacedKey(this, "title_id");
        keyMenu = new org.bukkit.NamespacedKey(this, "menu");
        keyTargetMenu = new org.bukkit.NamespacedKey(this, "target_menu");
        keyCommands = new org.bukkit.NamespacedKey(this, "commands");
        keySound = new org.bukkit.NamespacedKey(this, "sound");
        keyCloseOnClick = new org.bukkit.NamespacedKey(this, "close_on_click");
        keyConditionPlaceholder = new org.bukkit.NamespacedKey(this, "condition_placeholder");
        keyConditionEquals = new org.bukkit.NamespacedKey(this, "condition_equals");
        keyTrueMenu = new org.bukkit.NamespacedKey(this, "true_menu");
        keyFalseMenu = new org.bukkit.NamespacedKey(this, "false_menu");
        keyClansMenu = new org.bukkit.NamespacedKey(this, "clans_menu");
        keyMailId = new org.bukkit.NamespacedKey(this, "mail_id");
        keyMailSender = new org.bukkit.NamespacedKey(this, "mail_sender");
        keyFriendTargetUuid = new org.bukkit.NamespacedKey(this, "friend_target_uuid");
        keyFriendTargetName = new org.bukkit.NamespacedKey(this, "friend_target_name");
        keyFriendTargetOnline = new org.bukkit.NamespacedKey(this, "friend_target_online");
        keyRequiredPermission = new org.bukkit.NamespacedKey(this, "required_permission");
        keyRightAction = new org.bukkit.NamespacedKey(this, "right_action");
        keyRightCommands = new org.bukkit.NamespacedKey(this, "right_commands");

        saveDefaultConfig();
        loadAll();
        bedrockMenuManager = new BedrockMenuManager(this);
        bedrockMenuManager.enable();
        setupEconomy();

        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("social").setExecutor(this);
        getCommand("social").setTabCompleter(this);
        getCommand("titulos").setExecutor(this);
        getCommand("correo").setExecutor(this);
        getCommand("correo").setTabCompleter(this);
        getCommand("carta").setExecutor(this);
        getCommand("carta").setTabCompleter(this);
        getCommand("mdvsocial").setExecutor(this);
        getCommand("mdvsocial").setTabCompleter(this);
        getCommand("mdvadmin").setExecutor(this);
        getCommand("mdvitems").setExecutor(this);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new MDVSocialExpansion(this).register();
            getLogger().info("PlaceholderAPI detectado. Placeholders registrados.");
        }

        long cleanupMinutes = Math.max(5L, getConfig().getLong("mail.cleanup-interval-minutes", 30L));
        Bukkit.getScheduler().runTaskTimer(this, this::cleanupExpiredMail, 20L * 60L, cleanupMinutes * 60L * 20L);

        long titleValidationTicks = Math.max(20L,
                getConfig().getLong("settings.active-title-validation.interval-ticks", 100L));
        Bukkit.getScheduler().runTaskTimer(this, this::validateAllOnlineTitles, 40L, titleValidationTicks);

        if (getConfig().getBoolean("scoreboard-party-permission.enabled", true)) {
            long interval = Math.max(10L, getConfig().getLong("scoreboard-party-permission.sync-interval-ticks", 20L));
            Bukkit.getScheduler().runTaskTimer(this, this::syncAllScoreboardPartyPermissions, 20L, interval);
            Bukkit.getScheduler().runTaskLater(this, this::resetAllScoreboardPartyPermissions, 5L);
        }

        socialMenuItemManager = new SocialMenuItemManager(this);
        socialMenuItemManager.enable();
        playerHomesMenuManager = new PlayerHomesMenuManager(this);
        playerHomesMenuManager.enable();
        mmoItemsBrowserManager = new MMOItemsBrowserManager(this);
        mmoItemsBrowserManager.enable();
        startInteractiveChatProfileTask();

        getLogger().info("MDVSocial 1.6.3 habilitado. Bedrock social/party móvil + títulos/rangos editables.");
    }

    @Override
    public void onDisable() {
        if (interactiveChatProfileTask != null) {
            interactiveChatProfileTask.cancel();
            interactiveChatProfileTask = null;
        }
        interactiveChatProfiles.clear();
        if (socialMenuItemManager != null) {
            socialMenuItemManager.disable();
        }
        if (playerHomesMenuManager != null) {
            playerHomesMenuManager.disable();
        }
        resetAllScoreboardPartyPermissions();
        for (PermissionAttachment attachment : scoreboardPartyAttachments.values()) {
            try {
                attachment.remove();
            } catch (Throwable ignored) {
            }
        }
        scoreboardPartyAttachments.clear();
        saveData();
        saveMailData();
        if (data != null) {
            try {
                data.close();
            } catch (Exception e) {
                getLogger().warning("No se pudo cerrar player-data.db limpiamente: " + e.getMessage());
            }
        }
    }

    private void loadAll() {
        reloadConfig();
        loadData();
        loadMailData();
        cleanupExpiredMail();
        loadTitles();
        loadRanks();
        loadListSlots();
        ensureDefaultMenus();
        loadCustomMenus();
        loadExternalGuiActions();
        if (bedrockMenuManager != null)
            bedrockMenuManager.reload();
        if (mmoItemsBrowserManager != null)
            mmoItemsBrowserManager.reload();
    }

    private void loadData() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("No se pudo crear la carpeta de MDVSocial.");
            return;
        }

        File legacyYaml = new File(getDataFolder(), "player-data.yml");
        String databaseName = getConfig().getString("storage.player-data.database-file", "player-data.db");
        if (databaseName == null || databaseName.isBlank())
            databaseName = "player-data.db";
        dataFile = new File(getDataFolder(), databaseName);

        try {
            if (data != null)
                data.close();
            data = new PlayerDataStore(this, dataFile);
            data.open(legacyYaml);
        } catch (Exception e) {
            getLogger().severe("No se pudo abrir/migrar " + dataFile.getName() + ": " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void saveData() {
        if (data != null)
            data.flush();
    }

    private void loadMailData() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        mailFile = new File(getDataFolder(), "mail-data.yml");
        if (!mailFile.exists()) {
            try {
                mailFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("No se pudo crear mail-data.yml: " + e.getMessage());
            }
        }
        mailData = YamlConfiguration.loadConfiguration(mailFile);
    }

    private void saveMailData() {
        if (mailData == null || mailFile == null)
            return;
        try {
            mailData.save(mailFile);
        } catch (IOException e) {
            getLogger().severe("No se pudo guardar mail-data.yml: " + e.getMessage());
        }
    }

    private void loadTitles() {
        titles.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("titles");
        if (sec == null)
            return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection t = sec.getConfigurationSection(id);
            if (t == null)
                continue;
            String cleanId = normalize(id);
            TitleDef def = new TitleDef(
                    cleanId,
                    t.getString("display", cleanId),
                    t.getString("prefix", ""),
                    t.getString("material", "NAME_TAG"),
                    t.getString("head-owner", ""),
                    readTexture(t),
                    t.getBoolean("purchasable", false),
                    t.getDouble("price", 0),
                    t.getString("unlock-permission", ""),
                    t.getBoolean("hidden", false),
                    t.getBoolean("player-equippable", !t.getBoolean("hidden", false)),
                    t.getBoolean("punishment", false),
                    t.getStringList("lore"));
            titles.put(cleanId, def);
        }
    }

    private void loadRanks() {
        ranks.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("ranks");
        if (sec == null)
            return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection r = sec.getConfigurationSection(id);
            if (r == null)
                continue;
            String cleanId = normalize(id);
            RankDef def = new RankDef(
                    cleanId,
                    r.getString("display", cleanId),
                    r.getString("material", "PAPER"),
                    r.getString("permission", ""),
                    Math.max(1, r.getInt("page", 1)),
                    r.getInt("slot", -1),
                    r.getStringList("lore"));
            ranks.put(cleanId, def);
        }
    }

    private void loadListSlots() {
        listSlots.clear();
        List<Integer> configSlots = getConfig().getIntegerList("list-slots");
        if (configSlots.isEmpty()) {
            listSlots.addAll(Arrays.asList(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32,
                    33, 34, 37, 38, 39, 40, 41, 42, 43));
        } else {
            listSlots.addAll(configSlots);
        }
    }

    private boolean setupEconomy() {
        if (!getConfig().getBoolean("settings.use-vault-economy", true))
            return false;
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault"))
            return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null)
            return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("social")) {
            if (!(sender instanceof Player player)) {
                msg(sender, "only-players");
                return true;
            }
            if (!player.hasPermission("mdvsocial.use")) {
                msg(player, "no-permission");
                return true;
            }
            if (args.length >= 2 && isPlayerOptionsAlias(args[0])) {
                openPlayerOptionsMenu(player, args[1]);
            } else if (args.length >= 1) {
                openRequestedSocialMenu(player, args[0], args.length >= 2 ? parsePage(args[1]) : 1);
            } else {
                openSocialStart(player);
            }
            return true;
        }

        if (cmd.equals("titulos")) {
            if (!(sender instanceof Player player)) {
                msg(sender, "only-players");
                return true;
            }
            if (!player.hasPermission("mdvsocial.use")) {
                msg(player, "no-permission");
                return true;
            }
            handlePlayerTitleCommand(player, args);
            return true;
        }

        if (cmd.equals("correo") || cmd.equals("carta")) {
            if (!(sender instanceof Player player)) {
                msg(sender, "only-players");
                return true;
            }
            handleMailCommand(player, args);
            return true;
        }

        if (cmd.equals("mdvadmin")) {
            if (!(sender instanceof Player player)) {
                msg(sender, "only-players");
                return true;
            }
            openAdminMenu(player);
            return true;
        }

        if (cmd.equals("mdvitems")) {
            if (!(sender instanceof Player player)) {
                msg(sender, "only-players");
                return true;
            }
            if (mmoItemsBrowserManager == null) {
                player.sendMessage(color(getPrefix() + "&cLa biblioteca de objetos no está disponible."));
                return true;
            }
            mmoItemsBrowserManager.open(player);
            return true;
        }

        if (cmd.equals("mdvsocial")) {
            return handleAdminCommand(sender, args);
        }
        return false;
    }

    void openAdminMenu(Player player) {
        String menu = normalize(getConfig().getString("admin-menu.menu", "admin"));
        if (menu.isBlank())
            menu = "admin";
        if (!getConfig().getBoolean("admin-menu.enabled", true)) {
            player.sendMessage(color(getPrefix() + "&cEl menú administrativo está desactivado."));
            return;
        }
        openCustomMenu(player, menu, 1, "", 1);
    }

    void sendConfiguredMessage(CommandSender sender, String key) {
        msg(sender, key);
    }

    private void handlePlayerTitleCommand(Player player, String[] args) {
        if (args.length == 0) {
            openTitlesHome(player);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("quitar") || sub.equals("clear") || sub.equals("remover")) {
            clearActiveTitle(player);
            return;
        }
        if ((sub.equals("poner") || sub.equals("set") || sub.equals("equipar")) && args.length >= 2) {
            equipTitle(player, normalize(args[1]));
            return;
        }
        openTitlesHome(player);
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(color("&6MDVSocial &7comandos: reload, open, title, mail"));
            return true;
        }
        if (!sender.hasPermission("mdvsocial.admin")) {
            msg(sender, "no-permission");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            loadAll();
            setupEconomy();
            if (socialMenuItemManager != null) {
                socialMenuItemManager.reload();
            }
            if (playerHomesMenuManager != null) {
                playerHomesMenuManager.reload();
            }
            startInteractiveChatProfileTask();
            msg(sender, "reloaded");
            return true;
        }

        if (sub.equals("open")) {
            if (args.length < 3) {
                sender.sendMessage(color("&cUso: /mdvsocial open <jugador> <menu> [pagina]"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                msg(sender, "player-not-found");
                return true;
            }
            openRequestedSocialMenu(target, args[2], args.length >= 4 ? parsePage(args[3]) : 1);
            return true;
        }

        if (sub.equals("mail") || sub.equals("correo") || sub.equals("cartas")) {
            return handleAdminMailCommand(sender, args);
        }

        if (sub.equals("homes") || sub.equals("hogares") || sub.equals("casas")) {
            return handleAdminHomesCommand(sender, args);
        }

        if (!sub.equals("title")) {
            sender.sendMessage(color(
                    "&cUso: /mdvsocial reload | /mdvsocial open <jugador> <menu> [pagina] | /mdvsocial title ... | /mdvsocial mail ... | /mdvsocial homes ..."));
            return true;
        }

        if (args.length < 2) {
            sendTitleHelp(sender);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);

        if ((action.equals("punish") || action.equals("castigar")) && args.length >= 3) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            String titleId = args.length >= 4 ? normalize(args[3]) : getDefaultPunishmentTitleId();
            if (!isPunishmentTitle(titleId)) {
                msg(sender, "punishment-title-invalid");
                return true;
            }
            applyPunishmentTitle(target, titleId);
            msg(sender, "punishment-applied", Map.of("player", target.getName() == null ? args[2] : target.getName(),
                    "title", color(titles.get(titleId).display)));
            if (target.isOnline())
                msg(target.getPlayer(), "punishment-applied-target",
                        Map.of("title", color(titles.get(titleId).display)));
            return true;
        }

        if (action.equals("unpunish") || action.equals("quitar-castigo") || action.equals("perdonar")) {
            if (args.length < 3) {
                sendTitleHelp(sender);
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            if (!isPunished(target.getUniqueId())) {
                msg(sender, "punishment-not-active");
                return true;
            }
            removePunishmentTitle(target);
            msg(sender, "punishment-removed", Map.of("player", target.getName() == null ? args[2] : target.getName()));
            if (target.isOnline())
                msg(target.getPlayer(), "punishment-removed-target");
            return true;
        }

        if ((action.equals("set") || action.equals("clear")) && args.length >= 3) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            if (isPunished(target.getUniqueId())) {
                msg(sender, "punishment-must-remove-first");
                return true;
            }
        }

        if (action.equals("give") && args.length >= 4) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            String titleId = normalize(args[3]);
            if (!titles.containsKey(titleId)) {
                msg(sender, "title-not-found");
                return true;
            }
            giveTitle(target.getUniqueId(), target.getName(), titleId);
            msg(sender, "given-title");
            if (target.isOnline())
                msg(target.getPlayer(), "given-title");
            return true;
        }

        if (action.equals("remove") && args.length >= 4) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            String titleId = normalize(args[3]);
            removeTitle(target.getUniqueId(), titleId);
            msg(sender, "removed-title");
            return true;
        }

        if (action.equals("set") && args.length >= 4) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            String titleId = normalize(args[3]);
            if (!titles.containsKey(titleId)) {
                msg(sender, "title-not-found");
                return true;
            }
            giveTitle(target.getUniqueId(), target.getName(), titleId);
            setActiveTitle(target.getUniqueId(), titleId);
            saveData();
            if (target.isOnline())
                runEquipCommands(target.getPlayer(), titleId);
            msg(sender, "given-title");
            return true;
        }

        if (action.equals("clear") && args.length >= 3) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            setActiveTitle(target.getUniqueId(), getClearTargetTitleId());
            saveData();
            if (target.isOnline())
                runClearCommands(target.getPlayer());
            msg(sender, isMandatoryTitle() ? "title-reset-default" : "removed-title");
            return true;
        }

        if (action.equals("give-radius") && args.length >= 4) {
            if (!(sender instanceof Player player)) {
                msg(sender, "only-players");
                return true;
            }
            double radius = parseDouble(args[2], -1);
            String titleId = normalize(args[3]);
            if (radius <= 0 || !titles.containsKey(titleId)) {
                sendTitleHelp(sender);
                return true;
            }
            int amount = giveNear(player.getLocation(), radius, titleId);
            msg(sender, "boss-radius-given", Map.of("amount", String.valueOf(amount)));
            return true;
        }

        if (action.equals("give-near") && args.length >= 8) {
            World world = Bukkit.getWorld(args[2]);
            double x = parseDouble(args[3], Double.NaN);
            double y = parseDouble(args[4], Double.NaN);
            double z = parseDouble(args[5], Double.NaN);
            double radius = parseDouble(args[6], -1);
            String titleId = normalize(args[7]);
            if (world == null || Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) || radius <= 0
                    || !titles.containsKey(titleId)) {
                sendTitleHelp(sender);
                return true;
            }
            int amount = giveNear(new Location(world, x, y, z), radius, titleId);
            msg(sender, "boss-radius-given", Map.of("amount", String.valueOf(amount)));
            return true;
        }

        sendTitleHelp(sender);
        return true;
    }

    private boolean handleAdminHomesCommand(CommandSender sender, String[] args) {
        if (playerHomesMenuManager == null || args.length < 3) {
            sendAdminHomesHelp(sender);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("status") || action.equals("estado")) {
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(color("&cEl jugador debe estar conectado para calcular su límite actual."));
                return true;
            }
            List<String> locked = playerHomesMenuManager.getLockedHomeNames(target);
            sender.sendMessage(color("&6Hogares de &e" + target.getName()));
            sender.sendMessage(color("&7Límite actual: &e" + playerHomesMenuManager.getCurrentLimit(target)));
            sender.sendMessage(
                    color("&7Suspendidos: " + (locked.isEmpty() ? "&aNinguno" : "&c" + String.join("&7, &c", locked))));
            return true;
        }
        if (action.equals("restore") || action.equals("restaurar") || action.equals("unlock")) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            int removed = playerHomesMenuManager.restorePersistentLocks(target.getUniqueId());
            sender.sendMessage(color("&aSe limpiaron &e" + removed + " &abloqueos persistentes de &e"
                    + (target.getName() == null ? args[2] : target.getName()) + "&a."));
            return true;
        }
        sendAdminHomesHelp(sender);
        return true;
    }

    private void sendAdminHomesHelp(CommandSender sender) {
        sender.sendMessage(color("&6MDVSocial homes:"));
        sender.sendMessage(color("&e/mdvsocial homes status <jugador> &7(muestra límite y hogares suspendidos)"));
        sender.sendMessage(color(
                "&e/mdvsocial homes restore <jugador> &7(limpia bloqueos persistentes cuando restore-on-upgrade es false)"));
    }

    private boolean handleAdminMailCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendAdminMailHelp(sender);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list") || action.equals("listar")) {
            int page = args.length >= 3 ? Math.max(1, parsePage(args[2])) : 1;
            listServerMailCampaigns(sender, page);
            return true;
        }
        if (action.equals("view") || action.equals("ver")) {
            if (args.length < 3) {
                sendAdminMailHelp(sender);
                return true;
            }
            viewServerMailCampaign(sender, args[2]);
            return true;
        }
        if (action.equals("delete") || action.equals("eliminar") || action.equals("remove")) {
            if (args.length < 3) {
                sendAdminMailHelp(sender);
                return true;
            }
            deleteServerMailCampaign(sender, args[2]);
            return true;
        }
        if (action.equals("welcome-test") || action.equals("probar-bienvenida")) {
            if (args.length < 3) {
                sender.sendMessage(color("&cUso: /mdvsocial mail welcome-test <jugador>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                msg(sender, "player-not-found");
                return true;
            }
            sendWelcomeMail(target, true);
            sender.sendMessage(color("&aCorreo de bienvenida de prueba enviado a &e" + target.getName() + "&a."));
            return true;
        }
        if (action.equals("sendall") || action.equals("broadcast")) {
            if (args.length < 3) {
                sendAdminMailHelp(sender);
                return true;
            }
            String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            sendServerMailAll(sender, message, getConfig().getLong("mail.server-mail-expire-days", 30L));
            return true;
        }
        if (action.equals("sendall-never") || action.equals("broadcast-never")) {
            if (args.length < 3) {
                sendAdminMailHelp(sender);
                return true;
            }
            String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            sendServerMailAll(sender, message, -1L);
            return true;
        }
        if (action.equals("sendall-days") || action.equals("broadcast-days")) {
            if (args.length < 4) {
                sendAdminMailHelp(sender);
                return true;
            }
            long days;
            try {
                days = Long.parseLong(args[2]);
            } catch (Exception e) {
                sender.sendMessage(color("&cLos días deben ser un número. Usa -1 para que no expire."));
                return true;
            }
            String message = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
            sendServerMailAll(sender, message, days);
            return true;
        }

        sendAdminMailHelp(sender);
        return true;
    }

    private void sendAdminMailHelp(CommandSender sender) {
        sender.sendMessage(color("&6MDVSocial mail:"));
        sender.sendMessage(color("&e/mdvsocial mail list [página] &7(lista campañas activas)"));
        sender.sendMessage(color("&e/mdvsocial mail view <id> &7(muestra el correo completo)"));
        sender.sendMessage(color("&e/mdvsocial mail delete <id> &7(lo borra de todos los buzones)"));
        sender.sendMessage(color("&e/mdvsocial mail sendall <mensaje> &7(duración por config)"));
        sender.sendMessage(color("&e/mdvsocial mail sendall-days <días> <mensaje> &7(-1 = no expira)"));
        sender.sendMessage(color("&e/mdvsocial mail sendall-never <mensaje>"));
        sender.sendMessage(color("&e/mdvsocial mail welcome-test <jugador>"));
    }

    private void ensureDefaultMenus() {
        File folder = new File(getDataFolder(), "Menus");
        if (!folder.exists() && !folder.mkdirs()) {
            getLogger().warning("No se pudo crear la carpeta Menus.");
            return;
        }
        File main = new File(folder, "main.yml");
        if (!main.exists()) {
            try {
                Files.writeString(main.toPath(), defaultMainMenuYaml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("No se pudo crear Menus/main.yml: " + e.getMessage());
            }
        }
        File clan = new File(folder, "clan.yml");
        if (!clan.exists()) {
            try {
                Files.writeString(clan.toPath(), defaultClanMenuYaml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("No se pudo crear Menus/clan.yml: " + e.getMessage());
            }
        }
        File clanConClan = new File(folder, "clan_con_clan.yml");
        if (!clanConClan.exists()) {
            try {
                Files.writeString(clanConClan.toPath(), defaultClanConClanMenuYaml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("No se pudo crear Menus/clan_con_clan.yml: " + e.getMessage());
            }
        }
        File clanSinClan = new File(folder, "clan_sin_clan.yml");
        if (!clanSinClan.exists()) {
            try {
                Files.writeString(clanSinClan.toPath(), defaultClanSinClanMenuYaml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("No se pudo crear Menus/clan_sin_clan.yml: " + e.getMessage());
            }
        }
        File ayuda = new File(folder, "ayuda.yml");
        if (!ayuda.exists()) {
            try {
                Files.writeString(ayuda.toPath(), defaultAyudaMenuYaml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("No se pudo crear Menus/ayuda.yml: " + e.getMessage());
            }
        }
        File correo = new File(folder, "correo.yml");
        if (!correo.exists()) {
            try {
                Files.writeString(correo.toPath(), defaultCorreoMenuYaml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("No se pudo crear Menus/correo.yml: " + e.getMessage());
            }
        }
        File amigoOpciones = new File(folder, "amigo_opciones.yml");
        if (!amigoOpciones.exists()) {
            try {
                Files.writeString(amigoOpciones.toPath(), defaultAmigoOpcionesMenuYaml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("No se pudo crear Menus/amigo_opciones.yml: " + e.getMessage());
            }
        }
        File jugadorOpciones = new File(folder, "jugador_opciones.yml");
        if (!jugadorOpciones.exists()) {
            try {
                Files.writeString(jugadorOpciones.toPath(), defaultJugadorOpcionesMenuYaml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("No se pudo crear Menus/jugador_opciones.yml: " + e.getMessage());
            }
        }
        File admin = new File(folder, "admin.yml");
        if (!admin.exists()) {
            try {
                Files.writeString(admin.toPath(), defaultAdminMenuYaml(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                getLogger().warning("No se pudo crear Menus/admin.yml: " + e.getMessage());
            }
        }
    }

    private String defaultMainMenuYaml() {
        return """
                # ==========================================================
                # MDVSocial - Menu modular principal
                # Acciones disponibles:
                # OPEN_MENU / OPEN_CONDITIONAL_MENU / MDVCLANS_OPEN / COMMAND_PLAYER / BACK / CLOSE / PREVIOUS_PAGE / NEXT_PAGE / OPEN_TITLES
                # ==========================================================
                title: '&8MDVSocial'
                size: 27
                items:
                  titulos:
                    slot: 11
                    material: NAME_TAG
                    name: '&eTitulos y rangos'
                    lore:
                      - '&7Compra, equipa o revisa titulos.'
                      - '&eClick para abrir.'
                    action: OPEN_TITLES

                  clan:
                    slot: 13
                    material: SHIELD
                    name: '&aClan'
                    lore:
                      - '&7Abre el menu de clan.'
                      - '&8Detecta si tienes clan o no.'
                      - '&eClick para abrir.'
                    action: OPEN_CONDITIONAL_MENU
                    condition-placeholder: '%mdvclans_is_in_clan%'
                    condition-equals: 'true'
                    true-menu: clan_con_clan
                    false-menu: clan_sin_clan

                  ayuda:
                    slot: 15
                    material: BOOK
                    name: '&bAyuda social'
                    lore:
                      - '&7Comandos sociales utiles.'
                      - '&eClick para abrir.'
                    action: OPEN_MENU
                    target-menu: ayuda

                  cerrar:
                    slot: 26
                    material: BARRIER
                    name: '&cCerrar'
                    action: CLOSE
                """;
    }

    private String defaultClanMenuYaml() {
        return """
                title: '&8Clan'
                size: 27
                items:
                  detectar:
                    slot: 13
                    material: SHIELD
                    name: '&a&lClanes'
                    lore:
                      - ''
                      - '&7Este menu puente detecta'
                      - '&7si tienes clan o no.'
                      - ''
                      - '&eClick para continuar.'
                    action: OPEN_CONDITIONAL_MENU
                    condition-placeholder: '%mdvclans_is_in_clan%'
                    condition-equals: 'true'
                    true-menu: clan_con_clan
                    false-menu: clan_sin_clan

                  volver:
                    slot: 22
                    material: ARROW
                    name: '&eVolver'
                    action: BACK

                  cerrar:
                    slot: 26
                    material: BARRIER
                    name: '&cCerrar'
                    action: CLOSE
                """;
    }

    private String defaultClanConClanMenuYaml() {
        return """
                title: '&8&lClan'
                size: 27
                items:
                  gestion:
                    slot: 10
                    material: SHIELD
                    name: '&a&lGestion del clan'
                    lore:
                      - ''
                      - '&7Abre la interfaz dinamica'
                      - '&7principal de tu clan.'
                      - ''
                      - '&eClick para abrir.'
                    action: MDVCLANS_OPEN
                    clans-menu: gestion

                  miembros:
                    slot: 11
                    material: PLAYER_HEAD
                    head-owner: '{player}'
                    name: '&b&lMiembros'
                    lore:
                      - ''
                      - '&7Lista dinamica con cabezas,'
                      - '&7rangos y opciones por permiso.'
                      - ''
                      - '&eClick para abrir.'
                    action: MDVCLANS_OPEN
                    clans-menu: miembros

                  info:
                    slot: 12
                    material: WRITABLE_BOOK
                    name: '&e&lTablero e informacion'
                    lore:
                      - ''
                      - '&7Banner, tablero, buzon,'
                      - '&7solicitudes y logs.'
                      - ''
                      - '&eClick para abrir.'
                    action: MDVCLANS_OPEN
                    clans-menu: info

                  relaciones:
                    slot: 13
                    material: MAP
                    name: '&9&lRelaciones'
                    lore:
                      - ''
                      - '&7Aliados, enemigos,'
                      - '&7bajas y ranking.'
                      - ''
                      - '&eClick para abrir.'
                    action: MDVCLANS_OPEN
                    clans-menu: relaciones

                  recursos:
                    slot: 14
                    material: CHEST
                    name: '&6&lBanco y almacen'
                    lore:
                      - ''
                      - '&7Accede al banco y al'
                      - '&7almacen del clan.'
                      - ''
                      - '&eClick para abrir.'
                    action: MDVCLANS_OPEN
                    clans-menu: almacen

                  lista:
                    slot: 15
                    material: WHITE_BANNER
                    name: '&f&lLista de clanes'
                    lore:
                      - ''
                      - '&7Explora otros clanes'
                      - '&7de MDVCRAFT.'
                      - ''
                      - '&eClick para abrir.'
                    action: MDVCLANS_OPEN
                    clans-menu: lista

                  ajustes:
                    slot: 16
                    material: REDSTONE_TORCH
                    name: '&c&lAjustes del clan'
                    lore:
                      - ''
                      - '&7Opciones de administracion'
                      - '&7para rangos altos.'
                      - ''
                      - '&eClick para abrir.'
                    action: MDVCLANS_OPEN
                    clans-menu: ajustes

                  base:
                    slot: 22
                    material: ENDER_PEARL
                    name: '&b&lIr a la base'
                    lore:
                      - ''
                      - '&7Teletranspórtate a la base'
                      - '&7definida por el clan.'
                      - ''
                      - '&eClick para viajar.'
                    action: COMMAND_PLAYER
                    commands:
                      - 'clan base'

                  volver:
                    slot: 18
                    material: ARROW
                    name: '&6&lVolver'
                    action: BACK

                  cerrar:
                    slot: 26
                    material: BARRIER
                    name: '&c&lCerrar'
                    action: CLOSE
                """;
    }

    private String defaultClanSinClanMenuYaml() {
        return """
                title: '&8&lClanes'
                size: 27
                items:
                  lista:
                    slot: 11
                    material: WHITE_BANNER
                    name: '&f&lLista de clanes'
                    lore:
                      - ''
                      - '&7Mira los clanes existentes.'
                      - '&7Si uno esta abierto, puedes unirte.'
                      - '&7Si esta cerrado, puedes solicitar ingreso.'
                      - ''
                      - '&eClick para abrir.'
                    action: MDVCLANS_OPEN
                    clans-menu: lista_sinclan

                  crear:
                    slot: 15
                    material: EMERALD
                    name: '&a&lCrear clan'
                    lore:
                      - ''
                      - '&7Inicia la creacion de un clan.'
                      - '&7El chat te pedira ID y nombre.'
                      - ''
                      - '&eClick para comenzar.'
                    action: COMMAND_PLAYER
                    commands:
                      - 'clan crear'

                  volver:
                    slot: 22
                    material: ARROW
                    name: '&6&lVolver'
                    action: BACK

                  cerrar:
                    slot: 26
                    material: BARRIER
                    name: '&c&lCerrar'
                    action: CLOSE
                """;
    }

    private String defaultAyudaMenuYaml() {
        return """
                title: '&8Ayuda social'
                size: 27
                items:
                  amigos:
                    slot: 10
                    material: PLAYER_HEAD
                    head-owner: '{player}'
                    name: '&bAmigos'
                    lore:
                      - '&7Ejecuta /friends como jugador.'
                    action: COMMAND_PLAYER
                    commands:
                      - 'friends'

                  correo:
                    slot: 12
                    material: WRITABLE_BOOK
                    name: '&eCorreo'
                    lore:
                      - '&7Ejecuta /mail read como jugador.'
                    action: COMMAND_PLAYER
                    commands:
                      - 'mail read'

                  ejemplo_paginas:
                    slot: 14
                    material: MAP
                    name: '&dEjemplo de paginas'
                    lore:
                      - '&7Este mismo menu puede tener pages: 1, 2, 3...'
                      - '&7Usa NEXT_PAGE y PREVIOUS_PAGE.'

                  volver:
                    slot: 18
                    material: ARROW
                    name: '&eVolver'
                    action: BACK

                  cerrar:
                    slot: 26
                    material: BARRIER
                    name: '&cCerrar'
                    action: CLOSE
                """;
    }

    private String defaultCorreoMenuYaml() {
        return """
                title: '&8&lCorreo'
                size: 27
                items:
                  buzon:
                    slot: 11
                    material: CHEST
                    name: '&6&lBuzon'
                    lore:
                      - ''
                      - '&7Revisa las cartas que'
                      - '&7otros jugadores te enviaron.'
                      - ''
                      - '&eClick para abrir.'
                    action: OPEN_MAILBOX

                  enviar:
                    slot: 13
                    material: WRITABLE_BOOK
                    name: '&e&lEnviar carta'
                    lore:
                      - ''
                      - '&7Escribe una carta a otro'
                      - '&7jugador, incluso si no esta conectado.'
                      - ''
                      - '&eClick para comenzar.'
                    action: START_MAIL_SEND
                    close-on-click: true

                  bloquear:
                    slot: 15
                    material: RED_DYE
                    name: '&c&lBloquear cartas'
                    lore:
                      - ''
                      - '&7Bloquea a un jugador para'
                      - '&7que no pueda enviarte cartas.'
                      - ''
                      - '&eClick para escribir su nombre.'
                    action: START_MAIL_BLOCK
                    close-on-click: true

                  desbloquear:
                    slot: 16
                    material: LIME_DYE
                    name: '&a&lDesbloquear jugador'
                    lore:
                      - ''
                      - '&7Permite que un jugador bloqueado'
                      - '&7vuelva a enviarte cartas.'
                      - ''
                      - '&eClick para escribir su nombre.'
                    action: START_MAIL_UNBLOCK
                    close-on-click: true

                  volver:
                    slot: 22
                    material: PLAYER_HEAD
                    texture: 'eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ2OWUwNmU1ZGFkZmQ4NGU1ZjNkMWMyMTA2M2YyNTUzYjJmYTk0NWVlMWQ0ZDcxNTJmZGM1NDI1YmMxMmE5In19fQ=='
                    name: '&6&lVolver'
                    lore:
                      - '&7Regresa al menu social.'
                    action: OPEN_MENU
                    target-menu: menuamigos
                """;
    }

    private String defaultAmigoOpcionesMenuYaml() {
        return """
                title: '&8&lOpciones de {target}'
                size: 27
                items:
                  perfil:
                    slot: 10
                    material: PLAYER_HEAD
                    head-owner: '{target}'
                    name: '&e&l{target}'
                    lore:
                      - ''
                      - '&7Estado: {target_status}'
                      - '&7UUID: &8{target_uuid}'
                      - ''
                      - '&7Compañero registrado'
                      - '&7en tu libreta social.'
                      - ''
                      - '&8Usa las opciones cercanas'
                      - '&8para interactuar.'

                  carta:
                    slot: 12
                    material: WRITABLE_BOOK
                    name: '&6&lEnviar carta'
                    lore:
                      - ''
                      - '&7Escribe una carta para &e{target}&7.'
                      - '&7No tendrás que volver a escribir su nombre.'
                      - ''
                      - '&eClick para escribir el mensaje.'
                    action: START_MAIL_SEND_TARGET
                    close-on-click: true

                  party:
                    slot: 14
                    material: TOTEM_OF_UNDYING
                    name: '&d&lInvitar al grupo'
                    lore:
                      - ''
                      - '&7Invita a &e{target} &7a tu'
                      - '&7Grupo de Aventura.'
                      - ''
                      - '&8Si no tienes party, el comportamiento'
                      - '&8se controla desde config.yml.'
                      - ''
                      - '&eClick para invitar.'
                    action: INVITE_PARTY_TARGET
                    visible-when: online
                    close-on-click: true

                  tpa:
                    slot: 16
                    material: ENDER_PEARL
                    name: '&a&lSolicitar viaje'
                    lore:
                      - ''
                      - '&7Envía una solicitud de TPA'
                      - '&7a &e{target}&7.'
                      - ''
                      - '&eClick para solicitar.'
                    action: COMMAND_PLAYER
                    visible-when: online
                    commands:
                      - 'tpa {target}'

                  offline_info:
                    slot: 14
                    material: GRAY_DYE
                    name: '&8&lCompañero desconectado'
                    lore:
                      - ''
                      - '&7Este jugador no está conectado.'
                      - '&7Puedes enviarle una carta,'
                      - '&7pero no invitarlo a party ni TPA.'
                    visible-when: offline

                  volver:
                    slot: 22
                    material: PLAYER_HEAD
                    texture: 'eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ2OWUwNmU1ZGFkZmQ4NGU1ZjNkMWMyMTA2M2YyNTUzYjJmYTk0NWVlMWQ0ZDcxNTJmZGM1NDI1YmMxMmE5In19fQ=='
                    name: '&6&lVolver'
                    lore:
                      - '&7Regresa al menú social.'
                    action: COMMAND_PLAYER
                    commands:
                      - 'friends'
                """;
    }

    private String defaultJugadorOpcionesMenuYaml() {
        return """
                # ==========================================================
                # MDVSocial - Opciones de cualquier jugador
                #
                # Se abre desde el nombre interactivo del chat:
                # /social jugador <nombre>
                # ==========================================================
                title: '&8&lOpciones de {target}'
                size: 27

                items:
                  perfil:
                    slot: 4
                    material: PLAYER_HEAD
                    head-owner: '{target}'
                    name: '&e&lPerfil de {target}'
                    lore:
                      - ''
                      - '&e&l● &7&lInformación'
                      - '&7Estado: {target_status}'
                      - '&7Título: &r{target_title}'
                      - '&7Rango: &r{target_rank}'
                      - '&7Clan: &r{target_clan}'
                      - '&7Nivel: &e{target_level}'
                      - '&7Raza: &f{target_class}'
                      - ''
                      - '&bClick derecho &7para preparar'
                      - '&7un mensaje privado.'
                    right-action: SUGGEST_MSG_TARGET
                    close-on-click: true

                  solicitud_amistad:
                    slot: 10
                    material: PLAYER_HEAD
                    head-owner: '{target}'
                    name: '&b&lEnviar solicitud de amistad'
                    lore:
                      - ''
                      - '&b&l● &7&lAmistad de MMOCore'
                      - '&7Envía una solicitud a'
                      - '&e{target}&7.'
                      - ''
                      - '&eClick para enviar.'
                    action: INVITE_FRIEND_TARGET
                    visible-when: online_not_friend
                    close-on-click: true

                  amistad_existente:
                    slot: 10
                    material: LIME_DYE
                    name: '&a&lYa son amigos'
                    lore:
                      - ''
                      - '&7{target} ya forma parte'
                      - '&7de tu lista de amigos.'
                    visible-when: friend

                  amistad_desconectada:
                    slot: 10
                    material: GRAY_DYE
                    name: '&8&lAmistad no disponible'
                    lore:
                      - ''
                      - '&7Las solicitudes de amistad'
                      - '&7requieren que ambos jugadores'
                      - '&7estén conectados.'
                    visible-when: offline_not_friend

                  clan_invite:
                    slot: 11
                    material: PURPLE_BANNER
                    name: '&5&lInvitar al clan'
                    lore:
                      - ''
                      - '&5&l● &7&lInvitación de clan'
                      - '&7Envía una invitación a'
                      - '&e{target}&7.'
                      - ''
                      - '&8Puede funcionar aunque esté'
                      - '&8desconectado.'
                      - ''
                      - '&eClick para invitar.'
                    action: COMMAND_PLAYER
                    close-on-click: true
                    commands:
                      - 'clan invitar {target}'

                  carta:
                    slot: 13
                    material: WRITABLE_BOOK
                    name: '&6&lEnviar carta'
                    lore:
                      - ''
                      - '&6&l● &7&lCorrespondencia'
                      - '&7Escribe una carta para'
                      - '&e{target}&7.'
                      - ''
                      - '&eClick para escribir.'
                    action: START_MAIL_SEND_TARGET
                    close-on-click: true

                  party:
                    slot: 15
                    material: TOTEM_OF_UNDYING
                    name: '&d&lInvitar al grupo'
                    lore:
                      - ''
                      - '&d&l● &7&lGrupo de Aventura'
                      - '&7Invita a &e{target} &7a tu'
                      - '&7party temporal.'
                      - ''
                      - '&7Si no tienes party,'
                      - '&7se creará automáticamente.'
                      - ''
                      - '&eClick para invitar.'
                    action: INVITE_PARTY_TARGET
                    visible-when: online_not_self
                    close-on-click: true

                  party_offline:
                    slot: 15
                    material: GRAY_DYE
                    name: '&8&lGrupo no disponible'
                    lore:
                      - ''
                      - '&7El jugador debe estar'
                      - '&7conectado para recibir'
                      - '&7una invitación de grupo.'
                    visible-when: offline

                  tpa:
                    slot: 16
                    material: ENDER_PEARL
                    name: '&a&lSolicitar viaje'
                    lore:
                      - ''
                      - '&a&l● &7&lTeletransporte'
                      - '&7Envía una solicitud de TPA'
                      - '&7a &e{target}&7.'
                      - ''
                      - '&eClick para solicitar.'
                    action: COMMAND_PLAYER
                    visible-when: online_not_self
                    commands:
                      - 'tpa {target}'

                  tpa_offline:
                    slot: 16
                    material: GRAY_DYE
                    name: '&8&lViaje no disponible'
                    lore:
                      - ''
                      - '&7El jugador no está'
                      - '&7conectado actualmente.'
                    visible-when: offline

                  cerrar:
                    slot: 22
                    material: BARRIER
                    name: '&c&lCerrar'
                    lore:
                      - ''
                      - '&7Cierra este menú.'
                    action: CLOSE
                """;
    }

    private String defaultAdminMenuYaml() {
        return """
                # ==========================================================
                # MDVSocial - Menú administrativo completamente configurable
                #
                # Cada botón puede usar:
                #   permission: permiso.necesario
                #   hide-without-permission: true/false
                #   action: COMMAND_PLAYER | OPEN_MENU | OPEN_MMOITEMS_BROWSER | CLOSE
                #   commands: lista de comandos ejecutados COMO EL JUGADOR
                # ==========================================================
                permission: mdvsocial.admin-menu
                title: '&8&lAdministración de MDVCRAFT'
                size: 54
                items:
                  misiones:
                    slot: 11
                    material: WRITABLE_BOOK
                    name: '&6&lMisiones'
                    lore:
                      - ''
                      - '&7Abre el catálogo y editor visual'
                      - '&7de misiones de MDVQuest.'
                      - ''
                      - '&8Comando: /mdvquest admin'
                      - '&eClic para abrir.'
                    permission: mdvquest.editor
                    hide-without-permission: true
                    action: COMMAND_PLAYER
                    commands:
                      - 'mdvquest admin'

                  recetas:
                    slot: 13
                    material: CRAFTING_TABLE
                    name: '&6&lCrear recetas'
                    lore:
                      - ''
                      - '&7Abre solamente el creador de recetas.'
                      - '&7No permite editar recetas existentes.'
                      - ''
                      - '&8Comando: /mdvrecetas editor'
                      - '&eClic para abrir.'
                    permission: mdvrecetas.editor
                    hide-without-permission: true
                    action: COMMAND_PLAYER
                    commands:
                      - 'mdvrecetas editor'

                  objetos:
                    slot: 15
                    material: CHEST
                    name: '&6&lBiblioteca de MMOItems'
                    lore:
                      - ''
                      - '&7Explora los objetos por categoría'
                      - '&7y obtiene copias base para pruebas.'
                      - ''
                      - '&cNo permite crear, editar ni eliminar.'
                      - '&eClic para abrir.'
                    permission: mdvsocial.item-browser
                    hide-without-permission: true
                    action: OPEN_MMOITEMS_BROWSER

                  creativo:
                    slot: 29
                    material: GRASS_BLOCK
                    name: '&aModo creativo'
                    lore:
                      - '&8Comando: /gmc'
                      - ''
                      - '&eClic para cambiar.'
                    permission: essentials.gamemode.creative
                    hide-without-permission: true
                    action: COMMAND_PLAYER
                    commands: ['gmc']

                  supervivencia:
                    slot: 31
                    material: IRON_SWORD
                    name: '&eModo supervivencia'
                    lore:
                      - '&8Comando: /gms'
                      - ''
                      - '&eClic para cambiar.'
                    permission: essentials.gamemode.survival
                    hide-without-permission: true
                    action: COMMAND_PLAYER
                    commands: ['gms']

                  espectador:
                    slot: 33
                    material: ENDER_EYE
                    name: '&bModo espectador'
                    lore:
                      - '&8Comando: /gmsp'
                      - ''
                      - '&eClic para cambiar.'
                    permission: essentials.gamemode.spectator
                    hide-without-permission: true
                    action: COMMAND_PLAYER
                    commands: ['gmsp']

                  vuelo:
                    slot: 38
                    material: FEATHER
                    name: '&fAlternar vuelo'
                    lore:
                      - '&8Comando: /fly'
                      - ''
                      - '&eClic para alternar.'
                    permission: essentials.fly
                    hide-without-permission: true
                    action: COMMAND_PLAYER
                    commands: ['fly']
                    close-on-click: false

                  invulnerabilidad:
                    slot: 40
                    material: TOTEM_OF_UNDYING
                    name: '&6Alternar invulnerabilidad'
                    lore:
                      - '&8Comando: /god'
                      - ''
                      - '&eClic para alternar.'
                    permission: essentials.god
                    hide-without-permission: true
                    action: COMMAND_PLAYER
                    commands: ['god']
                    close-on-click: false

                  volver:
                    slot: 49
                    material: ARROW
                    name: '&eVolver al menú social'
                    action: COMMAND_PLAYER
                    commands: ['social']

                  cerrar:
                    slot: 53
                    material: BARRIER
                    name: '&cCerrar'
                    action: CLOSE
                """;
    }

    private void loadCustomMenus() {
        customMenus.clear();
        File folder = new File(getDataFolder(), "Menus");
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml")
                || name.toLowerCase(Locale.ROOT).endsWith(".yaml"));
        if (files == null)
            return;
        for (File file : files) {
            String fileName = file.getName();
            int dot = fileName.lastIndexOf('.');
            String id = normalize(dot > 0 ? fileName.substring(0, dot) : fileName);
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                CustomMenuDef def = parseCustomMenu(id, yaml);
                customMenus.put(id, def);
            } catch (Exception e) {
                getLogger().warning("No se pudo cargar menu " + file.getName() + ": " + e.getMessage());
            }
        }
        getLogger().info("Menus modulares cargados: " + customMenus.size());
    }

    private void loadExternalGuiActions() {
        externalGuiActions.clear();
        ConfigurationSection sec = getConfig().getConfigurationSection("external-gui-actions");
        if (sec == null)
            return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection actionSec = sec.getConfigurationSection(id);
            if (actionSec == null || !actionSec.getBoolean("enabled", true))
                continue;

            List<Integer> slots = new ArrayList<>(actionSec.getIntegerList("slots"));
            int singleSlot = actionSec.getInt("slot", Integer.MIN_VALUE);
            if (singleSlot != Integer.MIN_VALUE)
                slots.add(singleSlot);
            slots = slots.stream().filter(i -> i >= 0 && i < 54).distinct().collect(Collectors.toList());
            if (slots.isEmpty()) {
                getLogger().warning("external-gui-actions." + id + " no tiene slot/slots validos.");
                continue;
            }

            List<String> commands = new ArrayList<>(actionSec.getStringList("commands"));
            String singleCommand = actionSec.getString("command", "");
            if (commands.isEmpty() && singleCommand != null && !singleCommand.isBlank())
                commands.add(singleCommand);
            List<String> consoleCommands = new ArrayList<>(actionSec.getStringList("console-commands"));

            ExternalGuiAction def = new ExternalGuiAction(
                    id,
                    actionSec.getString("title", actionSec.getString("title-equals", "")),
                    actionSec.getString("title-contains", ""),
                    slots,
                    commands,
                    consoleCommands,
                    actionSec.getBoolean("close-on-click", true),
                    actionSec.getBoolean("cancel-event", true),
                    actionSec.getString("sound", ""));
            externalGuiActions.add(def);
        }
        if (!externalGuiActions.isEmpty())
            getLogger().info("Puentes de GUIs externas cargados: " + externalGuiActions.size());
    }

    private CustomMenuDef parseCustomMenu(String id, YamlConfiguration yaml) {
        String title = yaml.getString("title", "&8" + id);
        int size = normalizeMenuSize(yaml.getInt("size", 27));
        String permission = yaml.getString("permission", "");
        CustomMenuDef def = new CustomMenuDef(id, title, size, permission);

        ConfigurationSection pagesSec = yaml.getConfigurationSection("pages");
        if (pagesSec != null) {
            for (String pageKey : pagesSec.getKeys(false)) {
                int page = parsePage(pageKey);
                ConfigurationSection items = pagesSec.getConfigurationSection(pageKey + ".items");
                if (items == null)
                    items = pagesSec.getConfigurationSection(pageKey);
                loadCustomMenuItems(def, page, items);
            }
        } else {
            loadCustomMenuItems(def, 1, yaml.getConfigurationSection("items"));
        }
        if (def.pages.isEmpty())
            def.pages.put(1, new ArrayList<>());
        return def;
    }

    private int parsePage(String key) {
        try {
            return Math.max(1, Integer.parseInt(key));
        } catch (Exception e) {
            return 1;
        }
    }

    private void openRequestedSocialMenu(Player player, String rawMenu, int page) {
        String menu = normalize(rawMenu);
        if (menu.isBlank() || menu.equals("main") || menu.equals("inicio")) {
            if (customMenus.containsKey("main"))
                openCustomMenu(player, "main", Math.max(1, page), "", 1);
            else
                openMain(player);
            return;
        }
        if (menu.equals("titulos") || menu.equals("titles") || menu.equals("titulo")) {
            openTitlesHome(player);
            return;
        }
        if (menu.equals("correo") || menu.equals("mail") || menu.equals("cartas") || menu.equals("carta")) {
            if (customMenus.containsKey("correo"))
                openCustomMenu(player, "correo", 1, "menuamigos", 1);
            else
                openMailbox(player, 0);
            return;
        }
        if (menu.equals("mis_titulos") || menu.equals("my_titles") || menu.equals("my-titles")) {
            openTitleList(player, "MY_TITLES", Math.max(0, page - 1));
            return;
        }
        if (menu.equals("tienda") || menu.equals("shop") || menu.equals("title_shop") || menu.equals("title-shop")) {
            openTitleList(player, "SHOP", Math.max(0, page - 1));
            return;
        }
        if (menu.equals("rangos") || menu.equals("ranks")) {
            openRanks(player, Math.max(0, page - 1));
            return;
        }
        if (customMenus.containsKey(menu)) {
            openCustomMenu(player, menu, Math.max(1, page), "", 1);
            return;
        }
        player.sendMessage(color(getPrefix() + "&cEse menu no existe: &e" + rawMenu));
    }

    private int normalizeMenuSize(int size) {
        if (size < 9)
            size = 9;
        if (size > 54)
            size = 54;
        if (size % 9 != 0)
            size = ((size / 9) + 1) * 9;
        return size;
    }

    private void loadCustomMenuItems(CustomMenuDef def, int page, ConfigurationSection itemsSec) {
        List<CustomMenuItem> items = def.pages.computeIfAbsent(page, k -> new ArrayList<>());
        if (itemsSec == null)
            return;
        for (String key : itemsSec.getKeys(false)) {
            ConfigurationSection sec = itemsSec.getConfigurationSection(key);
            if (sec == null)
                continue;
            int slot = sec.getInt("slot", -1);
            if (slot < 0 || slot >= def.size) {
                getLogger().warning("Slot invalido en menu " + def.id + " item " + key + ": " + slot);
                continue;
            }
            String action = normalizeAction(sec.getString("left-action", sec.getString("action", "")));
            String rightAction = normalizeAction(sec.getString("right-action", ""));
            String target = normalize(sec.getString("target-menu", sec.getString("menu", "")));
            List<String> commands = new ArrayList<>(sec.getStringList("commands"));
            String singleCommand = sec.getString("command", "");
            if (commands.isEmpty() && singleCommand != null && !singleCommand.isBlank())
                commands.add(singleCommand);
            List<String> rightCommands = new ArrayList<>(sec.getStringList("right-commands"));
            String singleRightCommand = sec.getString("right-command", "");
            if (rightCommands.isEmpty() && singleRightCommand != null && !singleRightCommand.isBlank())
                rightCommands.add(singleRightCommand);
            CustomMenuItem item = new CustomMenuItem(
                    key,
                    slot,
                    sec.getString("material", "PAPER"),
                    sec.getInt("amount", 1),
                    sec.getString("name", sec.getString("display", "")),
                    sec.getStringList("lore"),
                    sec.getString("head-owner", ""),
                    readTexture(sec),
                    action,
                    rightAction,
                    target,
                    commands,
                    rightCommands,
                    sec.getBoolean("close-on-click", true),
                    sec.getString("visible-when", sec.getString("show-when", "always")),
                    sec.getString("condition-placeholder", sec.getString("placeholder", "")),
                    sec.getString("condition-equals", sec.getString("equals", "true")),
                    normalize(sec.getString("true-menu", sec.getString("menu-true", ""))),
                    normalize(sec.getString("false-menu", sec.getString("menu-false", ""))),
                    normalize(sec.getString("clans-menu", sec.getString("mdvclans-menu", target))),
                    sec.getString("sound", sec.getString("click-sound", "")),
                    sec.getString("permission", ""),
                    sec.getBoolean("hide-without-permission", sec.getBoolean("hide-no-permission", true)),
                    sec.getBoolean("use-clan-banner", sec.getBoolean("dynamic-clan-banner", false)));
            items.add(item);
        }
    }

    private String normalizeAction(String action) {
        if (action == null)
            return "";
        String a = action.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (a) {
            case "COMMAND", "RUN_COMMAND", "PLAYER_COMMAND", "COMMAND_PLAYER" -> "COMMAND_PLAYER";
            case "OPEN", "OPENMENU", "OPEN_MENU" -> "OPEN_MENU";
            case "OPEN_CONDITIONAL", "OPEN_CONDITIONAL_MENU", "CONDITIONAL_MENU", "MENU_CONDICIONAL" ->
                "OPEN_CONDITIONAL_MENU";
            case "MDVCLANS", "MDVCLANS_OPEN", "OPEN_MDVCLANS", "CLAN_DYNAMIC", "CLANES_DINAMICO" -> "MDVCLANS_OPEN";
            case "PREV_PAGE", "PREVIOUS", "PREVIOUS_PAGE" -> "PREVIOUS_PAGE";
            case "NEXT", "NEXT_PAGE" -> "NEXT_PAGE";
            case "OPEN_TITLE", "OPEN_TITLES", "TITLES" -> "OPEN_TITLES";
            case "OPEN_MMOITEMS_BROWSER", "MMOITEMS_BROWSER", "OPEN_ITEM_BROWSER", "ITEM_BROWSER" ->
                "OPEN_MMOITEMS_BROWSER";
            case "OPEN_MAIL", "OPEN_MAILBOX", "MAILBOX", "BUZON" -> "OPEN_MAILBOX";
            case "START_MAIL", "START_MAIL_SEND", "SEND_MAIL", "ENVIAR_CARTA" -> "START_MAIL_SEND";
            case "START_MAIL_TARGET", "START_MAIL_SEND_TARGET", "SEND_MAIL_TARGET", "ENVIAR_CARTA_TARGET",
                    "ENVIAR_CARTA_AMIGO" ->
                "START_MAIL_SEND_TARGET";
            case "INVITE_PARTY_TARGET", "PARTY_INVITE_TARGET", "INVITAR_PARTY", "INVITAR_GRUPO",
                    "INVITE_FRIEND_PARTY" ->
                "INVITE_PARTY_TARGET";
            case "INVITE_FRIEND_TARGET", "FRIEND_INVITE_TARGET", "SEND_FRIEND_REQUEST", "ENVIAR_SOLICITUD_AMISTAD" ->
                "INVITE_FRIEND_TARGET";
            case "SUGGEST_MSG_TARGET", "PREPARE_MSG_TARGET", "PRIVATE_MESSAGE_TARGET", "MENSAJE_PRIVADO_TARGET" ->
                "SUGGEST_MSG_TARGET";
            case "START_MAIL_BLOCK", "BLOCK_MAIL", "BLOQUEAR_CARTAS" -> "START_MAIL_BLOCK";
            case "START_MAIL_UNBLOCK", "UNBLOCK_MAIL", "DESBLOQUEAR_CARTAS" -> "START_MAIL_UNBLOCK";
            default -> a;
        };
    }

    String normalizeBedrockAction(String action) {
        String normalized = normalizeAction(action);
        return switch (normalized) {
            case "OPEN_FRIENDS_BEDROCK", "BEDROCK_FRIENDS", "OPEN_BEDROCK_FRIENDS" -> "OPEN_BEDROCK_FRIENDS";
            case "OPEN_PARTY_BEDROCK", "BEDROCK_PARTY", "OPEN_BEDROCK_PARTY" -> "OPEN_BEDROCK_PARTY";
            case "NONE", "INFO", "NO_ACTION" -> "NONE";
            default -> normalized;
        };
    }

    boolean isBedrockPlayer(Player player) {
        return bedrockMenuManager != null && bedrockMenuManager.isBedrock(player);
    }

    YamlConfiguration getBedrockMenuConfig(String menuId) {
        return bedrockMenuManager == null ? null : bedrockMenuManager.rawMenu(menuId);
    }

    boolean isBedrockFriend(Player player, UUID targetUuid) {
        return isMMOCoreFriend(player, targetUuid);
    }

    void sendNoPermission(Player player) {
        msg(player, "no-permission");
    }

    String bedrockText(String raw, Player player, UUID targetUuid, String targetName, boolean targetOnline) {
        if (raw == null)
            return "";
        return color(applyTargetPlaceholders(raw, player, targetUuid, targetName, targetOnline));
    }

    void openBedrockBack(Player player, BedrockMenuManager.BedrockMenuContext context) {
        if (context != null && context.previousMenu != null && !context.previousMenu.isBlank()) {
            openCustomMenu(player, context.previousMenu, context.previousPage, "", 1,
                    context.targetUuid, context.targetName, context.targetOnline);
        } else {
            openSocialStart(player);
        }
    }

    void handleBedrockMenuAction(Player player, BedrockMenuManager.BedrockMenuButton button,
            BedrockMenuManager.BedrockMenuContext context) {
        if (player == null || button == null || context == null)
            return;
        if (!button.permission.isBlank() && !player.hasPermission(button.permission)) {
            msg(player, "no-permission");
            playUiSound(player, "invalid");
            return;
        }

        String action = normalizeBedrockAction(button.action);
        playUiSound(player, button.sound == null || button.sound.isBlank() ? action : button.sound);

        switch (action) {
            case "", "NONE" -> openCustomMenu(player, context.menuId, context.page,
                    context.previousMenu, context.previousPage, context.targetUuid, context.targetName,
                    context.targetOnline);
            case "CLOSE" -> {
                // El formulario ya se cierra al elegir el boton.
            }
            case "OPEN_MAIN" -> openSocialStart(player);
            case "OPEN_TITLES", "OPEN_TITLES_HOME" -> openTitlesHome(player);
            case "OPEN_MY_TITLES" -> openTitleList(player, "MY_TITLES", 0);
            case "OPEN_SHOP" -> openTitleList(player, "SHOP", 0);
            case "OPEN_LOCKED" -> openTitleList(player, "LOCKED", 0);
            case "OPEN_RANKS" -> openRanks(player, 0);
            case "OPEN_MENU" -> openCustomMenu(player, button.targetMenu, 1, context.menuId, context.page,
                    context.targetUuid, context.targetName, context.targetOnline);
            case "OPEN_CONDITIONAL_MENU" -> {
                boolean result = evaluateMenuCondition(player, button.conditionPlaceholder, button.conditionEquals);
                String target = result ? button.trueMenu : button.falseMenu;
                openCustomMenu(player, target, 1, context.menuId, context.page,
                        context.targetUuid, context.targetName, context.targetOnline);
            }
            case "MDVCLANS_OPEN" -> {
                String clansMenu = button.clansMenu;
                if (clansMenu == null || clansMenu.isBlank())
                    clansMenu = button.targetMenu;
                if (clansMenu == null || clansMenu.isBlank())
                    clansMenu = "auto";
                Bukkit.dispatchCommand(player, "clan abrir " + clansMenu);
            }
            case "BACK" -> openBedrockBack(player, context);
            case "COMMAND_PLAYER" -> runBedrockPlayerCommands(player, button.commands, context);
            case "OPEN_MAILBOX" -> openMailbox(player, 0);
            case "OPEN_MMOITEMS_BROWSER" -> {
                if (mmoItemsBrowserManager == null)
                    player.sendMessage(color(getPrefix() + "&cLa biblioteca de objetos no está disponible."));
                else
                    mmoItemsBrowserManager.open(player);
            }
            case "START_MAIL_SEND" -> startMailRecipientPrompt(player,
                    context.menuId.isBlank() ? "correo" : context.menuId, context.page);
            case "START_MAIL_SEND_TARGET" -> startMailMessagePromptToTarget(player, context.targetUuid,
                    context.targetName, context.menuId.isBlank() ? "correo" : context.menuId, context.page);
            case "INVITE_PARTY_TARGET" -> inviteFriendToParty(player, context.targetUuid, context.targetName);
            case "INVITE_FRIEND_TARGET" -> inviteMMOCoreFriend(player, context.targetUuid, context.targetName);
            case "REMOVE_FRIEND_TARGET" -> openBedrockRemoveFriendConfirm(player, context.targetUuid,
                    context.targetName, context.targetOnline);
            case "SUGGEST_MSG_TARGET" -> openBedrockPrivateMessage(player, context.targetName, context);
            case "START_MAIL_BLOCK" -> startMailBlockPrompt(player, true,
                    context.menuId.isBlank() ? "correo" : context.menuId, context.page);
            case "START_MAIL_UNBLOCK" -> startMailBlockPrompt(player, false,
                    context.menuId.isBlank() ? "correo" : context.menuId, context.page);
            case "CLEAR_TITLE" -> {
                clearActiveTitle(player);
                openTitlesHome(player);
            }
            case "PREVIOUS_PAGE", "PREV_PAGE" -> openCustomMenu(player, context.menuId,
                    Math.max(1, context.page - 1), context.previousMenu, context.previousPage,
                    context.targetUuid, context.targetName, context.targetOnline);
            case "NEXT_PAGE" -> openCustomMenu(player, context.menuId, context.page + 1,
                    context.previousMenu, context.previousPage, context.targetUuid, context.targetName,
                    context.targetOnline);
            case "OPEN_BEDROCK_FRIENDS" -> openBedrockFriends(player, 0);
            case "OPEN_BEDROCK_PARTY" -> openBedrockParty(player);
            default -> {
                player.sendMessage(color(getPrefix() + "&cAcción Bedrock no reconocida: &e" + action));
                getLogger().warning("Acción Bedrock no reconocida en " + context.menuId + ": " + action);
            }
        }
    }

    private void runBedrockPlayerCommands(Player player, List<String> commands,
            BedrockMenuManager.BedrockMenuContext context) {
        if (commands == null || commands.isEmpty())
            return;
        for (String line : commands) {
            String cmd = applyTargetPlaceholders(line, player, context.targetUuid, context.targetName,
                    context.targetOnline).trim();
            if (cmd.isBlank())
                continue;
            if (cmd.startsWith("/"))
                cmd = cmd.substring(1);
            Bukkit.dispatchCommand(player, cmd);
        }
    }

    private void openSocialStart(Player player) {
        String start = normalize(getConfig().getString("settings.start-menu", "main"));
        if (customMenus.containsKey(start))
            openCustomMenu(player, start, 1, "", 1);
        else if (customMenus.containsKey("main"))
            openCustomMenu(player, "main", 1, "", 1);
        else
            openMain(player);
    }

    private void openCustomMenu(Player player, String menuId, int page, String previousMenu, int previousPage) {
        openCustomMenu(player, menuId, page, previousMenu, previousPage, null, "", false);
    }

    private void openCustomMenu(Player player, String menuId, int page, String previousMenu, int previousPage,
            UUID targetUuid, String targetName, boolean targetOnline) {
        menuId = normalize(menuId);
        if (isBedrockPlayer(player) && bedrockMenuManager != null) {
            boolean opened = bedrockMenuManager.open(player, menuId, page, previousMenu, previousPage,
                    targetUuid, targetName, targetOnline);
            if (opened)
                return;
            if (!getConfig().getBoolean("bedrock.fallback-to-java-menu", true)) {
                player.sendMessage(color(getPrefix() + "&cNo existe la version Bedrock del menu &e" + menuId + "&c."));
                return;
            }
        }
        CustomMenuDef def = customMenus.get(menuId);
        if (def == null) {
            player.sendMessage(color(getPrefix() + "&cEse menu no existe: &e" + menuId));
            return;
        }
        if (!def.permission.isBlank() && !player.hasPermission(def.permission)) {
            msg(player, "no-permission");
            return;
        }
        int maxPage = def.maxPage();
        page = Math.max(1, Math.min(page, maxPage));
        MenuHolder holder = new MenuHolder("CUSTOM_MENU", page, menuId,
                previousMenu == null ? "" : normalize(previousMenu), previousPage <= 0 ? 1 : previousPage, targetUuid,
                targetName, targetOnline);
        Inventory inv = Bukkit.createInventory(holder, def.size, color(applyTargetPlaceholders(
                def.title.replace("{page}", String.valueOf(page)).replace("{max_page}", String.valueOf(maxPage)),
                player, targetUuid, targetName, targetOnline)));
        holder.inventory = inv;
        fill(inv);

        List<CustomMenuItem> items = def.pages.getOrDefault(page, Collections.emptyList());
        for (CustomMenuItem menuItem : items) {
            if (!menuItem.isVisible(this, player, targetUuid, targetOnline))
                continue;
            if (!menuItem.permission.isBlank() && !player.hasPermission(menuItem.permission)
                    && menuItem.hideWithoutPermission)
                continue;
            if (menuItem.slot >= 0 && menuItem.slot < inv.getSize())
                inv.setItem(menuItem.slot, customMenuItemStack(player, menuItem, targetUuid, targetName, targetOnline));
        }
        player.openInventory(inv);
    }

    private ItemStack customMenuItemStack(Player player, CustomMenuItem def, UUID targetUuid, String targetName,
            boolean targetOnline) {
        String clanBannerData = def.useClanBanner ? getPlayerClanBannerData(player.getUniqueId()) : null;
        boolean usingClanBanner = def.useClanBanner && clanBannerData != null;
        Material mat = Material.matchMaterial(def.material.toUpperCase(Locale.ROOT));
        if (mat == null)
            mat = Material.PAPER;
        int amount = Math.max(1, Math.min(64, def.amount));
        ItemStack item = usingClanBanner ? bannerFromSerializedData(clanBannerData) : new ItemStack(mat, amount);
        item.setAmount(amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;
        if (usingClanBanner) {
            hideBannerTooltip(meta);
        }
        if (!usingClanBanner && mat == Material.PLAYER_HEAD && meta instanceof SkullMeta skull) {
            String texture = applyTargetPlaceholders(def.texture, player, targetUuid, targetName, targetOnline);
            if (texture != null && !texture.isBlank()) {
                applySkullTexture(skull, texture);
            } else if (def.headOwner != null && !def.headOwner.isBlank()) {
                OfflinePlayer owner = Bukkit.getOfflinePlayer(
                        applyTargetPlaceholders(def.headOwner, player, targetUuid, targetName, targetOnline));
                skull.setOwningPlayer(owner);
            }
            meta = skull;
        }
        if (def.name != null && !def.name.isBlank())
            meta.setDisplayName(color(applyTargetPlaceholders(def.name, player, targetUuid, targetName, targetOnline)));
        List<String> lore = new ArrayList<>();
        for (String line : def.lore)
            lore.add(color(applyTargetPlaceholders(line, player, targetUuid, targetName, targetOnline)));
        if (!lore.isEmpty())
            meta.setLore(lore);
        if (def.action != null && !def.action.isBlank())
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, def.action);
        if (def.rightAction != null && !def.rightAction.isBlank())
            meta.getPersistentDataContainer().set(keyRightAction, PersistentDataType.STRING, def.rightAction);
        if (def.targetMenu != null && !def.targetMenu.isBlank())
            meta.getPersistentDataContainer().set(keyTargetMenu, PersistentDataType.STRING, def.targetMenu);
        if (def.conditionPlaceholder != null && !def.conditionPlaceholder.isBlank())
            meta.getPersistentDataContainer().set(keyConditionPlaceholder, PersistentDataType.STRING,
                    def.conditionPlaceholder);
        if (def.conditionEquals != null && !def.conditionEquals.isBlank())
            meta.getPersistentDataContainer().set(keyConditionEquals, PersistentDataType.STRING, def.conditionEquals);
        if (def.trueMenu != null && !def.trueMenu.isBlank())
            meta.getPersistentDataContainer().set(keyTrueMenu, PersistentDataType.STRING, def.trueMenu);
        if (def.falseMenu != null && !def.falseMenu.isBlank())
            meta.getPersistentDataContainer().set(keyFalseMenu, PersistentDataType.STRING, def.falseMenu);
        if (def.clansMenu != null && !def.clansMenu.isBlank())
            meta.getPersistentDataContainer().set(keyClansMenu, PersistentDataType.STRING, def.clansMenu);
        if (targetUuid != null)
            meta.getPersistentDataContainer().set(keyFriendTargetUuid, PersistentDataType.STRING,
                    targetUuid.toString());
        if (targetName != null && !targetName.isBlank())
            meta.getPersistentDataContainer().set(keyFriendTargetName, PersistentDataType.STRING, targetName);
        meta.getPersistentDataContainer().set(keyFriendTargetOnline, PersistentDataType.STRING,
                String.valueOf(targetOnline));
        if (!def.commands.isEmpty())
            meta.getPersistentDataContainer().set(keyCommands, PersistentDataType.STRING,
                    String.join("\n", def.commands));
        if (!def.rightCommands.isEmpty())
            meta.getPersistentDataContainer().set(keyRightCommands, PersistentDataType.STRING,
                    String.join("\n", def.rightCommands));
        if (def.sound != null && !def.sound.isBlank())
            meta.getPersistentDataContainer().set(keySound, PersistentDataType.STRING, def.sound);
        if (def.permission != null && !def.permission.isBlank())
            meta.getPersistentDataContainer().set(keyRequiredPermission, PersistentDataType.STRING, def.permission);
        meta.getPersistentDataContainer().set(keyCloseOnClick, PersistentDataType.STRING,
                String.valueOf(def.closeOnClick));
        item.setItemMeta(meta);
        return item;
    }

    private String readTexture(ConfigurationSection sec) {
        if (sec == null)
            return "";
        String texture = sec.getString("custom-head-texture", "");
        if (texture == null || texture.isBlank())
            texture = sec.getString("texture", "");
        if (texture == null || texture.isBlank())
            texture = sec.getString("head-texture", "");
        if (texture == null || texture.isBlank())
            texture = sec.getString("skull-texture", "");
        if (texture == null || texture.isBlank())
            texture = sec.getString("texture-base64", "");
        return texture == null ? "" : texture.trim();
    }

    /**
     * Extrae la URL real desde una textura Base64 de Minecraft Heads.
     * Tambien acepta una URL directa http/https por comodidad.
     */
    private String extractTextureUrl(String textureValue) {
        if (textureValue == null)
            return "";
        String value = textureValue.trim();
        if (value.isBlank())
            return "";
        if (value.startsWith("http://") || value.startsWith("https://"))
            return value;

        try {
            String decoded = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            int urlKey = decoded.indexOf("\"url\"");
            if (urlKey < 0)
                return "";
            int colon = decoded.indexOf(':', urlKey);
            if (colon < 0)
                return "";
            int firstQuote = decoded.indexOf('\"', colon);
            if (firstQuote < 0)
                return "";
            int secondQuote = decoded.indexOf('\"', firstQuote + 1);
            if (secondQuote < 0)
                return "";
            return decoded.substring(firstQuote + 1, secondQuote).replace("\\/", "/");
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * Aplica texturas custom a cabezas usando la API publica de Bukkit/Paper.
     *
     * Version 1.1.6:
     * - Sin reflexion.
     * - Sin tocar campos internos del SkullMeta.
     * - Evita IllegalAccessException/IllegalArgumentException en Paper/Purpur
     * 1.21+.
     */
    private void applySkullTexture(SkullMeta skull, String textureValue) {
        if (skull == null || textureValue == null || textureValue.isBlank())
            return;

        String textureUrl = extractTextureUrl(textureValue.trim());
        if (textureUrl == null || textureUrl.isBlank()) {
            getLogger().warning("No se pudo aplicar textura custom de cabeza: textura invalida o Base64 sin URL.");
            return;
        }

        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "MDVSocial");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(textureUrl));
            profile.setTextures(textures);
            skull.setOwnerProfile(profile);
        } catch (Throwable ex) {
            getLogger().warning("No se pudo aplicar textura custom de cabeza con API publica: "
                    + ex.getClass().getSimpleName() + " - " + ex.getMessage());
        }
    }

    private String getPlayerClanBannerData(UUID playerUuid) {
        if (playerUuid == null)
            return null;
        try {
            org.bukkit.plugin.Plugin clans = Bukkit.getPluginManager().getPlugin("MDVClans");
            if (clans == null || !clans.isEnabled())
                return null;
            Method method = clans.getClass().getMethod("getPlayerClanBannerData", UUID.class);
            Object result = method.invoke(clans, playerUuid);
            return result == null ? null : String.valueOf(result);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ItemStack bannerFromSerializedData(String data) {
        if (data != null && !data.isBlank()) {
            try {
                return itemStackFromBase64(data);
            } catch (Throwable ignored) {
                return new ItemStack(Material.WHITE_BANNER);
            }
        }
        return new ItemStack(Material.WHITE_BANNER);
    }

    private ItemStack itemStackFromBase64(String data) throws IOException, ClassNotFoundException {
        try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(data)))) {
            Object object = dataInput.readObject();
            return object instanceof ItemStack stack ? stack : new ItemStack(Material.WHITE_BANNER);
        }
    }

    private void hideBannerTooltip(ItemMeta meta) {
        if (meta == null)
            return;
        try {
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        } catch (Throwable ignored) {
            // Compatibilidad con builds donde el flag no exista.
        }
    }

    private String applyPlayerPlaceholders(String input, Player player) {
        if (input == null)
            return "";
        String out = input.replace("{player}", player.getName());

        // Atajos internos para menus de MDVSocial.
        // Estos usan PlaceholderAPI si esta disponible, por ejemplo MMOCore.
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            String level = papi(player, "%mmocore_level%");
            String exp = papi(player, "%mmocore_experience%");
            String next = papi(player, "%mmocore_next_level%");
            String percent = stripPercent(papi(player, "%mmocore_level_percent%"));
            String clazz = papi(player, "%mmocore_class%");
            String classId = papi(player, "%mmocore_class_id%");
            String attributePoints = papi(player, "%mmocore_attribute_points%");

            out = out
                    .replace("{level}", level)
                    .replace("{exp}", exp)
                    .replace("{experience}", exp)
                    .replace("{next_level}", next)
                    .replace("{percent}", percent)
                    .replace("{progress}", progressBar(percent))
                    .replace("{class}", clazz)
                    .replace("{class_id}", classId)
                    .replace("{attribute_points}", attributePoints);

            // Permite usar cualquier placeholder normal, por ejemplo:
            // %mmocore_level%, %vault_eco_balance%, %player_name%, etc.
            out = PlaceholderAPI.setPlaceholders(player, out);
        }
        return out;
    }

    private String applyTargetPlaceholders(String input, Player player, UUID targetUuid, String targetName,
            boolean targetOnline) {
        if (input == null)
            return "";
        String safeName = targetName == null || targetName.isBlank() ? "jugador" : targetName;
        String uuidText = targetUuid == null ? "" : targetUuid.toString();

        String out = input
                .replace("{target}", safeName)
                .replace("{target_name}", safeName)
                .replace("{friend}", safeName)
                .replace("{friend_name}", safeName)
                .replace("{target_uuid}", uuidText)
                .replace("{friend_uuid}", uuidText)
                .replace("{target_online}", targetOnline ? "true" : "false")
                .replace("{friend_online}", targetOnline ? "true" : "false")
                .replace("{target_status}", targetOnline ? "&aEn línea" : "&7Desconectado")
                .replace("{friend_status}", targetOnline ? "&aEn línea" : "&7Desconectado");

        ChatProfileSnapshot targetProfile = resolveTargetProfile(targetUuid, safeName, targetOnline);
        out = out
                .replace("{target_level}", targetProfile.level)
                .replace("{target_class}", targetProfile.race)
                .replace("{target_race}", targetProfile.race)
                .replace("{target_title}", targetProfile.title)
                .replace("{target_rank}", targetProfile.rank)
                .replace("{target_clan}", targetProfile.clan)
                .replace("{target_is_friend}", isMMOCoreFriend(player, targetUuid) ? "true" : "false");

        out = applyPlayerPlaceholders(out, player);
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                out = PlaceholderAPI.setPlaceholders(player, out);
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private String papi(Player player, String placeholder) {
        try {
            String value = PlaceholderAPI.setPlaceholders(player, placeholder);
            if (value == null || value.equalsIgnoreCase(placeholder))
                return "";
            return value;
        } catch (Throwable ignored) {
            return "";
        }
    }

    /**
     * Parses a string using Placeholder API.
     **/
    private String papi(OfflinePlayer player, String placeholder) {
        if (player == null || placeholder == null || placeholder.isBlank())
            return "";
        try {
            String value = PlaceholderAPI.setPlaceholders(player, placeholder);
            if (value == null || value.equalsIgnoreCase(placeholder) || value.contains("%"))
                return "";
            return value;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String stripPercent(String value) {
        if (value == null)
            return "0";
        return value.replace("%", "").trim();
    }

    private String progressBar(String percentText) {
        double percent;
        try {
            percent = Double.parseDouble(stripPercent(percentText).replace(",", "."));
        } catch (Exception ignored) {
            percent = 0;
        }
        percent = Math.max(0, Math.min(100, percent));
        int total = 10;
        int filled = (int) Math.round((percent / 100.0) * total);
        StringBuilder bar = new StringBuilder();
        bar.append("&e");
        for (int i = 0; i < filled; i++)
            bar.append("|");
        bar.append("&7");
        for (int i = filled; i < total; i++)
            bar.append("|");
        return bar.toString();
    }

    private boolean mailEnabled() {
        return getConfig().getBoolean("mail.enabled", true);
    }

    private void handleMailCommand(Player player, String[] args) {
        if (!mailEnabled()) {
            msg(player, "mail-disabled");
            return;
        }
        if (!player.hasPermission("mdvsocial.mail.use")) {
            msg(player, "no-permission");
            return;
        }
        if (args.length == 0) {
            if (customMenus.containsKey("correo"))
                openCustomMenu(player, "correo", 1, "menuamigos", 1);
            else
                openMailbox(player, 0);
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "buzon", "mailbox", "recibidas" -> openMailbox(player, 0);
            case "enviar", "send" -> {
                if (!player.hasPermission("mdvsocial.mail.send")) {
                    msg(player, "no-permission");
                    return;
                }
                if (args.length >= 3) {
                    String targetName = args[1];
                    String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                    sendMailByName(player, targetName, message);
                } else {
                    startMailRecipientPrompt(player);
                }
            }
            case "bloquear", "block" -> {
                if (args.length >= 2)
                    blockMailByName(player, args[1]);
                else
                    startMailBlockPrompt(player, true);
            }
            case "desbloquear", "unblock" -> {
                if (args.length >= 2)
                    unblockMailByName(player, args[1]);
                else
                    startMailBlockPrompt(player, false);
            }
            case "bloqueados", "blocked" -> sendBlockedList(player);
            case "cancelar", "cancel" -> {
                MailComposeSession session = mailSessions.remove(player.getUniqueId());
                msg(player, "mail-cancelled");
                returnToMailSessionMenu(player, session);
            }
            default -> {
                player.sendMessage(color(getPrefix() + "&e/correo &7- abre el menu de correo"));
                player.sendMessage(color(getPrefix() + "&e/carta enviar <jugador> <mensaje>"));
                player.sendMessage(color(getPrefix() + "&e/carta bloquear <jugador>"));
                player.sendMessage(color(getPrefix() + "&e/carta desbloquear <jugador>"));
            }
        }
    }

    private void startMailRecipientPrompt(Player player) {
        startMailRecipientPrompt(player, "correo", 1);
    }

    private void startMailRecipientPrompt(Player player, String returnMenu, int returnPage) {
        if (isBedrockPlayer(player)) {
            openBedrockMailCompose(player, returnMenu, returnPage);
            return;
        }
        if (!mailEnabled()) {
            msg(player, "mail-disabled");
            return;
        }
        if (!player.hasPermission("mdvsocial.mail.send")) {
            msg(player, "no-permission");
            return;
        }
        mailSessions.put(player.getUniqueId(),
                new MailComposeSession(MailStage.RECIPIENT, null, returnMenu, returnPage));
        msg(player, "mail-recipient-prompt");
    }

    private void startMailMessagePromptToTarget(Player player, UUID targetUuid, String fallbackName, String returnMenu,
            int returnPage) {
        if (isBedrockPlayer(player)) {
            openBedrockMailMessageToTarget(player, targetUuid, fallbackName, returnMenu, returnPage);
            return;
        }
        if (!mailEnabled()) {
            msg(player, "mail-disabled");
            return;
        }
        if (!player.hasPermission("mdvsocial.mail.send")) {
            msg(player, "no-permission");
            return;
        }
        if (targetUuid == null) {
            msg(player, "social-target-not-found");
            return;
        }
        if (targetUuid.equals(player.getUniqueId())) {
            msg(player, "mail-self");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        String targetName = target.getName() == null || target.getName().isBlank() ? fallbackName : target.getName();
        if (targetName == null || targetName.isBlank())
            targetName = "jugador";
        mailSessions.put(player.getUniqueId(),
                new MailComposeSession(MailStage.MESSAGE, targetName, targetUuid, returnMenu, returnPage));
        msg(player, "mail-message-prompt", Map.of("target", targetName, "max", String.valueOf(getMaxMailLength())));
    }

    private void startMailBlockPrompt(Player player, boolean block) {
        startMailBlockPrompt(player, block, "correo", 1);
    }

    private void startMailBlockPrompt(Player player, boolean block, String returnMenu, int returnPage) {
        if (isBedrockPlayer(player)) {
            openBedrockMailBlockForm(player, block, returnMenu, returnPage);
            return;
        }
        mailSessions.put(player.getUniqueId(),
                new MailComposeSession(block ? MailStage.BLOCK : MailStage.UNBLOCK, null, returnMenu, returnPage));
        msg(player, block ? "mail-block-prompt" : "mail-unblock-prompt");
    }

    private void startMailReplyFromMail(Player player, String mailId, int returnPage) {
        if (isBedrockPlayer(player)) {
            openBedrockMailReply(player, mailId, returnPage);
            return;
        }
        if (!mailEnabled()) {
            msg(player, "mail-disabled");
            return;
        }
        if (!player.hasPermission("mdvsocial.mail.send")) {
            msg(player, "no-permission");
            return;
        }
        if (mailId == null || mailId.isBlank()
                || !mailData.contains(mailPath(player.getUniqueId(), "letters." + mailId))) {
            msg(player, "mail-not-found");
            return;
        }
        String base = mailPath(player.getUniqueId(), "letters." + mailId);
        String fromUuidText = mailData.getString(base + ".from-uuid", "");
        String fromName = mailData.getString(base + ".from-name",
                getConfig().getString("mail.server-author-name", "MDVCRAFT"));
        if (fromUuidText == null || fromUuidText.isBlank()) {
            msg(player, "mail-cannot-reply-server");
            return;
        }
        try {
            UUID targetUuid = UUID.fromString(fromUuidText);
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
            String targetName = target.getName() == null || target.getName().isBlank() ? fromName : target.getName();
            player.closeInventory();
            mailSessions.put(player.getUniqueId(), new MailComposeSession(MailStage.MESSAGE, targetName, targetUuid,
                    "MAILBOX", Math.max(0, returnPage)));
            msg(player, "mail-reply-prompt", Map.of("target", targetName, "max", String.valueOf(getMaxMailLength())));
        } catch (Exception ignored) {
            msg(player, "mail-cannot-reply-server");
        }
    }

    @EventHandler
    public void onMailChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        MailComposeSession session = mailSessions.get(player.getUniqueId());
        if (session == null)
            return;
        event.setCancelled(true);
        String text = event.getMessage() == null ? "" : event.getMessage().trim();
        Bukkit.getScheduler().runTask(this, () -> handleMailChatInput(player, text));
    }

    private void handleMailChatInput(Player player, String text) {
        MailComposeSession session = mailSessions.get(player.getUniqueId());
        if (session == null)
            return;
        if (text.equalsIgnoreCase("cancelar") || text.equalsIgnoreCase("cancel")) {
            mailSessions.remove(player.getUniqueId());
            msg(player, "mail-cancelled");
            returnToMailSessionMenu(player, session);
            return;
        }
        if (session.stage == MailStage.RECIPIENT) {
            OfflinePlayer target = findKnownOfflinePlayer(text);
            if (target == null) {
                sendPlayerNotFound(player, text);
                return;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                msg(player, "mail-self");
                return;
            }
            mailSessions.put(player.getUniqueId(), new MailComposeSession(MailStage.MESSAGE,
                    target.getName() == null ? text : target.getName(), session.returnMenu, session.returnPage));
            msg(player, "mail-message-prompt", Map.of("target", target.getName() == null ? text : target.getName(),
                    "max", String.valueOf(getMaxMailLength())));
            return;
        }
        if (session.stage == MailStage.MESSAGE) {
            String targetName = session.targetName;
            UUID targetUuid = session.targetUuid;
            mailSessions.remove(player.getUniqueId());
            if (targetUuid != null)
                sendMailByUuid(player, targetUuid, targetName, text);
            else
                sendMailByName(player, targetName, text);
            return;
        }
        if (session.stage == MailStage.BLOCK) {
            mailSessions.remove(player.getUniqueId());
            blockMailByName(player, text);
            return;
        }
        if (session.stage == MailStage.UNBLOCK) {
            mailSessions.remove(player.getUniqueId());
            unblockMailByName(player, text);
        }
    }

    private OfflinePlayer findKnownOfflinePlayer(String name) {
        if (name == null || name.isBlank())
            return null;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null)
            return online;
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        boolean allowUnknown = getConfig().getBoolean("mail.allow-unknown-targets", false);
        if (allowUnknown || off.hasPlayedBefore())
            return off;
        return null;
    }

    private void sendMailByName(Player sender, String targetName, String message) {
        OfflinePlayer target = findKnownOfflinePlayer(targetName);
        if (target == null) {
            sendPlayerNotFound(sender, targetName);
            return;
        }
        sendMail(sender, target, message);
    }

    private void sendMailByUuid(Player sender, UUID targetUuid, String fallbackName, String message) {
        if (targetUuid == null) {
            sendMailByName(sender, fallbackName, message);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline()
                && !getConfig().getBoolean("mail.allow-unknown-targets", false))) {
            msg(sender, "mail-player-not-found");
            return;
        }
        sendMail(sender, target, message);
    }

    private void sendMail(Player sender, OfflinePlayer target, String message) {
        if (!mailEnabled()) {
            msg(sender, "mail-disabled");
            return;
        }
        if (!sender.hasPermission("mdvsocial.mail.send")) {
            msg(sender, "no-permission");
            return;
        }
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            msg(sender, "mail-self");
            return;
        }
        String clean = sanitizeMailMessage(message);
        if (clean.isBlank()) {
            msg(sender, "mail-empty");
            return;
        }
        int max = getMaxMailLength();
        if (clean.length() > max) {
            msg(sender, "mail-too-long", Map.of("max", String.valueOf(max)));
            return;
        }
        if (isMailBlocked(target.getUniqueId(), sender.getUniqueId())) {
            msg(sender, "mail-blocked-by-target",
                    Map.of("target", target.getName() == null ? "ese jugador" : target.getName()));
            return;
        }
        int limit = getMailboxLimit(target);
        int count = getMailIds(target.getUniqueId()).size();
        if (count >= limit) {
            msg(sender, "mail-full", Map.of("target", target.getName() == null ? "ese jugador" : target.getName(),
                    "limit", String.valueOf(limit)));
            return;
        }
        long expiresAt = System.currentTimeMillis() + getMailExpireMillis();
        storeMail(target.getUniqueId(), target.getName() == null ? "jugador" : target.getName(),
                sender.getUniqueId().toString(), sender.getName(), clean, expiresAt);
        saveMailData();
        msg(sender, "mail-sent", Map.of("target", target.getName() == null ? "jugador" : target.getName()));
    }

    private void sendServerMailAll(CommandSender sender, String message, long expireDays) {
        if (!mailEnabled()) {
            msg(sender, "mail-disabled");
            return;
        }
        String clean = sanitizeMailMessage(message);
        if (clean.isBlank()) {
            msg(sender, "mail-empty");
            return;
        }
        int max = getMaxMailLength();
        if (clean.length() > max) {
            msg(sender, "mail-too-long", Map.of("max", String.valueOf(max)));
            return;
        }

        long sentAt = System.currentTimeMillis();
        long expiresAt = expireDays <= 0 ? 0L : sentAt + (expireDays * 24L * 60L * 60L * 1000L);
        String author = getConfig().getString("mail.server-author-name", "MDVCRAFT");
        boolean ignoreLimit = getConfig().getBoolean("mail.server-mail-ignore-mailbox-limit", true);
        String campaignId = createCampaignId();
        int sent = 0;
        int skipped = 0;
        for (OfflinePlayer target : Bukkit.getOfflinePlayers()) {
            if (target == null || target.getUniqueId() == null || !target.hasPlayedBefore())
                continue;
            if (!ignoreLimit) {
                int limit = getMailboxLimit(target);
                int count = getMailIds(target.getUniqueId()).size();
                if (count >= limit) {
                    skipped++;
                    continue;
                }
            }
            storeMail(target.getUniqueId(), target.getName() == null ? "jugador" : target.getName(), "", author, clean,
                    expiresAt, sentAt, "SERVER_BROADCAST", campaignId);
            sent++;
        }
        String registry = "broadcasts." + campaignId;
        mailData.set(registry + ".author", author);
        mailData.set(registry + ".message", clean);
        mailData.set(registry + ".sent-at", sentAt);
        mailData.set(registry + ".expires-at", expiresAt);
        mailData.set(registry + ".recipients", sent);
        mailData.set(registry + ".skipped", skipped);
        saveMailData();
        msg(sender, "mail-broadcast-sent",
                Map.of("sent", String.valueOf(sent), "skipped", String.valueOf(skipped), "id", campaignId));
    }

    private String createCampaignId() {
        String time = Long.toString(System.currentTimeMillis(), 36);
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        return time + "-" + random;
    }

    private String storeMail(UUID targetUuid, String toName, String fromUuid, String fromName, String message,
            long expiresAt) {
        return storeMail(targetUuid, toName, fromUuid, fromName, message, expiresAt, System.currentTimeMillis(), "",
                "");
    }

    private String storeMail(UUID targetUuid, String toName, String fromUuid, String fromName, String message,
            long expiresAt, long sentAt, String type, String campaignId) {
        String id = UUID.randomUUID().toString();
        String base = mailPath(targetUuid, "letters." + id);
        mailData.set(base + ".from-uuid", fromUuid == null ? "" : fromUuid);
        mailData.set(base + ".from-name", fromName == null || fromName.isBlank() ? "Desconocido" : fromName);
        mailData.set(base + ".to-name", toName == null ? "jugador" : toName);
        mailData.set(base + ".message", message);
        mailData.set(base + ".sent-at", sentAt);
        mailData.set(base + ".expires-at", expiresAt);
        mailData.set(base + ".read", false);
        if (type != null && !type.isBlank())
            mailData.set(base + ".type", type);
        if (campaignId != null && !campaignId.isBlank())
            mailData.set(base + ".broadcast-id", campaignId);
        return id;
    }

    private void sendWelcomeMailIfNeeded(Player player, boolean firstJoin) {
        if (player == null || !player.isOnline())
            return;
        if (!getConfig().getBoolean("mail.welcome.enabled", true))
            return;
        if (getConfig().getBoolean("mail.welcome.only-first-join", true) && !firstJoin)
            return;
        sendWelcomeMail(player, false);
    }

    private void sendWelcomeMail(Player player, boolean force) {
        if (player == null || !mailEnabled())
            return;
        String welcomeId = normalize(getConfig().getString("mail.welcome.id", "bienvenida-v1"));
        if (welcomeId.isBlank())
            welcomeId = "bienvenida-v1";
        String marker = mailPath(player.getUniqueId(), "system.welcome-delivered." + welcomeId);
        if (!force && mailData.getBoolean(marker, false))
            return;

        String message = sanitizeMailMessage(getConfig().getString("mail.welcome.message",
                "¡Bienvenido a MDVCRAFT! Revisa el menú social para descubrir tus sistemas de aventura."));
        if (message.isBlank())
            return;
        int max = getMaxMailLength();
        if (message.length() > max)
            message = message.substring(0, Math.max(1, max));
        boolean ignoreLimit = getConfig().getBoolean("mail.welcome.ignore-mailbox-limit", true);
        if (!ignoreLimit && getMailIds(player.getUniqueId()).size() >= getMailboxLimit(player))
            return;

        long sentAt = System.currentTimeMillis();
        long days = Math.max(1L, getConfig().getLong("mail.welcome.expire-days", 3L));
        long expiresAt = sentAt + days * 24L * 60L * 60L * 1000L;
        String author = getConfig().getString("mail.welcome.author-name",
                getConfig().getString("mail.server-author-name", "MDVCRAFT"));
        storeMail(player.getUniqueId(), player.getName(), "", author, message, expiresAt, sentAt, "WELCOME",
                "welcome-" + welcomeId);
        if (!force)
            mailData.set(marker, true);
        saveMailData();

        // La carta de bienvenida se entrega silenciosamente por defecto.
        // Esto evita mensajes de chat innecesarios al primer ingreso y, sobre todo,
        // que una configuración antigua muestre "Mensaje faltante: mail-received".
        if (getConfig().getBoolean("mail.welcome.notify-in-chat", false)) {
            msg(player, "mail-received", Map.of("sender", author));
        }
    }

    private void listServerMailCampaigns(CommandSender sender, int requestedPage) {
        List<ServerMailCampaign> campaigns = collectServerMailCampaigns();
        int perPage = 8;
        int maxPage = Math.max(1, (int) Math.ceil(campaigns.size() / (double) perPage));
        int page = Math.max(1, Math.min(requestedPage, maxPage));
        sender.sendMessage(color("&6Campañas de correo activas &7(" + page + "/" + maxPage + ")"));
        if (campaigns.isEmpty()) {
            sender.sendMessage(color("&7No hay correos globales activos."));
            return;
        }
        int start = (page - 1) * perPage;
        for (int i = start; i < Math.min(campaigns.size(), start + perPage); i++) {
            ServerMailCampaign campaign = campaigns.get(i);
            String expiry = campaign.expiresAt <= 0 ? "Nunca" : daysLeftText(campaign.expiresAt);
            sender.sendMessage(color("&e" + campaign.id + " &7| &f" + campaign.author + " &7| &a"
                    + campaign.recipients.size() + " buzones &7| &c" + expiry));
            sender.sendMessage(color("  &8" + shorten(campaign.message, 72)));
        }
        sender.sendMessage(color("&7Usa &e/mdvsocial mail view <id> &7o &e/mdvsocial mail delete <id>&7."));
    }

    private void viewServerMailCampaign(CommandSender sender, String id) {
        ServerMailCampaign campaign = findServerMailCampaign(id);
        if (campaign == null) {
            msg(sender, "mail-broadcast-not-found");
            return;
        }
        sender.sendMessage(color("&6Correo global &e" + campaign.id));
        sender.sendMessage(color("&7Autor: &f" + campaign.author));
        sender.sendMessage(color("&7Enviado: &f" + formatTime(campaign.sentAt)));
        sender.sendMessage(
                color("&7Expira: &f" + (campaign.expiresAt <= 0 ? "Nunca" : formatTime(campaign.expiresAt))));
        sender.sendMessage(color(
                "&7Buzones activos: &a" + campaign.recipients.size() + " &7(No leídos: &e" + campaign.unread + "&7)"));
        sender.sendMessage(color("&7Mensaje: &f" + campaign.message));
    }

    private void deleteServerMailCampaign(CommandSender sender, String id) {
        ServerMailCampaign campaign = findServerMailCampaign(id);
        if (campaign == null) {
            msg(sender, "mail-broadcast-not-found");
            return;
        }
        int removed = 0;
        ConfigurationSection mailboxes = mailData.getConfigurationSection("mailbox");
        if (mailboxes != null) {
            for (String uuidText : new ArrayList<>(mailboxes.getKeys(false))) {
                ConfigurationSection letters = mailData.getConfigurationSection("mailbox." + uuidText + ".letters");
                if (letters == null)
                    continue;
                for (String letterId : new ArrayList<>(letters.getKeys(false))) {
                    String base = "mailbox." + uuidText + ".letters." + letterId;
                    if (campaign.id.equalsIgnoreCase(effectiveCampaignId(base))) {
                        mailData.set(base, null);
                        removed++;
                    }
                }
            }
        }
        mailData.set("broadcasts." + campaign.id, null);
        saveMailData();
        msg(sender, "mail-broadcast-deleted", Map.of("id", campaign.id, "removed", String.valueOf(removed)));
    }

    private ServerMailCampaign findServerMailCampaign(String id) {
        if (id == null || id.isBlank())
            return null;
        return collectServerMailCampaigns().stream().filter(c -> c.id.equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    private List<ServerMailCampaign> collectServerMailCampaigns() {
        cleanupExpiredMail();
        Map<String, ServerMailCampaign> campaigns = new LinkedHashMap<>();
        ConfigurationSection mailboxes = mailData.getConfigurationSection("mailbox");
        if (mailboxes == null)
            return new ArrayList<>();

        for (String uuidText : mailboxes.getKeys(false)) {
            UUID recipient;
            try {
                recipient = UUID.fromString(uuidText);
            } catch (Exception ignored) {
                continue;
            }
            ConfigurationSection letters = mailData.getConfigurationSection("mailbox." + uuidText + ".letters");
            if (letters == null)
                continue;
            for (String letterId : letters.getKeys(false)) {
                String base = "mailbox." + uuidText + ".letters." + letterId;
                String type = mailData.getString(base + ".type", "");
                String fromUuid = mailData.getString(base + ".from-uuid", "");
                boolean serverBroadcast = "SERVER_BROADCAST".equalsIgnoreCase(type)
                        || (fromUuid.isBlank() && !"WELCOME".equalsIgnoreCase(type)
                                && !"MDVCLANS_INVITE".equalsIgnoreCase(type));
                if (!serverBroadcast)
                    continue;

                String campaignId = effectiveCampaignId(base);
                String author = mailData.getString(base + ".from-name", "MDVCRAFT");
                String message = mailData.getString(base + ".message", "");
                long sentAt = mailData.getLong(base + ".sent-at", 0L);
                long expiresAt = mailData.getLong(base + ".expires-at", 0L);
                ServerMailCampaign campaign = campaigns.computeIfAbsent(campaignId,
                        ignored -> new ServerMailCampaign(campaignId, author, message, sentAt, expiresAt));
                campaign.recipients.add(recipient);
                if (!mailData.getBoolean(base + ".read", false))
                    campaign.unread++;
                if (campaign.sentAt <= 0 || (sentAt > 0 && sentAt < campaign.sentAt))
                    campaign.sentAt = sentAt;
                if (campaign.expiresAt == 0 || expiresAt == 0)
                    campaign.expiresAt = 0;
                else
                    campaign.expiresAt = Math.max(campaign.expiresAt, expiresAt);
            }
        }
        List<ServerMailCampaign> out = new ArrayList<>(campaigns.values());
        out.sort(Comparator.comparingLong((ServerMailCampaign campaign) -> campaign.sentAt).reversed());
        return out;
    }

    private String effectiveCampaignId(String mailBase) {
        String stored = mailData.getString(mailBase + ".broadcast-id", "");
        if (!stored.isBlank())
            return stored;
        String author = mailData.getString(mailBase + ".from-name", "MDVCRAFT");
        String message = mailData.getString(mailBase + ".message", "");
        long sentMinute = mailData.getLong(mailBase + ".sent-at", 0L) / 60_000L;
        long expiryMinute = mailData.getLong(mailBase + ".expires-at", 0L) / 60_000L;
        return "legacy-" + Integer.toUnsignedString(Objects.hash(author, message, sentMinute, expiryMinute), 36);
    }

    public boolean sendClanInviteMail(UUID targetUuid, String targetName, UUID inviterUuid, String fromName,
            String clanTag, String clanName, String message, long expiresAt) {
        return sendClanInviteMail(targetUuid, targetName, inviterUuid, fromName, clanTag, clanName, message, "",
                expiresAt);
    }

    public boolean sendClanInviteMail(UUID targetUuid, String targetName, UUID inviterUuid, String fromName,
            String clanTag, String clanName, String message, String clanBannerData, long expiresAt) {
        if (!mailEnabled() || targetUuid == null || clanTag == null || clanTag.isBlank())
            return false;
        String clean = sanitizeMailMessage(message);
        if (clean.isBlank())
            clean = "El clan " + clanName + " [" + clanTag + "] te invitó a unirte.";
        int max = getMaxMailLength();
        if (clean.length() > max)
            clean = clean.substring(0, Math.max(0, max - 3)) + "...";

        boolean ignoreLimit = getConfig().getBoolean("mail.clan-invites.ignore-mailbox-limit", true);
        if (!ignoreLimit) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
            int limit = getMailboxLimit(target);
            int count = getMailIds(targetUuid).size();
            if (count >= limit)
                return false;
        }

        String senderName = fromName == null || fromName.isBlank() ? "MDVClans" : fromName;
        String id = storeMail(targetUuid, targetName == null || targetName.isBlank() ? "jugador" : targetName,
                inviterUuid == null ? "" : inviterUuid.toString(), senderName, clean, expiresAt);
        String base = mailPath(targetUuid, "letters." + id);
        mailData.set(base + ".type", "MDVCLANS_INVITE");
        mailData.set(base + ".clan-tag", clanTag);
        mailData.set(base + ".clan-name", clanName == null || clanName.isBlank() ? clanTag : clanName);
        mailData.set(base + ".clan-banner", clanBannerData == null ? "" : clanBannerData);
        mailData.set(base + ".inviter-uuid", inviterUuid == null ? "" : inviterUuid.toString());
        saveMailData();
        return true;
    }

    private String sanitizeMailMessage(String message) {
        if (message == null)
            return "";
        return message.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private int getMaxMailLength() {
        return Math.max(20, getConfig().getInt("mail.max-message-length", 180));
    }

    private long getMailExpireMillis() {
        long days = Math.max(1L, getConfig().getLong("mail.expire-after-days", 10L));
        return days * 24L * 60L * 60L * 1000L;
    }

    private int getMailboxLimit(OfflinePlayer player) {
        int limit = Math.max(1, getConfig().getInt("mail.default-mailbox-size", 10));
        if (player != null && player.isOnline()) {
            ConfigurationSection sec = getConfig().getConfigurationSection("mail.mailbox-size-permissions");
            if (sec != null) {
                Player online = player.getPlayer();
                for (String perm : sec.getKeys(false)) {
                    int value = sec.getInt(perm, limit);
                    if (online.hasPermission(perm) && value > limit)
                        limit = value;
                }
            }
        }
        return limit;
    }

    private String mailPath(UUID uuid, String child) {
        return "mailbox." + uuid + "." + child;
    }

    private List<String> getMailIds(UUID uuid) {
        cleanupExpiredMailFor(uuid);
        ConfigurationSection sec = mailData.getConfigurationSection(mailPath(uuid, "letters"));
        if (sec == null)
            return new ArrayList<>();
        List<String> ids = new ArrayList<>(sec.getKeys(false));
        ids.sort((a, b) -> Long.compare(mailData.getLong(mailPath(uuid, "letters." + b + ".sent-at"), 0L),
                mailData.getLong(mailPath(uuid, "letters." + a + ".sent-at"), 0L)));
        return ids;
    }

    private void cleanupExpiredMail() {
        if (mailData == null)
            return;
        ConfigurationSection mailboxes = mailData.getConfigurationSection("mailbox");
        if (mailboxes == null)
            return;
        boolean changed = false;
        for (String uuidText : mailboxes.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidText);
                if (cleanupExpiredMailFor(uuid))
                    changed = true;
            } catch (Exception ignored) {
            }
        }
        if (changed)
            saveMailData();
    }

    private boolean cleanupExpiredMailFor(UUID uuid) {
        if (mailData == null)
            return false;
        ConfigurationSection sec = mailData.getConfigurationSection(mailPath(uuid, "letters"));
        if (sec == null)
            return false;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (String id : new ArrayList<>(sec.getKeys(false))) {
            long expires = mailData.getLong(mailPath(uuid, "letters." + id + ".expires-at"), 0L);
            if (expires > 0 && expires <= now) {
                mailData.set(mailPath(uuid, "letters." + id), null);
                changed = true;
            }
        }
        if (changed)
            saveMailData();
        return changed;
    }

    private void openMailbox(Player player, int page) {
        if (isBedrockPlayer(player)) {
            openBedrockMailbox(player, page);
            return;
        }
        if (!mailEnabled()) {
            msg(player, "mail-disabled");
            return;
        }
        if (!player.hasPermission("mdvsocial.mail.read")) {
            msg(player, "no-permission");
            return;
        }
        List<String> ids = getMailIds(player.getUniqueId());
        List<Integer> slots = getMailSlots();
        int size = normalizeMenuSize(getConfig().getInt("mail.menus.mailbox.size", 54));
        int perPage = Math.max(1, slots.size());
        int maxPage = Math.max(0, (int) Math.ceil(ids.size() / (double) perPage) - 1);
        page = Math.max(0, Math.min(page, maxPage));
        String title = getConfig().getString("mail.menus.mailbox.title", "&8Buzon {page}/{max_page}")
                .replace("{page}", String.valueOf(page + 1))
                .replace("{max_page}", String.valueOf(maxPage + 1))
                .replace("{count}", String.valueOf(ids.size()))
                .replace("{limit}", String.valueOf(getMailboxLimit(player)));
        Inventory inv = createMenu("MAILBOX", size, title, page);
        fill(inv);
        int start = page * perPage;
        for (int i = 0; i < perPage; i++) {
            int index = start + i;
            if (index >= ids.size())
                break;
            int slot = slots.get(i);
            if (slot >= 0 && slot < inv.getSize())
                inv.setItem(slot, mailItem(player, ids.get(index)));
        }
        if (ids.isEmpty())
            inv.setItem(size / 2, emptyMailboxItem());
        if (page > 0)
            inv.setItem(size - 9, navItem("previous-page", "PREVIOUS_PAGE"));
        inv.setItem(size - 5, navItem("back", "OPEN_MENU"));
        setTargetMenuOnItem(inv, size - 5, "correo");
        if (page < maxPage)
            inv.setItem(size - 1, navItem("next-page", "NEXT_PAGE"));
        else
            inv.setItem(size - 1, navItem("close", "CLOSE"));
        player.openInventory(inv);
    }

    private List<Integer> getMailSlots() {
        List<Integer> slots = getConfig().getIntegerList("mail.menus.mailbox.slots");
        if (slots == null || slots.isEmpty())
            return new ArrayList<>(listSlots);
        return slots.stream().filter(i -> i >= 0 && i < 54).collect(Collectors.toList());
    }

    private ItemStack emptyMailboxItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(getConfig().getString("mail.items.empty.name", "&7Buzon vacio")));
        List<String> lore = getConfig().getStringList("mail.items.empty.lore");
        if (lore.isEmpty())
            lore = List.of("&8No tienes cartas guardadas.");
        meta.setLore(lore.stream().map(this::color).collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack mailItem(Player viewer, String id) {
        String base = mailPath(viewer.getUniqueId(), "letters." + id);
        String fromName = mailData.getString(base + ".from-name", "Desconocido");
        String fromUuidText = mailData.getString(base + ".from-uuid", "");
        String message = mailData.getString(base + ".message", "");
        boolean read = mailData.getBoolean(base + ".read", false);
        long sentAt = mailData.getLong(base + ".sent-at", 0L);
        long expiresAt = mailData.getLong(base + ".expires-at", 0L);
        boolean clanInviteMail = "MDVCLANS_INVITE".equalsIgnoreCase(mailData.getString(base + ".type", ""));
        String clanBannerData = mailData.getString(base + ".clan-banner", "");

        ItemStack item = clanInviteMail ? bannerFromSerializedData(clanBannerData)
                : new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;
        if (clanInviteMail) {
            hideBannerTooltip(meta);
        } else if (meta instanceof SkullMeta skull) {
            try {
                if (!fromUuidText.isBlank())
                    skull.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(fromUuidText)));
                else
                    skull.setOwningPlayer(Bukkit.getOfflinePlayer(fromName));
            } catch (Throwable ignored) {
                skull.setOwningPlayer(Bukkit.getOfflinePlayer(fromName));
            }
            meta = skull;
        }
        meta.setDisplayName(color((read ? "&eCarta de &f" : "&aNueva carta de &f") + fromName));
        List<String> lore = new ArrayList<>();
        lore.add(color(""));
        if (clanInviteMail)
            lore.add(color("&7Tipo: &dInvitación de clan"));
        lore.add(color("&7Enviada: &e" + formatTime(sentAt)));
        lore.add(color("&7Expira: &c" + daysLeftText(expiresAt)));
        lore.add(color(""));
        lore.add(color("&8\"" + shorten(message, 34) + "\""));
        lore.add(color(""));
        lore.add(color("&eClick para leer."));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "READ_MAIL");
        meta.getPersistentDataContainer().set(keyMailId, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private void openMailRead(Player player, String id, int page) {
        if (isBedrockPlayer(player)) {
            openBedrockMailRead(player, id, page);
            return;
        }
        if (id == null || id.isBlank() || !mailData.contains(mailPath(player.getUniqueId(), "letters." + id))) {
            msg(player, "mail-not-found");
            openMailbox(player, page);
            return;
        }
        String base = mailPath(player.getUniqueId(), "letters." + id);
        mailData.set(base + ".read", true);
        saveMailData();
        String fromName = mailData.getString(base + ".from-name", "Desconocido");
        String fromUuid = mailData.getString(base + ".from-uuid", "");
        String message = mailData.getString(base + ".message", "");
        String mailType = mailData.getString(base + ".type", "");
        boolean clanInviteMail = "MDVCLANS_INVITE".equalsIgnoreCase(mailType);
        String clanBannerData = mailData.getString(base + ".clan-banner", "");
        long sentAt = mailData.getLong(base + ".sent-at", 0L);
        long expiresAt = mailData.getLong(base + ".expires-at", 0L);

        int size = normalizeMenuSize(getConfig().getInt("mail.menus.read.size", 27));
        String title = getConfig().getString("mail.menus.read.title", "&8Carta de {sender}").replace("{sender}",
                fromName);
        MenuHolder holder = new MenuHolder("MAIL_READ", page);
        Inventory inv = Bukkit.createInventory(holder, size, color(title));
        holder.inventory = inv;
        fill(inv);

        ItemStack letter = clanInviteMail ? bannerFromSerializedData(clanBannerData)
                : new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = letter.getItemMeta();
        if (meta != null) {
            if (clanInviteMail)
                hideBannerTooltip(meta);
            meta.setDisplayName(
                    color((clanInviteMail ? "&d&lInvitación de clan de &f" : "&e&lCarta de &f") + fromName));
            List<String> lore = new ArrayList<>();
            lore.add(color(""));
            if (clanInviteMail)
                lore.add(color("&7Tipo: &dInvitación de clan"));
            lore.add(color("&7Enviada: &e" + formatTime(sentAt)));
            lore.add(color("&7Expira: &c" + daysLeftText(expiresAt)));
            lore.add(color(""));
            for (String line : wrapText(message, 38))
                lore.add(color("&f" + line));
            meta.setLore(lore);
            letter.setItemMeta(meta);
        }
        inv.setItem(getConfig().getInt("mail.menus.read.letter-slot", 13), letter);

        inv.setItem(getConfig().getInt("mail.menus.read.back-slot", 11),
                mailActionItem("items.back", "MAIL_BACK", id, fromUuid));
        if (clanInviteMail) {
            inv.setItem(getConfig().getInt("mail.menus.read.reply-slot", 14),
                    mailActionItem("mail.items.clan-invite-accept", "ACCEPT_CLAN_INVITE", id, fromUuid,
                            Material.LIME_DYE, "&a&lAceptar invitación", List.of("", "&7Acepta la invitación",
                                    "&7y entra al clan si hay cupo.", "", "&eClick para aceptar.")));
            inv.setItem(getConfig().getInt("mail.menus.read.delete-slot", 15), mailActionItem(
                    "mail.items.clan-invite-reject", "REJECT_CLAN_INVITE", id, fromUuid, Material.RED_DYE,
                    "&c&lRechazar invitación",
                    List.of("", "&7Rechaza la invitación", "&7y elimina esta carta.", "", "&eClick para rechazar.")));
        } else {
            inv.setItem(getConfig().getInt("mail.menus.read.reply-slot", 14),
                    mailActionItem("mail.items.reply", "REPLY_MAIL", id, fromUuid));
            inv.setItem(getConfig().getInt("mail.menus.read.delete-slot", 15),
                    mailActionItem("mail.items.delete", "DELETE_MAIL", id, fromUuid));
            inv.setItem(getConfig().getInt("mail.menus.read.block-slot", 16),
                    mailActionItem("mail.items.block-sender", "BLOCK_MAIL_SENDER", id, fromUuid));
        }
        player.openInventory(inv);
    }

    private ItemStack mailActionItem(String path, String action, String mailId, String senderUuid) {
        return mailActionItem(path, action, mailId, senderUuid, Material.PAPER, "", List.of());
    }

    private ItemStack mailActionItem(String path, String action, String mailId, String senderUuid, Material defMaterial,
            String defName, List<String> defLore) {
        ConfigurationSection sec = getConfig().getConfigurationSection(path);
        ItemStack item;
        if (sec == null) {
            item = new ItemStack(defMaterial == null ? Material.PAPER : defMaterial);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (defName != null && !defName.isBlank())
                    meta.setDisplayName(color(defName));
                if (defLore != null && !defLore.isEmpty())
                    meta.setLore(defLore.stream().map(this::color).collect(Collectors.toList()));
                if (action != null && !action.isBlank())
                    meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
                item.setItemMeta(meta);
            }
        } else {
            item = itemFromSection(sec, action, null);
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (mailId != null && !mailId.isBlank())
                meta.getPersistentDataContainer().set(keyMailId, PersistentDataType.STRING, mailId);
            if (senderUuid != null && !senderUuid.isBlank())
                meta.getPersistentDataContainer().set(keyMailSender, PersistentDataType.STRING, senderUuid);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void setTargetMenuOnItem(Inventory inv, int slot, String target) {
        ItemStack item = inv.getItem(slot);
        if (item == null || !item.hasItemMeta())
            return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(keyTargetMenu, PersistentDataType.STRING, target);
        item.setItemMeta(meta);
    }

    private void deleteMail(Player player, String id) {
        if (!player.hasPermission("mdvsocial.mail.delete")) {
            msg(player, "no-permission");
            return;
        }
        if (id == null || id.isBlank() || !mailData.contains(mailPath(player.getUniqueId(), "letters." + id))) {
            msg(player, "mail-not-found");
            return;
        }
        mailData.set(mailPath(player.getUniqueId(), "letters." + id), null);
        saveMailData();
        msg(player, "mail-deleted");
    }

    private void deleteMailInternal(UUID owner, String id) {
        if (owner == null || id == null || id.isBlank())
            return;
        mailData.set(mailPath(owner, "letters." + id), null);
        saveMailData();
    }

    private void handleClanInviteMailAction(Player player, String id, boolean accept, int page) {
        if (id == null || id.isBlank() || !mailData.contains(mailPath(player.getUniqueId(), "letters." + id))) {
            msg(player, "mail-not-found");
            openMailbox(player, page);
            return;
        }
        String base = mailPath(player.getUniqueId(), "letters." + id);
        String type = mailData.getString(base + ".type", "");
        String clanTag = mailData.getString(base + ".clan-tag", "");
        if (!"MDVCLANS_INVITE".equalsIgnoreCase(type) || clanTag == null || clanTag.isBlank()) {
            msg(player, "mail-not-found");
            openMailbox(player, page);
            return;
        }
        player.closeInventory();
        Bukkit.getScheduler().runTask(this, () -> {
            Bukkit.dispatchCommand(player, accept ? "clan aceptar " + clanTag : "clan rechazar " + clanTag);
            deleteMailInternal(player.getUniqueId(), id);
        });
    }

    private boolean blockMailSender(Player player, String senderUuidText) {
        if (senderUuidText == null || senderUuidText.isBlank()) {
            msg(player, "mail-cannot-block-server");
            return false;
        }
        try {
            UUID uuid = UUID.fromString(senderUuidText);
            addBlockedMail(player.getUniqueId(), uuid);
            OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
            msg(player, "mail-blocked", Map.of("target", off.getName() == null ? "jugador" : off.getName()));
            return true;
        } catch (Exception ignored) {
            msg(player, "mail-cannot-block-server");
            return false;
        }
    }

    private void blockMailByName(Player player, String targetName) {
        OfflinePlayer target = findKnownOfflinePlayer(targetName);
        if (target == null) {
            sendPlayerNotFound(player, targetName);
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            msg(player, "mail-self-block");
            return;
        }
        addBlockedMail(player.getUniqueId(), target.getUniqueId());
        msg(player, "mail-blocked", Map.of("target", target.getName() == null ? targetName : target.getName()));
    }

    private void unblockMailByName(Player player, String targetName) {
        OfflinePlayer target = findKnownOfflinePlayer(targetName);
        if (target == null) {
            sendPlayerNotFound(player, targetName);
            return;
        }
        List<String> list = new ArrayList<>(mailData.getStringList(mailPath(player.getUniqueId(), "blocked")));
        boolean removed = list.remove(target.getUniqueId().toString());
        mailData.set(mailPath(player.getUniqueId(), "blocked"), list);
        saveMailData();
        msg(player, removed ? "mail-unblocked" : "mail-not-blocked",
                Map.of("target", target.getName() == null ? targetName : target.getName()));
    }

    private boolean isMailBlocked(UUID recipient, UUID sender) {
        return mailData.getStringList(mailPath(recipient, "blocked")).contains(sender.toString());
    }

    private void addBlockedMail(UUID recipient, UUID sender) {
        List<String> list = new ArrayList<>(mailData.getStringList(mailPath(recipient, "blocked")));
        if (!list.contains(sender.toString()))
            list.add(sender.toString());
        mailData.set(mailPath(recipient, "blocked"), list);
        saveMailData();
    }

    private void sendBlockedList(Player player) {
        List<String> list = mailData.getStringList(mailPath(player.getUniqueId(), "blocked"));
        if (list.isEmpty()) {
            player.sendMessage(color(getPrefix() + "&7No tienes jugadores bloqueados para cartas."));
            return;
        }
        player.sendMessage(color(getPrefix() + "&eJugadores bloqueados:"));
        for (String uuidText : list) {
            try {
                OfflinePlayer off = Bukkit.getOfflinePlayer(UUID.fromString(uuidText));
                player.sendMessage(color("&8- &f" + (off.getName() == null ? uuidText : off.getName())));
            } catch (Exception ignored) {
                player.sendMessage(color("&8- &f" + uuidText));
            }
        }
    }

    private String formatTime(long millis) {
        if (millis <= 0)
            return "desconocido";
        try {
            DateTimeFormatter fmt = DateTimeFormatter
                    .ofPattern(getConfig().getString("mail.date-format", "dd/MM HH:mm"))
                    .withZone(ZoneId.systemDefault());
            return fmt.format(Instant.ofEpochMilli(millis));
        } catch (Exception e) {
            return String.valueOf(millis);
        }
    }

    private String daysLeftText(long expiresAt) {
        long diff = expiresAt - System.currentTimeMillis();
        if (expiresAt <= 0)
            return getConfig().getString("mail.never-expires-text", "no expira");
        if (diff <= 0)
            return "expirada";
        long days = diff / (24L * 60L * 60L * 1000L);
        long hours = (diff / (60L * 60L * 1000L)) % 24L;
        if (days > 0)
            return days + "d " + hours + "h";
        return Math.max(1, diff / (60L * 60L * 1000L)) + "h";
    }

    private String shorten(String text, int max) {
        if (text == null)
            return "";
        if (text.length() <= max)
            return text;
        return text.substring(0, Math.max(0, max - 3)) + "...";
    }

    private List<String> wrapText(String text, int maxLen) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank())
            return lines;
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > maxLen) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0)
                    line.append(' ');
                line.append(word);
            }
        }
        if (line.length() > 0)
            lines.add(line.toString());
        return lines;
    }

    private void returnToMailSessionMenu(Player player, MailComposeSession session) {
        if (session == null)
            return;
        String menu = normalize(session.returnMenu);
        if (menu.equals("mailbox") || menu.equals("buzon")) {
            int mailboxPage = Math.max(0, session.returnPage);
            Bukkit.getScheduler().runTask(this, () -> openMailbox(player, mailboxPage));
            return;
        }
        int page = Math.max(1, session.returnPage);
        if (!menu.isBlank() && customMenus.containsKey(menu)) {
            Bukkit.getScheduler().runTask(this, () -> openCustomMenu(player, menu, page, "", 1));
        } else if (customMenus.containsKey("correo")) {
            Bukkit.getScheduler().runTask(this, () -> openCustomMenu(player, "correo", 1, "menuamigos", 1));
        }
    }

    private void sendPlayerNotFound(Player player, String input) {
        List<String> suggestions = similarPlayerSuggestions(input,
                getConfig().getInt("mail.name-suggestions-limit", 5));
        if (suggestions.isEmpty()) {
            msg(player, "mail-player-not-found");
        } else {
            msg(player, "mail-player-not-found-suggestions", Map.of("suggestions", String.join(", ", suggestions)));
        }
    }

    private List<String> similarPlayerSuggestions(String input, int limit) {
        if (input == null || input.isBlank())
            return Collections.emptyList();
        String raw = input.trim();
        String low = raw.toLowerCase(Locale.ROOT);
        Map<String, Integer> scores = new HashMap<>();
        for (OfflinePlayer off : Bukkit.getOfflinePlayers()) {
            String name = off.getName();
            if (name == null || name.isBlank())
                continue;
            String n = name.toLowerCase(Locale.ROOT);
            int score;
            if (n.equals(low))
                score = 0;
            else if (n.startsWith(low))
                score = 1;
            else if (n.contains(low))
                score = 2;
            else {
                int dist = levenshtein(low, n);
                int max = Math.max(2, Math.min(4, Math.max(low.length(), n.length()) / 3));
                if (dist > max)
                    continue;
                score = 10 + dist;
            }
            scores.put(name, score);
        }
        return scores.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .limit(Math.max(1, limit))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private int levenshtein(String a, String b) {
        if (a == null)
            a = "";
        if (b == null)
            b = "";
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++)
            prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }

    private void sendTitleHelp(CommandSender sender) {
        sender.sendMessage(color("&6MDVSocial title:"));
        sender.sendMessage(color("&e/mdvsocial title give <jugador> <titulo>"));
        sender.sendMessage(color("&e/mdvsocial title remove <jugador> <titulo>"));
        sender.sendMessage(color("&e/mdvsocial title set <jugador> <titulo>"));
        sender.sendMessage(color("&e/mdvsocial title clear <jugador>"));
        sender.sendMessage(color("&e/mdvsocial title punish <jugador> [titulo_castigo]"));
        sender.sendMessage(color("&e/mdvsocial title unpunish <jugador>"));
        sender.sendMessage(color("&e/mdvsocial title give-radius <radio> <titulo> &7(jugador)"));
        sender.sendMessage(color("&e/mdvsocial title give-near <world> <x> <y> <z> <radio> <titulo>"));
    }

    private int giveNear(Location center, double radius, String titleId) {
        int count = 0;
        double radiusSq = radius * radius;
        for (Player p : center.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(center) <= radiusSq) {
                giveTitle(p.getUniqueId(), p.getName(), titleId);
                msg(p, "given-title");
                count++;
            }
        }
        saveData();
        return count;
    }

    private void openMain(Player player) {
        Inventory inv = createMenu("MAIN", getMenuSize("main"), getMenuTitle("main"), 0);
        fill(inv);
        placeConfiguredMainButton(inv, "main-menu.titles", "OPEN_TITLES");
        placeConfiguredMainButton(inv, "main-menu.clan", "COMMANDS");
        placeConfiguredMainButton(inv, "main-menu.social", "COMMANDS");
        inv.setItem(inv.getSize() - 1, navItem("close", "CLOSE"));
        player.openInventory(inv);
    }

    private void openTitlesHome(Player player) {
        if (isBedrockPlayer(player)) {
            openBedrockTitlesHome(player);
            return;
        }
        Inventory inv = createMenu("TITLES_HOME", getMenuSize("titles"), getMenuTitle("titles"), 0);
        fill(inv);
        inv.setItem(getConfig().getInt("titles-menu.my-titles.slot", 10),
                simpleItemFromPath("titles-menu.my-titles", "OPEN_MY_TITLES"));
        inv.setItem(getConfig().getInt("titles-menu.shop.slot", 12),
                simpleItemFromPath("titles-menu.shop", "OPEN_SHOP"));
        inv.setItem(getConfig().getInt("titles-menu.locked.slot", 14),
                simpleItemFromPath("titles-menu.locked", "OPEN_LOCKED"));
        inv.setItem(getConfig().getInt("titles-menu.ranks.slot", 16),
                simpleItemFromPath("titles-menu.ranks", "OPEN_RANKS"));
        int clearSlot = getConfig().getInt("titles-menu.clear.slot", 22);
        inv.setItem(clearSlot, navItem("clear-title", "CLEAR_TITLE"));
        inv.setItem(inv.getSize() - 5, navItem("back", "OPEN_MAIN"));
        inv.setItem(inv.getSize() - 1, navItem("close", "CLOSE"));
        player.openInventory(inv);
    }

    private void openTitleList(Player player, String type, int page) {
        if (isBedrockPlayer(player)) {
            openBedrockTitleList(player, type, page);
            return;
        }
        String menuKey = switch (type) {
            case "MY_TITLES" -> "my-titles";
            case "SHOP" -> "title-shop";
            case "LOCKED" -> "locked-titles";
            default -> "my-titles";
        };
        Inventory inv = createMenu(type, getMenuSize(menuKey), getMenuTitle(menuKey), page);
        fill(inv);

        List<TitleDef> list = filteredTitles(player, type);
        list.sort(Comparator.comparing(t -> stripColor(t.display)));
        int perPage = listSlots.size();
        int maxPage = Math.max(0, (int) Math.ceil(list.size() / (double) perPage) - 1);
        page = Math.max(0, Math.min(page, maxPage));
        ((MenuHolder) inv.getHolder()).page = page;

        int start = page * perPage;
        for (int i = 0; i < perPage; i++) {
            int index = start + i;
            if (index >= list.size())
                break;
            int slot = listSlots.get(i);
            if (slot >= 0 && slot < inv.getSize()) {
                inv.setItem(slot, titleItem(player, list.get(index), type));
            }
        }

        if (page > 0)
            inv.setItem(inv.getSize() - 9, navItem("previous-page", "PREV_PAGE"));
        inv.setItem(inv.getSize() - 5, navItem("back", "OPEN_TITLES_HOME"));
        if (page < maxPage)
            inv.setItem(inv.getSize() - 1, navItem("next-page", "NEXT_PAGE"));
        else
            inv.setItem(inv.getSize() - 1, navItem("close", "CLOSE"));
        player.openInventory(inv);
    }

    private void openRanks(Player player, int page) {
        if (isBedrockPlayer(player)) {
            openBedrockRanks(player, page);
            return;
        }
        Map<Integer, Map<Integer, RankDef>> layout = buildRankLayout();
        int maxPage = Math.max(0, layout.keySet().stream().max(Integer::compareTo).orElse(0));
        page = Math.max(0, Math.min(page, maxPage));

        Inventory inv = createMenu("RANKS", getMenuSize("ranks"), getMenuTitle("ranks"), page);
        fill(inv);
        ((MenuHolder) inv.getHolder()).page = page;

        Map<Integer, RankDef> currentPage = layout.getOrDefault(page, Collections.emptyMap());
        for (Map.Entry<Integer, RankDef> entry : currentPage.entrySet()) {
            int slot = entry.getKey();
            if (slot >= 0 && slot < inv.getSize())
                inv.setItem(slot, rankItem(player, entry.getValue()));
        }

        if (page > 0)
            inv.setItem(inv.getSize() - 9, navItem("previous-page", "PREV_PAGE"));
        inv.setItem(inv.getSize() - 5, navItem("back", "OPEN_TITLES_HOME"));
        if (page < maxPage)
            inv.setItem(inv.getSize() - 1, navItem("next-page", "NEXT_PAGE"));
        else
            inv.setItem(inv.getSize() - 1, navItem("close", "CLOSE"));
        player.openInventory(inv);
    }

    private Map<Integer, Map<Integer, RankDef>> buildRankLayout() {
        Map<Integer, Map<Integer, RankDef>> layout = new LinkedHashMap<>();
        List<RankDef> configured = ranks.values().stream()
                .filter(rank -> rank.slot >= 0)
                .sorted(Comparator.comparingInt((RankDef rank) -> rank.page).thenComparing(rank -> rank.id))
                .collect(Collectors.toCollection(ArrayList::new));
        List<RankDef> automatic = ranks.values().stream()
                .filter(rank -> rank.slot < 0)
                .sorted(Comparator.comparing(rank -> stripColor(rank.display)))
                .collect(Collectors.toCollection(ArrayList::new));

        for (RankDef rank : configured) {
            int page = Math.max(0, rank.page - 1);
            Map<Integer, RankDef> pageLayout = layout.computeIfAbsent(page, ignored -> new LinkedHashMap<>());
            if (pageLayout.putIfAbsent(rank.slot, rank) != null) {
                getLogger().warning("Dos rangos intentan usar el slot " + rank.slot + " en la página " + rank.page
                        + ". Se conserva el primero.");
            }
        }

        int page = 0;
        for (RankDef rank : automatic) {
            while (true) {
                Map<Integer, RankDef> pageLayout = layout.computeIfAbsent(page, ignored -> new LinkedHashMap<>());
                Integer free = listSlots.stream().filter(slot -> !pageLayout.containsKey(slot)).findFirst()
                        .orElse(null);
                if (free != null) {
                    pageLayout.put(free, rank);
                    break;
                }
                page++;
            }
        }
        if (layout.isEmpty())
            layout.put(0, new LinkedHashMap<>());
        return layout;
    }

    // ==========================================================
    // BEDROCK - MENUS DINAMICOS NATIVOS (Floodgate/Cumulus)
    // ==========================================================

    private boolean sendBedrockSimpleForm(Player player, SimpleForm.Builder builder) {
        if (!isBedrockPlayer(player))
            return false;
        beginBedrockUiSession(player);
        try {
            return FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder);
        } catch (Throwable ex) {
            getLogger().warning("No se pudo enviar SimpleForm a " + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    private boolean sendBedrockCustomForm(Player player, CustomForm.Builder builder) {
        if (!isBedrockPlayer(player))
            return false;
        beginBedrockUiSession(player);
        try {
            return FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder);
        } catch (Throwable ex) {
            getLogger().warning("No se pudo enviar CustomForm a " + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }

    private String bedrockConfiguredButtonText(String path, String fallback) {
        ConfigurationSection sec = getConfig().getConfigurationSection(path);
        if (sec == null)
            return color(fallback);
        String name = sec.getString("name", fallback);
        List<String> lore = sec.getStringList("lore");
        String extra = lore.stream()
                .filter(line -> line != null && !line.isBlank())
                .filter(line -> !line.toLowerCase(Locale.ROOT).contains("click"))
                .findFirst().orElse("");
        return color(name + (extra.isBlank() ? "" : "\n" + extra));
    }

    private void openBedrockTitlesHome(Player player) {
        YamlConfiguration ui = bedrockDynamicUi("titulos");
        String activeId = getActiveTitleId(player.getUniqueId());
        TitleDef active = titles.get(activeId);
        String activeName = active == null
                ? getConfig().getString("settings.default-title", "forastero")
                : active.display;
        Map<String, String> tokens = Map.of(
                "active_title", activeName == null ? "" : activeName,
                "active_title_id", activeId == null ? "" : activeId);

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(bedrockUiText(ui, "title", "&6&lTítulos y Rangos", player, tokens))
                .content(bedrockUiLines(ui, "content",
                        List.of("&7Título equipado: &r{active_title}", "&7Elige una categoría."), player, tokens));
        List<Runnable> actions = new ArrayList<>();

        if (ui.getBoolean("buttons.my-titles.enabled", true)) {
            addBedrockDynamicButton(builder, ui, "buttons.my-titles",
                    bedrockUiText(ui, "buttons.my-titles.text", "&a&lMis títulos", player, tokens));
            actions.add(() -> openBedrockTitleList(player, "MY_TITLES", 0));
        }
        if (ui.getBoolean("buttons.shop.enabled", true)) {
            addBedrockDynamicButton(builder, ui, "buttons.shop",
                    bedrockUiText(ui, "buttons.shop.text", "&6&lTienda de títulos", player, tokens));
            actions.add(() -> openBedrockTitleList(player, "SHOP", 0));
        }
        if (ui.getBoolean("buttons.locked.enabled", true)) {
            addBedrockDynamicButton(builder, ui, "buttons.locked",
                    bedrockUiText(ui, "buttons.locked.text", "&c&lTítulos bloqueados", player, tokens));
            actions.add(() -> openBedrockTitleList(player, "LOCKED", 0));
        }
        if (ui.getBoolean("buttons.ranks.enabled", true)) {
            addBedrockDynamicButton(builder, ui, "buttons.ranks",
                    bedrockUiText(ui, "buttons.ranks.text", "&b&lRangos", player, tokens));
            actions.add(() -> openBedrockRanks(player, 0));
        }
        if (ui.getBoolean("buttons.clear.enabled", true)) {
            addBedrockDynamicButton(builder, ui, "buttons.clear",
                    bedrockUiText(ui, "buttons.clear.text", "&eQuitar título", player, tokens));
            actions.add(() -> {
                clearActiveTitle(player);
                openBedrockTitlesHome(player);
            });
        }
        addBedrockDynamicButton(builder, ui, "buttons.back",
                bedrockUiText(ui, "buttons.back.text", "&6Volver", player, tokens));
        actions.add(() -> openSocialStart(player));

        builder.validResultHandler(response -> runBedrockUiAction(player, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size())
                actions.get(index).run();
        }));
        sendBedrockSimpleForm(player, builder);
    }

    private void openBedrockTitleList(Player player, String type, int page) {
        YamlConfiguration ui = bedrockDynamicUi("titulos_lista");
        String section = switch (type) {
            case "SHOP" -> "shop";
            case "LOCKED" -> "locked";
            default -> "my-titles";
        };
        List<TitleDef> list = filteredTitles(player, type);
        list.sort(Comparator.comparing(t -> stripColor(t.display)));

        int perPage = getBedrockDynamicPageSize(player);
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
                .title(bedrockUiText(ui, base + ".title", defaultTitle, player, pageTokens))
                .content(list.isEmpty()
                        ? bedrockUiText(ui, base + ".empty", "&7No hay títulos en esta categoría.", player, pageTokens)
                        : bedrockUiLines(ui, base + ".content", List.of("&7Selecciona un título."), player,
                                pageTokens));
        List<Runnable> actions = new ArrayList<>();

        for (int i = start; i < end; i++) {
            TitleDef title = list.get(i);
            boolean active = getActiveTitleId(player.getUniqueId()).equals(title.id);
            String status;
            String requirement = title.unlockPermission == null || title.unlockPermission.isBlank()
                    ? "&cBloqueado"
                    : "&cRequiere: &7" + title.unlockPermission;
            if (type.equals("MY_TITLES"))
                status = active ? "&aEquipado" : "&eToca para equipar";
            else if (type.equals("SHOP"))
                status = "&6" + formatPrice(title.price) + " monedas";
            else
                status = requirement;

            Map<String, String> tokens = new HashMap<>(pageTokens);
            tokens.put("title_id", title.id);
            tokens.put("title_display", title.display);
            tokens.put("price", formatPrice(title.price));
            tokens.put("status", status);
            tokens.put("requirement", requirement);
            tokens.put("active", active ? "true" : "false");
            String genericEntryPath = base + ".entry";
            String specificEntryPath = "titles." + title.id;
            String entryPath = ui.isConfigurationSection(specificEntryPath) ? specificEntryPath : genericEntryPath;
            String genericTemplate = ui.getString(genericEntryPath + ".text", "{title_display}\n{status}");
            String text = bedrockUiText(ui, entryPath + ".text", genericTemplate, player, tokens);
            addBedrockDynamicButton(builder, ui, entryPath, text);

            if (type.equals("MY_TITLES")) {
                actions.add(() -> {
                    equipTitle(player, title.id);
                    openBedrockTitleList(player, type, safePage);
                });
            } else if (type.equals("SHOP")) {
                actions.add(() -> {
                    buyTitle(player, title.id);
                    openBedrockTitleList(player, type, safePage);
                });
            } else {
                actions.add(() -> {
                    msg(player, "title-locked");
                    openBedrockTitleList(player, type, safePage);
                });
            }
        }

        if (safePage > 0) {
            addBedrockDynamicButton(builder, ui, "buttons.previous",
                    bedrockUiText(ui, "buttons.previous.text", "&ePágina anterior", player, pageTokens));
            actions.add(() -> openBedrockTitleList(player, type, safePage - 1));
        }
        if (safePage < maxPage) {
            addBedrockDynamicButton(builder, ui, "buttons.next",
                    bedrockUiText(ui, "buttons.next.text", "&ePágina siguiente", player, pageTokens));
            actions.add(() -> openBedrockTitleList(player, type, safePage + 1));
        }
        addBedrockDynamicButton(builder, ui, "buttons.back",
                bedrockUiText(ui, "buttons.back.text", "&6Volver a Títulos", player, pageTokens));
        actions.add(() -> openBedrockTitlesHome(player));

        builder.validResultHandler(response -> runBedrockUiAction(player, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size())
                actions.get(index).run();
        }));
        sendBedrockSimpleForm(player, builder);
    }

    private void openBedrockRanks(Player player, int page) {
        YamlConfiguration ui = bedrockDynamicUi("rangos");
        Map<Integer, Map<Integer, RankDef>> layout = buildRankLayout();
        int maxPage = Math.max(0, layout.keySet().stream().max(Integer::compareTo).orElse(0));
        int safePage = Math.max(0, Math.min(page, maxPage));
        Map<Integer, RankDef> current = layout.getOrDefault(safePage, Collections.emptyMap());
        Map<String, String> pageTokens = Map.of(
                "page", String.valueOf(safePage + 1),
                "max_page", String.valueOf(maxPage + 1));

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(bedrockUiText(ui, "title", "&b&lRangos &8({page}/{max_page})", player, pageTokens))
                .content(bedrockUiLines(ui, "content", List.of("&7Tus rangos y requisitos actuales."), player,
                        pageTokens));
        List<Runnable> actions = new ArrayList<>();

        current.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            RankDef rank = entry.getValue();
            boolean owned = rank.permission == null || rank.permission.isBlank()
                    || player.hasPermission(rank.permission);
            Map<String, String> tokens = new HashMap<>(pageTokens);
            tokens.put("rank_id", rank.id);
            tokens.put("rank_display", rank.display);
            tokens.put("permission", rank.permission == null ? "" : rank.permission);
            tokens.put("status", owned ? "&aObtenido" : "&cNo obtenido");
            String path = owned ? "rank.owned" : "rank.locked";
            String specificPath = "ranks." + rank.id;
            String entryPath = ui.isConfigurationSection(specificPath) ? specificPath : path;
            String genericTemplate = ui.getString(path + ".text", "{rank_display}\n{status}");
            addBedrockDynamicButton(builder, ui, entryPath,
                    bedrockUiText(ui, entryPath + ".text", genericTemplate, player, tokens));
            actions.add(() -> openBedrockRanks(player, safePage));
        });

        if (safePage > 0) {
            addBedrockDynamicButton(builder, ui, "buttons.previous",
                    bedrockUiText(ui, "buttons.previous.text", "&ePágina anterior", player, pageTokens));
            actions.add(() -> openBedrockRanks(player, safePage - 1));
        }
        if (safePage < maxPage) {
            addBedrockDynamicButton(builder, ui, "buttons.next",
                    bedrockUiText(ui, "buttons.next.text", "&ePágina siguiente", player, pageTokens));
            actions.add(() -> openBedrockRanks(player, safePage + 1));
        }
        addBedrockDynamicButton(builder, ui, "buttons.back",
                bedrockUiText(ui, "buttons.back.text", "&6Volver a Títulos", player, pageTokens));
        actions.add(() -> openBedrockTitlesHome(player));

        builder.validResultHandler(response -> runBedrockUiAction(player, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size())
                actions.get(index).run();
        }));
        sendBedrockSimpleForm(player, builder);
    }

    private void openBedrockMailbox(Player player, int page) {
        if (!mailEnabled()) {
            msg(player, "mail-disabled");
            return;
        }
        if (!player.hasPermission("mdvsocial.mail.read")) {
            msg(player, "no-permission");
            return;
        }
        List<String> ids = getMailIds(player.getUniqueId());
        int perPage = getBedrockDynamicPageSize(player);
        int maxPage = Math.max(0, (int) Math.ceil(ids.size() / (double) perPage) - 1);
        int safePage = Math.max(0, Math.min(page, maxPage));
        int start = safePage * perPage;
        int end = Math.min(ids.size(), start + perPage);

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(color("&6&lBuzón") + " §8(" + (safePage + 1) + "/" + (maxPage + 1) + ")")
                .content(color("&7Cartas: &f" + ids.size() + "&7/&f" + getMailboxLimit(player)
                        + (ids.isEmpty() ? "\n&8No tienes cartas guardadas." : "\n&7Toca una carta para leerla.")));
        List<Runnable> actions = new ArrayList<>();

        for (int i = start; i < end; i++) {
            String id = ids.get(i);
            String base = mailPath(player.getUniqueId(), "letters." + id);
            String sender = mailData.getString(base + ".from-name", "Desconocido");
            String message = mailData.getString(base + ".message", "");
            boolean read = mailData.getBoolean(base + ".read", false);
            String label = (read ? "&eCarta de &f" : "&aNueva carta de &f") + sender
                    + "\n&8" + shorten(message, 38);
            builder.button(color(label));
            actions.add(() -> openBedrockMailRead(player, id, safePage));
        }

        if (safePage > 0) {
            builder.button(color("&ePágina anterior"));
            actions.add(() -> openBedrockMailbox(player, safePage - 1));
        }
        if (safePage < maxPage) {
            builder.button(color("&ePágina siguiente"));
            actions.add(() -> openBedrockMailbox(player, safePage + 1));
        }
        builder.button(color("&6Volver a Correo"));
        actions.add(() -> openCustomMenu(player, "correo", 1, "menuamigos", 1));

        builder.validResultHandler(response -> runBedrockUiAction(player, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size())
                actions.get(index).run();
        }));
        sendBedrockSimpleForm(player, builder);
    }

    private void openBedrockMailRead(Player player, String id, int page) {
        if (id == null || id.isBlank() || !mailData.contains(mailPath(player.getUniqueId(), "letters." + id))) {
            msg(player, "mail-not-found");
            openBedrockMailbox(player, page);
            return;
        }
        String base = mailPath(player.getUniqueId(), "letters." + id);
        mailData.set(base + ".read", true);
        saveMailData();

        String fromName = mailData.getString(base + ".from-name", "Desconocido");
        String fromUuid = mailData.getString(base + ".from-uuid", "");
        String message = mailData.getString(base + ".message", "");
        String mailType = mailData.getString(base + ".type", "");
        boolean clanInvite = "MDVCLANS_INVITE".equalsIgnoreCase(mailType);
        long sentAt = mailData.getLong(base + ".sent-at", 0L);
        long expiresAt = mailData.getLong(base + ".expires-at", 0L);

        String content = color("&7De: &f" + fromName
                + "\n&7Enviada: &e" + formatTime(sentAt)
                + "\n&7Expira: &c" + daysLeftText(expiresAt)
                + "\n\n&f" + message);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(color((clanInvite ? "&d&lInvitación de clan" : "&6&lCarta") + " &8- &f" + fromName))
                .content(content);
        List<Runnable> actions = new ArrayList<>();

        if (clanInvite) {
            builder.button(color("&aAceptar invitación"));
            actions.add(() -> handleClanInviteMailAction(player, id, true, page));
            builder.button(color("&cRechazar invitación"));
            actions.add(() -> handleClanInviteMailAction(player, id, false, page));
        } else {
            builder.button(color("&eResponder"));
            actions.add(() -> openBedrockMailReply(player, id, page));
            builder.button(color("&cEliminar carta"));
            actions.add(() -> confirmBedrockDeleteMail(player, id, page));
            if (fromUuid != null && !fromUuid.isBlank()) {
                builder.button(color("&4Bloquear remitente"));
                actions.add(() -> {
                    blockMailSender(player, fromUuid);
                    openBedrockMailbox(player, page);
                });
            }
        }
        builder.button(color("&6Volver al buzón"));
        actions.add(() -> openBedrockMailbox(player, page));

        builder.validResultHandler(response -> runBedrockUiAction(player, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size())
                actions.get(index).run();
        }));
        sendBedrockSimpleForm(player, builder);
    }

    private void confirmBedrockDeleteMail(Player player, String id, int page) {
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(color("&c&lEliminar carta"))
                .content(color("&7¿Seguro que quieres eliminar esta carta?"))
                .button(color("&cSí, eliminar"))
                .button(color("&aNo, volver"));
        builder.validResultHandler(response -> runBedrockUiAction(player, () -> {
            if (response.clickedButtonId() == 0) {
                deleteMail(player, id);
                openBedrockMailbox(player, page);
            } else {
                openBedrockMailRead(player, id, page);
            }
        }));
        sendBedrockSimpleForm(player, builder);
    }

    private void openBedrockPrivateMessage(Player player, String targetName,
            BedrockMenuManager.BedrockMenuContext context) {
        String safeName = targetName == null ? "" : targetName.trim();
        if (safeName.isBlank()) {
            msg(player, "social-target-not-found");
            return;
        }
        CustomForm.Builder builder = CustomForm.builder()
                .title(color("&b&lMensaje para &f" + safeName))
                .input(color("&eMensaje"), "Escribe tu mensaje privado...", "");
        builder.validResultHandler(response -> {
            String message = response.asInput(0);
            runBedrockUiAction(player, () -> {
                if (message == null || message.isBlank()) {
                    openCustomMenu(player, context.menuId, context.page, context.previousMenu, context.previousPage,
                            context.targetUuid, context.targetName, context.targetOnline);
                    return;
                }
                Bukkit.dispatchCommand(player, "msg " + safeName + " " + message.trim());
            });
        });
        sendBedrockCustomForm(player, builder);
    }

    private void openBedrockMailCompose(Player player, String returnMenu, int returnPage) {
        if (!mailEnabled()) {
            msg(player, "mail-disabled");
            return;
        }
        if (!player.hasPermission("mdvsocial.mail.send")) {
            msg(player, "no-permission");
            return;
        }
        CustomForm.Builder builder = CustomForm.builder()
                .title(color("&6&lEnviar carta"))
                .input(color("&eDestinatario"), "Nombre del jugador", "")
                .input(color("&eMensaje"), "Escribe tu carta...", "");
        builder.validResultHandler(response -> {
            String target = response.asInput(0);
            String message = response.asInput(1);
            runBedrockUiAction(player, () -> {
                if (target == null || target.isBlank()) {
                    msg(player, "mail-player-not-found");
                    openBedrockMailCompose(player, returnMenu, returnPage);
                    return;
                }
                sendMailByName(player, target.trim(), message == null ? "" : message.trim());
                returnFromBedrockMail(player, returnMenu, returnPage);
            });
        });
        sendBedrockCustomForm(player, builder);
    }

    private void openBedrockMailMessageToTarget(Player player, UUID targetUuid, String fallbackName,
            String returnMenu, int returnPage) {
        if (!mailEnabled()) {
            msg(player, "mail-disabled");
            return;
        }
        if (!player.hasPermission("mdvsocial.mail.send")) {
            msg(player, "no-permission");
            return;
        }
        if (targetUuid == null) {
            msg(player, "social-target-not-found");
            return;
        }
        if (targetUuid.equals(player.getUniqueId())) {
            msg(player, "mail-self");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        String targetName = target.getName() == null || target.getName().isBlank() ? fallbackName : target.getName();
        if (targetName == null || targetName.isBlank())
            targetName = "jugador";
        final String safeTargetName = targetName;

        CustomForm.Builder builder = CustomForm.builder()
                .title(color("&6&lCarta para &f" + safeTargetName))
                .input(color("&eMensaje &7(máx. " + getMaxMailLength() + ")"), "Escribe tu carta...", "");
        builder.validResultHandler(response -> {
            String message = response.asInput(0);
            runBedrockUiAction(player, () -> {
                sendMailByUuid(player, targetUuid, safeTargetName, message == null ? "" : message.trim());
                returnFromBedrockMail(player, returnMenu, returnPage);
            });
        });
        sendBedrockCustomForm(player, builder);
    }

    private void openBedrockMailBlockForm(Player player, boolean block, String returnMenu, int returnPage) {
        CustomForm.Builder builder = CustomForm.builder()
                .title(color(block ? "&c&lBloquear cartas" : "&a&lDesbloquear cartas"))
                .input(color("&eJugador"), "Nombre del jugador", "");
        builder.validResultHandler(response -> {
            String target = response.asInput(0);
            runBedrockUiAction(player, () -> {
                if (target == null || target.isBlank()) {
                    returnFromBedrockMail(player, returnMenu, returnPage);
                    return;
                }
                if (block)
                    blockMailByName(player, target.trim());
                else
                    unblockMailByName(player, target.trim());
                returnFromBedrockMail(player, returnMenu, returnPage);
            });
        });
        sendBedrockCustomForm(player, builder);
    }

    private void openBedrockMailReply(Player player, String mailId, int returnPage) {
        if (mailId == null || mailId.isBlank()
                || !mailData.contains(mailPath(player.getUniqueId(), "letters." + mailId))) {
            msg(player, "mail-not-found");
            openBedrockMailbox(player, returnPage);
            return;
        }
        String base = mailPath(player.getUniqueId(), "letters." + mailId);
        String fromUuidText = mailData.getString(base + ".from-uuid", "");
        String fromName = mailData.getString(base + ".from-name",
                getConfig().getString("mail.server-author-name", "MDVCRAFT"));
        if (fromUuidText == null || fromUuidText.isBlank()) {
            msg(player, "mail-cannot-reply-server");
            openBedrockMailRead(player, mailId, returnPage);
            return;
        }
        try {
            UUID targetUuid = UUID.fromString(fromUuidText);
            openBedrockMailMessageToTarget(player, targetUuid, fromName, "MAILBOX", returnPage);
        } catch (Exception ignored) {
            msg(player, "mail-cannot-reply-server");
            openBedrockMailRead(player, mailId, returnPage);
        }
    }

    private void returnFromBedrockMail(Player player, String returnMenu, int returnPage) {
        if (player == null || !player.isOnline())
            return;
        if (returnMenu != null && returnMenu.equalsIgnoreCase("MAILBOX")) {
            openBedrockMailbox(player, Math.max(0, returnPage));
            return;
        }
        String menu = returnMenu == null || returnMenu.isBlank() ? "correo" : returnMenu;
        openCustomMenu(player, menu, Math.max(1, returnPage), "", 1);
    }

    private void openBedrockFriends(Player player, int page) {
        YamlConfiguration ui = bedrockDynamicUi("amigos_lista");
        List<UUID> friends = getMMOCoreFriendUuids(player);
        friends.sort(Comparator.comparing(uuid -> {
            OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
            return off.getName() == null ? uuid.toString() : off.getName().toLowerCase(Locale.ROOT);
        }));
        List<Object> pending = getMMOCorePendingRequests(player, "FriendRequest");
        int perPage = getBedrockDynamicPageSize(player);
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
                .title(bedrockUiText(ui, "title", "&a&lLista de Amigos", player, tokens)
                        + color(" &8(" + (safePage + 1) + "/" + (maxPage + 1) + ")"))
                .content(bedrockUiLines(ui, "content",
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
            String text = bedrockUiText(ui, path + ".text",
                    (isOnline ? "&a● &f" : "&8● &7") + "{friend_name}", player, friendTokens);
            addBedrockDynamicButton(builder, ui, path, text);
            actions.add(() -> openCustomMenu(player, "amigo_opciones", 1, "menuamigos", 1,
                    uuid, name, isOnline));
        }

        if (ui.getBoolean("buttons.add.enabled", true)) {
            addBedrockDynamicButton(builder, ui, "buttons.add",
                    bedrockUiText(ui, "buttons.add.text", "&a&lAñadir amigo", player, tokens));
            actions.add(() -> openBedrockFriendAddForm(player, safePage));
        }
        if (ui.getBoolean("buttons.requests.enabled", true)) {
            addBedrockDynamicButton(builder, ui, "buttons.requests",
                    bedrockUiText(ui, "buttons.requests.text", "&eSolicitudes ({request_count})", player, tokens));
            actions.add(() -> openBedrockFriendRequests(player, safePage));
        }
        if (safePage > 0) {
            addBedrockDynamicButton(builder, ui, "buttons.previous",
                    bedrockUiText(ui, "buttons.previous.text", "&ePágina anterior", player, tokens));
            actions.add(() -> openBedrockFriends(player, safePage - 1));
        }
        if (safePage < maxPage) {
            addBedrockDynamicButton(builder, ui, "buttons.next",
                    bedrockUiText(ui, "buttons.next.text", "&ePágina siguiente", player, tokens));
            actions.add(() -> openBedrockFriends(player, safePage + 1));
        }
        addBedrockDynamicButton(builder, ui, "buttons.back",
                bedrockUiText(ui, "buttons.back.text", "&6Volver", player, tokens));
        actions.add(() -> openCustomMenu(player, "menuamigos", 1, "main", 1));

        builder.validResultHandler(response -> runBedrockUiAction(player, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size())
                actions.get(index).run();
        }));
        sendBedrockSimpleForm(player, builder);
    }

    private void openBedrockFriendAddForm(Player player, int returnPage) {
        YamlConfiguration ui = bedrockDynamicUi("amigos_lista");
        CustomForm.Builder builder = CustomForm.builder()
                .title(bedrockUiText(ui, "add-form.title", "&a&lAñadir amigo", player, Map.of()))
                .input(bedrockUiText(ui, "add-form.input-label", "&eNombre del jugador", player, Map.of()),
                        stripBedrockFormatting(ui.getString("add-form.input-placeholder", "Escribe el nombre...")), "");
        builder.validResultHandler(response -> {
            String rawName = response.asInput(0);
            runBedrockUiAction(player, () -> {
                Player target = findOnlinePlayerIgnoreCase(rawName);
                if (target == null) {
                    msg(player, "friend-target-not-found");
                    openBedrockFriendAddForm(player, returnPage);
                    return;
                }
                inviteMMOCoreFriend(player, target.getUniqueId(), target.getName());
                openBedrockFriends(player, returnPage);
            });
        });
        sendBedrockCustomForm(player, builder);
    }

    private void openBedrockFriendRequests(Player player, int returnPage) {
        YamlConfiguration ui = bedrockDynamicUi("amigos_lista");
        List<Object> requests = getMMOCorePendingRequests(player, "FriendRequest");
        Map<String, String> tokens = Map.of("request_count", String.valueOf(requests.size()));
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(bedrockUiText(ui, "requests-menu.title", "&e&lSolicitudes de Amistad", player, tokens))
                .content(requests.isEmpty()
                        ? bedrockUiText(ui, "requests-menu.empty", "&7No tienes solicitudes pendientes.", player,
                                tokens)
                        : bedrockUiText(ui, "requests-menu.content", "&7Solicitudes pendientes: &f{request_count}",
                                player, tokens));
        List<Runnable> actions = new ArrayList<>();
        for (Object request : requests) {
            String creator = getMMOCoreRequestCreatorName(request);
            Map<String, String> requestTokens = Map.of("creator", creator, "request_count",
                    String.valueOf(requests.size()));
            addBedrockDynamicButton(builder, ui, "requests-menu.request",
                    bedrockUiText(ui, "requests-menu.request.text", "&e{creator}", player, requestTokens));
            actions.add(() -> openBedrockFriendRequestDetail(player, request, returnPage));
        }
        addBedrockDynamicButton(builder, ui, "requests-menu.back",
                bedrockUiText(ui, "requests-menu.back", "&6Volver a Amigos", player, tokens));
        actions.add(() -> openBedrockFriends(player, returnPage));
        builder.validResultHandler(response -> runBedrockUiAction(player, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size())
                actions.get(index).run();
        }));
        sendBedrockSimpleForm(player, builder);
    }

    private void openBedrockFriendRequestDetail(Player player, Object request, int returnPage) {
        YamlConfiguration ui = bedrockDynamicUi("amigos_lista");
        String creator = getMMOCoreRequestCreatorName(request);
        Map<String, String> tokens = Map.of("creator", creator);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(bedrockUiText(ui, "requests-menu.detail.title", "&e&lSolicitud de {creator}", player, tokens))
                .content(bedrockUiText(ui, "requests-menu.detail.content",
                        "&7¿Quieres aceptar la solicitud de amistad de &f{creator}&7?", player, tokens));
        addBedrockDynamicButton(builder, ui, "requests-menu.detail.accept",
                bedrockUiText(ui, "requests-menu.detail.accept", "&aAceptar solicitud", player, tokens));
        addBedrockDynamicButton(builder, ui, "requests-menu.detail.deny",
                bedrockUiText(ui, "requests-menu.detail.deny", "&cRechazar solicitud", player, tokens));
        addBedrockDynamicButton(builder, ui, "requests-menu.detail.back",
                bedrockUiText(ui, "requests-menu.detail.back", "&6Volver", player, tokens));
        builder.validResultHandler(response -> runBedrockUiAction(player, () -> {
            int index = response.clickedButtonId();
            if (index == 0) {
                resolveMMOCoreRequest(player, request, true);
                openBedrockFriends(player, returnPage);
            } else if (index == 1) {
                resolveMMOCoreRequest(player, request, false);
                openBedrockFriendRequests(player, returnPage);
            } else {
                openBedrockFriendRequests(player, returnPage);
            }
        }));
        sendBedrockSimpleForm(player, builder);
    }

    private void openBedrockRemoveFriendConfirm(Player player, UUID targetUuid, String targetName,
            boolean targetOnline) {
        if (targetUuid == null) {
            msg(player, "social-target-not-found");
            return;
        }
        YamlConfiguration ui = bedrockDynamicUi("amigo_opciones");
        String name = targetName == null || targetName.isBlank()
                ? Bukkit.getOfflinePlayer(targetUuid).getName()
                : targetName;
        if (name == null || name.isBlank())
            name = targetUuid.toString().substring(0, 8);
        Map<String, String> tokens = Map.of("target", name);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(bedrockUiText(ui, "remove-friend-confirm.title", "&c&lEliminar a {target}", player, tokens))
                .content(bedrockUiText(ui, "remove-friend-confirm.content",
                        "&7¿Seguro que quieres eliminar a &f{target} &7de tus amigos?", player, tokens));
        addBedrockDynamicButton(builder, ui, "remove-friend-confirm.confirm",
                bedrockUiText(ui, "remove-friend-confirm.confirm.text", "&cEliminar amigo", player, tokens));
        addBedrockDynamicButton(builder, ui, "remove-friend-confirm.back",
                bedrockUiText(ui, "remove-friend-confirm.back.text", "&6Volver", player, tokens));
        String finalName = name;
        builder.validResultHandler(response -> runBedrockUiAction(player, () -> {
            if (response.clickedButtonId() == 0) {
                removeMMOCoreFriend(player, targetUuid, finalName);
                openBedrockFriends(player, 0);
            } else {
                openCustomMenu(player, "amigo_opciones", 1, "menuamigos", 1,
                        targetUuid, finalName, targetOnline);
            }
        }));
        sendBedrockSimpleForm(player, builder);
    }

    private List<UUID> getMMOCoreFriendUuids(Player player) {
        if (player == null || !Bukkit.getPluginManager().isPluginEnabled("MMOCore"))
            return new ArrayList<>();
        try {
            Object data = getMMOCorePlayerData(player);
            if (data == null)
                return new ArrayList<>();
            for (String methodName : List.of("getFriends", "getFriendList", "getFriendUUIDs", "getFriendUuids")) {
                try {
                    Method method = data.getClass().getMethod(methodName);
                    Object result = method.invoke(data);
                    List<UUID> parsed = extractUuids(result);
                    if (!parsed.isEmpty())
                        return parsed;
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ex) {
            getLogger().fine("No se pudo leer lista de amigos MMOCore para Bedrock: " + ex.getMessage());
        }
        return new ArrayList<>();
    }

    private List<UUID> extractUuids(Object value) {
        List<UUID> out = new ArrayList<>();
        if (value == null)
            return out;
        if (value instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                UUID uuid = extractUuid(key);
                if (uuid != null)
                    out.add(uuid);
            }
            for (Object val : map.values()) {
                UUID uuid = extractUuid(val);
                if (uuid != null && !out.contains(uuid))
                    out.add(uuid);
            }
            return out;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                UUID uuid = extractUuid(item);
                if (uuid != null && !out.contains(uuid))
                    out.add(uuid);
            }
            return out;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                UUID uuid = extractUuid(java.lang.reflect.Array.get(value, i));
                if (uuid != null && !out.contains(uuid))
                    out.add(uuid);
            }
            return out;
        }
        UUID single = extractUuid(value);
        if (single != null)
            out.add(single);
        return out;
    }

    private UUID extractUuid(Object value) {
        if (value == null)
            return null;
        if (value instanceof UUID uuid)
            return uuid;
        if (value instanceof Player p)
            return p.getUniqueId();
        if (value instanceof OfflinePlayer p)
            return p.getUniqueId();
        if (value instanceof String str) {
            try {
                return UUID.fromString(str);
            } catch (Exception ignored) {
                OfflinePlayer off = findKnownOfflinePlayer(str);
                return off == null ? null : off.getUniqueId();
            }
        }
        for (String methodName : List.of("getUniqueId", "getUniqueID", "getUUID", "getUuid")) {
            try {
                Method method = value.getClass().getMethod(methodName);
                Object result = method.invoke(value);
                UUID uuid = extractUuid(result);
                if (uuid != null)
                    return uuid;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private void openBedrockParty(Player player) {
        YamlConfiguration ui = bedrockDynamicUi("party");
        Object party = getMMOCoreParty(player);
        int max = getConfiguredPartyMaxMembers();
        int count = countMMOCorePartyMembers(party);
        List<String> members = getMMOCorePartyMemberNames(party);
        List<Object> pending = getMMOCorePendingRequests(player, "PartyInvite");
        Map<String, String> tokens = Map.of(
                "member_count", String.valueOf(count),
                "max_members", String.valueOf(max),
                "request_count", String.valueOf(pending.size()));

        String contentPath = party == null ? "content-no-party" : "content-party";
        List<String> defaults = party == null
                ? List.of("&7No perteneces a un Grupo de Aventura.", "&7Puedes crear uno o aceptar una invitación.")
                : List.of("&7Integrantes: &f{member_count}&7/&f{max_members}");
        StringBuilder content = new StringBuilder(bedrockUiLines(ui, contentPath, defaults, player, tokens));
        if (party != null) {
            for (String member : members) {
                Map<String, String> memberTokens = new HashMap<>(tokens);
                memberTokens.put("member_name", member);
                content.append('\n')
                        .append(bedrockUiText(ui, "member-line", "&7• &f{member_name}", player, memberTokens));
            }
        }

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(bedrockUiText(ui, "title", "&d&lGrupo de Aventura", player, tokens))
                .content(content.toString());
        List<Runnable> actions = new ArrayList<>();

        if (party == null && ui.getBoolean("buttons.create.enabled", true)) {
            addBedrockDynamicButton(builder, ui, "buttons.create",
                    bedrockUiText(ui, "buttons.create.text", "&aCrear Grupo de Aventura", player, tokens));
            actions.add(() -> {
                try {
                    Object data = getMMOCorePlayerData(player);
                    Object created = createMMOCoreParty(data);
                    if (created != null) {
                        syncScoreboardPartyPermission(player);
                        msg(player, "party-auto-created");
                    } else {
                        msg(player, "party-error");
                    }
                } catch (Throwable ex) {
                    msg(player, "party-error");
                }
                openBedrockParty(player);
            });
        }
        if (ui.getBoolean("buttons.invite.enabled", true)) {
            addBedrockDynamicButton(builder, ui, "buttons.invite",
                    bedrockUiText(ui, "buttons.invite.text", "&d&lInvitar jugador", player, tokens));
            actions.add(() -> openBedrockPartyInviteForm(player));
        }
        if (ui.getBoolean("buttons.invite-friends.enabled", true)) {
            addBedrockDynamicButton(builder, ui, "buttons.invite-friends",
                    bedrockUiText(ui, "buttons.invite-friends.text", "&aInvitar desde Amigos", player, tokens));
            actions.add(() -> openBedrockFriends(player, 0));
        }
        if (ui.getBoolean("buttons.requests.enabled", true)) {
            addBedrockDynamicButton(builder, ui, "buttons.requests",
                    bedrockUiText(ui, "buttons.requests.text", "&eInvitaciones ({request_count})", player, tokens));
            actions.add(() -> openBedrockPartyRequests(player));
        }
        if (party != null && ui.getBoolean("buttons.leave.enabled", true)) {
            addBedrockDynamicButton(builder, ui, "buttons.leave",
                    bedrockUiText(ui, "buttons.leave.text", "&c&lSalir del grupo", player, tokens));
            actions.add(() -> openBedrockPartyLeaveConfirm(player));
        }
        addBedrockDynamicButton(builder, ui, "buttons.back",
                bedrockUiText(ui, "buttons.back.text", "&6Volver", player, tokens));
        actions.add(() -> openCustomMenu(player, "menuamigos", 1, "main", 1));

        builder.validResultHandler(response -> runBedrockUiAction(player, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size())
                actions.get(index).run();
        }));
        sendBedrockSimpleForm(player, builder);
    }

    private void openBedrockPartyLeaveConfirm(Player player) {
        YamlConfiguration ui = bedrockDynamicUi("party");
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(bedrockUiText(ui, "leave-confirm.title", "&c&lSalir del Grupo", player, Map.of()))
                .content(bedrockUiText(ui, "leave-confirm.content",
                        "&7¿Seguro que quieres abandonar tu Grupo de Aventura?", player, Map.of()));
        addBedrockDynamicButton(builder, ui, "leave-confirm.confirm",
                bedrockUiText(ui, "leave-confirm.confirm.text", "&cSí, salir del grupo", player, Map.of()));
        addBedrockDynamicButton(builder, ui, "leave-confirm.back",
                bedrockUiText(ui, "leave-confirm.back.text", "&6Volver", player, Map.of()));
        builder.validResultHandler(response -> runBedrockUiAction(player, () -> {
            if (response.clickedButtonId() == 0) {
                leaveMMOCoreParty(player);
                openBedrockParty(player);
            } else {
                openBedrockParty(player);
            }
        }));
        sendBedrockSimpleForm(player, builder);
    }

    private void openBedrockPartyInviteForm(Player player) {
        YamlConfiguration ui = bedrockDynamicUi("party");
        CustomForm.Builder builder = CustomForm.builder()
                .title(bedrockUiText(ui, "invite-form.title", "&d&lInvitar al Grupo", player, Map.of()))
                .input(bedrockUiText(ui, "invite-form.input-label", "&eNombre del jugador", player, Map.of()),
                        stripBedrockFormatting(ui.getString("invite-form.input-placeholder", "Escribe el nombre...")),
                        "");
        builder.validResultHandler(response -> {
            String rawName = response.asInput(0);
            runBedrockUiAction(player, () -> {
                Player target = findOnlinePlayerIgnoreCase(rawName);
                if (target == null) {
                    msg(player, "party-target-not-found");
                    openBedrockPartyInviteForm(player);
                    return;
                }
                invitePlayerToPartyFromBedrockForm(player, target);
                openBedrockParty(player);
            });
        });
        sendBedrockCustomForm(player, builder);
    }

    private void openBedrockPartyRequests(Player player) {
        YamlConfiguration ui = bedrockDynamicUi("party");
        List<Object> requests = getMMOCorePendingRequests(player, "PartyInvite");
        Map<String, String> tokens = Map.of("request_count", String.valueOf(requests.size()));
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(bedrockUiText(ui, "requests-menu.title", "&e&lInvitaciones a Grupos", player, tokens))
                .content(requests.isEmpty()
                        ? bedrockUiText(ui, "requests-menu.empty", "&7No tienes invitaciones pendientes.", player,
                                tokens)
                        : bedrockUiText(ui, "requests-menu.content", "&7Invitaciones pendientes: &f{request_count}",
                                player, tokens));
        List<Runnable> actions = new ArrayList<>();
        for (Object request : requests) {
            String creator = getMMOCoreRequestCreatorName(request);
            Map<String, String> requestTokens = Map.of("creator", creator, "request_count",
                    String.valueOf(requests.size()));
            addBedrockDynamicButton(builder, ui, "requests-menu.request",
                    bedrockUiText(ui, "requests-menu.request.text", "&d{creator}", player, requestTokens));
            actions.add(() -> openBedrockPartyRequestDetail(player, request));
        }
        addBedrockDynamicButton(builder, ui, "requests-menu.back",
                bedrockUiText(ui, "requests-menu.back", "&6Volver al Grupo", player, tokens));
        actions.add(() -> openBedrockParty(player));
        builder.validResultHandler(response -> runBedrockUiAction(player, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size())
                actions.get(index).run();
        }));
        sendBedrockSimpleForm(player, builder);
    }

    private void openBedrockPartyRequestDetail(Player player, Object request) {
        YamlConfiguration ui = bedrockDynamicUi("party");
        String creator = getMMOCoreRequestCreatorName(request);
        Map<String, String> tokens = Map.of("creator", creator);
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(bedrockUiText(ui, "requests-menu.detail.title", "&d&lInvitación de {creator}", player, tokens))
                .content(bedrockUiText(ui, "requests-menu.detail.content",
                        "&7¿Quieres unirte al Grupo de Aventura de &f{creator}&7?", player, tokens));
        addBedrockDynamicButton(builder, ui, "requests-menu.detail.accept",
                bedrockUiText(ui, "requests-menu.detail.accept", "&aAceptar invitación", player, tokens));
        addBedrockDynamicButton(builder, ui, "requests-menu.detail.deny",
                bedrockUiText(ui, "requests-menu.detail.deny", "&cRechazar invitación", player, tokens));
        addBedrockDynamicButton(builder, ui, "requests-menu.detail.back",
                bedrockUiText(ui, "requests-menu.detail.back", "&6Volver", player, tokens));
        builder.validResultHandler(response -> runBedrockUiAction(player, () -> {
            int index = response.clickedButtonId();
            if (index == 0) {
                if (resolveMMOCoreRequest(player, request, true)) {
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (!player.isOnline())
                            return;
                        player.closeInventory();
                        syncScoreboardPartyPermission(player);
                        openBedrockParty(player);
                    }, 2L);
                } else {
                    openBedrockPartyRequests(player);
                }
            } else if (index == 1) {
                if (resolveMMOCoreRequest(player, request, false))
                    msg(player, "party-request-denied", Map.of("target", creator));
                openBedrockPartyRequests(player);
            } else {
                openBedrockPartyRequests(player);
            }
        }));
        sendBedrockSimpleForm(player, builder);
    }

    long beginBedrockUiSession(Player player) {
        if (player == null)
            return 0L;
        long sessionId = System.nanoTime();
        bedrockUiSession.put(player.getUniqueId(), sessionId);
        return sessionId;
    }

    void runBedrockUiAction(Player player, Runnable action) {
        if (player == null || action == null || !player.isOnline())
            return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long debounce = Math.max(0L, getConfig().getLong("bedrock.mobile-safety.click-debounce-ms", 120L));
        Long previous = bedrockUiLastAction.put(uuid, now);
        if (previous != null && now - previous < debounce)
            return;

        Long sessionId = bedrockUiSession.get(uuid);
        int generation = bedrockUiActionGeneration.merge(uuid, 1, Integer::sum);
        long delay = Math.max(0L, getConfig().getLong("bedrock.mobile-safety.action-delay-ticks", 2L));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline())
                return;

            Long activeSession = bedrockUiSession.get(uuid);
            if (sessionId != null && (activeSession == null || !activeSession.equals(sessionId)))
                return;

            Integer currentGeneration = bedrockUiActionGeneration.get(uuid);
            if (currentGeneration == null || currentGeneration != generation)
                return;

            action.run();
        }, delay);
    }

    private int getBedrockDynamicPageSize(Player player) {
        int standard = Math.max(4, Math.min(15, getConfig().getInt("bedrock.dynamic-page-size", 8)));
        if (!getConfig().getBoolean("bedrock.mobile-safety.enabled", true) || !isBedrockTouchClient(player))
            return standard;
        return Math.max(4, Math.min(10, getConfig().getInt("bedrock.mobile-safety.dynamic-page-size", 5)));
    }

    private boolean isBedrockTouchClient(Player player) {
        if (player == null || !isBedrockPlayer(player))
            return false;
        try {
            Object floodgatePlayer = FloodgateApi.getInstance().getPlayer(player.getUniqueId());
            if (floodgatePlayer == null)
                return false;
            for (String methodName : List.of("getInputMode", "getDeviceOs", "getDeviceOS")) {
                try {
                    Method method = floodgatePlayer.getClass().getMethod(methodName);
                    Object value = method.invoke(floodgatePlayer);
                    String name = value == null ? "" : value.toString().toUpperCase(Locale.ROOT);
                    if (name.contains("TOUCH") || name.contains("ANDROID") || name.contains("IOS")
                            || name.contains("FIRE_OS") || name.contains("FIREOS"))
                        return true;
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private YamlConfiguration bedrockDynamicUi(String menuId) {
        YamlConfiguration ui = getBedrockMenuConfig(menuId);
        return ui == null ? new YamlConfiguration() : ui;
    }

    private String bedrockUiText(YamlConfiguration ui, String path, String def, Player player,
            Map<String, String> tokens) {
        String raw = ui == null ? def : ui.getString(path, def);
        return color(replaceBedrockTokens(raw, player, tokens));
    }

    private String bedrockUiLines(YamlConfiguration ui, String path, List<String> defaults, Player player,
            Map<String, String> tokens) {
        List<String> lines = ui == null ? Collections.emptyList() : ui.getStringList(path);
        if (lines.isEmpty())
            lines = defaults;
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (out.length() > 0)
                out.append('\n');
            out.append(color(replaceBedrockTokens(line, player, tokens)));
        }
        return out.toString();
    }

    private String replaceBedrockTokens(String raw, Player player, Map<String, String> tokens) {
        String text = raw == null ? "" : raw;
        if (tokens != null) {
            for (Map.Entry<String, String> entry : tokens.entrySet())
                text = text.replace("{" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
        }
        return applyPlayerPlaceholders(text, player);
    }

    private void addBedrockDynamicButton(SimpleForm.Builder builder, YamlConfiguration ui, String path, String text) {
        String data = ui == null ? "" : ui.getString(path + ".image.data", "");
        if (data == null || data.isBlank()) {
            builder.button(text);
            return;
        }
        String type = ui.getString(path + ".image.type", "URL");
        builder.button(text, "PATH".equalsIgnoreCase(type) ? FormImage.Type.PATH : FormImage.Type.URL, data);
    }

    private String stripBedrockFormatting(String text) {
        if (text == null)
            return "";
        return ChatColor.stripColor(color(text));
    }

    private Player findOnlinePlayerIgnoreCase(String rawName) {
        if (rawName == null || rawName.isBlank())
            return null;
        String name = rawName.trim();
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null)
            return exact;
        for (Player online : Bukkit.getOnlinePlayers())
            if (online.getName().equalsIgnoreCase(name))
                return online;
        return null;
    }

    private List<Object> getMMOCorePendingRequests(Player player, String simpleClassName) {
        List<Object> out = new ArrayList<>();
        if (player == null || simpleClassName == null || !Bukkit.getPluginManager().isPluginEnabled("MMOCore"))
            return out;
        try {
            Object targetData = getMMOCorePlayerData(player);
            Object requestManager = getMMOCoreRequestManager();
            if (targetData == null || requestManager == null)
                return out;

            Collection<?> values = getMMOCoreRequestValues(requestManager);
            if (values.isEmpty()) {
                Object one = findSingleMMOCoreRequest(requestManager, targetData, simpleClassName);
                if (one != null)
                    values = List.of(one);
            }
            for (Object request : values) {
                if (request == null || !request.getClass().getSimpleName().equalsIgnoreCase(simpleClassName))
                    continue;
                try {
                    Method timedOut = request.getClass().getMethod("isTimedOut");
                    Object expired = timedOut.invoke(request);
                    if (expired instanceof Boolean b && b)
                        continue;
                } catch (Throwable ignored) {
                }
                try {
                    Method getTarget = request.getClass().getMethod("getTarget");
                    Object target = getTarget.invoke(request);
                    if (targetData.equals(target))
                        out.add(request);
                } catch (Throwable ignored) {
                }
            }
            out.sort(Comparator.comparing(this::getMMOCoreRequestCreatorName, String.CASE_INSENSITIVE_ORDER));
        } catch (Throwable ex) {
            getLogger().fine("No se pudieron leer solicitudes MMOCore para Bedrock: " + ex.getMessage());
        }
        return out;
    }

    private Object findSingleMMOCoreRequest(Object requestManager, Object targetData, String simpleClassName) {
        try {
            String className = switch (simpleClassName) {
                case "FriendRequest" -> "net.Indyuce.mmocore.api.player.social.FriendRequest";
                case "PartyInvite" -> "net.Indyuce.mmocore.party.provided.PartyInvite";
                default -> "";
            };
            if (className.isBlank())
                return null;
            Class<?> requestClass = Class.forName(className);
            for (Method method : requestManager.getClass().getMethods()) {
                if (!method.getName().equals("findRequest") || method.getParameterCount() != 2)
                    continue;
                return method.invoke(requestManager, targetData, requestClass);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private Object getMMOCoreRequestManager() throws Exception {
        Class<?> mmocoreClass = Class.forName("net.Indyuce.mmocore.MMOCore");
        Object mmocore = mmocoreClass.getField("plugin").get(null);
        if (mmocore == null)
            return null;
        return mmocoreClass.getField("requestManager").get(mmocore);
    }

    private Collection<?> getMMOCoreRequestValues(Object requestManager) throws Exception {
        Field field;
        try {
            field = requestManager.getClass().getDeclaredField("requests");
        } catch (NoSuchFieldException ex) {
            field = null;
            for (Field candidate : requestManager.getClass().getDeclaredFields()) {
                if (Map.class.isAssignableFrom(candidate.getType())) {
                    field = candidate;
                    break;
                }
            }
            if (field == null)
                return Collections.emptyList();
        }
        field.setAccessible(true);
        Object value = field.get(requestManager);
        if (value instanceof Map<?, ?> map)
            return new ArrayList<>(map.values());
        if (value instanceof Collection<?> collection)
            return new ArrayList<>(collection);
        return Collections.emptyList();
    }

    private String getMMOCoreRequestCreatorName(Object request) {
        if (request == null)
            return "Jugador";
        try {
            Method getCreator = request.getClass().getMethod("getCreator");
            Object creator = getCreator.invoke(request);
            String name = getMMOCorePlayerDataName(creator);
            return name == null || name.isBlank() ? "Jugador" : name;
        } catch (Throwable ignored) {
            return "Jugador";
        }
    }

    private boolean resolveMMOCoreRequest(Player player, Object request, boolean accept) {
        if (player == null || request == null)
            return false;
        try {
            Object targetData = getMMOCorePlayerData(player);
            Method getTarget = request.getClass().getMethod("getTarget");
            Object target = getTarget.invoke(request);
            if (targetData == null || !targetData.equals(target))
                return false;
            try {
                Method timedOut = request.getClass().getMethod("isTimedOut");
                Object expired = timedOut.invoke(request);
                if (expired instanceof Boolean b && b) {
                    msg(player, "social-request-expired");
                    return false;
                }
            } catch (NoSuchMethodException ignored) {
            }
            Method method = request.getClass().getMethod(accept ? "accept" : "deny");
            method.invoke(request);
            return true;
        } catch (Throwable ex) {
            getLogger().warning("No se pudo " + (accept ? "aceptar" : "rechazar")
                    + " solicitud MMOCore: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            msg(player, "social-request-error");
            return false;
        }
    }

    private Inventory createMenu(String type, int size, String title, int page) {
        MenuHolder holder = new MenuHolder(type, page);
        Inventory inv = Bukkit.createInventory(holder, size, color(title));
        holder.inventory = inv;
        return inv;
    }

    private int getMenuSize(String key) {
        int size = getConfig().getInt("menus." + key + ".size", 54);
        if (size < 9)
            size = 9;
        if (size > 54)
            size = 54;
        if (size % 9 != 0)
            size = ((size / 9) + 1) * 9;
        return size;
    }

    private String getMenuTitle(String key) {
        return getConfig().getString("menus." + key + ".title", "&8MDVSocial");
    }

    private void fill(Inventory inv) {
        ItemStack filler = itemFromSection(getConfig().getConfigurationSection("items.filler"), "", null);
        for (int i = 0; i < inv.getSize(); i++)
            inv.setItem(i, filler);
    }

    private void placeConfiguredMainButton(Inventory inv, String path, String action) {
        int slot = getConfig().getInt(path + ".slot", -1);
        if (slot < 0 || slot >= inv.getSize())
            return;
        inv.setItem(slot, simpleItemFromPath(path, action));
    }

    private ItemStack simpleItemFromPath(String path, String action) {
        ConfigurationSection sec = getConfig().getConfigurationSection(path);
        ItemStack item = itemFromSection(sec, action, null);
        if (action.equals("COMMANDS") && sec != null) {
            ItemMeta meta = item.getItemMeta();
            meta.getPersistentDataContainer().set(keyMenu, PersistentDataType.STRING, path);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack navItem(String itemKey, String action) {
        return itemFromSection(getConfig().getConfigurationSection("items." + itemKey), action, null);
    }

    private ItemStack itemFromSection(ConfigurationSection sec, String action, String titleId) {
        String matName = sec != null ? sec.getString("material", "PAPER") : "PAPER";
        Material mat = Material.matchMaterial(matName == null ? "PAPER" : matName.toUpperCase(Locale.ROOT));
        if (mat == null)
            mat = Material.PAPER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        if (sec != null && mat == Material.PLAYER_HEAD && meta instanceof SkullMeta skull) {
            String texture = readTexture(sec);
            if (texture != null && !texture.isBlank()) {
                applySkullTexture(skull, texture);
            } else {
                String ownerName = sec.getString("head-owner", "");
                if (ownerName != null && !ownerName.isBlank() && !ownerName.contains("{player}")) {
                    skull.setOwningPlayer(Bukkit.getOfflinePlayer(ownerName));
                }
            }
            meta = skull;
        }

        if (sec != null) {
            String name = sec.getString("name", sec.getString("display", ""));
            if (name != null && !name.isEmpty())
                meta.setDisplayName(color(name));
            List<String> lore = sec.getStringList("lore").stream().map(this::color).collect(Collectors.toList());
            if (!lore.isEmpty())
                meta.setLore(lore);
        }
        if (action != null && !action.isEmpty())
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
        if (sec != null) {
            String sound = sec.getString("sound", sec.getString("click-sound", ""));
            if (sound != null && !sound.isBlank())
                meta.getPersistentDataContainer().set(keySound, PersistentDataType.STRING, sound);
        }
        if (titleId != null && !titleId.isEmpty())
            meta.getPersistentDataContainer().set(keyTitle, PersistentDataType.STRING, titleId);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack titleItem(Player player, TitleDef title, String menuType) {
        Material mat = Material.matchMaterial(title.material.toUpperCase(Locale.ROOT));
        if (mat == null)
            mat = Material.NAME_TAG;
        ItemStack item = new ItemStack(mat);

        if (mat == Material.PLAYER_HEAD) {
            SkullMeta skull = (SkullMeta) item.getItemMeta();
            if (title.texture != null && !title.texture.isBlank()) {
                applySkullTexture(skull, applyPlayerPlaceholders(title.texture, player));
            } else if (title.headOwner != null && !title.headOwner.isBlank()) {
                OfflinePlayer owner = Bukkit.getOfflinePlayer(applyPlayerPlaceholders(title.headOwner, player));
                skull.setOwningPlayer(owner);
            }
            item.setItemMeta(skull);
        }

        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(title.display));
        List<String> lore = new ArrayList<>();
        if (!title.lore.isEmpty()) {
            for (String line : title.lore)
                lore.add(color(line));
        }
        lore.add("");
        boolean owned = hasTitle(player, title.id);
        if (menuType.equals("MY_TITLES")) {
            String active = getActiveTitleId(player.getUniqueId());
            lore.add(color(active.equals(title.id) ? "&aEstado: equipado" : "&eClick para equipar."));
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "EQUIP_TITLE");
        } else if (menuType.equals("SHOP")) {
            lore.add(color("&ePrecio: &6" + formatPrice(title.price) + " monedas"));
            lore.add(color("&eClick para comprar."));
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "BUY_TITLE");
        } else {
            lore.add(color(owned ? "&aDesbloqueado" : "&cBloqueado"));
            if (title.unlockPermission != null && !title.unlockPermission.isBlank()) {
                lore.add(color("&7Requiere: &e" + title.unlockPermission));
            }
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, "LOCKED_TITLE");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(keyTitle, PersistentDataType.STRING, title.id);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack rankItem(Player player, RankDef rank) {
        Material mat = Material.matchMaterial(rank.material.toUpperCase(Locale.ROOT));
        if (mat == null)
            mat = Material.PAPER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        boolean owned = rank.permission == null || rank.permission.isBlank() || player.hasPermission(rank.permission);
        meta.setDisplayName(color(rank.display));
        List<String> lore = new ArrayList<>();
        for (String line : rank.lore) {
            lore.add(color(line.replace("{status}", owned ? "&aObtenido" : "&cNo obtenido")));
        }
        if (lore.isEmpty())
            lore.add(color(owned ? "&aObtenido" : "&cNo obtenido"));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private List<TitleDef> filteredTitles(Player player, String type) {
        List<TitleDef> out = new ArrayList<>();
        for (TitleDef title : titles.values()) {
            if (title.punishment)
                continue;
            if (isHiddenTitle(title.id))
                continue;
            boolean owned = hasTitle(player, title.id);
            if (type.equals("MY_TITLES") && owned)
                out.add(title);
            else if (type.equals("SHOP") && title.purchasable && !owned)
                out.add(title);
            else if (type.equals("LOCKED") && !owned && !title.purchasable)
                out.add(title);
        }
        return out;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean firstJoin = !player.hasPlayedBefore();

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline())
                return;
            validateActiveTitle(player, true);
            sendWelcomeMailIfNeeded(player, firstJoin);
        }, 20L);
        Bukkit.getScheduler().runTaskLater(this, () -> validateActiveTitle(player, true), 100L);
        Bukkit.getScheduler().runTaskLater(this, () -> refreshInteractiveChatProfile(player), 40L);

        if (!getConfig().getBoolean("scoreboard-party-permission.enabled", true))
            return;
        if (getConfig().getBoolean("scoreboard-party-permission.reset-on-join", true)) {
            setScoreboardPartyPermission(player, false);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> syncScoreboardPartyPermission(player), 20L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        interactiveChatProfiles.remove(player.getUniqueId());
        bedrockUiLastAction.remove(player.getUniqueId());
        bedrockUiActionGeneration.remove(player.getUniqueId());
        bedrockUiSession.remove(player.getUniqueId());
        if (!getConfig().getBoolean("scoreboard-party-permission.enabled", true))
            return;
        setScoreboardPartyPermission(player, false);
        PermissionAttachment attachment = scoreboardPartyAttachments.remove(player.getUniqueId());
        if (attachment != null) {
            try {
                attachment.remove();
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean isPlayerOptionsAlias(String raw) {
        if (raw == null)
            return false;
        String value = normalize(raw);
        return value.equals("jugador") || value.equals("player") || value.equals("perfil") || value.equals("opciones");
    }

    private void openPlayerOptionsMenu(Player viewer, String targetName) {
        OfflinePlayer target = findKnownOfflinePlayer(targetName);
        if (target == null) {
            msg(viewer, "social-target-not-found");
            return;
        }
        String menu = normalize(getConfig().getString("interactive-chat.options-menu", "jugador_opciones"));
        String resolvedName = target.getName() == null || target.getName().isBlank() ? targetName : target.getName();
        boolean online = Bukkit.getPlayer(target.getUniqueId()) != null;
        openCustomMenu(viewer, menu, 1, "", 1, target.getUniqueId(), resolvedName, online);
    }

    private void startInteractiveChatProfileTask() {
        if (interactiveChatProfileTask != null) {
            interactiveChatProfileTask.cancel();
            interactiveChatProfileTask = null;
        }
        interactiveChatProfiles.clear();
        interactiveChatEnabled = getConfig().getBoolean("interactive-chat.enabled", true);
        interactiveHoverTemplate = List.copyOf(getConfig().getStringList("interactive-chat.hover"));
        if (!interactiveChatEnabled)
            return;

        refreshAllInteractiveChatProfiles();
        long interval = Math.max(20L, getConfig().getLong("interactive-chat.profile-cache-refresh-ticks", 100L));
        interactiveChatProfileTask = Bukkit.getScheduler().runTaskTimer(this, this::refreshAllInteractiveChatProfiles,
                interval, interval);
    }

    private void refreshAllInteractiveChatProfiles() {
        if (!interactiveChatEnabled)
            return;
        for (Player player : Bukkit.getOnlinePlayers())
            refreshInteractiveChatProfile(player);
        interactiveChatProfiles.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    private void refreshInteractiveChatProfile(Player player) {
        if (player == null || !player.isOnline())
            return;
        interactiveChatProfiles.put(player.getUniqueId(), buildChatProfile(player));
    }

    private String getPlayerClan(OfflinePlayer player) {
        String noClanText = getConfig().getString(
                "interactive-chat.profile.no-clan-text",
                "&8Sin clan");

        String clanPlaceholder = getConfig().getString(
                "interactive-chat.profile.clan-placeholder",
                "%mdvclans_clan_line_of_{name}%");

        clanPlaceholder = clanPlaceholder.replace(
                "{name}",
                player.getName() != null ? player.getName() : "");

        return cleanProfileValue(
                papi(player, clanPlaceholder),
                noClanText);
    }

    private ChatProfileSnapshot buildChatProfile(OfflinePlayer player) {
        String name = player.getName() != null
                ? player.getName()
                : "Unknown";

        String levelPlaceholder = getConfig().getString(
                "interactive-chat.profile.level-placeholder",
                "%mmocore_level%");

        String level = cleanProfileValue(
                papi(player, levelPlaceholder),
                "&7Desconocido");

        String race = resolvePlayerRace(player);

        TitleDef equipped = player.isOnline()
                ? getActiveTitle(player.getPlayer())
                : getEquippedTitle(player.getUniqueId());

        String title = equipped == null
                ? getConfig().getString(
                        "interactive-chat.profile.no-title-text",
                        "&7Sin título")
                : equipped.display;

        String rank = resolveRankDisplay(player);

        String clan = getPlayerClan(player);

        return new ChatProfileSnapshot(
                name,
                toAmpersand(level),
                toAmpersand(race),
                toAmpersand(title),
                toAmpersand(rank),
                toAmpersand(clan));
    }

    private String resolvePlayerRace(OfflinePlayer player) {
        List<String> racePlaceholders = getConfig().getStringList(
                "interactive-chat.profile.race-placeholders");

        if (racePlaceholders.isEmpty()) {
            racePlaceholders = List.of(
                    "%mmocore_race%",
                    "%mmocore_class%");
        }

        for (String placeholder : racePlaceholders) {
            String race = cleanProfileValue(
                    papi(player, placeholder),
                    "");

            if (!race.isBlank())
                return race;
        }

        return "&7Sin raza";
    }

    private ChatProfileSnapshot buildChatProfile(Player player) {
        return buildChatProfile((OfflinePlayer) player);
    }

    private ChatProfileSnapshot resolveTargetProfile(
            UUID targetUuid,
            String targetName,
            boolean targetOnline) {
        if (targetUuid != null) {
            ChatProfileSnapshot cached = interactiveChatProfiles.get(targetUuid);

            if (cached != null)
                return cached;

            Player online = Bukkit.getPlayer(targetUuid);

            if (online != null) {
                ChatProfileSnapshot built = buildChatProfile(online);
                interactiveChatProfiles.put(targetUuid, built);
                return built;
            }
        }

        OfflinePlayer target = targetUuid == null
                ? Bukkit.getOfflinePlayer(targetName)
                : Bukkit.getOfflinePlayer(targetUuid);

        return buildChatProfile(target);
    }

    private String resolveRankDisplay(OfflinePlayer player) {
        if (player == null)
            return getConfig().getString("interactive-chat.profile.no-rank-text", "&7Sin rango");

        // Fuente principal: grupo directo de LuckPerms con mayor peso.
        // El placeholder oficial respeta el peso configurado en LuckPerms y no depende
        // del orden de permisos heredados ni del primary group manual del jugador.
        List<String> highestWeightPlaceholders = getConfig()
                .getStringList("interactive-chat.profile.highest-weight-group-placeholders");
        if (highestWeightPlaceholders.isEmpty()) {
            highestWeightPlaceholders = List.of("%luckperms_highest_group_by_weight%");
        }
        for (String placeholder : highestWeightPlaceholders) {
            String group = cleanProfileValue(papi(player, placeholder), "");
            if (!group.isBlank())
                return resolveLuckPermsGroupDisplay(group);
        }

        // Compatibilidad para instalaciones donde el placeholder anterior no esté
        // disponible.
        List<String> primaryGroupPlaceholders = getConfig()
                .getStringList("interactive-chat.profile.primary-group-placeholders");
        if (primaryGroupPlaceholders.isEmpty()) {
            primaryGroupPlaceholders = List.of("%luckperms_primary_group_name%", "%luckperms_primary_group%");
        }
        for (String placeholder : primaryGroupPlaceholders) {
            String group = cleanProfileValue(papi(player, placeholder), "");
            if (!group.isBlank())
                return resolveLuckPermsGroupDisplay(group);
        }

        // Último fallback para servidores sin placeholders de LuckPerms.
        Player online = player.getPlayer();
        if (online != null) {
            List<String> priority = getConfig().getStringList("interactive-chat.profile.rank-priority-fallback");
            if (priority.isEmpty())
                priority = List.of("heroe", "noble", "campeon", "caballero", "creador", "embajador", "aventurero");
            for (String raw : priority) {
                RankDef rank = ranks.get(normalize(raw));
                if (rank != null && rank.permission != null && !rank.permission.isBlank()
                        && online.hasPermission(rank.permission)) {
                    return rank.display;
                }
            }
        }

        return getConfig().getString("interactive-chat.profile.no-rank-text", "&7Sin rango");
    }

    private String resolveLuckPermsGroupDisplay(String rawGroup) {
        String plain = ChatColor.stripColor(color(rawGroup));
        if (plain == null || plain.isBlank()) {
            return getConfig().getString("interactive-chat.profile.no-rank-text", "&7Sin rango");
        }

        String groupId = normalize(plain).replace('-', '_');
        String custom = getConfig().getString("interactive-chat.profile.group-display-names." + groupId, "");
        if (custom != null && !custom.isBlank())
            return custom;

        RankDef configured = ranks.get(groupId);
        if (configured != null)
            return configured.display;

        return "&f" + prettifyGroupName(plain);
    }

    private String prettifyGroupName(String raw) {
        if (raw == null || raw.isBlank())
            return "Sin rango";
        String normalized = raw.trim().replace('_', ' ').replace('-', ' ');
        StringBuilder out = new StringBuilder(normalized.length());
        boolean capitalize = true;
        for (char c : normalized.toCharArray()) {
            if (Character.isWhitespace(c)) {
                out.append(c);
                capitalize = true;
            } else if (capitalize) {
                out.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }

    private String cleanProfileValue(String value, String fallback) {
        if (value == null)
            return fallback == null ? "" : fallback;
        String clean = value.trim();
        if (clean.isBlank() || clean.equalsIgnoreCase("none") || clean.equalsIgnoreCase("null")
                || clean.equalsIgnoreCase("nomatch") || clean.equalsIgnoreCase("n/a") || clean.contains("%")) {
            return fallback == null ? "" : fallback;
        }
        return clean;
    }

    private String toAmpersand(String value) {
        return value == null ? "" : value.replace('§', '&');
    }

    private Component buildInteractiveHover(ChatProfileSnapshot profile) {
        List<String> lines = interactiveHoverTemplate;
        if (lines.isEmpty()) {
            lines = List.of(
                    "&6&l{player}",
                    "",
                    "&e&l● &7&lInformación",
                    "&7Nivel: &e{level}",
                    "&7Raza: &f{race}",
                    "&7Rango: &r{rank}",
                    "&7Título: &r{title}",
                    "&7Clan: &r{clan}",
                    "",
                    "&aClic para mirar opciones",
                    "&bShift + clic para escribirle");
        }

        String rendered = lines.stream()
                .map(line -> line
                        .replace("{player}", profile.playerName)
                        .replace("{level}", profile.level)
                        .replace("{race}", profile.race)
                        .replace("{class}", profile.race)
                        .replace("{rank}", profile.rank)
                        .replace("{title}", profile.title)
                        .replace("{clan}", profile.clan))
                .collect(Collectors.joining("\n"));

        return legacyAmpersand.deserialize(rendered);
    }

    /**
     * Decora el nombre una vez que todos los formateadores de chat ya instalaron
     * su renderer. Esto es imprescindible para LPC: su renderer construye el
     * mensaje desde un String y no utiliza sourceDisplayName, por lo que decorar
     * displayName antes de LPC pierde el hover/click.
     *
     * MONITOR se usa intencionalmente como etapa final de composición: no se
     * cancela el evento ni se cambia el mensaje escrito, solo se envuelve el
     * renderer que quedó configurado por LPC u otro formateador.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractiveChat(AsyncChatEvent event) {
        if (!interactiveChatEnabled)
            return;
        Player sender = event.getPlayer();
        if (mailSessions.containsKey(sender.getUniqueId()))
            return;

        ChatProfileSnapshot profile = interactiveChatProfiles.get(sender.getUniqueId());
        if (profile == null)
            return;

        ChatRenderer previous = event.renderer();
        Component hover = buildInteractiveHover(profile);
        String optionsCommand = "/social jugador " + sender.getName();
        String messageCommand = "/msg " + sender.getName() + " ";
        String literalName = sender.getName();

        event.renderer((source, displayName, message, viewer) -> {
            Component rendered = previous.render(source, displayName, message, viewer);

            // LPC inserta {name} directamente dentro del componente final.
            // Reemplazamos solo la primera aparición y conservamos el estilo
            // (color, negrita, prefijos y sufijos) aplicado por LPC.
            return rendered.replaceText(TextReplacementConfig.builder()
                    .matchLiteral(literalName)
                    .times(1)
                    .replacement(match -> match
                            .hoverEvent(HoverEvent.showText(hover))
                            .clickEvent(ClickEvent.runCommand(optionsCommand))
                            .insertion(messageCommand))
                    .build());
        });
    }

    private boolean isMMOCoreFriend(Player player, UUID targetUuid) {
        if (player == null || targetUuid == null || !Bukkit.getPluginManager().isPluginEnabled("MMOCore"))
            return false;
        try {
            Object playerData = getMMOCorePlayerData(player);
            if (playerData == null)
                return false;
            Method hasFriend = playerData.getClass().getMethod("hasFriend", UUID.class);
            Object result = hasFriend.invoke(playerData, targetUuid);
            return result instanceof Boolean value && value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void inviteMMOCoreFriend(Player player, UUID targetUuid, String fallbackName) {
        if (targetUuid == null) {
            msg(player, "social-target-not-found");
            return;
        }
        Player target = Bukkit.getPlayer(targetUuid);
        String targetName = target != null ? target.getName()
                : (fallbackName == null || fallbackName.isBlank() ? "jugador" : fallbackName);

        if (targetUuid.equals(player.getUniqueId())) {
            msg(player, "friend-self");
            return;
        }
        if (target == null) {
            msg(player, "friend-target-offline", Map.of("target", targetName));
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
            msg(player, "friend-mmocore-missing");
            return;
        }
        if (isMMOCoreFriend(player, targetUuid)) {
            msg(player, "friend-already", Map.of("target", targetName));
            return;
        }

        try {
            Object playerData = getMMOCorePlayerData(player);
            Object targetData = getMMOCorePlayerData(target);
            if (playerData == null || targetData == null) {
                msg(player, "friend-error");
                return;
            }
            Method sendFriendRequest = playerData.getClass().getMethod("sendFriendRequest", playerData.getClass());
            sendFriendRequest.invoke(playerData, targetData);
            msg(player, "friend-request-sent", Map.of("target", targetName));
        } catch (Throwable ex) {
            getLogger().warning("No se pudo enviar solicitud de amistad MMOCore: " + ex.getClass().getSimpleName()
                    + " - " + ex.getMessage());
            msg(player, "friend-error");
        }
    }

    private void suggestPrivateMessage(Player player, String targetName) {
        String safeName = targetName == null || targetName.isBlank() ? "jugador" : targetName;
        String command = "/msg " + safeName + " ";
        String configured = getConfig().getString("messages.private-message-suggest",
                "&bHaz clic aquí para escribirle a &e{target}&b.");
        Component prompt = legacyAmpersand.deserialize(configured.replace("{target}", safeName))
                .clickEvent(ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(legacyAmpersand.deserialize("&7Preparar &f" + command)));
        player.sendMessage(prompt);
    }

    private void syncAllScoreboardPartyPermissions() {
        if (!getConfig().getBoolean("scoreboard-party-permission.enabled", true))
            return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            syncScoreboardPartyPermission(player);
        }
    }

    private void resetAllScoreboardPartyPermissions() {
        if (!getConfig().getBoolean("scoreboard-party-permission.enabled", true))
            return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            setScoreboardPartyPermission(player, false);
        }
    }

    private void syncScoreboardPartyPermission(Player player) {
        if (player == null || !player.isOnline())
            return;
        boolean inParty = getMMOCoreParty(player) != null;
        setScoreboardPartyPermission(player, inParty);
    }

    private void setScoreboardPartyPermission(Player player, boolean value) {
        if (player == null)
            return;
        String permission = getConfig().getString("scoreboard-party-permission.permission", "animatedscoreboard.party");
        if (permission == null || permission.isBlank())
            return;

        PermissionAttachment attachment = scoreboardPartyAttachments.computeIfAbsent(player.getUniqueId(),
                id -> player.addAttachment(this));
        attachment.setPermission(permission, value);
        player.recalculatePermissions();

        if (getConfig().getBoolean("scoreboard-party-permission.debug", false)) {
            getLogger().info("Scoreboard party permission: " + player.getName() + " -> " + permission + " = " + value);
        }
    }

    private boolean handleExternalFriendOptionsClick(InventoryClickEvent event, Player player) {
        if (!getConfig().getBoolean("social-friend-options.enabled", true))
            return false;
        if (event.getClickedInventory() == null)
            return false;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory()))
            return false;

        String clickMode = getConfig().getString("social-friend-options.click", "LEFT");
        if (!matchesConfiguredClick(event.getClick(), clickMode))
            return false;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize())
            return false;
        List<Integer> slots = getConfig().getIntegerList("social-friend-options.slots");
        if (!slots.isEmpty() && !slots.contains(slot))
            return false;

        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null)
            title = event.getView().getTitle();
        String normalizedTitle = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
        String equals = ChatColor.stripColor(color(getConfig().getString("social-friend-options.title", ""))).trim()
                .toLowerCase(Locale.ROOT);
        String contains = ChatColor.stripColor(color(getConfig().getString("social-friend-options.title-contains", "")))
                .trim().toLowerCase(Locale.ROOT);
        boolean titleMatches = (!equals.isBlank() && normalizedTitle.equals(equals))
                || (!contains.isBlank() && normalizedTitle.contains(contains));
        if (!titleMatches && equals.isBlank() && contains.isBlank())
            titleMatches = true;
        if (!titleMatches)
            return false;

        ItemStack clicked = event.getCurrentItem();
        UUID targetUuid = extractMMOCoreFriendUuid(clicked);
        if (targetUuid == null)
            return false;

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
        String targetName = target.getName() == null || target.getName().isBlank() ? "jugador" : target.getName();
        boolean targetOnline = Bukkit.getPlayer(targetUuid) != null;

        event.setCancelled(getConfig().getBoolean("social-friend-options.cancel-event", true));
        playUiSound(player, getConfig().getString("social-friend-options.sound", "open"));
        String targetMenu = normalize(getConfig().getString("social-friend-options.target-menu", "amigo_opciones"));
        Bukkit.getScheduler().runTask(this,
                () -> openCustomMenu(player, targetMenu, 1, "", 1, targetUuid, targetName, targetOnline));
        return true;
    }

    private boolean matchesConfiguredClick(org.bukkit.event.inventory.ClickType click, String configured) {
        String value = configured == null ? "LEFT"
                : configured.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (value) {
            case "ANY", "ALL" -> true;
            case "LEFT", "LEFT_CLICK" -> click == org.bukkit.event.inventory.ClickType.LEFT;
            case "SHIFT_LEFT", "SHIFT_LEFT_CLICK" -> click == org.bukkit.event.inventory.ClickType.SHIFT_LEFT;
            case "RIGHT", "RIGHT_CLICK" -> click == org.bukkit.event.inventory.ClickType.RIGHT;
            case "SHIFT_RIGHT", "SHIFT_RIGHT_CLICK" -> click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT;
            default -> click == org.bukkit.event.inventory.ClickType.LEFT;
        };
    }

    private UUID extractMMOCoreFriendUuid(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta())
            return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        for (org.bukkit.NamespacedKey key : pdc.getKeys()) {
            if (!key.getNamespace().equalsIgnoreCase("mmocore"))
                continue;
            if (!key.getKey().equalsIgnoreCase("Uuid"))
                continue;
            String value = pdc.get(key, PersistentDataType.STRING);
            if (value == null || value.isBlank())
                return null;
            try {
                return UUID.fromString(value);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean handleExternalGuiClick(InventoryClickEvent event, Player player) {
        if (externalGuiActions.isEmpty())
            return false;
        if (event.getClickedInventory() == null)
            return false;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory()))
            return false;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize())
            return false;

        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null)
            title = event.getView().getTitle();

        for (ExternalGuiAction def : externalGuiActions) {
            if (!def.matches(title, slot))
                continue;
            if (def.cancelEvent)
                event.setCancelled(true);
            playUiSound(player, def.sound == null || def.sound.isBlank() ? "default" : def.sound);
            runExternalGuiCommands(player, def);
            return true;
        }
        return false;
    }

    private void runExternalGuiCommands(Player player, ExternalGuiAction def) {
        Runnable task = () -> {
            if (def.closeOnClick)
                player.closeInventory();
            for (String raw : def.playerCommands) {
                String cmd = applyPlayerPlaceholders(raw, player).trim();
                if (cmd.isBlank())
                    continue;
                if (cmd.startsWith("/"))
                    cmd = cmd.substring(1);
                Bukkit.dispatchCommand(player, cmd);
            }
            for (String raw : def.consoleCommands) {
                String cmd = applyPlayerPlaceholders(raw, player).trim();
                if (cmd.isBlank())
                    continue;
                if (cmd.startsWith("/"))
                    cmd = cmd.substring(1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        };
        Bukkit.getScheduler().runTask(this, task);
    }

    public void openSocialMenu(Player player, String menuId, int page) {
        if (player == null)
            return;
        openRequestedSocialMenu(player, menuId, Math.max(1, page));
    }

    public Inventory createCoreInventory(String title, int size, boolean fillWithDefaultFiller) {
        Inventory inv = Bukkit.createInventory((InventoryHolder) null, normalizeMenuSize(size), color(title));
        if (fillWithDefaultFiller)
            fill(inv);
        return inv;
    }

    public ItemStack createCoreButton(Material material, int amount, String name, List<String> lore, String action,
            String targetMenu, List<String> commands, boolean closeOnClick, String sound) {
        Material mat = material == null ? Material.PAPER : material;
        ItemStack item = new ItemStack(mat, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;
        if (name != null && !name.isBlank())
            meta.setDisplayName(color(name));
        if (lore != null && !lore.isEmpty())
            meta.setLore(lore.stream().map(this::color).collect(Collectors.toList()));
        String normalizedAction = normalizeAction(action);
        if (normalizedAction != null && !normalizedAction.isBlank())
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, normalizedAction);
        String normalizedTarget = normalize(targetMenu);
        if (!normalizedTarget.isBlank())
            meta.getPersistentDataContainer().set(keyTargetMenu, PersistentDataType.STRING, normalizedTarget);
        if (commands != null && !commands.isEmpty())
            meta.getPersistentDataContainer().set(keyCommands, PersistentDataType.STRING, String.join("\n", commands));
        if (sound != null && !sound.isBlank())
            meta.getPersistentDataContainer().set(keySound, PersistentDataType.STRING, sound);
        meta.getPersistentDataContainer().set(keyCloseOnClick, PersistentDataType.STRING, String.valueOf(closeOnClick));
        item.setItemMeta(meta);
        return item;
    }

    public String getCoreButtonAction(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta())
            return "";
        String action = item.getItemMeta().getPersistentDataContainer().get(keyAction, PersistentDataType.STRING);
        return action == null ? "" : action;
    }

    public String getCoreButtonTargetMenu(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta())
            return "";
        String target = item.getItemMeta().getPersistentDataContainer().get(keyTargetMenu, PersistentDataType.STRING);
        return target == null ? "" : target;
    }

    public List<String> getCoreButtonCommands(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta())
            return Collections.emptyList();
        String raw = item.getItemMeta().getPersistentDataContainer().get(keyCommands, PersistentDataType.STRING);
        if (raw == null || raw.isBlank())
            return Collections.emptyList();
        return Arrays.asList(raw.split("\\n"));
    }

    public boolean coreButtonShouldCloseOnClick(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta())
            return false;
        return shouldCloseOnClick(item.getItemMeta().getPersistentDataContainer());
    }

    public String colorize(String text) {
        return color(text);
    }

    private void playButtonSound(Player player, String action, PersistentDataContainer pdc) {
        if (player == null)
            return;
        String explicit = pdc == null ? "" : pdc.get(keySound, PersistentDataType.STRING);
        if (explicit != null && !explicit.isBlank() && playUiSoundInternal(player, explicit))
            return;
        if (action != null && !action.isBlank() && playUiSoundInternal(player, "actions." + action))
            return;
        String mapped = mapActionSound(action);
        if (!mapped.isBlank() && playUiSoundInternal(player, mapped))
            return;
        playUiSoundInternal(player, "default");
    }

    public void playUiSound(Player player, String soundKey) {
        if (player == null)
            return;
        if (!playUiSoundInternal(player, soundKey == null || soundKey.isBlank() ? "default" : soundKey)) {
            playUiSoundInternal(player, "default");
        }
    }

    private String mapActionSound(String action) {
        if (action == null)
            return "default";
        return switch (action) {
            case "CLOSE" -> "close";
            case "BACK" -> "back";
            case "PREVIOUS_PAGE", "PREV_PAGE" -> "page";
            case "NEXT_PAGE" -> "page";
            case "OPEN_MENU", "OPEN_CONDITIONAL_MENU", "OPEN_MAIN", "OPEN_TITLES", "OPEN_TITLES_HOME", "OPEN_MY_TITLES",
                    "OPEN_SHOP", "OPEN_LOCKED", "OPEN_RANKS", "OPEN_MAILBOX", "READ_MAIL", "MAIL_BACK",
                    "SUGGEST_MSG_TARGET" ->
                "open";
            case "LOCKED_TITLE" -> "invalid";
            case "BUY_TITLE", "EQUIP_TITLE", "CLEAR_TITLE", "ACCEPT_CLAN_INVITE", "INVITE_FRIEND_TARGET" -> "confirm";
            case "DELETE_MAIL", "REJECT_CLAN_INVITE", "BLOCK_MAIL_SENDER" -> "danger";
            default -> "default";
        };
    }

    private boolean playUiSoundInternal(Player player, String keyOrSound) {
        if (player == null || keyOrSound == null || keyOrSound.isBlank())
            return false;
        if (!getConfig().getBoolean("ui-core.sounds.enabled", true))
            return true;
        UiSoundDef def = resolveUiSound(keyOrSound);
        if (def == null)
            return false;
        try {
            player.playSound(player.getLocation(), def.sound, def.volume, def.pitch);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private UiSoundDef resolveUiSound(String keyOrSound) {
        String value = keyOrSound == null ? "" : keyOrSound.trim();
        if (value.isBlank())
            value = "default";

        String path = "ui-core.sounds." + value;
        UiSoundDef fromPath = soundFromConfigPath(path);
        if (fromPath != null)
            return fromPath;

        String normalizedKey = value.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (!normalizedKey.equals(value)) {
            fromPath = soundFromConfigPath("ui-core.sounds." + normalizedKey);
            if (fromPath != null)
                return fromPath;
        }

        UiSoundDef builtIn = defaultUiSound(value);
        if (builtIn != null)
            return builtIn;

        return soundFromInline(value);
    }

    private UiSoundDef defaultUiSound(String key) {
        String value = key == null ? "default"
                : key.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (value.startsWith("actions."))
            value = value.substring("actions.".length()).toLowerCase(Locale.ROOT);
        return switch (value) {
            case "default" -> new UiSoundDef(Sound.UI_BUTTON_CLICK, 0.6F, 1.2F);
            case "open", "open_menu", "open_conditional_menu", "open_main", "open_titles", "open_titles_home",
                    "open_my_titles", "open_shop", "open_locked", "open_ranks", "open_mailbox", "read_mail",
                    "mail_back", "mdvclans_open" ->
                new UiSoundDef(Sound.UI_BUTTON_CLICK, 0.65F, 1.35F);
            case "back" -> new UiSoundDef(Sound.UI_BUTTON_CLICK, 0.55F, 0.85F);
            case "close" -> new UiSoundDef(Sound.BLOCK_CHEST_CLOSE, 0.55F, 1.15F);
            case "page", "next_page", "previous_page", "prev_page" ->
                new UiSoundDef(Sound.ITEM_BOOK_PAGE_TURN, 0.7F, 1.15F);
            case "confirm", "buy_title", "equip_title", "clear_title", "accept_clan_invite", "invite_friend_target" ->
                new UiSoundDef(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.65F, 1.25F);
            case "danger", "delete_mail", "reject_clan_invite", "block_mail_sender" ->
                new UiSoundDef(Sound.ENTITY_VILLAGER_NO, 0.6F, 0.85F);
            case "invalid", "locked_title" -> new UiSoundDef(Sound.BLOCK_NOTE_BLOCK_BASS, 0.65F, 0.65F);
            default -> null;
        };
    }

    private UiSoundDef soundFromConfigPath(String path) {
        if (path == null || path.isBlank())
            return null;
        if (getConfig().isConfigurationSection(path)) {
            ConfigurationSection sec = getConfig().getConfigurationSection(path);
            if (sec == null)
                return null;
            String sound = sec.getString("sound", sec.getString("name", ""));
            UiSoundDef def = soundFromInline(sound);
            if (def == null)
                return null;
            def.volume = (float) sec.getDouble("volume", def.volume);
            def.pitch = (float) sec.getDouble("pitch", def.pitch);
            return def;
        }
        if (getConfig().isString(path))
            return soundFromInline(getConfig().getString(path, ""));
        return null;
    }

    private UiSoundDef soundFromInline(String raw) {
        if (raw == null || raw.isBlank())
            return null;
        String[] parts = raw.trim().split("[;|,]");
        String soundName = parts[0].trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            Sound sound = Sound.valueOf(soundName);
            float volume = parts.length >= 2 ? parseFloat(parts[1], 0.6F) : 0.6F;
            float pitch = parts.length >= 3 ? parseFloat(parts[2], 1.2F) : 1.2F;
            return new UiSoundDef(sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private float parseFloat(String raw, float fallback) {
        try {
            return Float.parseFloat(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean evaluateMenuCondition(Player player, String placeholder, String expected) {
        if (placeholder == null || placeholder.isBlank())
            return false;
        String value = applyPlayerPlaceholders(placeholder, player);
        if (expected == null || expected.isBlank())
            expected = "true";
        return value.trim().equalsIgnoreCase(expected.trim());
    }

    private boolean shouldCloseOnClick(PersistentDataContainer pdc) {
        String value = pdc.get(keyCloseOnClick, PersistentDataType.STRING);
        return value == null || !value.equalsIgnoreCase("false");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (mmoItemsBrowserManager != null
                && mmoItemsBrowserManager.isBrowserInventory(event.getView().getTopInventory()))
            return;
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            if (handleExternalFriendOptionsClick(event, player))
                return;
            handleExternalGuiClick(event, player);
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta())
            return;
        ItemMeta meta = clicked.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        boolean rightClick = event.isRightClick();
        String action = rightClick ? pdc.get(keyRightAction, PersistentDataType.STRING) : null;
        if (action == null || action.isBlank())
            action = pdc.get(keyAction, PersistentDataType.STRING);
        if (action == null || action.isBlank())
            return;
        String requiredPermission = pdc.get(keyRequiredPermission, PersistentDataType.STRING);
        if (requiredPermission != null && !requiredPermission.isBlank() && !player.hasPermission(requiredPermission)) {
            msg(player, "no-permission");
            playButtonSound(player, "invalid", pdc);
            return;
        }
        playButtonSound(player, action, pdc);

        switch (action) {
            case "CLOSE" -> player.closeInventory();
            case "OPEN_MAIN" -> openSocialStart(player);
            case "OPEN_TITLES", "OPEN_TITLES_HOME" -> openTitlesHome(player);
            case "OPEN_MY_TITLES" -> openTitleList(player, "MY_TITLES", 0);
            case "OPEN_SHOP" -> openTitleList(player, "SHOP", 0);
            case "OPEN_LOCKED" -> openTitleList(player, "LOCKED", 0);
            case "OPEN_RANKS" -> openRanks(player, 0);
            case "OPEN_MENU" -> {
                String target = pdc.get(keyTargetMenu, PersistentDataType.STRING);
                openCustomMenu(player, target, 1, holder.menuId, holder.page);
            }
            case "OPEN_CONDITIONAL_MENU" -> {
                String placeholder = pdc.get(keyConditionPlaceholder, PersistentDataType.STRING);
                String expected = pdc.get(keyConditionEquals, PersistentDataType.STRING);
                String trueMenu = pdc.get(keyTrueMenu, PersistentDataType.STRING);
                String falseMenu = pdc.get(keyFalseMenu, PersistentDataType.STRING);
                boolean result = evaluateMenuCondition(player, placeholder, expected);
                openCustomMenu(player, result ? trueMenu : falseMenu, 1, holder.menuId, holder.page);
            }
            case "MDVCLANS_OPEN" -> {
                if (shouldCloseOnClick(pdc))
                    player.closeInventory();
                String clansMenu = pdc.get(keyClansMenu, PersistentDataType.STRING);
                if (clansMenu == null || clansMenu.isBlank())
                    clansMenu = pdc.get(keyTargetMenu, PersistentDataType.STRING);
                if (clansMenu == null || clansMenu.isBlank())
                    clansMenu = "auto";
                Bukkit.dispatchCommand(player, "clan abrir " + clansMenu);
            }
            case "BACK" -> {
                if (holder.previousMenu != null && !holder.previousMenu.isBlank())
                    openCustomMenu(player, holder.previousMenu, holder.previousPage, "", 1);
                else
                    openSocialStart(player);
            }
            case "COMMAND_PLAYER" -> runPlayerCommandsFromPdc(player, pdc, holder, rightClick);
            case "OPEN_MAILBOX" -> openMailbox(player, 0);
            case "OPEN_MMOITEMS_BROWSER" -> {
                if (mmoItemsBrowserManager == null)
                    player.sendMessage(color(getPrefix() + "&cLa biblioteca de objetos no está disponible."));
                else
                    mmoItemsBrowserManager.open(player);
            }
            case "START_MAIL_SEND" -> {
                if (shouldCloseOnClick(pdc))
                    player.closeInventory();
                startMailRecipientPrompt(player, holder.menuId.isBlank() ? "correo" : holder.menuId, holder.page);
            }
            case "START_MAIL_SEND_TARGET" -> {
                if (shouldCloseOnClick(pdc))
                    player.closeInventory();
                startMailMessagePromptToTarget(player, holder.targetUuid, holder.targetName,
                        holder.menuId.isBlank() ? "correo" : holder.menuId, holder.page);
            }
            case "INVITE_PARTY_TARGET" -> {
                if (shouldCloseOnClick(pdc))
                    player.closeInventory();
                inviteFriendToParty(player, holder.targetUuid, holder.targetName);
            }
            case "INVITE_FRIEND_TARGET" -> {
                if (shouldCloseOnClick(pdc))
                    player.closeInventory();
                inviteMMOCoreFriend(player, holder.targetUuid, holder.targetName);
            }
            case "SUGGEST_MSG_TARGET" -> {
                if (shouldCloseOnClick(pdc))
                    player.closeInventory();
                suggestPrivateMessage(player, holder.targetName);
            }
            case "START_MAIL_BLOCK" -> {
                if (shouldCloseOnClick(pdc))
                    player.closeInventory();
                startMailBlockPrompt(player, true, holder.menuId.isBlank() ? "correo" : holder.menuId, holder.page);
            }
            case "START_MAIL_UNBLOCK" -> {
                if (shouldCloseOnClick(pdc))
                    player.closeInventory();
                startMailBlockPrompt(player, false, holder.menuId.isBlank() ? "correo" : holder.menuId, holder.page);
            }
            case "CLEAR_TITLE" -> {
                clearActiveTitle(player);
                openTitlesHome(player);
            }
            case "PREVIOUS_PAGE", "PREV_PAGE" -> {
                if (holder.type.equals("CUSTOM_MENU"))
                    openCustomMenu(player, holder.menuId, holder.page - 1, holder.previousMenu, holder.previousPage);
                else
                    openSamePagedMenu(player, holder.type, holder.page - 1);
            }
            case "NEXT_PAGE" -> {
                if (holder.type.equals("CUSTOM_MENU"))
                    openCustomMenu(player, holder.menuId, holder.page + 1, holder.previousMenu, holder.previousPage);
                else
                    openSamePagedMenu(player, holder.type, holder.page + 1);
            }
            case "EQUIP_TITLE" -> {
                String titleId = pdc.get(keyTitle, PersistentDataType.STRING);
                equipTitle(player, titleId);
                openTitleList(player, "MY_TITLES", holder.page);
            }
            case "BUY_TITLE" -> {
                String titleId = pdc.get(keyTitle, PersistentDataType.STRING);
                buyTitle(player, titleId);
                openTitleList(player, "SHOP", holder.page);
            }
            case "LOCKED_TITLE" -> msg(player, "title-locked");
            case "READ_MAIL" -> {
                String mailId = pdc.get(keyMailId, PersistentDataType.STRING);
                openMailRead(player, mailId, holder.page);
            }
            case "DELETE_MAIL" -> {
                String mailId = pdc.get(keyMailId, PersistentDataType.STRING);
                deleteMail(player, mailId);
                openMailbox(player, holder.page);
            }
            case "MAIL_BACK" -> openMailbox(player, holder.page);
            case "REPLY_MAIL" -> {
                String mailId = pdc.get(keyMailId, PersistentDataType.STRING);
                startMailReplyFromMail(player, mailId, holder.page);
            }
            case "BLOCK_MAIL_SENDER" -> {
                String senderUuid = pdc.get(keyMailSender, PersistentDataType.STRING);
                if (blockMailSender(player, senderUuid))
                    player.closeInventory();
            }
            case "ACCEPT_CLAN_INVITE" -> {
                String mailId = pdc.get(keyMailId, PersistentDataType.STRING);
                handleClanInviteMailAction(player, mailId, true, holder.page);
            }
            case "REJECT_CLAN_INVITE" -> {
                String mailId = pdc.get(keyMailId, PersistentDataType.STRING);
                handleClanInviteMailAction(player, mailId, false, holder.page);
            }
            case "COMMANDS" -> runConfiguredCommands(player, pdc.get(keyMenu, PersistentDataType.STRING));
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // reservado para futuras sesiones de menu
    }

    private void openSamePagedMenu(Player player, String type, int page) {
        if (type.equals("RANKS"))
            openRanks(player, page);
        else if (type.equals("MAILBOX"))
            openMailbox(player, page);
        else if (type.equals("MY_TITLES") || type.equals("SHOP") || type.equals("LOCKED"))
            openTitleList(player, type, page);
    }

    private void runPlayerCommandsFromPdc(Player player, PersistentDataContainer pdc) {
        runPlayerCommandsFromPdc(player, pdc, null, false);
    }

    private void runPlayerCommandsFromPdc(Player player, PersistentDataContainer pdc, MenuHolder holder) {
        runPlayerCommandsFromPdc(player, pdc, holder, false);
    }

    private void runPlayerCommandsFromPdc(Player player, PersistentDataContainer pdc, MenuHolder holder,
            boolean rightClick) {
        String raw = rightClick ? pdc.get(keyRightCommands, PersistentDataType.STRING) : null;
        if (raw == null || raw.isBlank())
            raw = pdc.get(keyCommands, PersistentDataType.STRING);
        if (raw == null || raw.isBlank())
            return;
        String close = pdc.get(keyCloseOnClick, PersistentDataType.STRING);
        if (close == null || Boolean.parseBoolean(close))
            player.closeInventory();
        UUID targetUuid = holder == null ? readUuidFromPdc(pdc) : holder.targetUuid;
        String targetName = holder == null ? pdc.get(keyFriendTargetName, PersistentDataType.STRING)
                : holder.targetName;
        boolean targetOnline = holder != null && holder.targetOnline;
        String onlineRaw = pdc.get(keyFriendTargetOnline, PersistentDataType.STRING);
        if (holder == null && onlineRaw != null)
            targetOnline = Boolean.parseBoolean(onlineRaw);
        for (String line : raw.split("\\n")) {
            String cmd = applyTargetPlaceholders(line, player, targetUuid, targetName, targetOnline).trim();
            if (cmd.isBlank())
                continue;
            if (cmd.startsWith("/"))
                cmd = cmd.substring(1);
            Bukkit.dispatchCommand(player, cmd);
        }
    }

    private UUID readUuidFromPdc(PersistentDataContainer pdc) {
        String raw = pdc.get(keyFriendTargetUuid, PersistentDataType.STRING);
        if (raw == null || raw.isBlank())
            return null;
        try {
            return UUID.fromString(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void invitePlayerToPartyFromBedrockForm(Player player, Player target) {
        if (player == null || target == null)
            return;
        if (target.getUniqueId().equals(player.getUniqueId())) {
            msg(player, "party-self");
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
            msg(player, "party-mmocore-missing");
            return;
        }
        // El formulario de Grupo de Aventura debe ser autocontenido: si aún no hay
        // party, creamos una party registrada antes de ejecutar el comando oficial.
        if (getMMOCoreParty(player) == null) {
            try {
                Object data = getMMOCorePlayerData(player);
                Object created = createMMOCoreParty(data);
                if (created == null) {
                    msg(player, "party-error");
                    return;
                }
                syncScoreboardPartyPermission(player);
                msg(player, "party-auto-created");
            } catch (Throwable ex) {
                getLogger().warning("No se pudo crear party para invitación Bedrock: " + ex.getMessage());
                msg(player, "party-error");
                return;
            }
        }
        String template = getConfig().getString("bedrock.party.direct-invite-command", "party invite {target}");
        String command = (template == null ? "party invite {target}" : template)
                .replace("{target}", target.getName())
                .trim();
        if (command.startsWith("/"))
            command = command.substring(1);
        boolean dispatched = false;
        try {
            dispatched = Bukkit.dispatchCommand(player, command);
        } catch (Throwable ex) {
            getLogger().fine("No se pudo usar el comando oficial de party: " + ex.getMessage());
        }

        // Verificamos que MMOCore haya registrado realmente la PartyInvite. Un comando
        // puede devolver true aunque otra versión cambie su sintaxis. Si no existe la
        // request, usamos el puente API/reflection que ya funciona desde Amigos.
        boolean requestCreated = hasPendingMMOCoreRequestFrom(target, "PartyInvite", player.getUniqueId());
        if (!dispatched || !requestCreated) {
            inviteFriendToParty(player, target.getUniqueId(), target.getName());
            requestCreated = hasPendingMMOCoreRequestFrom(target, "PartyInvite", player.getUniqueId());
        } else {
            syncScoreboardPartyPermission(player);
        }

        // Para Java añadimos una línea de compatibilidad con comandos explícitos. Esto
        // evita depender de cómo el cliente renderice el JSON clickable de MMOCore.
        if (requestCreated && !isBedrockPlayer(target)
                && getConfig().getBoolean("bedrock.party.java-fallback-buttons", true)) {
            sendJavaPartyInviteFallbackControls(target, player);
        }
    }

    private boolean hasPendingMMOCoreRequestFrom(Player target, String simpleClassName, UUID creatorUuid) {
        if (target == null || creatorUuid == null)
            return false;
        for (Object request : getMMOCorePendingRequests(target, simpleClassName)) {
            try {
                Method getCreator = request.getClass().getMethod("getCreator");
                UUID uuid = extractUuid(getCreator.invoke(request));
                if (creatorUuid.equals(uuid))
                    return true;
            } catch (Throwable ignored) {
                String creator = getMMOCoreRequestCreatorName(request);
                OfflinePlayer off = Bukkit.getOfflinePlayer(creatorUuid);
                if (off.getName() != null && off.getName().equalsIgnoreCase(creator))
                    return true;
            }
        }
        return false;
    }

    private void sendJavaPartyInviteFallbackControls(Player target, Player inviter) {
        if (target == null || inviter == null || !target.isOnline())
            return;
        String uuid = inviter.getUniqueId().toString();
        Component prefix = legacyAmpersand.deserialize("&d[Grupo] &f" + inviter.getName()
                + " &7te invitó. ");
        Component accept = legacyAmpersand.deserialize("&8[&a&lACEPTAR&8]")
                .clickEvent(ClickEvent.runCommand("/party accept " + uuid))
                .hoverEvent(HoverEvent.showText(legacyAmpersand.deserialize("&aAceptar invitación")));
        Component deny = legacyAmpersand.deserialize(" &8[&c&lRECHAZAR&8]")
                .clickEvent(ClickEvent.runCommand("/party deny " + uuid))
                .hoverEvent(HoverEvent.showText(legacyAmpersand.deserialize("&cRechazar invitación")));
        target.sendMessage(prefix.append(accept).append(deny));
    }

    private boolean removeMMOCoreFriend(Player player, UUID targetUuid, String fallbackName) {
        if (player == null || targetUuid == null || !Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
            msg(player, "friend-error");
            return false;
        }
        String targetName = fallbackName == null || fallbackName.isBlank()
                ? Bukkit.getOfflinePlayer(targetUuid).getName()
                : fallbackName;
        if (targetName == null || targetName.isBlank())
            targetName = "jugador";
        try {
            Class<?> playerDataClass = Class.forName("net.Indyuce.mmocore.api.player.PlayerData");
            Method getData = playerDataClass.getMethod("get", OfflinePlayer.class);
            Method removeFriend = playerDataClass.getMethod("removeFriend", UUID.class);
            Object playerData = getData.invoke(null, player);
            if (playerData == null)
                throw new IllegalStateException("PlayerData no disponible");
            removeFriend.invoke(playerData, targetUuid);

            // MMOCore almacena amistad de forma recíproca. Actualizamos también al otro
            // jugador, incluso si está desconectado, mediante
            // PlayerData#get(OfflinePlayer).
            try {
                OfflinePlayer other = Bukkit.getOfflinePlayer(targetUuid);
                Object targetData = getData.invoke(null, other);
                if (targetData != null)
                    removeFriend.invoke(targetData, player.getUniqueId());
            } catch (Throwable ex) {
                getLogger().fine("No se pudo limpiar la amistad recíproca offline: " + ex.getMessage());
            }
            msg(player, "friend-removed", Map.of("target", targetName));
            return true;
        } catch (Throwable ex) {
            getLogger().warning("No se pudo eliminar amistad MMOCore: "
                    + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            msg(player, "friend-remove-error", Map.of("target", targetName));
            return false;
        }
    }

    private boolean leaveMMOCoreParty(Player player) {
        if (player == null || !Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
            msg(player, "party-mmocore-missing");
            return false;
        }
        try {
            Object playerData = getMMOCorePlayerData(player);
            if (playerData == null)
                throw new IllegalStateException("PlayerData no disponible");
            Method getParty = playerData.getClass().getMethod("getParty");
            Object party = getParty.invoke(playerData);
            if (party == null) {
                msg(player, "party-not-in-party");
                return false;
            }
            Method removeMember;
            try {
                removeMember = party.getClass().getMethod("removeMember", playerData.getClass());
            } catch (NoSuchMethodException ex) {
                removeMember = null;
                for (Method method : party.getClass().getMethods()) {
                    if (method.getName().equals("removeMember") && method.getParameterCount() == 1) {
                        removeMember = method;
                        break;
                    }
                }
                if (removeMember == null)
                    throw ex;
            }
            removeMember.invoke(party, playerData);
            setScoreboardPartyPermission(player, false);
            msg(player, "party-left");
            return true;
        } catch (Throwable ex) {
            getLogger().warning("No se pudo salir de la party MMOCore: "
                    + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            msg(player, "party-leave-error");
            return false;
        }
    }

    private void inviteFriendToParty(Player player, UUID targetUuid, String fallbackName) {
        if (targetUuid == null) {
            msg(player, "social-target-not-found");
            return;
        }
        Player targetPlayer = Bukkit.getPlayer(targetUuid);
        String targetName = targetPlayer != null ? targetPlayer.getName()
                : (fallbackName == null || fallbackName.isBlank() ? "jugador" : fallbackName);
        if (targetPlayer == null) {
            msg(player, "party-target-offline", Map.of("target", targetName));
            return;
        }
        if (targetUuid.equals(player.getUniqueId())) {
            msg(player, "party-self");
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("MMOCore")) {
            msg(player, "party-mmocore-missing");
            return;
        }

        try {
            Class<?> playerDataClass = Class.forName("net.Indyuce.mmocore.api.player.PlayerData");
            Method getData = playerDataClass.getMethod("get", OfflinePlayer.class);
            Object playerData = getData.invoke(null, player);
            Object targetData = getData.invoke(null, targetPlayer);
            Method getParty = playerDataClass.getMethod("getParty");
            Object party = getParty.invoke(playerData);

            if (party == null) {
                String behavior = getConfig().getString("social-friend-options.party.when-no-party", "message");
                if (behavior != null && (behavior.equalsIgnoreCase("create") || behavior.equalsIgnoreCase("auto-create")
                        || behavior.equalsIgnoreCase("create-and-invite")
                        || behavior.equalsIgnoreCase("create_and_invite"))) {
                    party = createMMOCoreParty(playerData);
                    if (party != null) {
                        msg(player, "party-auto-created");
                        syncScoreboardPartyPermission(player);
                    }
                } else {
                    msg(player, "party-must-create", Map.of("target", targetName));
                    return;
                }
            }

            if (party == null) {
                msg(player, "party-error");
                return;
            }

            try {
                Method hasMember = party.getClass().getMethod("hasMember", UUID.class);
                Object already = hasMember.invoke(party, targetUuid);
                if (already instanceof Boolean b && b) {
                    msg(player, "party-target-already-member", Map.of("target", targetName));
                    return;
                }
            } catch (NoSuchMethodException ignored) {
            }

            int max = Math.max(2, getConfig().getInt("social-friend-options.party.max-members", 5));
            try {
                Method countMembers = party.getClass().getMethod("countMembers");
                Object count = countMembers.invoke(party);
                if (count instanceof Number n && n.intValue() >= max) {
                    msg(player, "party-full", Map.of("max", String.valueOf(max)));
                    return;
                }
            } catch (NoSuchMethodException ignored) {
            }

            Method invite;
            try {
                invite = party.getClass().getMethod("sendPartyInvite", playerDataClass, playerDataClass);
            } catch (NoSuchMethodException ignored) {
                invite = party.getClass().getMethod("sendInvite", playerDataClass, playerDataClass);
            }
            invite.invoke(party, playerData, targetData);
            syncScoreboardPartyPermission(player);
            msg(player, "party-invite-sent", Map.of("target", targetName));
        } catch (Throwable ex) {
            getLogger().warning(
                    "No se pudo invitar amigo a party: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            msg(player, "party-error");
        }
    }

    private Object createMMOCoreParty(Object playerData) throws Exception {
        Class<?> mmocoreClass = Class.forName("net.Indyuce.mmocore.MMOCore");
        Object mmocore = mmocoreClass.getField("plugin").get(null);
        if (mmocore == null)
            return null;
        Object partyModule = mmocoreClass.getField("partyModule").get(mmocore);
        if (partyModule == null)
            return null;
        Method create = partyModule.getClass().getMethod("newRegisteredParty", playerData.getClass());
        return create.invoke(partyModule, playerData);
    }

    private Object getMMOCorePlayerData(Player player) throws Exception {
        Class<?> playerDataClass = Class.forName("net.Indyuce.mmocore.api.player.PlayerData");
        Method getData = playerDataClass.getMethod("get", OfflinePlayer.class);
        return getData.invoke(null, player);
    }

    private Object getMMOCoreParty(Player player) {
        if (player == null || !Bukkit.getPluginManager().isPluginEnabled("MMOCore"))
            return null;
        try {
            Object playerData = getMMOCorePlayerData(player);
            if (playerData == null)
                return null;
            Method getParty = playerData.getClass().getMethod("getParty");
            return getParty.invoke(playerData);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int getConfiguredPartyMaxMembers() {
        return Math.max(2, getConfig().getInt("social-friend-options.party.max-members", 5));
    }

    private int countMMOCorePartyMembers(Object party) {
        if (party == null)
            return 0;
        try {
            Method countMembers = party.getClass().getMethod("countMembers");
            Object count = countMembers.invoke(party);
            if (count instanceof Number n)
                return Math.max(0, n.intValue());
        } catch (Throwable ignored) {
        }
        return getMMOCorePartyMemberNames(party).size();
    }

    private List<String> getMMOCorePartyMemberNames(Object party) {
        if (party == null)
            return Collections.emptyList();
        List<?> rawMembers = Collections.emptyList();

        String[] memberMethods = { "getOnlineMembers", "getMembers" };
        for (String methodName : memberMethods) {
            try {
                Method method = party.getClass().getMethod(methodName);
                Object result = method.invoke(party);
                if (result instanceof Iterable<?> iterable) {
                    List<Object> collected = new ArrayList<>();
                    for (Object value : iterable)
                        collected.add(value);
                    if (!collected.isEmpty()) {
                        rawMembers = collected;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        if (rawMembers.isEmpty())
            return Collections.emptyList();
        List<String> names = new ArrayList<>();
        for (Object member : rawMembers) {
            String name = getMMOCorePlayerDataName(member);
            if (name != null && !name.isBlank())
                names.add(name);
        }
        return names;
    }

    private String getMMOCorePlayerDataName(Object playerData) {
        if (playerData == null)
            return null;

        String[] objectMethods = { "getPlayer", "getOfflinePlayer", "getBukkitPlayer", "getProfile" };
        for (String methodName : objectMethods) {
            try {
                Method method = playerData.getClass().getMethod(methodName);
                Object value = method.invoke(playerData);
                String name = extractPlayerLikeName(value);
                if (name != null && !name.isBlank())
                    return name;
            } catch (Throwable ignored) {
            }
        }

        String[] stringMethods = { "getName", "getPlayerName", "getUsername" };
        for (String methodName : stringMethods) {
            try {
                Method method = playerData.getClass().getMethod(methodName);
                Object value = method.invoke(playerData);
                if (value instanceof String str && !str.isBlank())
                    return str;
            } catch (Throwable ignored) {
            }
        }

        String[] uuidMethods = { "getUniqueId", "getUniqueID", "getUUID", "getUuid" };
        for (String methodName : uuidMethods) {
            try {
                Method method = playerData.getClass().getMethod(methodName);
                Object value = method.invoke(playerData);
                if (value instanceof UUID uuid) {
                    OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
                    return offline.getName() == null ? uuid.toString().substring(0, 8) : offline.getName();
                }
            } catch (Throwable ignored) {
            }
        }

        return null;
    }

    private String extractPlayerLikeName(Object value) {
        if (value == null)
            return null;
        if (value instanceof Player player)
            return player.getName();
        if (value instanceof OfflinePlayer offlinePlayer)
            return offlinePlayer.getName();

        try {
            Method getName = value.getClass().getMethod("getName");
            Object name = getName.invoke(value);
            if (name instanceof String str && !str.isBlank())
                return str;
        } catch (Throwable ignored) {
        }

        try {
            Method getUniqueId = value.getClass().getMethod("getUniqueId");
            Object uuid = getUniqueId.invoke(value);
            if (uuid instanceof UUID id) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
                return offline.getName() == null ? id.toString().substring(0, 8) : offline.getName();
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private String partyScoreboardPlaceholder(Player player, String key) {
        Object party = getMMOCoreParty(player);
        int max = getConfiguredPartyMaxMembers();

        if (party == null) {
            return switch (key) {
                case "party_in_group", "party_in_party" -> "false";
                case "party_max" -> String.valueOf(max);
                case "party_count" -> "0";
                default -> "";
            };
        }

        int count = countMMOCorePartyMembers(party);
        List<String> members = getMMOCorePartyMemberNames(party);

        if (key.equals("party_header"))
            return color("&dGrupo: &f" + count + "&7/&f" + max);
        if (key.equals("party_count"))
            return String.valueOf(count);
        if (key.equals("party_max"))
            return String.valueOf(max);
        if (key.equals("party_in_group") || key.equals("party_in_party"))
            return "true";
        if (key.equals("party_spacer"))
            return " ";
        if (key.equals("party_members"))
            return members.isEmpty() ? "" : String.join(", ", members);

        if (key.startsWith("party_member_")) {
            String rest = key.substring("party_member_".length());
            String mode = "line";

            if (rest.endsWith("_health_line")) {
                rest = rest.substring(0, rest.length() - "_health_line".length());
                mode = "health_line";
            } else if (rest.endsWith("_health_bar")) {
                rest = rest.substring(0, rest.length() - "_health_bar".length());
                mode = "health_bar";
            } else if (rest.endsWith("_health")) {
                rest = rest.substring(0, rest.length() - "_health".length());
                mode = "health";
            } else if (rest.endsWith("_name")) {
                rest = rest.substring(0, rest.length() - "_name".length());
                mode = "name";
            }

            try {
                int index = Integer.parseInt(rest) - 1;
                if (index < 0 || index >= members.size())
                    return "";
                String memberName = members.get(index);

                return switch (mode) {
                    case "name" -> color(memberName);
                    case "health" -> color(getPartyMemberHealthCounter(memberName));
                    case "health_bar" -> color(getPartyMemberHealthBar(memberName));
                    case "health_line" -> color("&7• &f" + memberName + " " + getPartyMemberHealthCounter(memberName));
                    default -> color("&7• &f" + memberName);
                };
            } catch (NumberFormatException ignored) {
                return "";
            }
        }

        return "";
    }

    private String getPartyMemberHealthCounter(String memberName) {
        Player online = Bukkit.getPlayerExact(memberName);
        if (online == null || !online.isOnline())
            return "&8Desconectado";

        double health = Math.max(0.0D, online.getHealth());
        double maxHealth = Math.max(1.0D, online.getMaxHealth());
        int healthPoints = (int) Math.ceil(health);
        int maxHealthPoints = (int) Math.ceil(maxHealth);
        return "&c" + healthPoints + "&7/&c" + maxHealthPoints + "❤";
    }

    private String getPartyMemberHealthBar(String memberName) {
        Player online = Bukkit.getPlayerExact(memberName);
        if (online == null || !online.isOnline())
            return "&8----------";

        double health = Math.max(0.0D, online.getHealth());
        double maxHealth = Math.max(1.0D, online.getMaxHealth());
        int filled = (int) Math.round((health / maxHealth) * 10.0D);
        filled = Math.max(0, Math.min(10, filled));

        StringBuilder bar = new StringBuilder("&8[");
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? "&c|" : "&7|");
        }
        bar.append("&8]");
        return bar.toString();
    }

    private void runConfiguredCommands(Player player, String path) {
        if (path == null || path.isBlank())
            return;
        List<String> commands = getConfig().getStringList(path + ".commands");
        player.closeInventory();
        for (String raw : commands) {
            String cmd = raw.replace("{player}", player.getName());
            if (cmd.startsWith("/"))
                cmd = cmd.substring(1);
            Bukkit.dispatchCommand(player, cmd);
        }
    }

    private void buyTitle(Player player, String titleId) {
        titleId = normalize(titleId);
        TitleDef title = titles.get(titleId);
        if (title == null) {
            msg(player, "title-not-found");
            return;
        }
        if (hasTitle(player, titleId)) {
            msg(player, "already-owned");
            return;
        }
        if (!title.purchasable) {
            msg(player, "title-locked");
            return;
        }
        if (getConfig().getBoolean("settings.use-vault-economy", true)) {
            if (economy == null) {
                player.sendMessage(color(getPrefix() + "&cVault/Economy no esta disponible."));
                return;
            }
            if (economy.getBalance(player) < title.price) {
                msg(player, "not-enough-money", Map.of("price", formatPrice(title.price)));
                return;
            }
            economy.withdrawPlayer(player, title.price);
        }
        giveTitle(player.getUniqueId(), player.getName(), titleId);
        saveData();
        msg(player, "title-bought", Map.of("title", color(title.display)));
    }

    private void equipTitle(Player player, String titleId) {
        if (isPunished(player.getUniqueId())) {
            msg(player, "punishment-title-locked");
            return;
        }
        titleId = normalize(titleId);
        TitleDef title = titles.get(titleId);
        if (title == null) {
            msg(player, "title-not-found");
            return;
        }
        if (!title.playerEquippable) {
            msg(player, "title-not-player-equippable");
            return;
        }
        if (!hasTitle(player, titleId)) {
            msg(player, "title-locked");
            return;
        }
        setActiveTitle(player.getUniqueId(), titleId);
        saveData();
        runEquipCommands(player, titleId);
        msg(player, "title-equipped", Map.of("title", color(title.display)));
    }

    private void clearActiveTitle(Player player) {
        if (isPunished(player.getUniqueId())) {
            msg(player, "punishment-title-locked");
            return;
        }
        if (!allowClearTitle()) {
            msg(player, "title-clear-disabled");
            return;
        }
        setActiveTitle(player.getUniqueId(), "");
        saveData();
        runClearCommands(player);
        msg(player, "title-cleared");
    }

    private void runEquipCommands(Player player, String titleId) {
        TitleDef title = titles.get(normalize(titleId));
        if (title == null)
            return;
        for (String raw : getConfig().getStringList("settings.commands-on-title-equip")) {
            String cmd = raw
                    .replace("{player}", player.getName())
                    .replace("{title_id}", title.id)
                    .replace("{title_display}", color(title.display))
                    .replace("{title_prefix}", color(title.prefix));
            if (cmd.startsWith("/"))
                cmd = cmd.substring(1);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    private void runClearCommands(Player player) {
        for (String raw : getConfig().getStringList("settings.commands-on-title-clear")) {
            String cmd = raw.replace("{player}", player.getName());
            if (cmd.startsWith("/"))
                cmd = cmd.substring(1);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    private boolean isPunishmentSystemEnabled() {
        return getConfig().getBoolean("punishment-titles.enabled", true);
    }

    private String getDefaultPunishmentTitleId() {
        return normalize(getConfig().getString("punishment-titles.default-title", "pecador"));
    }

    private boolean isPunishmentTitle(String titleId) {
        if (!isPunishmentSystemEnabled())
            return false;
        TitleDef title = titles.get(normalize(titleId));
        return title != null && title.punishment;
    }

    private boolean isPunished(UUID uuid) {
        return uuid != null && isPunishmentSystemEnabled() && data.getBoolean(path(uuid, "punishment.active"), false);
    }

    private String getPunishmentTitleId(UUID uuid) {
        String titleId = normalize(data.getString(path(uuid, "punishment.title"), getDefaultPunishmentTitleId()));
        return isPunishmentTitle(titleId) ? titleId : getDefaultPunishmentTitleId();
    }

    private void applyPunishmentTitle(OfflinePlayer target, String titleId) {
        UUID uuid = target.getUniqueId();
        titleId = normalize(titleId);
        if (!isPunished(uuid)) {
            data.set(path(uuid, "punishment.previous-title"), getStoredActiveTitleId(uuid));
        }
        data.set(path(uuid, "punishment.active"), true);
        data.set(path(uuid, "punishment.title"), titleId);
        if (target.getName() != null)
            data.set(path(uuid, "last-name"), target.getName());
        saveData();
        if (target.isOnline())
            runEquipCommands(target.getPlayer(), titleId);
    }

    private void removePunishmentTitle(OfflinePlayer target) {
        UUID uuid = target.getUniqueId();
        String previous = normalize(data.getString(path(uuid, "punishment.previous-title"), ""));
        data.set(path(uuid, "punishment"), null);
        Player online = target.getPlayer();
        String restored = previous;
        if (online != null && (restored.isBlank() || !titles.containsKey(restored) || !hasTitle(online, restored))) {
            restored = getInvalidTitleFallbackId();
        }
        if (restored.isBlank())
            restored = getClearTargetTitleId();
        setActiveTitle(uuid, restored);
        saveData();
        if (online != null) {
            validateActiveTitle(online, false);
            runEquipCommands(online, getActiveTitleId(uuid));
        }
    }

    private String getInvalidTitleFallbackId() {
        String fallback = normalize(getConfig().getString("settings.invalid-title-fallback", "aventurero"));
        if (titles.containsKey(fallback))
            return fallback;
        String def = getDefaultTitleId();
        return titles.containsKey(def) ? def : "";
    }

    private void validateActiveTitle(Player player, boolean notify) {
        if (player == null || !player.isOnline())
            return;
        UUID uuid = player.getUniqueId();
        if (isPunished(uuid)) {
            String punishment = getPunishmentTitleId(uuid);
            if (isPunishmentTitle(punishment))
                return;
            data.set(path(uuid, "punishment"), null);
        }

        String stored = getStoredActiveTitleId(uuid);
        if (stored.isBlank())
            return;
        TitleDef current = titles.get(stored);
        if (current != null && hasTitle(player, stored))
            return;

        String fallback = getInvalidTitleFallbackId();
        if (!fallback.isBlank() && titles.containsKey(fallback)) {
            setActiveTitle(uuid, fallback);
            saveData();
            runEquipCommands(player, fallback);
            if (notify)
                msg(player, "title-invalid-reset", Map.of("title", color(titles.get(fallback).display)));
        } else {
            setActiveTitle(uuid, getClearTargetTitleId());
            saveData();
            if (notify)
                msg(player, "title-invalid-cleared");
        }
    }

    private void validateAllOnlineTitles() {
        if (!getConfig().getBoolean("settings.active-title-validation.enabled", true))
            return;
        for (Player player : Bukkit.getOnlinePlayers())
            validateActiveTitle(player, true);
    }

    public boolean hasTitle(Player player, String titleId) {
        titleId = normalize(titleId);
        if (player.hasPermission("mdvsocial.admin"))
            return true;
        TitleDef title = titles.get(titleId);
        if (title == null)
            return false;
        if (title.punishment)
            return false;
        if (titleId.equals(getDefaultTitleId()))
            return true;
        if (getDefaultUnlockedTitles().contains(titleId))
            return true;
        if (getUnlockedTitles(player.getUniqueId()).contains(titleId))
            return true;
        return title.unlockPermission != null && !title.unlockPermission.isBlank()
                && player.hasPermission(title.unlockPermission);
    }

    private void giveTitle(UUID uuid, String name, String titleId) {
        titleId = normalize(titleId);
        Set<String> set = getUnlockedTitles(uuid);
        set.add(titleId);
        data.set(path(uuid, "unlocked"), new ArrayList<>(set));
        if (name != null)
            data.set(path(uuid, "last-name"), name);
        saveData();
    }

    private void removeTitle(UUID uuid, String titleId) {
        titleId = normalize(titleId);
        Set<String> set = getUnlockedTitles(uuid);
        set.remove(titleId);
        data.set(path(uuid, "unlocked"), new ArrayList<>(set));
        if (getStoredActiveTitleId(uuid).equals(titleId))
            data.set(path(uuid, "active"), getInvalidTitleFallbackId());
        saveData();
    }

    private Set<String> getUnlockedTitles(UUID uuid) {
        List<String> list = data.getStringList(path(uuid, "unlocked"));
        return list.stream().map(this::normalize).collect(Collectors.toCollection(HashSet::new));
    }

    private String getStoredActiveTitleId(UUID uuid) {
        return normalize(data.getString(path(uuid, "active"), ""));
    }

    public String getActiveTitleId(UUID uuid) {
        if (uuid == null)
            return "";
        if (isPunished(uuid)) {
            String punishment = getPunishmentTitleId(uuid);
            if (titles.containsKey(punishment))
                return punishment;
        }
        String active = getStoredActiveTitleId(uuid);
        if (active.isBlank() && isMandatoryTitle()) {
            String def = getDefaultTitleId();
            if (!def.isBlank() && titles.containsKey(def))
                return def;
        }
        return active;
    }

    private void setActiveTitle(UUID uuid, String titleId) {
        data.set(path(uuid, "active"), normalize(titleId));
    }

    public TitleDef getActiveTitle(Player player) {
        if (player == null)
            return null;
        validateActiveTitle(player, false);
        String id = getActiveTitleId(player.getUniqueId());
        if (id.isBlank())
            return null;
        TitleDef title = titles.get(id);
        if (title == null)
            return null;
        if (isPunished(player.getUniqueId()))
            return title;
        if (getConfig().getBoolean("settings.hide-invalid-active-title", true) && !hasTitle(player, id))
            return null;
        return title;
    }

    /**
     * API publica para otros plugins: devuelve el titulo equipado de cualquier
     * jugador por UUID.
     * Para jugadores offline no se revalidan permisos dinamicos, solo se lee el
     * titulo guardado/default.
     */
    public TitleDef getEquippedTitle(UUID uuid) {
        if (uuid == null)
            return null;
        Player online = Bukkit.getPlayer(uuid);
        if (online != null)
            return getActiveTitle(online);
        String id = getActiveTitleId(uuid);
        if (id.isBlank())
            return null;
        return titles.get(id);
    }

    public String getEquippedTitleId(UUID uuid) {
        TitleDef title = getEquippedTitle(uuid);
        return title == null ? "" : title.id;
    }

    public String getEquippedTitleDisplay(UUID uuid, boolean colored) {
        TitleDef title = getEquippedTitle(uuid);
        if (title == null)
            return "";
        return colored ? color(title.display) : stripColor(title.display);
    }

    public String getEquippedTitlePrefix(UUID uuid, boolean colored) {
        TitleDef title = getEquippedTitle(uuid);
        if (title == null)
            return "";
        return colored ? color(title.prefix) : stripColor(title.prefix);
    }

    public int countUnlocked(UUID uuid) {
        Set<String> all = new HashSet<>(getDefaultUnlockedTitles());
        all.addAll(getUnlockedTitles(uuid));
        all.removeIf(this::isHiddenTitle);
        return all.size();
    }

    private boolean isMandatoryTitle() {
        return getConfig().getBoolean("settings.mandatory-title", false);
    }

    private boolean allowClearTitle() {
        return getConfig().getBoolean("settings.allow-clear-title", !isMandatoryTitle());
    }

    private String getDefaultTitleId() {
        return normalize(getConfig().getString("settings.default-title", ""));
    }

    private String getClearTargetTitleId() {
        if (!isMandatoryTitle())
            return "";
        String def = getDefaultTitleId();
        return titles.containsKey(def) ? def : "";
    }

    private Set<String> getDefaultUnlockedTitles() {
        return getConfig().getStringList("settings.default-unlocked-titles")
                .stream()
                .map(this::normalize)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private boolean isHiddenTitle(String titleId) {
        titleId = normalize(titleId);
        TitleDef def = titles.get(titleId);
        if (def != null && def.hidden)
            return true;
        if (getConfig().getBoolean("settings.hide-default-title-in-menus", true) && titleId.equals(getDefaultTitleId()))
            return true;
        for (String hidden : getConfig().getStringList("settings.hidden-titles")) {
            if (titleId.equals(normalize(hidden)))
                return true;
        }
        return false;
    }

    private String path(UUID uuid, String child) {
        return "players." + uuid + "." + child;
    }

    private String normalize(String s) {
        if (s == null)
            return "";
        return s.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private String color(String s) {
        if (s == null)
            return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String stripColor(String s) {
        return ChatColor.stripColor(color(s));
    }

    private String getPrefix() {
        return color(getConfig().getString("messages.prefix", "&6[MDVSocial] &r"));
    }

    private void msg(CommandSender sender, String key) {
        msg(sender, key, Collections.emptyMap());
    }

    private void msg(CommandSender sender, String key, Map<String, String> replacements) {
        String raw = getConfig().getString("messages." + key, defaultMessage(key));
        for (Map.Entry<String, String> e : replacements.entrySet())
            raw = raw.replace("{" + e.getKey() + "}", e.getValue());
        sender.sendMessage(color(getPrefix() + raw));
    }

    private String defaultMessage(String key) {
        return switch (key) {
            case "friend-target-not-found" -> "&cEse jugador no está conectado o el nombre no es válido.";
            case "friend-request-accepted" -> "&aAceptaste la solicitud de amistad de &e{target}&a.";
            case "friend-request-denied" -> "&eRechazaste la solicitud de amistad de &c{target}&e.";
            case "party-target-not-found" -> "&cEse jugador no está conectado o el nombre no es válido.";
            case "party-request-accepted" -> "&aAceptaste la invitación al grupo de &e{target}&a.";
            case "party-request-denied" -> "&eRechazaste la invitación al grupo de &c{target}&e.";
            case "social-request-expired" -> "&cEsa solicitud ya expiró.";
            case "social-request-error" -> "&cNo se pudo procesar esa solicitud de MMOCore.";
            case "friend-removed" -> "&eEliminaste a &c{target} &ede tu lista de amigos.";
            case "friend-remove-error" -> "&cNo se pudo eliminar a &e{target} &cde tus amigos.";
            case "party-left" -> "&eSaliste de tu Grupo de Aventura.";
            case "party-not-in-party" -> "&cNo perteneces a ningún Grupo de Aventura.";
            case "party-leave-error" -> "&cNo se pudo abandonar el Grupo de Aventura.";
            default -> "&cMensaje faltante: " + key;
        };
    }

    private String formatPrice(double price) {
        if (Math.floor(price) == price)
            return String.valueOf((long) price);
        return String.format(Locale.US, "%.2f", price);
    }

    private double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("social")) {
            if (args.length == 1) {
                List<String> menus = new ArrayList<>(customMenus.keySet());
                menus.addAll(Arrays.asList("main", "titulos", "mis_titulos", "tienda", "rangos", "jugador"));
                return partial(args[0], menus);
            }
            if (args.length == 2 && isPlayerOptionsAlias(args[0])) {
                return partial(args[1],
                        Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
            }
            return Collections.emptyList();
        }

        if (commandName.equals("correo") || commandName.equals("carta")) {
            if (args.length == 1)
                return partial(args[0],
                        Arrays.asList("buzon", "enviar", "bloquear", "desbloquear", "bloqueados", "cancelar"));
            if (args.length == 2
                    && Arrays.asList("enviar", "bloquear", "desbloquear").contains(args[0].toLowerCase(Locale.ROOT))) {
                return null;
            }
            return Collections.emptyList();
        }

        if (!commandName.equals("mdvsocial"))
            return Collections.emptyList();
        if (args.length == 1)
            return partial(args[0], Arrays.asList("reload", "open", "title", "mail", "homes"));
        if (args.length == 3 && args[0].equalsIgnoreCase("open")) {
            List<String> menus = new ArrayList<>(customMenus.keySet());
            menus.addAll(Arrays.asList("main", "titulos", "mis_titulos", "tienda", "rangos"));
            return partial(args[2], menus);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mail")) {
            return partial(args[1], Arrays.asList("list", "view", "delete", "sendall", "sendall-days", "sendall-never",
                    "welcome-test"));
        }
        if (args.length == 2 && Arrays.asList("homes", "hogares", "casas").contains(args[0].toLowerCase(Locale.ROOT))) {
            return partial(args[1], Arrays.asList("status", "restore"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("title")) {
            return partial(args[1],
                    Arrays.asList("give", "remove", "set", "clear", "punish", "unpunish", "give-radius", "give-near"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("title")
                && Arrays.asList("give", "remove", "set").contains(args[1].toLowerCase(Locale.ROOT))) {
            return partial(args[3], new ArrayList<>(titles.keySet()));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("title")
                && Arrays.asList("punish", "castigar").contains(args[1].toLowerCase(Locale.ROOT))) {
            return partial(args[3],
                    titles.values().stream().filter(t -> t.punishment).map(t -> t.id).collect(Collectors.toList()));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("title") && args[1].equalsIgnoreCase("give-radius")) {
            return partial(args[3], new ArrayList<>(titles.keySet()));
        }
        if (args.length == 8 && args[0].equalsIgnoreCase("title") && args[1].equalsIgnoreCase("give-near")) {
            return partial(args[7], new ArrayList<>(titles.keySet()));
        }
        return Collections.emptyList();
    }

    private List<String> partial(String token, List<String> values) {
        String low = token.toLowerCase(Locale.ROOT);
        return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(low)).sorted()
                .collect(Collectors.toList());
    }

    static final class UiSoundDef {
        final Sound sound;
        float volume;
        float pitch;

        UiSoundDef(Sound sound, float volume, float pitch) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }
    }

    static final class MenuHolder implements InventoryHolder {
        final String type;
        int page;
        final String menuId;
        final String previousMenu;
        final int previousPage;
        final UUID targetUuid;
        final String targetName;
        final boolean targetOnline;
        Inventory inventory;

        MenuHolder(String type, int page) {
            this(type, page, "", "", 1, null, "", false);
        }

        MenuHolder(String type, int page, String menuId, String previousMenu, int previousPage) {
            this(type, page, menuId, previousMenu, previousPage, null, "", false);
        }

        MenuHolder(String type, int page, String menuId, String previousMenu, int previousPage, UUID targetUuid,
                String targetName, boolean targetOnline) {
            this.type = type;
            this.page = page;
            this.menuId = menuId == null ? "" : menuId;
            this.previousMenu = previousMenu == null ? "" : previousMenu;
            this.previousPage = previousPage <= 0 ? 1 : previousPage;
            this.targetUuid = targetUuid;
            this.targetName = targetName == null ? "" : targetName;
            this.targetOnline = targetOnline;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    static final class ExternalGuiAction {
        final String id;
        final String titleEquals;
        final String titleContains;
        final List<Integer> slots;
        final List<String> playerCommands;
        final List<String> consoleCommands;
        final boolean closeOnClick;
        final boolean cancelEvent;
        final String sound;

        ExternalGuiAction(String id, String titleEquals, String titleContains, List<Integer> slots,
                List<String> playerCommands, List<String> consoleCommands, boolean closeOnClick, boolean cancelEvent,
                String sound) {
            this.id = id == null ? "" : id;
            this.titleEquals = titleEquals == null ? ""
                    : ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', titleEquals)).trim()
                            .toLowerCase(Locale.ROOT);
            this.titleContains = titleContains == null ? ""
                    : ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', titleContains)).trim()
                            .toLowerCase(Locale.ROOT);
            this.slots = slots == null ? Collections.emptyList() : slots;
            this.playerCommands = playerCommands == null ? Collections.emptyList() : playerCommands;
            this.consoleCommands = consoleCommands == null ? Collections.emptyList() : consoleCommands;
            this.closeOnClick = closeOnClick;
            this.cancelEvent = cancelEvent;
            this.sound = sound == null ? "" : sound;
        }

        boolean matches(String rawTitle, int slot) {
            if (!slots.contains(slot))
                return false;
            String title = rawTitle == null ? "" : rawTitle.trim().toLowerCase(Locale.ROOT);
            if (!titleEquals.isBlank() && title.equals(titleEquals))
                return true;
            if (!titleContains.isBlank() && title.contains(titleContains))
                return true;
            return titleEquals.isBlank() && titleContains.isBlank();
        }
    }

    static final class CustomMenuDef {
        final String id;
        final String title;
        final int size;
        final String permission;
        final Map<Integer, List<CustomMenuItem>> pages = new HashMap<>();

        CustomMenuDef(String id, String title, int size, String permission) {
            this.id = id;
            this.title = title;
            this.size = size;
            this.permission = permission == null ? "" : permission.trim();
        }

        int maxPage() {
            if (pages.isEmpty())
                return 1;
            return pages.keySet().stream().max(Integer::compareTo).orElse(1);
        }
    }

    static final class CustomMenuItem {
        final String id;
        final int slot;
        final String material;
        final int amount;
        final String name;
        final List<String> lore;
        final String headOwner;
        final String texture;
        final String action;
        final String rightAction;
        final String targetMenu;
        final List<String> commands;
        final List<String> rightCommands;
        final boolean closeOnClick;
        final String visibleWhen;
        final String conditionPlaceholder;
        final String conditionEquals;
        final String trueMenu;
        final String falseMenu;
        final String clansMenu;
        final String sound;
        final String permission;
        final boolean hideWithoutPermission;
        final boolean useClanBanner;

        CustomMenuItem(String id, int slot, String material, int amount, String name, List<String> lore,
                String headOwner, String texture, String action, String rightAction, String targetMenu,
                List<String> commands, List<String> rightCommands, boolean closeOnClick, String visibleWhen,
                String conditionPlaceholder, String conditionEquals, String trueMenu, String falseMenu,
                String clansMenu, String sound, String permission, boolean hideWithoutPermission,
                boolean useClanBanner) {
            this.id = id;
            this.slot = slot;
            this.material = material == null ? "PAPER" : material;
            this.amount = amount;
            this.name = name == null ? "" : name;
            this.lore = lore == null ? Collections.emptyList() : lore;
            this.headOwner = headOwner == null ? "" : headOwner;
            this.texture = texture == null ? "" : texture;
            this.action = action == null ? "" : action;
            this.rightAction = rightAction == null ? "" : rightAction;
            this.targetMenu = targetMenu == null ? "" : targetMenu;
            this.commands = commands == null ? Collections.emptyList() : commands;
            this.rightCommands = rightCommands == null ? Collections.emptyList() : rightCommands;
            this.closeOnClick = closeOnClick;
            this.visibleWhen = visibleWhen == null ? "always"
                    : visibleWhen.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
            this.conditionPlaceholder = conditionPlaceholder == null ? "" : conditionPlaceholder;
            this.conditionEquals = conditionEquals == null ? "true" : conditionEquals;
            this.trueMenu = trueMenu == null ? "" : trueMenu;
            this.falseMenu = falseMenu == null ? "" : falseMenu;
            this.clansMenu = clansMenu == null ? "" : clansMenu;
            this.sound = sound == null ? "" : sound;
            this.permission = permission == null ? "" : permission.trim();
            this.hideWithoutPermission = hideWithoutPermission;
            this.useClanBanner = useClanBanner;
        }

        boolean isVisible(MDVSocialPlugin plugin, Player viewer, UUID targetUuid, boolean targetOnline) {
            boolean hasTarget = targetUuid != null;
            boolean self = hasTarget && viewer.getUniqueId().equals(targetUuid);
            boolean friend = hasTarget && plugin.isMMOCoreFriend(viewer, targetUuid);
            return switch (visibleWhen) {
                case "online", "target_online", "friend_online" -> hasTarget && targetOnline;
                case "offline", "target_offline", "friend_offline" -> hasTarget && !targetOnline;
                case "target", "has_target" -> hasTarget;
                case "friend", "is_friend", "already_friend" -> hasTarget && friend;
                case "not_friend", "non_friend" -> hasTarget && !friend && !self;
                case "online_friend" -> hasTarget && targetOnline && friend;
                case "online_not_friend", "online_non_friend" -> hasTarget && targetOnline && !friend && !self;
                case "offline_not_friend", "offline_non_friend" -> hasTarget && !targetOnline && !friend && !self;
                case "self" -> self;
                case "not_self", "other" -> hasTarget && !self;
                case "online_not_self", "online_other" -> hasTarget && targetOnline && !self;
                default -> true;
            };
        }
    }

    static final class ServerMailCampaign {
        final String id;
        String author;
        String message;
        long sentAt;
        long expiresAt;
        final Set<UUID> recipients = new HashSet<>();
        int unread;

        ServerMailCampaign(String id, String author, String message, long sentAt, long expiresAt) {
            this.id = id;
            this.author = author == null || author.isBlank() ? "MDVCRAFT" : author;
            this.message = message == null ? "" : message;
            this.sentAt = sentAt;
            this.expiresAt = expiresAt;
        }
    }

    enum MailStage {
        RECIPIENT, MESSAGE, BLOCK, UNBLOCK
    }

    static final class MailComposeSession {
        final MailStage stage;
        final String targetName;
        final UUID targetUuid;
        final String returnMenu;
        final int returnPage;

        MailComposeSession(MailStage stage, String targetName) {
            this(stage, targetName, null, "correo", 1);
        }

        MailComposeSession(MailStage stage, String targetName, String returnMenu, int returnPage) {
            this(stage, targetName, null, returnMenu, returnPage);
        }

        MailComposeSession(MailStage stage, String targetName, UUID targetUuid, String returnMenu, int returnPage) {
            this.stage = stage;
            this.targetName = targetName;
            this.targetUuid = targetUuid;
            String normalizedReturnMenu = returnMenu == null ? "correo" : returnMenu;
            this.returnMenu = normalizedReturnMenu;
            this.returnPage = normalizedReturnMenu.equalsIgnoreCase("MAILBOX")
                    || normalizedReturnMenu.equalsIgnoreCase("buzon")
                            ? Math.max(0, returnPage)
                            : (returnPage <= 0 ? 1 : returnPage);
        }
    }

    static final class ChatProfileSnapshot {
        final String playerName;
        final String level;
        final String race;
        final String title;
        final String rank;
        final String clan;

        ChatProfileSnapshot(String playerName, String level, String race, String title, String rank, String clan) {
            this.playerName = playerName == null ? "jugador" : playerName;
            this.level = level == null ? "&7Desconocido" : level;
            this.race = race == null ? "&7Sin raza" : race;
            this.title = title == null ? "&7Sin título" : title;
            this.rank = rank == null ? "&7Sin rango" : rank;
            this.clan = clan == null ? "&8Sin clan" : clan;
        }
    }

    public static final class TitleDef {
        public final String id;
        public final String display;
        public final String prefix;
        public final String material;
        public final String headOwner;
        public final String texture;
        public final boolean purchasable;
        public final double price;
        public final String unlockPermission;
        public final boolean hidden;
        public final boolean playerEquippable;
        public final boolean punishment;
        public final List<String> lore;

        TitleDef(String id, String display, String prefix, String material, String headOwner, String texture,
                boolean purchasable, double price, String unlockPermission, boolean hidden, boolean playerEquippable,
                boolean punishment, List<String> lore) {
            this.id = id;
            this.display = display;
            this.prefix = prefix;
            this.material = material;
            this.headOwner = headOwner;
            this.texture = texture == null ? "" : texture;
            this.purchasable = purchasable;
            this.price = price;
            this.unlockPermission = unlockPermission;
            this.hidden = hidden;
            this.playerEquippable = playerEquippable;
            this.punishment = punishment;
            this.lore = lore == null ? Collections.emptyList() : lore;
        }
    }

    static final class RankDef {
        final String id;
        final String display;
        final String material;
        final String permission;
        final int page;
        final int slot;
        final List<String> lore;

        RankDef(String id, String display, String material, String permission, int page, int slot, List<String> lore) {
            this.id = id;
            this.display = display;
            this.material = material;
            this.permission = permission;
            this.page = page;
            this.slot = slot;
            this.lore = lore == null ? Collections.emptyList() : lore;
        }
    }

    public static final class MDVSocialExpansion extends PlaceholderExpansion {
        private final MDVSocialPlugin plugin;

        MDVSocialExpansion(MDVSocialPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public String getIdentifier() {
            return "mdvsocial";
        }

        @Override
        public String getAuthor() {
            return "MDVCRAFT";
        }

        @Override
        public String getVersion() {
            return plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(Player player, String params) {
            return handlePlaceholder(player, params);
        }

        @Override
        public String onRequest(OfflinePlayer offlinePlayer, String params) {
            return handlePlaceholder(offlinePlayer, params);
        }

        private String handlePlaceholder(OfflinePlayer viewer, String params) {
            if (viewer == null || params == null)
                return "";
            String p = params.toLowerCase(Locale.ROOT);

            UUID viewerUuid = viewer.getUniqueId();
            TitleDef active = viewer instanceof Player online ? plugin.getActiveTitle(online)
                    : plugin.getEquippedTitle(viewerUuid);

            String targetValue;
            if ((targetValue = afterPrefix(p, "title_of_")) != null)
                return plugin.getEquippedTitleDisplay(resolveTarget(targetValue), false);
            if ((targetValue = afterPrefix(p, "title_colored_of_")) != null)
                return plugin.getEquippedTitleDisplay(resolveTarget(targetValue), true);
            if ((targetValue = afterPrefix(p, "title_prefix_of_")) != null)
                return plugin.getEquippedTitlePrefix(resolveTarget(targetValue), true);
            if ((targetValue = afterPrefix(p, "title_prefix_plain_of_")) != null)
                return plugin.getEquippedTitlePrefix(resolveTarget(targetValue), false);
            if ((targetValue = afterPrefix(p, "title_id_of_")) != null)
                return plugin.getEquippedTitleId(resolveTarget(targetValue));
            if ((targetValue = afterPrefix(p, "active_title_of_")) != null)
                return plugin.getEquippedTitleId(resolveTarget(targetValue));

            return switch (p) {
                case "title" -> active == null ? "" : ChatColor.stripColor(plugin.color(active.display));
                case "title_colored" -> active == null ? "" : plugin.color(active.display);
                case "title_prefix" -> active == null ? "" : plugin.color(active.prefix);
                case "title_prefix_plain" -> active == null ? "" : ChatColor.stripColor(plugin.color(active.prefix));
                case "active_title", "title_id" -> active == null ? "" : active.id;
                case "unlocked_titles" -> String.valueOf(plugin.countUnlocked(viewerUuid));
                case "party_header", "party_count", "party_max", "party_in_group", "party_in_party", "party_spacer",
                        "party_members" ->
                    viewer instanceof Player online ? plugin.partyScoreboardPlaceholder(online, p) : "";
                default -> p.startsWith("party_member_") && viewer instanceof Player online
                        ? plugin.partyScoreboardPlaceholder(online, p)
                        : "";
            };
        }

        private String afterPrefix(String value, String prefix) {
            return value.startsWith(prefix) ? value.substring(prefix.length()) : null;
        }

        private UUID resolveTarget(String token) {
            if (token == null || token.isBlank())
                return null;
            String raw = token.trim();
            if (raw.startsWith("uuid_"))
                raw = raw.substring("uuid_".length());
            try {
                return UUID.fromString(raw);
            } catch (IllegalArgumentException ignored) {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(raw);
                return offline == null ? null : offline.getUniqueId();
            }
        }
    }
}
