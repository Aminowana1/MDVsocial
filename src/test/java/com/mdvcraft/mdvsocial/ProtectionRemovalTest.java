package com.mdvcraft.mdvsocial;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProtectionRemovalTest {
    MDVSocialPlugin plugin;
    ProtectionStonesHook api;
    PlayerProtectionsMenuManager menu;
    Player player;
    PlayerInventory inventory;
    ItemStack refund;
    Object region;
    ProtectionStonesHook.Key key;

    @BeforeEach void setup() throws Exception {
        plugin = mock(MDVSocialPlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        api = mock(ProtectionStonesHook.class);
        menu = new PlayerProtectionsMenuManager(plugin);
        Field field = PlayerProtectionsMenuManager.class.getDeclaredField("api");
        field.setAccessible(true);
        field.set(menu, api);
        player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);
        inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.firstEmpty()).thenReturn(0);
        refund = mock(ItemStack.class);
        when(inventory.addItem(refund)).thenReturn(new HashMap<>());
        region = new Object();
        key = new ProtectionStonesHook.Key(UUID.randomUUID(), "ps1x64y2z", 1, 64, 2);
        when(api.requireOwned(player, key)).thenReturn(region);
        when(api.refund(region, true)).thenReturn(refund);
        when(api.delete(region, player)).thenReturn(true);
    }

    @Test void fullInventoryPreservesRegion() throws Exception {
        when(inventory.firstEmpty()).thenReturn(-1);
        assertThrows(IllegalArgumentException.class, () -> menu.deleteOwnedProtection(player, key));
        verify(api, never()).delete(any(), any());
        verify(inventory, never()).addItem(any(ItemStack.class));
    }

    @Test void cancelledEventNeverGivesItem() throws Exception {
        when(api.delete(region, player)).thenReturn(false);
        assertFalse(menu.deleteOwnedProtection(player, key));
        verify(inventory, never()).addItem(any(ItemStack.class));
    }

    @Test void successGivesOriginalItemOnlyAfterDeletion() throws Exception {
        assertTrue(menu.deleteOwnedProtection(player, key));
        var order = inOrder(api, inventory);
        order.verify(api).requireOwned(player, key);
        order.verify(api).refund(region, true);
        order.verify(inventory).firstEmpty();
        order.verify(api).delete(region, player);
        order.verify(inventory).addItem(refund);
    }

    @Test void lostOwnershipStopsDeletionAndRefund() throws Exception {
        when(api.requireOwned(player, key)).thenThrow(new IllegalArgumentException("Ya no eres dueño"));
        assertThrows(IllegalArgumentException.class, () -> menu.deleteOwnedProtection(player, key));
        verify(api, never()).delete(any(), any());
        verify(inventory, never()).addItem(any(ItemStack.class));
    }

    @Test void revokedRemotePermissionStopsBeforeResolvingRegion() throws Exception {
        when(player.hasPermission("protectionstones.unclaim.remote")).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> menu.deleteOwnedProtection(player, key));
        verifyNoInteractions(api);
    }

    @Test void noDropDoesNotRequireInventorySpaceOrGiveItem() throws Exception {
        when(api.refund(region, true)).thenReturn(null);
        when(inventory.firstEmpty()).thenReturn(-1);
        assertTrue(menu.deleteOwnedProtection(player, key));
        verify(inventory, never()).firstEmpty();
        verify(inventory, never()).addItem(any(ItemStack.class));
    }

    @Test void failedApiNeverGivesItem() throws Exception {
        when(api.delete(region, player)).thenThrow(new ReflectiveOperationException("API failure"));
        assertThrows(ReflectiveOperationException.class, () -> menu.deleteOwnedProtection(player, key));
        verify(inventory, never()).addItem(any(ItemStack.class));
    }

    @Test void listenerFillingInventoryDoesNotLoseRefund() throws Exception {
        HashMap<Integer, ItemStack> leftovers = new HashMap<>();
        leftovers.put(0, refund);
        when(inventory.addItem(refund)).thenReturn(leftovers);
        World world = mock(World.class);
        Location location = new Location(world, 8, 64, 8);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        assertTrue(menu.deleteOwnedProtection(player, key));
        verify(world).dropItemNaturally(location, refund);
    }
}
