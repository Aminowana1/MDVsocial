package com.mdvcraft.mdvsocial;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/** Optional integration: invokes the public PS 2.10 API, never console commands or WG deletions. */
class ProtectionStonesHook {
    private final Class<?> playerClass, regionClass, groupClass, mergedClass;

    ProtectionStonesHook(Plugin protectionStones) throws ClassNotFoundException {
        ClassLoader loader = protectionStones.getClass().getClassLoader();
        playerClass = Class.forName("dev.espi.protectionstones.PSPlayer", true, loader);
        regionClass = Class.forName("dev.espi.protectionstones.PSRegion", true, loader);
        groupClass = Class.forName("dev.espi.protectionstones.PSGroupRegion", true, loader);
        mergedClass = Class.forName("dev.espi.protectionstones.PSMergedRegion", true, loader);
    }

    record Key(UUID world, String id, int x, int y, int z) {}
    record Protection(Key key, String worldName, String name, ItemStack item,
                      List<UUID> members, boolean merged, boolean configured) {}

    private Object player(Player player) throws ReflectiveOperationException {
        return playerClass.getMethod("fromPlayer", Player.class).invoke(null, player);
    }

    int limit(Player player) throws ReflectiveOperationException {
        return (Integer) call(player(player), "getGlobalRegionLimits");
    }

    List<Protection> owned(Player player) throws ReflectiveOperationException {
        List<Protection> result = new ArrayList<>();
        Object psPlayer = player(player);
        for (World world : Bukkit.getWorlds()) {
            Collection<?> regions = (Collection<?>) call(psPlayer, "getPSRegions",
                    new Class<?>[]{World.class, boolean.class}, world, false);
            for (Object region : regions) {
                Collection<?> stones = groupClass.isInstance(region)
                        ? (Collection<?>) call(region, "getMergedRegions") : List.of(region);
                for (Object stone : stones) result.add(snapshot(stone));
            }
        }
        result.sort(Comparator.comparing(Protection::worldName).thenComparing(p -> p.key().id()));
        return result;
    }

    /** PS documents getPSRegions as async-safe; item/block snapshots are deliberately deferred to the server thread. */
    List<Object> queryOwned(UUID uuid, List<World> worlds) throws ReflectiveOperationException {
        Object psPlayer = playerClass.getMethod("fromUUID", UUID.class).invoke(null, uuid);
        List<Object> result = new ArrayList<>();
        for (World world : worlds) {
            Collection<?> regions = (Collection<?>) call(psPlayer, "getPSRegions",
                    new Class<?>[]{World.class, boolean.class}, world, false);
            for (Object region : regions) {
                if (groupClass.isInstance(region)) result.addAll((Collection<?>) call(region, "getMergedRegions"));
                else result.add(region);
            }
        }
        return result;
    }

    /** Re-resolve by world UUID and exact stone location for EVERY action, including form callbacks. */
    Object requireOwned(Player player, Key key) throws ReflectiveOperationException {
        World world = Bukkit.getWorld(key.world());
        if (world == null) throw new IllegalArgumentException("El mundo de esa protección no está cargado.");
        Object region = regionClass.getMethod("fromLocationUnsafe", Location.class)
                .invoke(null, new Location(world, key.x(), key.y(), key.z()));
        if (region == null || !key.id().equals(call(region, "getId"))
                || !(Boolean) call(region, "isOwner", new Class<?>[]{UUID.class}, player.getUniqueId()))
            throw new IllegalArgumentException("La protección ya no existe o ya no eres su dueño.");
        // Never accidentally delete a whole group from an individual stone entry.
        if (groupClass.isInstance(region))
            throw new IllegalArgumentException("La protección cambió de grupo. Vuelve a abrir el menú.");
        return region;
    }

    Protection snapshot(Object region) throws ReflectiveOperationException {
        World world = (World) call(region, "getWorld");
        Block block = (Block) call(region, "getProtectBlock");
        String id = (String) call(region, "getId");
        String name = (String) call(region, "getName");
        Object type = call(region, "getTypeOptions");
        ItemStack item = type == null ? new ItemStack(Material.BARRIER) : ((ItemStack) call(type, "createItem")).clone();
        item.setAmount(1);
        @SuppressWarnings("unchecked")
        List<UUID> members = new ArrayList<>((Collection<UUID>) call(region, "getMembers"));
        return new Protection(new Key(world.getUID(), id, block.getX(), block.getY(), block.getZ()),
                world.getName(), name == null || name.isBlank() ? id : name, item, members,
                mergedClass.isInstance(region), type != null);
    }

    void addMember(Object region, UUID member) throws ReflectiveOperationException {
        if ((Boolean) call(region, "isOwner", new Class<?>[]{UUID.class}, member))
            throw new IllegalArgumentException("Ese jugador ya es dueño de la protección.");
        if ((Boolean) call(region, "isMember", new Class<?>[]{UUID.class}, member))
            throw new IllegalArgumentException("Ese jugador ya es miembro.");
        call(region, "addMember", new Class<?>[]{UUID.class}, member);
    }

    void removeMember(Object region, UUID member) throws ReflectiveOperationException {
        if ((Boolean) call(region, "isOwner", new Class<?>[]{UUID.class}, member))
            throw new IllegalArgumentException("No puedes eliminar dueños desde la lista de miembros.");
        if (!(Boolean) call(region, "isMember", new Class<?>[]{UUID.class}, member))
            throw new IllegalArgumentException("Ese jugador ya no es miembro.");
        call(region, "removeMember", new Class<?>[]{UUID.class}, member);
    }

    ItemStack refund(Object region, boolean returnBlock) throws ReflectiveOperationException {
        if ("RENTING".equals(String.valueOf(call(region, "getRentStage"))))
            throw new IllegalArgumentException("No puedes remover una protección alquilada.");
        Object type = call(region, "getTypeOptions");
        if (type == null)
            throw new IllegalArgumentException("El tipo de esta protección ya no está configurado en ProtectionStones.");
        if (!returnBlock || type.getClass().getField("noDrop").getBoolean(type)) return null;
        ItemStack item = ((ItemStack) call(type, "createItem")).clone();
        item.setAmount(1);
        return item;
    }

    boolean delete(Object region, Player player) throws ReflectiveOperationException {
        return (Boolean) call(region, "deleteRegion", new Class<?>[]{boolean.class, Player.class}, true, player);
    }

    static Object call(Object object, String name) throws ReflectiveOperationException {
        return call(object, name, new Class<?>[0]);
    }

    static Object call(Object object, String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        Method method = object.getClass().getMethod(name, types);
        try {
            return method.invoke(object, args);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof IllegalArgumentException argument) throw argument;
            throw ex;
        }
    }
}
