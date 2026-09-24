package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.*;
import java.util.function.Supplier;
import org.bukkit.configuration.file.YamlConfiguration;
import static com.mdvcraft.mdvsocial.ProtectionMenuFiles.*;
import java.util.logging.Level;

import static com.mdvcraft.mdvsocial.ProtectionStonesHook.*;

/** Both interfaces share actions and authorization. All API/inventory mutations run on the server thread. */
public final class PlayerProtectionsMenuManager implements Listener, CommandExecutor, TabCompleter {
    private final MDVSocialPlugin plugin;
    private ProtectionStonesHook api;
    private final ProtectionMenuFiles menuFiles;
    private final Map<UUID, PendingInput> pending = new HashMap<>();
    private final Map<UUID, Long> revisions = new HashMap<>();
    private long sequence;
    private final Map<UUID, Long> loading = new HashMap<>();
    private final java.util.concurrent.ThreadPoolExecutor scanner = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.ArrayBlockingQueue<>(64),
            task -> { Thread t = new Thread(task, "MDVSocial-Protections"); t.setDaemon(true); return t; });
    private record PendingInput(Key key, long expires) {}
    private record Entry(String style, Supplier<ItemStack> icon, Map<String, String> tokens, CheckedAction action) {}
    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }

    public PlayerProtectionsMenuManager(MDVSocialPlugin plugin) { this.plugin = plugin; this.menuFiles = new ProtectionMenuFiles(plugin); }

    public void enable() {
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        PluginCommand command = Objects.requireNonNull(plugin.getCommand("protes"));
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    public void reload() {
        menuFiles.reload();
        loading.clear();
        scanner.getQueue().clear();
        pending.clear();
        revisions.clear();
        api = null;
        Plugin ps = Bukkit.getPluginManager().getPlugin("ProtectionStones");
        if (ps != null && ps.isEnabled() && Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            try {
                api = new ProtectionStonesHook(ps);
            } catch (ReflectiveOperationException | LinkageError ex) {
                plugin.getLogger().log(Level.WARNING, "API de ProtectionStones incompatible; menú desactivado.", ex);
            }
        }
    }

    public void disable() {
        scanner.shutdownNow();
        loading.clear();
        pending.clear();
        revisions.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) p.closeInventory();
        }
        HandlerList.unregisterAll(this);
        api = null;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Solo jugadores."); return true; }
        execute(player, () -> {
            if (args.length == 0) { openMain(player, 0); return; }
            if (args.length == 1 && args[0].equalsIgnoreCase("cancelar")) {
                pending.remove(player.getUniqueId());
                openMain(player, 0);
                return;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("agregar")) {
                PendingInput input = pending.remove(player.getUniqueId());
                if (input == null || System.currentTimeMillis() > input.expires())
                    throw new IllegalArgumentException("Abre una protección y pulsa Agregar miembro primero.");
                String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                addByName(player, input.key(), name);
                return;
            }
            message(player, "&eUsa /protes, /protes agregar <nombre> o /protes cancelar.");
        });
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("agregar", "cancelar").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("agregar")) return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }

    private void checkAccess(Player p) {
        if (!p.isOnline()) throw new IllegalArgumentException("El jugador ya no está conectado.");
        if (!plugin.getConfig().getBoolean("protections-menu.enabled", true))
            throw new IllegalArgumentException("El menú de protecciones está desactivado.");
        permission(p, "mdvsocial.protections.use");
        if (api == null || !Bukkit.getPluginManager().isPluginEnabled("ProtectionStones")
                || !Bukkit.getPluginManager().isPluginEnabled("WorldGuard"))
            throw new IllegalArgumentException("ProtectionStones y WorldGuard deben estar activos para usar este menú.");
    }

    private void permission(Player p, String permission) {
        if (!p.hasPermission(permission)) throw new IllegalArgumentException("No tienes permiso: " + permission);
    }

    private void execute(Player player, CheckedAction action) {
        try {
            checkAccess(player);
            action.run();
        } catch (IllegalArgumentException ex) {
            message(player, "&c" + ex.getMessage());
        } catch (Exception | LinkageError ex) {
            message(player, "&cNo se pudo completar la acción. Revisa la consola del servidor.");
            plugin.getLogger().log(Level.WARNING, "Error en el menú de protecciones de " + player.getName(), ex);
        }
    }

    private void openMain(Player p, int page) throws Exception {
        if (loading.containsKey(p.getUniqueId())) return;
        long revision = nextRevision(p);
        loading.put(p.getUniqueId(), revision);
        ProtectionStonesHook hook = api;
        UUID uuid = p.getUniqueId();
        List<org.bukkit.World> worlds = List.copyOf(Bukkit.getWorlds());
        p.closeInventory();
        Inventory expectedInventory = p.getOpenInventory().getTopInventory();
        long bedrockSession = plugin.isBedrockPlayer(p) ? plugin.beginBedrockUiSession(p) : 0L;
        try {
            scanner.execute(() -> {
                List<Object> regions;
                try { regions = hook.queryOwned(uuid, worlds); }
                catch (Exception ex) {
                    if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                        if (loading.remove(uuid, revision) && current(p, revision))
                            execute(p, () -> { throw ex; });
                    });
                    return;
                }
                if (!plugin.isEnabled()) return;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!loading.remove(uuid, revision) || api != hook || !current(p, revision)
                            || p.getOpenInventory().getTopInventory() != expectedInventory) return;
                    Runnable render = () -> execute(p, () -> {
                        List<Protection> protections = new ArrayList<>();
                        for (Object region : regions) {
                            if ((Boolean) call(region, "isOwner", new Class<?>[]{UUID.class}, uuid))
                                protections.add(hook.snapshot(region));
                        }
                        protections.sort(Comparator.comparing(Protection::worldName).thenComparing(r -> r.key().id()));
                        renderMain(p, page, protections);
                    });
                    if (bedrockSession != 0L) plugin.runBedrockUiAction(p, bedrockSession, render);
                    else render.run();
                });
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            loading.remove(uuid);
            message(p, "&eEl gestor está ocupado. Vuelve a abrir /protes en unos segundos.");
        }
    }

    private YamlConfiguration definition(Player p, String id) {
        return menuFiles.get(plugin.isBedrockPlayer(p), id);
    }

    private Map<String, String> tokens(Protection protection) {
        ItemMeta meta = protection.item().getItemMeta();
        Map<String, String> values = new HashMap<>();
        values.put("protection", protection.name());
        values.put("item_name", meta.hasDisplayName() ? meta.getDisplayName() : protection.item().getType().name());
        values.put("material", protection.item().getType().name());
        values.put("world", protection.worldName());
        values.put("x", String.valueOf(protection.key().x()));
        values.put("y", String.valueOf(protection.key().y()));
        values.put("z", String.valueOf(protection.key().z()));
        values.put("members", String.valueOf(protection.members().size()));
        values.put("group_notice", groupNotice(protection).strip());
        values.put("details", description(protection));
        return values;
    }

    private void renderMain(Player p, int ignoredPage, List<Protection> protections) throws Exception {
        pending.remove(p.getUniqueId());
        int limit = api.limit(p);
        YamlConfiguration config = definition(p, MAIN);
        Map<String, String> values = new HashMap<>();
        values.put("count", String.valueOf(protections.size()));
        values.put("limit", limit < 0 ? config.getString("messages.unlimited", "Sin límite") : String.valueOf(limit));
        values.put("limit_notice", limit >= 0 && protections.size() > limit ? config.getString("messages.over-limit", "") : "");
        values.put("empty_notice", protections.isEmpty() ? config.getString("messages.empty", "") : "");
        List<Entry> entries = new ArrayList<>();
        for (Protection protection : protections) entries.add(new Entry("protection", protection::item,
                tokens(protection), () -> openDetails(p, protection.key())));
        show(p, MAIN, values, entries, List.of(), 0, null, () -> p.performCommand("social"));
    }

    private void openDetails(Player p, Key key) throws Exception {
        pending.remove(p.getUniqueId());
        Protection protection = api.snapshot(api.requireOwned(p, key));
        List<Entry> entries = List.of(
                entry("location", Material.MAP, () -> {
                    api.requireOwned(p, key);
                    message(p, "&b" + protection.worldName() + " &7— &e" + coordinates(key));
                    openDetails(p, key);
                }),
                entry("members", Material.PLAYER_HEAD, () -> openMembers(p, key, 0)),
                entry("remove", Material.BARRIER, () -> confirmDelete(p, key)));
        show(p, OPTIONS, tokens(protection), entries, List.of(), 0, protection.item(), () -> openMain(p, 0));
    }

    private void openMembers(Player p, Key key, int page) throws Exception {
        Protection protection = api.snapshot(api.requireOwned(p, key));
        List<Entry> entries = new ArrayList<>();
        List<UUID> members = new ArrayList<>(protection.members());
        Map<UUID, String> names = new HashMap<>();
        for (UUID uuid : members) names.put(uuid, playerName(uuid));
        members.sort(Comparator.<UUID, String>comparing(names::get, String.CASE_INSENSITIVE_ORDER).thenComparing(UUID::toString));
        for (UUID uuid : members) entries.add(new Entry("member", () -> head(uuid),
                Map.of("member", names.get(uuid), "uuid", uuid.toString()), () -> confirmMemberRemoval(p, key, uuid)));
        show(p, MEMBERS, tokens(protection), entries,
                List.of(entry("add", Material.LIME_DYE, () -> addPrompt(p, key))), page, null, () -> openDetails(p, key));
    }

    private void confirmMemberRemoval(Player p, Key key, UUID member) throws Exception {
        permission(p, "mdvsocial.protections.members");
        permission(p, "protectionstones.members");
        Protection protection = api.snapshot(api.requireOwned(p, key));
        Map<String, String> values = tokens(protection);
        YamlConfiguration config = definition(p, CONFIRM);
        values.put("action", config.getString("labels.member", "Quitar miembro"));
        values.put("target", playerName(member));
        values.put("warning", config.getString("warnings.member", ""));
        show(p, CONFIRM, values, List.of(entry("confirm", Material.RED_CONCRETE, () -> {
            permission(p, "mdvsocial.protections.members");
            permission(p, "protectionstones.members");
            api.removeMember(api.requireOwned(p, key), member);
            message(p, "&aMiembro eliminado.");
            openMembers(p, key, 0);
        })), List.of(), 0, null, () -> openMembers(p, key, 0));
    }

    private void addPrompt(Player p, Key key) throws Exception {
        permission(p, "mdvsocial.protections.members");
        permission(p, "protectionstones.members");
        Protection protection = api.snapshot(api.requireOwned(p, key));
        pending.put(p.getUniqueId(), new PendingInput(key, System.currentTimeMillis() + 120_000));
        List<Entry> entries = new ArrayList<>();
        for (Player target : Bukkit.getOnlinePlayers().stream().sorted(Comparator.comparing(Player::getName)).toList()) {
            if (target.getUniqueId().equals(p.getUniqueId())) continue;
            UUID uuid = target.getUniqueId();
            entries.add(new Entry("player", () -> head(uuid), Map.of("member", target.getName(), "uuid", uuid.toString()),
                    () -> addMember(p, key, uuid)));
        }
        Map<String, String> values = tokens(protection);
        values.put("count", String.valueOf(entries.size()));
        show(p, SEARCH, values, entries, List.of(entry("name", Material.NAME_TAG, () -> namePrompt(p, key))),
                0, null, () -> { pending.remove(p.getUniqueId()); openMembers(p, key, 0); });
    }

    private void namePrompt(Player p, Key key) throws Exception {
        api.requireOwned(p, key);
        if (!plugin.isBedrockPlayer(p)) {
            pending.put(p.getUniqueId(), new PendingInput(key, System.currentTimeMillis() + 120_000));
            p.closeInventory();
            message(p, definition(p, SEARCH).getString("name-prompt", ""));
            return;
        }
        long revision = nextRevision(p);
        long session = plugin.beginBedrockUiSession(p);
        YamlConfiguration config = definition(p, INPUT);
        CustomForm.Builder form = CustomForm.builder().title(color(config.getString("title", "Agregar miembro")))
                .input(color(config.getString("label", "Nombre exacto")), config.getString("placeholder", "Nombre"), config.getString("default", ""));
        form.validResultHandler(response -> {
            String name = response.asInput(0);
            plugin.runBedrockUiAction(p, session, () -> {
                if (!current(p, revision)) return;
                nextRevision(p);
                execute(p, () -> addByName(p, key, name));
            });
        });
        if (!FloodgateApi.getInstance().sendForm(p.getUniqueId(), form))
            message(p, "&cNo se pudo abrir el formulario. Vuelve a usar /protes.");
    }

    private void addByName(Player p, Key key, String input) throws Exception {
        String name = input == null ? "" : input.trim();
        if (name.isEmpty() || name.length() > 64 || name.indexOf('\n') >= 0)
            throw new IllegalArgumentException("Introduce un nombre de jugador válido.");
        // Never invent offline UUIDs or perform a blocking Mojang lookup. This preserves Floodgate identities.
        OfflinePlayer target = Bukkit.getPlayerExact(name);
        if (target == null) target = Bukkit.getOfflinePlayerIfCached(name);
        if (target == null || (!target.isOnline() && !target.hasPlayedBefore()))
            throw new IllegalArgumentException("Ese jugador no es conocido. Debe entrar al servidor primero; incluye su prefijo Bedrock.");
        addMember(p, key, target.getUniqueId());
    }

    private void addMember(Player p, Key key, UUID member) throws Exception {
        permission(p, "mdvsocial.protections.members");
        permission(p, "protectionstones.members");
        api.addMember(api.requireOwned(p, key), member);
        pending.remove(p.getUniqueId());
        message(p, "&aMiembro agregado: " + playerName(member));
        openMembers(p, key, 0);
    }

    private void deletePermission(Player p) {
        permission(p, "mdvsocial.protections.remove");
        permission(p, "protectionstones.unclaim");
        permission(p, "protectionstones.unclaim.remote");
    }

    private void confirmDelete(Player p, Key key) throws Exception {
        deletePermission(p);
        Object region = api.requireOwned(p, key);
        Protection protection = api.snapshot(region);
        ItemStack refund = api.refund(region, returnBlock());
        Map<String, String> values = tokens(protection);
        YamlConfiguration config = definition(p, CONFIRM);
        values.put("action", config.getString("labels.protection", "Borrar protección"));
        values.put("target", protection.name());
        values.put("refund_notice", config.getString(refund == null ? "warnings.no-refund" : "warnings.refund", ""));
        values.put("warning", replace(config.getString("warnings.protection", ""), values));
        show(p, CONFIRM, values, List.of(entry("confirm", Material.RED_CONCRETE, () -> removeProtection(p, key))),
                List.of(), 0, null, () -> openDetails(p, key));
    }

    private boolean returnBlock() { return plugin.getConfig().getBoolean("protections-menu.return-block", true); }

    private void removeProtection(Player p, Key key) throws Exception {
        if (deleteOwnedProtection(p, key)) openMain(p, 0);
    }

    boolean deleteOwnedProtection(Player p, Key key) throws Exception {
        deletePermission(p);
        Object region = api.requireOwned(p, key);
        ItemStack refund = api.refund(region, returnBlock());
        // A whole empty storage slot guarantees room for the one returned stone.
        if (refund != null && p.getInventory().firstEmpty() < 0)
            throw new IllegalArgumentException("Deja un espacio vacío en el inventario. No se ha eliminado la protección.");
        // PSRemoveEvent may be cancelled: never hand out the stone before a successful deletion.
        if (!api.delete(region, p)) {
            message(p, "&cProtectionStones canceló la eliminación. No se devolvió ningún bloque.");
            return false;
        }
        if (refund != null) {
            // A listener may fill the inventory during PSRemoveEvent; retain the successfully reclaimed item.
            p.getInventory().addItem(refund).values().forEach(item -> p.getWorld().dropItemNaturally(p.getLocation(), item));
        }
        message(p, "&aProtección eliminada.");
        return true;
    }

    private String description(Protection protection) {
        ItemMeta meta = protection.item().getItemMeta();
        String name = meta.hasDisplayName() ? meta.getDisplayName() : protection.item().getType().name();
        String lore = meta.hasLore() ? "\n" + String.join("\n", Objects.requireNonNull(meta.getLore())) : "";
        return name + lore + "\n\n&7Protección: &e" + protection.name() + "\n&7Material: &f" + protection.item().getType()
                + "\n&7Mundo: &f" + protection.worldName() + "\n&7Coordenadas: &f" + coordinates(protection.key())
                + "\n&7Miembros: &f" + protection.members().size() + groupNotice(protection);
    }

    private String groupNotice(Protection protection) {
        return protection.merged() ? "\n&eFusionada: los cambios de miembros afectan al grupo completo." : "";
    }
    private String coordinates(Key key) { return "X: " + key.x() + " Y: " + key.y() + " Z: " + key.z(); }
    private String playerName(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString() : name;
    }

    private Entry entry(String style, Material material, CheckedAction action) {
        return new Entry(style, () -> new ItemStack(material), Map.of(), action);
    }

    static ItemStack head(UUID uuid) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        applyHeadProfile(meta, uuid);
        item.setItemMeta(meta);
        return item;
    }

    static void applyHeadProfile(SkullMeta meta, UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) meta.setPlayerProfile(online.getPlayerProfile());
        else meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
    }

    private List<String> lines(YamlConfiguration config, String path, Map<String, String> values) {
        List<String> result = new ArrayList<>();
        for (String line : config.getStringList(path)) {
            String expanded = replace(line, values);
            if (!line.isEmpty() && expanded.isEmpty()) continue;
            result.addAll(Arrays.asList(expanded.split("\n", -1)));
        }
        return result;
    }

    private ItemStack styled(YamlConfiguration config, String path, ItemStack base, Map<String, String> values) {
        ItemStack item = base.clone();
        String materialName = config.getString(path + ".material");
        Material material = materialName == null ? null : Material.matchMaterial(materialName);
        if (material != null && material != item.getType() && material.isItem() && !material.isAir()) item.setType(material);
        ItemMeta meta = item.getItemMeta();
        if (config.contains(path + ".name")) meta.setDisplayName(color(replace(config.getString(path + ".name", ""), values)));
        List<String> lore = config.getBoolean(path + ".preserve-lore", false) && meta.hasLore()
                ? new ArrayList<>(Objects.requireNonNull(meta.getLore())) : new ArrayList<>();
        lore.addAll(lines(config, path + ".lore", values).stream().map(PlayerProtectionsMenuManager::color).toList());
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private long nextRevision(Player p) {
        long revision = ++sequence;
        revisions.put(p.getUniqueId(), revision);
        return revision;
    }
    private boolean current(Player p, long revision) {
        return p.isOnline() && Objects.equals(revisions.get(p.getUniqueId()), revision);
    }

    private void show(Player p, String id, Map<String, String> originalValues, List<Entry> entries,
                      List<Entry> fixed, int requestedPage, ItemStack headerBase, CheckedAction back) {
        boolean bedrock = plugin.isBedrockPlayer(p);
        YamlConfiguration config = definition(p, id);
        Map<String, String> values = new HashMap<>(originalValues);
        values.put("player", p.getName());
        List<Integer> slots = bedrock ? List.of() : slots(id, config, entries.size());
        int capacity = bedrock ? (paginated(id) ? Math.max(1, Math.min(100, config.getInt("page-size", 6)))
                : Math.max(1, entries.size())) : slots.size();
        Page page = page(entries.size(), capacity, requestedPage, paginated(id));
        List<Entry> visible = entries.subList(page.from(), page.to());
        String title = color(replace(config.getString("title", "Protecciones"), values));
        String content = String.join("\n", lines(config, "content", values));
        long revision = nextRevision(p);
        CheckedAction previous = () -> show(p, id, originalValues, entries, fixed, page.index() - 1, headerBase, back);
        CheckedAction next = () -> show(p, id, originalValues, entries, fixed, page.index() + 1, headerBase, back);
        if (bedrock) {
            long session = plugin.beginBedrockUiSession(p);
            SimpleForm.Builder builder = SimpleForm.builder().title(title).content(color(content));
            List<CheckedAction> actions = new ArrayList<>();
            List<Entry> buttons = new ArrayList<>(fixed);
            buttons.addAll(visible);
            for (Entry entry : buttons) {
                Map<String, String> buttonValues = new HashMap<>(values);
                buttonValues.putAll(entry.tokens());
                builder.button(color(replace(config.getString("items." + entry.style() + ".text", entry.style()), buttonValues)));
                actions.add(entry.action());
            }
            if (page.previous()) { builder.button(color(config.getString("navigation.previous", "Anterior"))); actions.add(previous); }
            if (page.next()) { builder.button(color(config.getString("navigation.next", "Siguiente"))); actions.add(next); }
            builder.button(color(config.getString("navigation.back", "Volver"))); actions.add(back);
            if (config.getBoolean("show-close", true)) {
                builder.button(color(config.getString("navigation.close", "Cerrar")));
                actions.add(() -> pending.remove(p.getUniqueId()));
            }
            builder.validResultHandler(response -> {
                int index = response.clickedButtonId();
                plugin.runBedrockUiAction(p, session, () -> {
                    if (!current(p, revision) || index < 0 || index >= actions.size()) return;
                    nextRevision(p);
                    execute(p, actions.get(index));
                });
            });
            if (!FloodgateApi.getInstance().sendForm(p.getUniqueId(), builder))
                message(p, "&cNo se pudo abrir el formulario. Vuelve a usar /protes.");
            return;
        }
        MenuHolder holder = new MenuHolder(p.getUniqueId(), revision);
        Inventory inventory = Bukkit.createInventory(holder, config.getInt("size"), title);
        holder.inventory = inventory;
        ItemStack glass = styled(config, "filler", new ItemStack(Material.BLACK_STAINED_GLASS_PANE), values);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, glass);
        if (config.getBoolean("header.enabled", true)) {
            ItemStack header = styled(config, "header", headerBase == null ? new ItemStack(Material.BOOK) : headerBase, values);
            if (!config.contains("header.lore")) {
                ItemMeta meta = header.getItemMeta();
                List<String> lore = meta.hasLore() ? new ArrayList<>(Objects.requireNonNull(meta.getLore())) : new ArrayList<>();
                lore.addAll(Arrays.stream(content.split("\n")).map(PlayerProtectionsMenuManager::color).toList());
                meta.setLore(lore);
                header.setItemMeta(meta);
            }
            inventory.setItem(config.getInt("header.slot"), header);
        }
        for (int i = 0; i < visible.size(); i++) place(holder, slots.get(i), config, visible.get(i), values);
        for (Entry entry : fixed) place(holder, config.getInt("items." + entry.style() + ".slot"), config, entry, values);
        navigation(holder, config, "back", values, back);
        if (page.previous()) navigation(holder, config, "previous", values, previous);
        if (page.next()) navigation(holder, config, "next", values, next);
        p.openInventory(inventory);
    }

    private void place(MenuHolder holder, int slot, YamlConfiguration config, Entry entry, Map<String, String> values) {
        Map<String, String> merged = new HashMap<>(values);
        merged.putAll(entry.tokens());
        holder.inventory.setItem(slot, styled(config, "items." + entry.style(), entry.icon().get(), merged));
        holder.actions.put(slot, entry.action());
    }

    private void navigation(MenuHolder holder, YamlConfiguration config, String name, Map<String, String> values, CheckedAction action) {
        String path = "navigation." + name;
        int slot = config.getInt(path + ".slot");
        holder.inventory.setItem(slot, styled(config, path, new ItemStack(Material.ARROW), values));
        holder.actions.put(slot, action);
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true); // Covers shift, number keys, offhand swap, double click and bottom inventory.
        if (!(event.getWhoClicked() instanceof Player p) || !holder.owner.equals(p.getUniqueId())
                || !current(p, holder.revision) || holder.consumed || !event.isLeftClick() || event.isShiftClick()) return;
        CheckedAction action = holder.actions.get(event.getRawSlot());
        if (action == null) return;
        holder.consumed = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!current(p, holder.revision) || p.getOpenInventory().getTopInventory() != holder.inventory) return;
            nextRevision(p);
            p.closeInventory();
            execute(p, action);
        });
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        loading.remove(event.getPlayer().getUniqueId());
        pending.remove(event.getPlayer().getUniqueId());
        revisions.remove(event.getPlayer().getUniqueId());
    }

    private static class MenuHolder implements InventoryHolder {
        final UUID owner;
        final long revision;
        final Map<Integer, CheckedAction> actions = new HashMap<>();
        Inventory inventory;
        boolean consumed;
        MenuHolder(UUID owner, long revision) { this.owner = owner; this.revision = revision; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private static String color(String text) { return ChatColor.translateAlternateColorCodes('&', text); }
    private void message(Player player, String text) { player.sendMessage(color("&6Protes &8» " + text)); }
}
