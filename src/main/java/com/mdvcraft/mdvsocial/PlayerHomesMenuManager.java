package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.util.FormImage;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Menú personal de hogares usando EssentialsX como backend.
 *
 * El modo LOCK_EXCESS conserva todos los hogares de EssentialsX, pero suspende
 * los que exceden el límite actual de MDVSocial. La selección se mantiene
 * estable:
 * primero preferred-home-names y luego el resto por nombre.
 */
public final class PlayerHomesMenuManager implements Listener, CommandExecutor, TabCompleter {

    private static final String CONFIG_PATH = "homes-menu";

    private final MDVSocialPlugin plugin;
    private final org.bukkit.NamespacedKey keyAction;
    private final org.bukkit.NamespacedKey keyHomeName;
    private final org.bukkit.NamespacedKey keyHomeNumber;

    private boolean enabled;
    private String usePermission;
    private int size;
    private String title;
    private int maxVisibleHomes;
    private String defaultHomeNameFormat;
    private String userdataPath;
    private boolean sortHomesByName;
    private boolean closeOnAction;
    private int defaultMaxHomes;

    private boolean lockExcessEnabled;
    private String lockMode;
    private boolean restoreOnUpgrade;
    private boolean allowDeleteLocked;
    private String lockBypassPermission;
    private List<String> preferredHomeNames = new ArrayList<>();
    private Set<String> interceptedHomeCommands = new HashSet<>();

    private String teleportCommand;
    private String setCommand;
    private String deleteCommand;

    private File lockDataFile;
    private YamlConfiguration lockData;
    private CoalescingFileWriter lockWriter;
    private boolean essentialsApiLogged;
    private boolean essentialsApiFailureLogged;

    public PlayerHomesMenuManager(MDVSocialPlugin plugin) {
        this.plugin = plugin;
        this.keyAction = new org.bukkit.NamespacedKey(plugin, "homes_action");
        this.keyHomeName = new org.bukkit.NamespacedKey(plugin, "homes_name");
        this.keyHomeNumber = new org.bukkit.NamespacedKey(plugin, "homes_number");
    }

