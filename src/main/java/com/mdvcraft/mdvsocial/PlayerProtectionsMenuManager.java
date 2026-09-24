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
import java.util.logging.Level;

import static com.mdvcraft.mdvsocial.ProtectionStonesHook.*;

/** Both interfaces share actions and authorization. All API/inventory mutations run on the server thread. */
public final class PlayerProtectionsMenuManager implements Listener, CommandExecutor, TabCompleter {
    private final MDVSocialPlugin plugin;
    private ProtectionStonesHook api;
    private final Map<UUID, PendingInput> pending = new HashMap<>();
    private final Map<UUID, Long> revisions = new HashMap<>();
    private long sequence;
    private record PendingInput(Key key, long expires) {}
    private record Entry(ItemStack icon, String label, CheckedAction action) {}
    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }

    public PlayerProtectionsMenuManager(MDVSocialPlugin plugin) { this.plugin = plugin; }

    public void enable() {
        reload();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        PluginCommand command = Objects.requireNonNull(plugin.getCommand("protes"));
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    public void reload() {
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
        pending.remove(p.getUniqueId());
        List<Protection> protections = api.owned(p);
        int limit = api.limit(p);
        String limitText = limit < 0 ? "Sin límite" : String.valueOf(limit);
        String content = "&7Protecciones colocadas: &e" + protections.size() + " &7/ &e" + limitText
                + "\n&7Límite obtenido de tus permisos de ProtectionStones."
                + (limit >= 0 && protections.size() > limit ? "\n&cSuperas tu límite actual. Puedes gestionar todas tus protecciones." : "")
                + (protections.isEmpty() ? "\n&7Todavía no tienes protecciones." : "");
        List<Entry> entries = new ArrayList<>();
        for (Protection protection : protections) {
            ItemMeta meta = protection.item().getItemMeta();
            String itemName = meta.hasDisplayName() ? meta.getDisplayName() : protection.item().getType().name();
            entries.add(new Entry(protectionIcon(protection), itemName + " &7[" + protection.item().getType() + "]\n&7" + protection.worldName()
                    + " · " + coordinates(protection.key()), () -> openDetails(p, protection.key())));
        }
        show(p, "&8Tus protecciones", content, entries, page, () -> p.performCommand("social"));
    }

    private void openDetails(Player p, Key key) throws Exception {
        pending.remove(p.getUniqueId());
        Protection protection = api.snapshot(api.requireOwned(p, key));
        String content = description(protection);
        List<Entry> entries = List.of(
                new Entry(protectionIcon(protection), "&bVer coordenadas", () -> {
                    api.requireOwned(p, key);
                    message(p, "&b" + protection.worldName() + " &7— &e" + coordinates(key));
                    openDetails(p, key);
                }),
                entry(Material.PLAYER_HEAD, "&aMiembros (" + protection.members().size() + ")",
                        "&7Ver, agregar o quitar miembros.", () -> openMembers(p, key, 0)),
                entry(Material.TNT, "&cRemover protección", "&7Eliminar a distancia con confirmación.",
                        () -> confirmDelete(p, key)));
        show(p, "&8Gestionar protección", content, entries, 0, () -> openMain(p, 0));
    }

    private void openMembers(Player p, Key key, int page) throws Exception {
        Protection protection = api.snapshot(api.requireOwned(p, key));
        List<Entry> entries = new ArrayList<>();
        entries.add(entry(Material.LIME_DYE, "&aAgregar miembro", "&7Elige un jugador o introduce su nombre.",
                () -> addPrompt(p, key)));
        List<UUID> members = new ArrayList<>(protection.members());
        members.sort(Comparator.comparing(this::playerName, String.CASE_INSENSITIVE_ORDER).thenComparing(UUID::toString));
        for (UUID uuid : members) {
            String name = playerName(uuid);
            ItemStack skull = icon(Material.PLAYER_HEAD, "&e" + name, "&7Pulsa para quitarlo de miembros.", "&8" + uuid);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            skull.setItemMeta(meta);
            entries.add(new Entry(skull, "&e" + name + "\n&7Quitar miembro", () -> confirmMemberRemoval(p, key, uuid)));
        }
        show(p, "&8Miembros", "&7Protección: &e" + protection.name() + "\n&7Miembros: &e" + members.size()
                + groupNotice(protection), entries, page, () -> openDetails(p, key));
    }

    private void confirmMemberRemoval(Player p, Key key, UUID member) throws Exception {
        permission(p, "mdvsocial.protections.members");
        permission(p, "protectionstones.members");
        Protection protection = api.snapshot(api.requireOwned(p, key));
        show(p, "&8Quitar miembro", "&7¿Quitar a &e" + playerName(member) + "&7?" + groupNotice(protection),
                List.of(entry(Material.RED_DYE, "&cConfirmar", "&7Perderá su acceso como miembro.", () -> {
                    permission(p, "mdvsocial.protections.members");
                    permission(p, "protectionstones.members");
                    api.removeMember(api.requireOwned(p, key), member);
                    message(p, "&aMiembro eliminado.");
                    openMembers(p, key, 0);
                })), 0, () -> openMembers(p, key, 0));
    }

    private void addPrompt(Player p, Key key) throws Exception {
        permission(p, "mdvsocial.protections.members");
        permission(p, "protectionstones.members");
        api.requireOwned(p, key);
        if (plugin.isBedrockPlayer(p)) {
            long revision = nextRevision(p);
            long session = plugin.beginBedrockUiSession(p);
            CustomForm.Builder form = CustomForm.builder().title(color("&8Agregar miembro"))
                    .input("Nombre exacto del jugador (incluye el prefijo Bedrock)", "Nombre", "");
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
            return;
        }
        pending.put(p.getUniqueId(), new PendingInput(key, System.currentTimeMillis() + 120_000));
        List<Entry> entries = new ArrayList<>();
        entries.add(entry(Material.NAME_TAG, "&eEscribir un nombre", "&7También permite jugadores desconectados conocidos.", () -> {
            api.requireOwned(p, key);
            pending.put(p.getUniqueId(), new PendingInput(key, System.currentTimeMillis() + 120_000));
            p.closeInventory();
            message(p, "&eEscribe /protes agregar <nombre exacto> &7(2 minutos). Usa /protes cancelar para volver.");
        }));
        for (Player target : Bukkit.getOnlinePlayers().stream().sorted(Comparator.comparing(Player::getName)).toList()) {
            if (target.getUniqueId().equals(p.getUniqueId())) continue;
            UUID uuid = target.getUniqueId();
            entries.add(entry(Material.PLAYER_HEAD, "&a" + target.getName(), "&7Agregar como miembro.",
                    () -> addMember(p, key, uuid)));
        }
        show(p, "&8Agregar miembro", "&7Selecciona un jugador o escribe un nombre.", entries, 0,
                () -> { pending.remove(p.getUniqueId()); openMembers(p, key, 0); });
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
        String content = description(protection) + "\n\n&cSe quitará esta protección y su bloque."
                + (refund == null ? "\n&cNo se devolverá el bloque." : "\n&aEl bloque volverá a tu inventario si hay espacio.")
                + "\n&cLa zona podría quedar expuesta.";
        show(p, "&8Confirmar eliminación", content,
                List.of(entry(Material.RED_CONCRETE, "&cSí, remover esta protección", "&7Esta acción no se puede deshacer.",
                        () -> removeProtection(p, key))), 0, () -> openDetails(p, key));
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

    private ItemStack protectionIcon(Protection protection) {
        ItemStack item = protection.item().clone();
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.hasLore() ? new ArrayList<>(Objects.requireNonNull(meta.getLore())) : new ArrayList<>();
        lore.add("");
        lore.add(color("&7Protección: &e" + protection.name()));
        lore.add(color("&7Mundo: &f" + protection.worldName()));
        lore.add(color("&7Coordenadas: &f" + coordinates(protection.key())));
        lore.add(color("&7Miembros: &f" + protection.members().size()));
        if (protection.merged()) lore.add(color("&eFusionada: miembros compartidos."));
        if (!protection.configured()) lore.add(color("&cTipo no configurado en ProtectionStones."));
        lore.add(color("&aPulsa para gestionar."));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
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

    private Entry entry(Material material, String label, String lore, CheckedAction action) {
        return new Entry(icon(material, label, lore), label, action);
    }

    private static ItemStack icon(Material material, String label, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(color(label));
        meta.setLore(Arrays.stream(lore).map(PlayerProtectionsMenuManager::color).toList());
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

    private void show(Player p, String title, String content, List<Entry> entries, int requestedPage,
                      CheckedAction back) {
        int pageSize = plugin.isBedrockPlayer(p) ? 6 : 36;
        int pages = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        List<Entry> visible = entries.subList(page * pageSize, Math.min(entries.size(), (page + 1) * pageSize));
        long revision = nextRevision(p);
        if (plugin.isBedrockPlayer(p)) {
            long session = plugin.beginBedrockUiSession(p);
            SimpleForm.Builder builder = SimpleForm.builder().title(color(title))
                    .content(color(content + "\n\n&7Página " + (page + 1) + "/" + pages));
            List<CheckedAction> actions = new ArrayList<>();
            for (Entry entry : visible) { builder.button(color(entry.label())); actions.add(entry.action()); }
            if (page > 0) {
                builder.button("Anterior"); actions.add(() -> show(p, title, content, entries, page - 1, back));
            }
            if (page + 1 < pages) {
                builder.button("Siguiente"); actions.add(() -> show(p, title, content, entries, page + 1, back));
            }
            builder.button("Volver / Cancelar"); actions.add(back);
            builder.button("Cerrar"); actions.add(() -> pending.remove(p.getUniqueId()));
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
        Inventory inventory = Bukkit.createInventory(holder, 54, color(title));
        holder.inventory = inventory;
        ItemStack glass = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < 54; slot++) inventory.setItem(slot, glass);
        inventory.setItem(4, icon(Material.BOOK, title, content.split("\n")));
        int[] centered = {19, 21, 23, 25};
        for (int i = 0; i < visible.size(); i++) {
            int slot = visible.size() <= 4 ? centered[i] : 9 + i;
            inventory.setItem(slot, visible.get(i).icon());
            holder.actions.put(slot, visible.get(i).action());
        }
        if (page > 0) button(holder, 45, Material.ARROW, "&eAnterior", () -> show(p, title, content, entries, page - 1, back));
        button(holder, 48, Material.ARROW, "&eVolver / Cancelar", back);
        inventory.setItem(49, icon(Material.PAPER, "&fPágina " + (page + 1) + "/" + pages));
        button(holder, 50, Material.BARRIER, "&cCerrar", () -> { pending.remove(p.getUniqueId()); p.closeInventory(); });
        if (page + 1 < pages) button(holder, 53, Material.ARROW, "&eSiguiente", () -> show(p, title, content, entries, page + 1, back));
        p.openInventory(inventory);
    }

    private void button(MenuHolder holder, int slot, Material material, String label, CheckedAction action) {
        holder.inventory.setItem(slot, icon(material, label));
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
