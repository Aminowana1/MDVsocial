package com.mdvcraft.mdvsocial.quickaccept;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/**
 * "Quick accept": a player accepts the latest offered request by sneaking
 * several times quickly within a time window. Independent from any feature;
 * callers only build a {@link QuickAcceptRequest}. One offer per player: a new
 * offer replaces the previous one (the older request is still answerable from
 * its menu).
 */
public final class QuickAcceptService implements Listener {

    private final JavaPlugin plugin;
    private final Predicate<Player> isBedrock;
    private final BiConsumer<Player, String> soundPlayer;
    private final BiConsumer<Player, String> messenger;
    private final Map<UUID, Offer> offers = new ConcurrentHashMap<>();

    private record Offer(QuickAcceptRequest request, SneakSequence sequence, QuickAcceptSettings settings,
            BukkitTask expiryTask) {
    }

    /**
     * @param isBedrock   tells Bedrock players apart (for {@code bedrock-only})
     * @param soundPlayer plays a UI sound key to a player
     * @param messenger   sends an already formatted (&amp;-coded) message to a player
     */
    public QuickAcceptService(JavaPlugin plugin, Predicate<Player> isBedrock, BiConsumer<Player, String> soundPlayer,
            BiConsumer<Player, String> messenger) {
        this.plugin = plugin;
        this.isBedrock = isBedrock;
        this.soundPlayer = soundPlayer;
        this.messenger = messenger;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void shutdown() {
        offers.values().forEach(offer -> offer.expiryTask().cancel());
        offers.clear();
    }

    public QuickAcceptSettings settings(String type) {
        return QuickAcceptSettings.from(plugin.getConfig().getConfigurationSection("quick-accept"), type);
    }

    /** Whether {@code player} would get a quick accept offer for {@code type}. */
    public boolean isAvailable(Player player, String type) {
        QuickAcceptSettings settings = settings(type);
        return player != null && settings.enabled() && (!settings.bedrockOnly() || isBedrock.test(player));
    }

    /** @return true when the offer was registered (see {@link #isAvailable}). */
    public boolean offer(Player player, QuickAcceptRequest request) {
        if (!isAvailable(player, request.type()))
            return false;
        QuickAcceptSettings settings = settings(request.type());
        UUID uuid = player.getUniqueId();
        long expiresAt = System.currentTimeMillis() + settings.windowSeconds() * 1000L;
        BukkitTask expiry = Bukkit.getScheduler().runTaskLater(plugin, () -> expire(uuid),
                settings.windowSeconds() * 20L + 1L);
        Offer previous = offers.put(uuid, new Offer(request,
                new SneakSequence(settings.sneaksRequired(), settings.maxGapMillis(), expiresAt), settings, expiry));
        if (previous != null)
            previous.expiryTask().cancel();
        return true;
    }

    public void cancel(Player player) {
        Offer offer = offers.remove(player.getUniqueId());
        if (offer != null)
            offer.expiryTask().cancel();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking())
            return;
        Player player = event.getPlayer();
        Offer offer = offers.get(player.getUniqueId());
        if (offer == null)
            return;
        switch (offer.sequence().register(System.currentTimeMillis())) {
            case EXPIRED -> expire(player.getUniqueId());
            case PROGRESS -> {
            }
            case COMPLETE -> {
                if (!offers.remove(player.getUniqueId(), offer))
                    return;
                offer.expiryTask().cancel();
                if (!offer.request().stillValid().test(player))
                    return;
                if (!offer.settings().triggerSound().isBlank())
                    soundPlayer.accept(player, offer.settings().triggerSound());
                offer.request().onAccept().accept(player);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer());
    }

    private void expire(UUID uuid) {
        Offer offer = offers.remove(uuid);
        if (offer == null)
            return;
        offer.expiryTask().cancel();
        Player player = Bukkit.getPlayer(uuid);
        String message = offer.settings().expiredMessage();
        if (player != null && message != null && !message.isBlank() && offer.request().stillValid().test(player))
            messenger.accept(player, message);
    }
}
