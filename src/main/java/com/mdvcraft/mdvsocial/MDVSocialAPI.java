package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * API simple de MDVSocial para consultar titulos, cartas de clan y reutilizar utilidades de UI.
 *
 * Uso pensado para plugins propios:
 * - MDVSocialAPI.openMenu(player, "main");
 * - MDVSocialAPI.playUISound(player, "open");
 * - MDVSocialAPI.createInventory("&8Mi Menu", 54, true);
 * - MDVSocialAPI.createButton(...);
 *
 * Importante: las UIs especificas de cada plugin siguen manejando su propia logica.
 * MDVSocial solo aporta base visual, botones, sonidos y utilidades comunes.
 */
public final class MDVSocialAPI {
    private MDVSocialAPI() { }

    private static MDVSocialPlugin plugin() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("MDVSocial");
        if (!(plugin instanceof MDVSocialPlugin social) || !plugin.isEnabled()) return null;
        return social;
    }

    public static MDVSocialPlugin.TitleDef getEquippedTitle(UUID uuid) {
        MDVSocialPlugin plugin = plugin();
        return plugin == null ? null : plugin.getEquippedTitle(uuid);
    }

    public static String getEquippedTitleId(UUID uuid) {
        MDVSocialPlugin plugin = plugin();
        return plugin == null ? "" : plugin.getEquippedTitleId(uuid);
    }

    public static String getEquippedTitle(UUID uuid, boolean colored) {
        MDVSocialPlugin plugin = plugin();
        return plugin == null ? "" : plugin.getEquippedTitleDisplay(uuid, colored);
    }

    public static String getEquippedTitleColored(UUID uuid) {
        return getEquippedTitle(uuid, true);
    }

    public static String getEquippedTitlePlain(UUID uuid) {
        return getEquippedTitle(uuid, false);
    }

    public static String getEquippedTitlePrefix(UUID uuid, boolean colored) {
        MDVSocialPlugin plugin = plugin();
        return plugin == null ? "" : plugin.getEquippedTitlePrefix(uuid, colored);
    }

    public static boolean sendClanInviteMail(UUID targetUuid, String targetName, UUID inviterUuid, String fromName, String clanTag, String clanName, String message, long expiresAt) {
        MDVSocialPlugin plugin = plugin();
        return plugin != null && plugin.sendClanInviteMail(targetUuid, targetName, inviterUuid, fromName, clanTag, clanName, message, "", expiresAt);
    }

    public static boolean sendClanInviteMail(UUID targetUuid, String targetName, UUID inviterUuid, String fromName, String clanTag, String clanName, String message, String clanBannerData, long expiresAt) {
        MDVSocialPlugin plugin = plugin();
        return plugin != null && plugin.sendClanInviteMail(targetUuid, targetName, inviterUuid, fromName, clanTag, clanName, message, clanBannerData, expiresAt);
    }

    /** Abre un menu modular de plugins/MDVSocial/Menus/<menuId>.yml. */
    public static boolean openMenu(Player player, String menuId) {
        return openMenu(player, menuId, 1);
    }

    /** Abre un menu modular de MDVSocial en una pagina concreta. */
    public static boolean openMenu(Player player, String menuId, int page) {
        MDVSocialPlugin plugin = plugin();
        if (plugin == null || player == null) return false;
        plugin.openSocialMenu(player, menuId, page);
        return true;
    }

    /**
     * Abre directamente el selector nativo de clases/razas de MMOCore para
     * jugadores Bedrock. No abre el menú principal de MDVSocial.
     *
     * Pensado para gates externos como MDVAspectos. Java devuelve false.
     */
    public static boolean openBedrockRaceSelection(Player player) {
        MDVSocialPlugin plugin = plugin();
        return plugin != null && player != null && plugin.openBedrockRaceSelection(player);
    }

    /** Reproduce un sonido comun del core UI. Ejemplos: default, open, back, close, page, confirm, danger, invalid. */
    public static boolean playUISound(Player player, String soundKey) {
        MDVSocialPlugin plugin = plugin();
        if (plugin == null || player == null) return false;
        plugin.playUiSound(player, soundKey);
        return true;
    }

    /** Crea un inventario visual con colores de MDVSocial. La logica del click la maneja el plugin que lo abre. */
    public static Inventory createInventory(String title, int size, boolean fillWithDefaultFiller) {
        MDVSocialPlugin plugin = plugin();
        return plugin == null ? null : plugin.createCoreInventory(title, size, fillWithDefaultFiller);
    }

    /** Crea un boton con metadatos comunes de MDVSocial. */
    public static ItemStack createButton(Material material, int amount, String name, List<String> lore, String action, String targetMenu, List<String> commands, boolean closeOnClick, String sound) {
        MDVSocialPlugin plugin = plugin();
        if (plugin == null) return new ItemStack(material == null ? Material.PAPER : material, Math.max(1, Math.min(64, amount)));
        return plugin.createCoreButton(material, amount, name, lore, action, targetMenu, commands, closeOnClick, sound);
    }

    public static ItemStack createBackButton() {
        return createButton(Material.ARROW, 1, "&eVolver", Collections.emptyList(), "BACK", "", Collections.emptyList(), false, "back");
    }

    public static ItemStack createCloseButton() {
        return createButton(Material.BARRIER, 1, "&cCerrar", Collections.emptyList(), "CLOSE", "", Collections.emptyList(), true, "close");
    }

    public static ItemStack createNextPageButton() {
        return createButton(Material.ARROW, 1, "&aPagina siguiente", Collections.emptyList(), "NEXT_PAGE", "", Collections.emptyList(), false, "page");
    }

    public static ItemStack createPreviousPageButton() {
        return createButton(Material.ARROW, 1, "&ePagina anterior", Collections.emptyList(), "PREVIOUS_PAGE", "", Collections.emptyList(), false, "page");
    }

    public static String getButtonAction(ItemStack item) {
        MDVSocialPlugin plugin = plugin();
        return plugin == null ? "" : plugin.getCoreButtonAction(item);
    }

    public static String getButtonTargetMenu(ItemStack item) {
        MDVSocialPlugin plugin = plugin();
        return plugin == null ? "" : plugin.getCoreButtonTargetMenu(item);
    }

    public static List<String> getButtonCommands(ItemStack item) {
        MDVSocialPlugin plugin = plugin();
        return plugin == null ? Collections.emptyList() : plugin.getCoreButtonCommands(item);
    }

    public static boolean shouldCloseOnClick(ItemStack item) {
        MDVSocialPlugin plugin = plugin();
        return plugin != null && plugin.coreButtonShouldCloseOnClick(item);
    }

    public static String colorize(String text) {
        MDVSocialPlugin plugin = plugin();
        return plugin == null ? (text == null ? "" : text) : plugin.colorize(text);
    }
}