    public void enable() {
        loadLockData();
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        PluginCommand command = plugin.getCommand("casa");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        } else {
            plugin.getLogger().warning("No se pudo registrar /casa porque no existe en plugin.yml.");
        }
    }

    public void disable() {
        saveLockData();
        if (lockWriter != null) try { lockWriter.close(); }
        catch (Exception e) { plugin.getLogger().severe("No se pudo guardar homes-lock-data.yml: " + e); }
        HandlerList.unregisterAll(this);
    }

    public void reload() {
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean(CONFIG_PATH + ".enabled", true);
        usePermission = config.getString(CONFIG_PATH + ".use-permission", "mdvsocial.homes.use");
        size = normalizeMenuSize(config.getInt(CONFIG_PATH + ".size", 54));
        title = config.getString(CONFIG_PATH + ".title", "&8Casas &8- &6&l{player}");
        maxVisibleHomes = Math.max(1, Math.min(9, config.getInt(CONFIG_PATH + ".max-visible-homes", 4)));
        defaultHomeNameFormat = config.getString(CONFIG_PATH + ".default-home-name-format", "casa{number}");
        userdataPath = config.getString(CONFIG_PATH + ".essentials-userdata-path", "plugins/Essentials/userdata");
        sortHomesByName = config.getBoolean(CONFIG_PATH + ".sort-homes-by-name", false);
        closeOnAction = config.getBoolean(CONFIG_PATH + ".close-on-action", true);
        defaultMaxHomes = Math.max(0, config.getInt(CONFIG_PATH + ".max-homes.default", 1));

        lockExcessEnabled = config.getBoolean(CONFIG_PATH + ".suspended-homes.enabled", true);
        lockMode = config.getString(CONFIG_PATH + ".suspended-homes.mode", "LOCK_EXCESS");
        restoreOnUpgrade = config.getBoolean(CONFIG_PATH + ".suspended-homes.restore-on-upgrade", true);
        allowDeleteLocked = config.getBoolean(CONFIG_PATH + ".suspended-homes.allow-delete-locked", true);
        lockBypassPermission = config.getString(CONFIG_PATH + ".suspended-homes.bypass-permission",
                "mdvsocial.homes.lock.bypass");
        preferredHomeNames = config.getStringList(CONFIG_PATH + ".suspended-homes.preferred-home-names").stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(ArrayList::new));
        if (preferredHomeNames.isEmpty()) {
            for (int i = 1; i <= maxVisibleHomes; i++)
                preferredHomeNames.add(generateHomeName(i));
        }
        interceptedHomeCommands = config.getStringList(CONFIG_PATH + ".suspended-homes.intercept-commands").stream()
                .filter(s -> s != null && !s.isBlank())
                .map(this::normalizeCommandLabel)
                .collect(Collectors.toCollection(HashSet::new));
        if (interceptedHomeCommands.isEmpty()) {
            interceptedHomeCommands.addAll(List.of("home", "ehome", "essentials:home", "essentials:ehome"));
        }

        teleportCommand = normalizeCommand(config.getString(CONFIG_PATH + ".commands.teleport", "home {home_name}"));
        setCommand = normalizeCommand(config.getString(CONFIG_PATH + ".commands.set", "sethome {home_name}"));
        deleteCommand = normalizeCommand(config.getString(CONFIG_PATH + ".commands.delete", "delhome {home_name}"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(getMessage("only-players", "&cSolo jugadores.")));
            return true;
        }
        if (!enabled) {
            sendMessage(player, "disabled", "&cEl menú de casas está desactivado.", Map.of());
            return true;
        }
        if (usePermission != null && !usePermission.isBlank() && !player.hasPermission(usePermission)) {
            sendMessage(player, "no-permission", "&cNo tienes permiso para usar este menú.", Map.of());
            return true;
        }
        openHomesMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

    private void openHomesMenu(Player player) {
        if (plugin.isBedrockPlayer(player)) {
            openBedrockHomesMenu(player);
            return;
        }
        List<HomeData> homes = readHomes(player);
        int maxHomes = getMaxHomes(player);
        Set<String> lockedNames = syncLockedHomes(player, homes, maxHomes);
        List<HomeData> displayedHomes = homes.stream()
                .map(home -> home.withLocked(lockedNames.contains(normalizeHomeName(home.name))))
                .collect(Collectors.toCollection(ArrayList::new));

        Inventory inv = Bukkit.createInventory(new HomesHolder(), size,
                color(applyGlobalPlaceholders(title, player, displayedHomes, maxHomes)));
        fill(inv, player, displayedHomes, maxHomes);
        inv.setItem(slot("items.info.slot", 13),
                itemFromPath("items.info", "INFO", null, 0, player, displayedHomes, maxHomes));

        // Show pre-existing homes outside the configured default slot count too;
        // never hide an Essentials home just because its ordinal exceeds the GUI default.
        for (int i = 1; i <= Math.min(9, Math.max(maxVisibleHomes, displayedHomes.size())); i++) {
            HomeData home = homeAtPosition(displayedHomes, i);
            boolean slotWithinLimit = i <= maxHomes;
            int teleportSlot = slot("items.teleport.slot-" + i, defaultTeleportSlot(i));
            int setSlot = slot("items.set.slot-" + i, defaultSetSlot(i));

            if (home != null && home.locked) {
                inv.setItem(teleportSlot, itemFromPath("items.teleport.locked", "LOCKED_HOME", home, i, player,
                        displayedHomes, maxHomes));
                String lockedAction = allowDeleteLocked ? "DELETE_LOCKED_HOME" : "LOCKED_HOME";
                inv.setItem(setSlot,
                        itemFromPath("items.set.locked", lockedAction, home, i, player, displayedHomes, maxHomes));
                continue;
            }

            if (!slotWithinLimit) {
                HomeData lockedSlot = HomeData.missing(generateHomeName(i)).withLocked(true);
                inv.setItem(teleportSlot, itemFromPath("items.teleport.locked", "LOCKED_HOME", lockedSlot, i, player,
                        displayedHomes, maxHomes));
                inv.setItem(setSlot, itemFromPath("items.set.locked", "LOCKED_HOME", lockedSlot, i, player,
                        displayedHomes, maxHomes));
                continue;
            }

            if (home == null) {
                String generatedName = generateHomeName(i);
                HomeData missingHome = HomeData.missing(generatedName);
                inv.setItem(teleportSlot, itemFromPath("items.teleport.missing", "MISSING", missingHome, i, player,
                        displayedHomes, maxHomes));
                String setMissingPath = section("items.set.missing") != null || section("set.missing") != null
                        ? "items.set.missing"
                        : "items.set.available";
                inv.setItem(setSlot,
                        itemFromPath(setMissingPath, "SET_HOME", missingHome, i, player, displayedHomes, maxHomes));
            } else {
                inv.setItem(teleportSlot, itemFromPath("items.teleport.available", "TELEPORT_HOME", home, i, player,
                        displayedHomes, maxHomes));
                inv.setItem(setSlot,
                        itemFromPath("items.set.available", "SET_HOME", home, i, player, displayedHomes, maxHomes));
            }
        }

        ConfigurationSection back = section("items.back");
        if (back == null || back.getBoolean("enabled", true)) {
            int backSlot = slot("items.back.slot", Math.min(size - 5, 49));
            if (backSlot >= 0 && backSlot < size) {
                inv.setItem(backSlot, itemFromPath("items.back", "BACK", null, 0, player, displayedHomes, maxHomes));
            }
        }

        player.openInventory(inv);
    }

    private void openBedrockHomesMenu(Player player) {
        List<HomeData> homes = readHomes(player);
        int maxHomes = getMaxHomes(player);
        Set<String> lockedNames = syncLockedHomes(player, homes, maxHomes);
        List<HomeData> displayed = homes.stream()
                .map(home -> home.withLocked(lockedNames.contains(normalizeHomeName(home.name))))
                .collect(Collectors.toCollection(ArrayList::new));
        YamlConfiguration ui = bedrockHomesUi();

        boolean hasLocked = lockExcessEnabled && displayed.stream().anyMatch(home -> home.locked);
        String lockWarning = hasLocked
                ? bedrockHomeText(ui, "lock-warning", "&cAlgunos hogares están suspendidos por tu límite actual.",
                        player, null, 0, displayed, maxHomes)
                : "";
        String content = bedrockHomeLines(ui, "content",
                List.of("&7Hogares: &f{home_count}&7/&f{max_homes}", "{lock_warning}"),
                player, null, 0, displayed, maxHomes)
                .replace("{lock_warning}", lockWarning);

        SimpleForm.Builder builder = SimpleForm.builder()
                .title(bedrockHomeText(ui, "title", title, player, null, 0, displayed, maxHomes))
                .content(content);
        List<Runnable> actions = new ArrayList<>();

        int totalSlots = Math.max(maxVisibleHomes, Math.min(displayed.size(), 9));
        for (int i = 1; i <= totalSlots; i++) {
            HomeData home = homeAtPosition(displayed, i);
            boolean withinLimit = i <= maxHomes;
            if (home == null) {
                String generated = generateHomeName(i);
                HomeData missing = HomeData.missing(generated);
                if (withinLimit) {
                    addBedrockHomeButton(builder, ui, "buttons.create",
                            bedrockHomeText(ui, "buttons.create.text",
                                    "&aCrear &f{home_name}\n&7Guardar ubicación actual",
                                    player, missing, i, displayed, maxHomes));
                    int number = i;
                    actions.add(() -> runHomeCommand(player, setCommand, generated, number, true));
                } else {
                    addBedrockHomeButton(builder, ui, "buttons.locked-slot",
                            bedrockHomeText(ui, "buttons.locked-slot.text",
                                    "&8Espacio {home_number} bloqueado\n&7Aumenta tu límite de hogares",
                                    player, missing.withLocked(true), i, displayed, maxHomes));
                    actions.add(() -> openBedrockHomesMenu(player));
                }
                continue;
            }

            int number = i;
            if (home.locked) {
                addBedrockHomeButton(builder, ui, "buttons.locked-home",
                        bedrockHomeText(ui, "buttons.locked-home.text", "&c{home_name}\n&7Hogar suspendido",
                                player, home, i, displayed, maxHomes));
                HomeData selected = home;
                actions.add(() -> openBedrockLockedHome(player, selected, number));
            } else {
                addBedrockHomeButton(builder, ui, "buttons.home",
                        bedrockHomeText(ui, "buttons.home.text",
                                "&6{home_name}\n&7{home_world} {home_x}, {home_y}, {home_z}",
                                player, home, i, displayed, maxHomes));
                HomeData selected = home;
                actions.add(() -> openBedrockHomeActions(player, selected, number));
            }
        }

        addBedrockHomeButton(builder, ui, "buttons.back",
                bedrockHomeText(ui, "buttons.back.text", "&6Volver a MDVSocial",
                        player, null, 0, displayed, maxHomes));
        actions.add(() -> Bukkit.dispatchCommand(player, "social"));

        builder.validResultHandler(response -> Bukkit.getScheduler().runTask(plugin, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size())
                actions.get(index).run();
        }));
        sendBedrockForm(player, builder);
    }

    private void openBedrockHomeActions(Player player, HomeData home, int number) {
        List<HomeData> homes = readHomes(player);
        int maxHomes = getMaxHomes(player);
        YamlConfiguration ui = bedrockHomesUi();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(bedrockHomeText(ui, "home-actions.title", "&6&l{home_name}", player, home, number, homes,
                        maxHomes))
                .content(bedrockHomeLines(ui, "home-actions.content",
                        List.of("&7Mundo: &f{home_world}", "&7X: &f{home_x}  &7Y: &f{home_y}  &7Z: &f{home_z}"),
                        player, home, number, homes, maxHomes));
        addBedrockHomeButton(builder, ui, "home-actions.teleport",
                bedrockHomeText(ui, "home-actions.teleport", "&aTeletransportarse", player, home, number, homes,
                        maxHomes));
        addBedrockHomeButton(builder, ui, "home-actions.update",
                bedrockHomeText(ui, "home-actions.update", "&eActualizar ubicación", player, home, number, homes,
                        maxHomes));
        addBedrockHomeButton(builder, ui, "home-actions.delete",
                bedrockHomeText(ui, "home-actions.delete", "&cEliminar hogar", player, home, number, homes, maxHomes));
        addBedrockHomeButton(builder, ui, "home-actions.back",
                bedrockHomeText(ui, "home-actions.back", "&6Volver", player, home, number, homes, maxHomes));
        builder.validResultHandler(response -> Bukkit.getScheduler().runTask(plugin, () -> {
            switch (response.clickedButtonId()) {
                case 0 -> runHomeCommand(player, teleportCommand, home.name, number, true);
                case 1 -> runHomeCommand(player, setCommand, home.name, number, true);
                case 2 -> confirmBedrockDeleteHome(player, home, number);
                default -> openBedrockHomesMenu(player);
            }
        }));
        sendBedrockForm(player, builder);
    }

    private void openBedrockLockedHome(Player player, HomeData home, int number) {
        List<HomeData> homes = readHomes(player);
        int maxHomes = getMaxHomes(player);
        YamlConfiguration ui = bedrockHomesUi();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(bedrockHomeText(ui, "locked-menu.title", "&c&lHogar suspendido", player, home, number, homes,
                        maxHomes))
                .content(bedrockHomeLines(ui, "locked-menu.content",
                        List.of("&7{home_name} está guardado pero supera tu límite actual.",
                                "&7No puedes teletransportarte hasta recuperar un espacio."),
                        player, home, number, homes, maxHomes));
        List<Runnable> actions = new ArrayList<>();
        if (allowDeleteLocked) {
            addBedrockHomeButton(builder, ui, "locked-menu.delete",
                    bedrockHomeText(ui, "locked-menu.delete", "&cEliminar hogar suspendido", player, home, number,
                            homes, maxHomes));
            actions.add(() -> confirmBedrockDeleteHome(player, home, number));
        }
        addBedrockHomeButton(builder, ui, "locked-menu.back",
                bedrockHomeText(ui, "locked-menu.back", "&6Volver", player, home, number, homes, maxHomes));
        actions.add(() -> openBedrockHomesMenu(player));
        builder.validResultHandler(response -> Bukkit.getScheduler().runTask(plugin, () -> {
            int index = response.clickedButtonId();
            if (index >= 0 && index < actions.size())
                actions.get(index).run();
        }));
        sendBedrockForm(player, builder);
    }

    private void confirmBedrockDeleteHome(Player player, HomeData home, int number) {
        List<HomeData> homes = readHomes(player);
        int maxHomes = getMaxHomes(player);
        YamlConfiguration ui = bedrockHomesUi();
        SimpleForm.Builder builder = SimpleForm.builder()
                .title(bedrockHomeText(ui, "delete-confirm.title", "&c&lEliminar {home_name}", player, home, number,
                        homes, maxHomes))
                .content(bedrockHomeText(ui, "delete-confirm.content",
                        "&7Esta acción elimina el hogar de EssentialsX. ¿Continuar?", player, home, number, homes,
                        maxHomes));
        addBedrockHomeButton(builder, ui, "delete-confirm.confirm",
                bedrockHomeText(ui, "delete-confirm.confirm", "&cSí, eliminar", player, home, number, homes, maxHomes));
        addBedrockHomeButton(builder, ui, "delete-confirm.cancel",
                bedrockHomeText(ui, "delete-confirm.cancel", "&aNo, volver", player, home, number, homes, maxHomes));
        builder.validResultHandler(response -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (response.clickedButtonId() == 0)
                runHomeCommand(player, deleteCommand, home.name, number, true);
            else
                openBedrockHomesMenu(player);
        }));
        sendBedrockForm(player, builder);
    }

    private YamlConfiguration bedrockHomesUi() {
        YamlConfiguration config = plugin.getBedrockMenuConfig("homes");
        return config == null ? new YamlConfiguration() : config;
    }

    private String bedrockHomeLines(YamlConfiguration ui, String path, List<String> defaults,
            Player player, HomeData home, int number, List<HomeData> homes, int maxHomes) {
        List<String> lines = ui.getStringList(path);
        if (lines.isEmpty())
            lines = defaults;
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (out.length() > 0)
                out.append('\n');
            out.append(bedrockHomeRaw(line, player, home, number, homes, maxHomes));
        }
        return out.toString();
    }

    private String bedrockHomeText(YamlConfiguration ui, String path, String def,
            Player player, HomeData home, int number, List<HomeData> homes, int maxHomes) {
        return bedrockHomeRaw(ui.getString(path, def), player, home, number, homes, maxHomes);
    }

    private String bedrockHomeRaw(String raw, Player player, HomeData home, int number,
            List<HomeData> homes, int maxHomes) {
        String text = home == null
                ? applyGlobalPlaceholders(raw, player, homes, maxHomes)
                : applyPlaceholders(raw, player, home, number, homes, maxHomes);
        return color(text);
    }

    private void addBedrockHomeButton(SimpleForm.Builder builder, YamlConfiguration ui, String path, String text) {
        String data = ui.getString(path + ".image.data", "");
        if (data == null || data.isBlank()) {
            builder.button(text);
            return;
        }
        String type = ui.getString(path + ".image.type", "URL");
        builder.button(text, "PATH".equalsIgnoreCase(type) ? FormImage.Type.PATH : FormImage.Type.URL, data);
    }

    private boolean sendBedrockForm(Player player, SimpleForm.Builder builder) {
        try {
            return FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder);
        } catch (Throwable ex) {
            plugin.getLogger().warning("No se pudo enviar formulario de hogares a " + player.getName()
                    + ": " + ex.getMessage());
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (!(event.getView().getTopInventory().getHolder() instanceof HomesHolder))
            return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta())
            return;

        PersistentDataContainer pdc = clicked.getItemMeta().getPersistentDataContainer();
        String action = pdc.get(keyAction, PersistentDataType.STRING);
        if (action == null || action.isBlank())
            return;

        String homeName = pdc.get(keyHomeName, PersistentDataType.STRING);
        Integer homeNumber = pdc.get(keyHomeNumber, PersistentDataType.INTEGER);
        if (homeNumber == null)
            homeNumber = 0;

        switch (action) {
            case "TELEPORT_HOME" -> {
                if (isHomeLocked(player, homeName)) {
                    sendLockedMessage(player, homeName, homeNumber);
                    return;
                }
                runHomeCommand(player, teleportCommand, homeName, homeNumber, true);
            }
            case "SET_HOME" -> {
                if (isDeleteClick(event.getClick())) {
                    if (homeExists(player, homeName)) {
                        runHomeCommand(player, deleteCommand, homeName, homeNumber, true);
                    } else {
                        sendMessage(player, "home-not-set", "&cEse hogar aún no está establecido.",
                                Map.of("home", safe(homeName), "number", String.valueOf(homeNumber)));
                    }
                } else {
                    runHomeCommand(player, setCommand, homeName, homeNumber, true);
                }
            }
            case "DELETE_LOCKED_HOME" -> {
                if (!allowDeleteLocked) {
                    sendLockedMessage(player, homeName, homeNumber);
                } else if (!isDeleteClick(event.getClick())) {
                    sendMessage(player, "locked-delete-hint",
                            "&eUsa click derecho para eliminar el hogar suspendido &6{home}&e.",
                            Map.of("home", safe(homeName), "number", String.valueOf(homeNumber)));
                } else if (homeExists(player, homeName)) {
                    runHomeCommand(player, deleteCommand, homeName, homeNumber, true);
                } else {
                    sendMessage(player, "home-not-set", "&cEse hogar aún no está establecido.",
                            Map.of("home", safe(homeName), "number", String.valueOf(homeNumber)));
                }
            }
            case "LOCKED_HOME" -> sendLockedMessage(player, homeName, homeNumber);
            case "BACK" -> {
                if (closeOnAction)
                    player.closeInventory();
                String backCommand = normalizeCommand(sectionString("items.back.command", "social"));
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(player, backCommand));
            }
            default -> {
            }
        }
    }

    /**
     * Bloquea también /home, /ehome y las variantes con namespace de EssentialsX.
     * Se usa el evento de comandos porque EssentialsX no expone un evento
     * específico
     * cancelable para seleccionar un hogar antes de iniciar todos sus flujos de
     * teleport.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHomeCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String raw = event.getMessage();
        if (raw == null || raw.length() < 2)
            return;
        String withoutSlash = raw.substring(1).trim();
        if (withoutSlash.isEmpty())
            return;
        String[] parts = withoutSlash.split("\\s+", 2);
        String label = normalizeCommandLabel(parts[0]);
        if (!interceptedHomeCommands.contains(label))
            return;

        // Bedrock: /home sin argumentos abre siempre el Form nativo de MDVSocial.
        // Esto no cambia el comportamiento de /home <nombre>, ni el de jugadores Java.
        if ((parts.length == 1 || parts[1].isBlank()) && plugin.isBedrockPlayer(player)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> openBedrockHomesMenu(player));
            return;
        }

        if (!isLockExcessActive())
            return;
        if (hasLockBypass(player))
            return;

        List<HomeData> homes = readHomes(player);
        int maxHomes = getMaxHomes(player);
        Set<String> locked = syncLockedHomes(player, homes, maxHomes);
        if (homes.isEmpty())
            return; // Conserva spawn-if-no-home de EssentialsX para Java.

        if (parts.length == 1 || parts[1].isBlank()) {
            HomeData firstActive = homes.stream()
                    .filter(home -> !locked.contains(normalizeHomeName(home.name)))
                    .findFirst()
                    .orElse(null);
            if (firstActive == null) {
                event.setCancelled(true);
                sendMessage(player, "all-homes-locked",
                        "&cTodos tus hogares están suspendidos. Recupera un límite mayor o elimina uno.",
                        Map.of("max_homes", String.valueOf(maxHomes)));
                return;
            }
            event.setMessage("/" + parts[0] + " " + firstActive.name);
            return;
        }

        String requested = stripQuotes(parts[1].trim());
        int colon = requested.indexOf(':');
        if (colon >= 0) {
            String owner = requested.substring(0, colon);
            if (!owner.equalsIgnoreCase(player.getName()))
                return;
            requested = requested.substring(colon + 1);
        }
        HomeData matched = findHome(homes, requested);
        if (matched == null)
            return;
        if (!locked.contains(normalizeHomeName(matched.name)))
            return;

        event.setCancelled(true);
        sendLockedMessage(player, matched.name, Math.max(1, homes.indexOf(matched) + 1));
    }

    public int restorePersistentLocks(UUID uuid) {
        if (uuid == null || lockData == null)
            return 0;
        List<String> before = lockData.getStringList(lockPath(uuid));
        lockData.set(lockPath(uuid), null);
        saveLockData();
        return before.size();
    }

    public List<String> getLockedHomeNames(Player player) {
        if (player == null)
            return Collections.emptyList();
        List<HomeData> homes = readHomes(player);
        Set<String> locked = syncLockedHomes(player, homes, getMaxHomes(player));
        return homes.stream().filter(h -> locked.contains(normalizeHomeName(h.name))).map(h -> h.name)
                .collect(Collectors.toList());
    }

    public int getCurrentLimit(Player player) {
        return getMaxHomes(player);
    }

    private boolean isDeleteClick(ClickType click) {
        return click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
    }

    private void runHomeCommand(Player player, String commandTemplate, String homeName, int homeNumber,
            boolean requireName) {
        if (requireName && (homeName == null || homeName.isBlank())) {
            sendMessage(player, "home-not-set", "&cEse hogar aún no está establecido.",
                    Map.of("home", "", "number", String.valueOf(homeNumber)));
            return;
        }
        if (commandTemplate.equalsIgnoreCase(teleportCommand) && isHomeLocked(player, homeName)) {
            sendLockedMessage(player, homeName, homeNumber);
            return;
        }
        if (closeOnAction)
            player.closeInventory();
        String command = commandTemplate
                .replace("{player}", player.getName())
                .replace("{home}", safe(homeName))
                .replace("{home_name}", safe(homeName))
                .replace("{home_number}", String.valueOf(homeNumber));
        if (command.startsWith("/"))
            command = command.substring(1);
        final String finalCommand = command;
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(player, finalCommand));
    }

    private boolean homeExists(Player player, String homeName) {
        return findHome(readHomes(player), homeName) != null;
    }

    private HomeData findHome(List<HomeData> homes, String homeName) {
        if (homeName == null || homeName.isBlank())
            return null;
        for (HomeData home : homes) {
            if (home.name.equalsIgnoreCase(homeName))
                return home;
        }
        return null;
    }

    private boolean isHomeLocked(Player player, String homeName) {
        if (!isLockExcessActive() || hasLockBypass(player) || homeName == null || homeName.isBlank())
            return false;
        List<HomeData> homes = readHomes(player);
        Set<String> locked = syncLockedHomes(player, homes, getMaxHomes(player));
        return locked.contains(normalizeHomeName(homeName));
    }

    private void sendLockedMessage(Player player, String homeName, int homeNumber) {
        sendMessage(player, "home-suspended",
                "&cEl hogar &e{home} &cestá suspendido porque supera tu límite actual de &e{max_homes}&c.", Map.of(
                        "home", safe(homeName),
                        "number", String.valueOf(homeNumber),
                        "max_homes", String.valueOf(getMaxHomes(player))));
    }

    private boolean isLockExcessActive() {
        return enabled && lockExcessEnabled && "LOCK_EXCESS".equalsIgnoreCase(lockMode);
    }

    private boolean hasLockBypass(Player player) {
        return player != null && lockBypassPermission != null && !lockBypassPermission.isBlank()
                && player.hasPermission(lockBypassPermission);
    }

    private Set<String> syncLockedHomes(Player player, List<HomeData> homes, int maxHomes) {
        if (!isLockExcessActive() || hasLockBypass(player))
            return Collections.emptySet();

        Set<String> existing = homes.stream().map(h -> normalizeHomeName(h.name))
                .collect(Collectors.toCollection(HashSet::new));
        Set<String> dynamicExcess = new HashSet<>();
        for (int slot = maxHomes + 1; slot <= Math.max(maxVisibleHomes, homes.size()); slot++) {
            HomeData home = homeAtPosition(homes, slot);
            if (home != null) dynamicExcess.add(normalizeHomeName(home.name));
        }

        Set<String> locked = new HashSet<>(dynamicExcess);
        if (!restoreOnUpgrade && lockData != null) {
            locked.addAll(lockData.getStringList(lockPath(player.getUniqueId())).stream().map(this::normalizeHomeName)
                    .collect(Collectors.toSet()));
        }
        locked.retainAll(existing);
        persistLockedHomes(player.getUniqueId(), locked);
        return locked;
    }

    private void persistLockedHomes(UUID uuid, Set<String> locked) {
        if (lockData == null || uuid == null)
            return;
        List<String> sorted = new ArrayList<>(locked);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        List<String> old = lockData.getStringList(lockPath(uuid));
        if (old.equals(sorted))
            return;
        if (sorted.isEmpty())
            lockData.set(lockPath(uuid), null);
        else
            lockData.set(lockPath(uuid), sorted);
        saveLockData();
    }

    private List<HomeData> readHomes(Player player) {
        List<HomeData> apiHomes = readHomesFromEssentialsApi(player);
        List<HomeData> out = apiHomes != null ? apiHomes : readHomesFromYaml(player);
        if (isLockExcessActive()) {
            sortHomesStable(out);
        } else if (sortHomesByName) {
            out.sort(Comparator.comparing(h -> h.name.toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    /** Exact numbered homes never move; legacy/custom names fill only unused slots. */
    private HomeData homeAtPosition(List<HomeData> homes, int position) {
        if (homes == null || position < 1) return null;
        String expected = normalizeHomeName(generateHomeName(position));
        for (HomeData home : homes) {
            if (normalizeHomeName(home.name).equals(expected)) return home;
        }
        // Some pre-fix homes had an arbitrary Essentials name. Keep them accessible
        // without moving casa2 into casa1 if casa1 has not yet been created.
        Set<String> numbered = new HashSet<>();
        for (int slot = 1; slot <= Math.max(maxVisibleHomes, homes.size()); slot++) {
            numbered.add(normalizeHomeName(generateHomeName(slot)));
        }
        int freeSlot = 0;
        for (int slot = 1; slot <= Math.max(maxVisibleHomes, homes.size()); slot++) {
            String candidate = normalizeHomeName(generateHomeName(slot));
            boolean occupied = false;
            for (HomeData home : homes) {
                if (normalizeHomeName(home.name).equals(candidate)) { occupied = true; break; }
            }
            if (occupied) continue;
            freeSlot++;
            if (slot == position) {
                int legacy = 0;
                for (HomeData home : homes) {
                    if (numbered.contains(normalizeHomeName(home.name))) continue;
                    if (++legacy == freeSlot) return home;
                }
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<HomeData> readHomesFromEssentialsApi(Player player) {
        Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
        if (essentials == null || !essentials.isEnabled())
            return null;
        try {
            Method getUser = essentials.getClass().getMethod("getUser", UUID.class);
            Object user = getUser.invoke(essentials, player.getUniqueId());
            if (user == null)
                return null;
            Method getHomes = user.getClass().getMethod("getHomes");
            Object rawHomes = getHomes.invoke(user);
            if (!(rawHomes instanceof Collection<?> names))
                return null;

            Method getHome = user.getClass().getMethod("getHome", String.class);
            List<HomeData> out = new ArrayList<>();
            for (Object rawName : names) {
                String name = String.valueOf(rawName);
                try {
                    Object rawLocation = getHome.invoke(user, name);
                    if (rawLocation instanceof Location loc) {
                        out.add(new HomeData(name, loc.getWorld() == null ? "world" : loc.getWorld().getName(),
                                loc.getX(), loc.getY(), loc.getZ(), true, false));
                    } else {
                        out.add(new HomeData(name, "world", 0, 0, 0, true, false));
                    }
                } catch (Throwable ignored) {
                    out.add(new HomeData(name, "world", 0, 0, 0, true, false));
                }
            }
            if (!essentialsApiLogged) {
                plugin.getLogger().info("Integración de hogares conectada a la API de EssentialsX.");
                essentialsApiLogged = true;
            }
            return out;
        } catch (Throwable ex) {
            if (!essentialsApiFailureLogged) {
                plugin.getLogger()
                        .warning("No se pudo leer la API de hogares de EssentialsX; se usará userdata como respaldo: "
                                + ex.getClass().getSimpleName());
                essentialsApiFailureLogged = true;
            }
            return null;
        }
    }

    private List<HomeData> readHomesFromYaml(Player player) {
        File file = new File(resolveUserdataFolder(), player.getUniqueId().toString() + ".yml");
        if (!file.exists())
            return new ArrayList<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection homesSec = yaml.getConfigurationSection("homes");
        if (homesSec == null)
            return new ArrayList<>();

        List<HomeData> out = new ArrayList<>();
        for (String name : homesSec.getKeys(false)) {
            ConfigurationSection homeSec = homesSec.getConfigurationSection(name);
            if (homeSec != null) {
                out.add(new HomeData(
                        name,
                        homeSec.getString("world", homeSec.getString("world-name", "world")),
                        homeSec.getDouble("x", homeSec.getDouble("loc.x", 0)),
                        homeSec.getDouble("y", homeSec.getDouble("loc.y", 0)),
                        homeSec.getDouble("z", homeSec.getDouble("loc.z", 0)),
                        true,
                        false));
                continue;
            }

            String raw = homesSec.getString(name, "");
            HomeData parsed = parseLegacyHome(name, raw);
            if (parsed != null)
                out.add(parsed);
        }
        return out;
    }

    private void sortHomesStable(List<HomeData> homes) {
        Map<String, Integer> preferredOrder = new HashMap<>();
        for (int i = 0; i < preferredHomeNames.size(); i++) {
            preferredOrder.putIfAbsent(normalizeHomeName(preferredHomeNames.get(i)), i);
        }
        homes.sort(Comparator
                .comparingInt(
                        (HomeData home) -> preferredOrder.getOrDefault(normalizeHomeName(home.name), Integer.MAX_VALUE))
                .thenComparing(home -> home.name.toLowerCase(Locale.ROOT))
                .thenComparing(home -> home.name));
    }

    private HomeData parseLegacyHome(String name, String raw) {
        if (raw == null || raw.isBlank())
            return null;
        String[] parts = raw.split(",");
        if (parts.length < 4)
            return new HomeData(name, "world", 0, 0, 0, true, false);
        try {
            return new HomeData(name, parts[0].trim(), Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim()), true, false);
        } catch (Exception ignored) {
            return new HomeData(name, "world", 0, 0, 0, true, false);
        }
    }

    private File resolveUserdataFolder() {
        if (userdataPath == null || userdataPath.isBlank())
            return new File("plugins/Essentials/userdata");
        File file = new File(userdataPath);
        if (file.isAbsolute())
            return file;
        return new File(Bukkit.getWorldContainer(), userdataPath);
    }

    private int getMaxHomes(Player player) {
        int max = defaultMaxHomes;
        List<Map<?, ?>> list = plugin.getConfig().getMapList(CONFIG_PATH + ".max-homes.permission-limits");
        for (Map<?, ?> entry : list) {
            Object permObj = entry.get("permission");
            Object homesObj = entry.get("homes");
            if (homesObj == null)
                homesObj = entry.get("max");
            String permission = permObj == null ? "" : String.valueOf(permObj);
            int homes = parseInt(homesObj, -1);
            if (!permission.isBlank() && homes > max && player.hasPermission(permission))
                max = homes;
        }

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(CONFIG_PATH + ".max-homes.permissions");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                int homes = sec.getInt(key, -1);
                if (homes > max && player.hasPermission(key))
                    max = homes;
            }
        }
        return Math.max(0, Math.min(maxVisibleHomes, max));
    }

    private int parseInt(Object value, int def) {
        if (value instanceof Number n)
            return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return def;
        }
    }

    private String generateHomeName(int number) {
        return safe(defaultHomeNameFormat).replace("{number}", String.valueOf(number)).replace("{home_number}",
                String.valueOf(number));
    }

    private ItemStack itemFromPath(String relativePath, String action, HomeData home, int number, Player player,
            List<HomeData> homes, int maxHomes) {
        ConfigurationSection sec = section(relativePath);
        String matName = sec == null ? "PAPER" : sec.getString("material", "PAPER");
        Material material = Material.matchMaterial(matName == null ? "PAPER" : matName.toUpperCase(Locale.ROOT));
        if (material == null)
            material = Material.PAPER;

        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, sec == null ? 1 : sec.getInt("amount", 1))));
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        if (sec != null && material == Material.PLAYER_HEAD && meta instanceof SkullMeta skull) {
            String texture = readTexture(sec);
            if (!texture.isBlank()) {
                applySkullTexture(skull, texture);
            } else {
                String owner = sec.getString("head-owner", "");
                if (owner != null && !owner.isBlank()) {
                    owner = applyPlaceholders(owner, player, home, number, homes, maxHomes);
                    skull.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
                }
            }
            meta = skull;
        }

        String name = sec == null ? "" : sec.getString("name", "");
        if (name != null && !name.isBlank())
            meta.setDisplayName(color(applyPlaceholders(name, player, home, number, homes, maxHomes)));
        List<String> lore = sec == null ? Collections.emptyList() : sec.getStringList("lore");
        if (!lore.isEmpty()) {
            meta.setLore(
                    lore.stream().map(line -> color(applyPlaceholders(line, player, home, number, homes, maxHomes)))
                            .collect(Collectors.toList()));
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        if (action != null && !action.isBlank())
            meta.getPersistentDataContainer().set(keyAction, PersistentDataType.STRING, action);
        if (home != null && home.name != null && !home.name.isBlank())
            meta.getPersistentDataContainer().set(keyHomeName, PersistentDataType.STRING, home.name);
        if (number > 0)
            meta.getPersistentDataContainer().set(keyHomeNumber, PersistentDataType.INTEGER, number);
        item.setItemMeta(meta);
        return item;
    }

    private String applyGlobalPlaceholders(String input, Player player, List<HomeData> homes, int maxHomes) {
        long locked = homes.stream().filter(h -> h.exists && h.locked).count();
        long active = homes.stream().filter(h -> h.exists && !h.locked).count();
        return safe(input)
                .replace("{player}", player.getName())
                .replace("{homes}", String.valueOf(homes.size()))
                .replace("{home_count}", String.valueOf(homes.size()))
                .replace("{active_homes}", String.valueOf(active))
                .replace("{locked_homes}", String.valueOf(locked))
                .replace("{max_homes}", String.valueOf(maxHomes))
                .replace("{max_visible_homes}", String.valueOf(maxVisibleHomes));
    }

    private String applyPlaceholders(String input, Player player, HomeData home, int number, List<HomeData> homes,
            int maxHomes) {
        if (home == null)
            home = HomeData.missing(generateHomeName(number));
        String currentWorld = player.getWorld().getName();
        int currentX = player.getLocation().getBlockX();
        int currentY = player.getLocation().getBlockY();
        int currentZ = player.getLocation().getBlockZ();
        String status = home.locked
                ? color(sectionString("status-text.suspended", "&cSuspendida"))
                : (home.exists ? color(sectionString("status-text.established", "&aEstablecida"))
                        : color(sectionString("status-text.missing", "&cNo establecida")));

        return applyGlobalPlaceholders(input, player, homes, maxHomes)
                .replace("{home_number}", String.valueOf(number))
                .replace("{home_name}", home.name)
                .replace("{home_display}", home.name)
                .replace("{home}", home.name)
                .replace("{home_status}", status)
                .replace("{home_locked}", String.valueOf(home.locked))
                .replace("{home_world}", home.world)
                .replace("{home_x}", home.exists ? String.valueOf((int) Math.floor(home.x)) : "-")
                .replace("{home_y}", home.exists ? String.valueOf((int) Math.floor(home.y)) : "-")
                .replace("{home_z}", home.exists ? String.valueOf((int) Math.floor(home.z)) : "-")
                .replace("{current_world}", currentWorld)
                .replace("{current_x}", String.valueOf(currentX))
                .replace("{current_y}", String.valueOf(currentY))
                .replace("{current_z}", String.valueOf(currentZ))
                .replace("{world}", currentWorld)
                .replace("{x}", String.valueOf(currentX))
                .replace("{y}", String.valueOf(currentY))
                .replace("{z}", String.valueOf(currentZ));
    }

    private void fill(Inventory inv, Player player, List<HomeData> homes, int maxHomes) {
        ConfigurationSection sec = section("items.filler");
        if (sec == null || !sec.getBoolean("enabled", true))
            return;
        ItemStack filler = itemFromPath("items.filler", "", null, 0, player, homes, maxHomes);
        for (int i = 0; i < inv.getSize(); i++)
            inv.setItem(i, filler);
    }

    private int slot(String path, int def) {
        return Math.max(0, Math.min(size - 1, plugin.getConfig().getInt(CONFIG_PATH + "." + path, def)));
    }

    private int defaultTeleportSlot(int number) {
        int[] slots = { 10, 19, 28, 37, 46, 11, 20, 29, 38 };
        return slots[Math.max(0, Math.min(slots.length - 1, number - 1))];
    }

    private int defaultSetSlot(int number) {
        int[] slots = { 16, 25, 34, 43, 52, 15, 24, 33, 42 };
        return slots[Math.max(0, Math.min(slots.length - 1, number - 1))];
    }

    private int normalizeMenuSize(int value) {
        int normalized = Math.max(9, Math.min(54, value));
        if (normalized % 9 != 0)
            normalized = ((normalized / 9) + 1) * 9;
        return normalized;
    }

    private ConfigurationSection section(String relativePath) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection(CONFIG_PATH + "." + relativePath);
        if (sec != null)
            return sec;
        if (relativePath != null && relativePath.startsWith("items.")) {
            return plugin.getConfig()
                    .getConfigurationSection(CONFIG_PATH + "." + relativePath.substring("items.".length()));
        }
        return null;
    }

    private String sectionString(String relativePath, String def) {
        String value = plugin.getConfig().getString(CONFIG_PATH + "." + relativePath, null);
        if (value != null)
            return value;
        if (relativePath != null && relativePath.startsWith("items.")) {
            value = plugin.getConfig().getString(CONFIG_PATH + "." + relativePath.substring("items.".length()), null);
            if (value != null)
                return value;
        }
        return def;
    }

    private String normalizeCommand(String raw) {
        if (raw == null || raw.isBlank())
            return "social";
        String out = raw.trim();
        if (out.startsWith("/"))
            out = out.substring(1);
        return out;
    }

    private String normalizeCommandLabel(String raw) {
        String out = normalizeCommand(raw).toLowerCase(Locale.ROOT);
        return out.endsWith(":") ? out.substring(0, out.length() - 1) : out;
    }

    private String normalizeHomeName(String raw) {
        return stripQuotes(safe(raw).trim()).toLowerCase(Locale.ROOT);
    }

    private String stripQuotes(String raw) {
        if (raw == null || raw.length() < 2)
            return raw == null ? "" : raw;
        if ((raw.startsWith("\"") && raw.endsWith("\"")) || (raw.startsWith("'") && raw.endsWith("'"))) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
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
            int firstQuote = decoded.indexOf('"', colon);
            if (firstQuote < 0)
                return "";
            int secondQuote = decoded.indexOf('"', firstQuote + 1);
            if (secondQuote < 0)
                return "";
            return decoded.substring(firstQuote + 1, secondQuote).replace("\\/", "/");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private void applySkullTexture(SkullMeta skull, String textureValue) {
        if (skull == null || textureValue == null || textureValue.isBlank())
            return;
        String textureUrl = extractTextureUrl(textureValue.trim());
        if (textureUrl == null || textureUrl.isBlank())
            return;
        try {
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), "MDVHome");
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(new URL(textureUrl));
            profile.setTextures(textures);
            skull.setOwnerProfile(profile);
        } catch (Throwable ignored) {
        }
    }

    private String getMessage(String key, String def) {
        return plugin.getConfig().getString(CONFIG_PATH + ".messages." + key, def);
    }

    private void sendMessage(Player player, String key, String def, Map<String, String> placeholders) {
        String raw = getMessage(key, def);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        player.sendMessage(color(plugin.getConfig().getString("messages.prefix", "&6[MDVSocial] &r") + raw));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private void loadLockData() {
        if (!plugin.getDataFolder().exists())
            plugin.getDataFolder().mkdirs();
        lockDataFile = new File(plugin.getDataFolder(), "homes-lock-data.yml");
        lockData = YamlConfiguration.loadConfiguration(lockDataFile);
        lockWriter = new CoalescingFileWriter(lockDataFile.toPath(),
                error -> plugin.getLogger().severe("Error guardando homes-lock-data.yml: " + error));
    }

    private void saveLockData() {
        if (lockData == null || lockDataFile == null)
            return;
        try {
            lockWriter.save(lockData.saveToString());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("No se pudo guardar homes-lock-data.yml: " + ex.getMessage());
        }
    }

    private String lockPath(UUID uuid) {
        return "players." + uuid + ".locked";
    }

    private static final class HomeData {
        final String name;
        final String world;
        final double x;
        final double y;
        final double z;
        final boolean exists;
        final boolean locked;

        HomeData(String name, String world, double x, double y, double z, boolean exists, boolean locked) {
            this.name = name == null ? "" : name;
            this.world = world == null || world.isBlank() ? "world" : world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.exists = exists;
            this.locked = locked;
        }

        HomeData withLocked(boolean locked) {
            return new HomeData(name, world, x, y, z, exists, locked);
        }

        static HomeData missing(String name) {
            return new HomeData(name, "-", 0, 0, 0, false, false);
        }
    }

    private static final class HomesHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
