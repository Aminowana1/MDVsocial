package com.mdvcraft.mdvsocial.tpa;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Teleport requests: send, accept, deny, cancel and expire. Every method tells
 * the involved players what happened; callers only pick the request.
 */
public final class TpaService {

    public static final String PERMISSION = "mdvsocial.tpa";

    private final TpaHost host;
    private final TpaMessages messages;
    private final Supplier<TpaSettings> settings;
    private final TpaRequestBook book = new TpaRequestBook();
    private final PlayerDeadlines cooldowns = new PlayerDeadlines();
    private final TpaTeleporter teleporter;
    private final TpaNotifier notifier;

    TpaService(TpaHost host, TpaMessages messages, Supplier<TpaSettings> settings, TpaTeleporter teleporter,
            TpaNotifier notifier) {
        this.host = host;
        this.messages = messages;
        this.settings = settings;
        this.teleporter = teleporter;
        this.notifier = notifier;
    }

    public boolean isEnabled() {
        return settings.get().enabled();
    }

    /** @return true when the player may use the TPA; otherwise tells them why not. */
    public boolean checkAccess(Player player) {
        if (!player.hasPermission(PERMISSION)) {
            messages.send(player, "no-permission");
            return false;
        }
        if (!isEnabled()) {
            messages.send(player, "disabled");
            return false;
        }
        return true;
    }

    /**
     * Sends a request from {@code sender} to {@code target}. Once it goes out the
     * sender's menus are closed, so a request made from a menu does not leave it open.
     * Between members of the same party it is accepted right away (unless
     * {@code tpa.party-auto-accept.ignore}).
     *
     * @return true when it went out; false when it was refused (the sender was told why)
     */
    public boolean request(Player sender, Player target) {
        TpaSettings config = settings.get();
        if (target == null || !target.isOnline() || !sender.canSee(target)) {
            messages.send(sender, "player-not-found");
            return false;
        }
        if (target.equals(sender)) {
            messages.send(sender, "cannot-self");
            return false;
        }
        if (!config.allowCrossWorld() && !target.getWorld().equals(sender.getWorld())) {
            messages.send(sender, "cross-world-disabled");
            return false;
        }
        long now = System.currentTimeMillis();
        long wait = cooldowns.remainingSeconds(sender.getUniqueId(), now);
        if (wait > 0) {
            messages.send(sender, "cooldown", Map.of("seconds", String.valueOf(wait)));
            return false;
        }
        cooldowns.start(sender.getUniqueId(), now, config.cooldownSeconds() * 1000L);
        host.closeMenus(sender);

        // Like letters and party invites, a block is never revealed to the sender.
        if (!host.canContact(sender.getUniqueId(), target.getUniqueId())) {
            messages.send(sender, "request-not-delivered", Map.of("target", target.getName()));
            return true;
        }

        TpaRequest request = book.add(sender.getUniqueId(), target.getUniqueId(), now,
                config.requestTimeoutSeconds() * 1000L);
        if (!config.ignorePartyAutoAccept() && host.sameParty(sender, target)) {
            messages.send(sender, "party-auto-accepted", Map.of("target", target.getName()));
            messages.send(target, "party-auto-accepted-target", Map.of("player", sender.getName()));
            accept(target, request, false);
            return true;
        }
        String seconds = String.valueOf(config.requestTimeoutSeconds());
        messages.send(sender, "request-sent", Map.of("target", target.getName(), "seconds", seconds));
        notifier.notify(target, sender, request, this);
        return true;
    }

    /** Valid requests received by {@code target}, newest first. */
    public List<TpaRequest> pending(Player target) {
        return book.incoming(target.getUniqueId(), System.currentTimeMillis());
    }

    /** The request {@code sender} has out, or null. */
    public TpaRequest outgoing(Player sender) {
        TpaRequest request = book.outgoing(sender.getUniqueId());
        return request == null || request.isExpired(System.currentTimeMillis()) ? null : request;
    }

    public boolean isPending(TpaRequest request) {
        return book.contains(request) && !request.isExpired(System.currentTimeMillis());
    }

    public void acceptLatest(Player target) {
        TpaRequest request = book.latest(target.getUniqueId(), System.currentTimeMillis());
        if (request == null)
            messages.send(target, "no-pending-request");
        else
            accept(target, request, true);
    }

    public void denyLatest(Player target) {
        TpaRequest request = book.latest(target.getUniqueId(), System.currentTimeMillis());
        if (request == null)
            messages.send(target, "no-pending-request");
        else
            deny(target, request);
    }

    /** Accepts the request of {@code senderId}; tells the target when it is no longer valid. */
    public boolean accept(Player target, UUID senderId) {
        TpaRequest request = find(target, senderId);
        return request != null && accept(target, request, true);
    }

    public boolean deny(Player target, UUID senderId) {
        TpaRequest request = find(target, senderId);
        if (request == null)
            return false;
        deny(target, request);
        return true;
    }

    public void cancelOutgoing(Player sender) {
        TpaRequest request = book.outgoing(sender.getUniqueId());
        if (request == null) {
            messages.send(sender, "no-outgoing-request");
            return;
        }
        book.remove(request);
        messages.send(sender, "request-cancelled");
        messages.send(Bukkit.getPlayer(request.target()), "request-cancelled-target",
                Map.of("player", sender.getName()));
    }

    private TpaRequest find(Player target, UUID senderId) {
        TpaRequest request = senderId == null ? null
                : book.find(target.getUniqueId(), senderId, System.currentTimeMillis());
        if (request == null)
            messages.send(target, "request-not-valid");
        return request;
    }

    /** @param announce whether the target hears "you accepted X" (not for automatic accepts) */
    private boolean accept(Player target, TpaRequest request, boolean announce) {
        book.remove(request);
        Player requester = Bukkit.getPlayer(request.sender());
        if (requester == null) {
            messages.send(target, "requester-offline");
            return false;
        }
        if (!settings.get().allowCrossWorld() && !target.getWorld().equals(requester.getWorld())) {
            messages.send(target, "cross-world-disabled");
            return false;
        }
        if (announce)
            messages.send(target, "request-accepted-target", Map.of("player", requester.getName()));
        teleporter.start(requester, target);
        return true;
    }

    private void deny(Player target, TpaRequest request) {
        book.remove(request);
        Player requester = Bukkit.getPlayer(request.sender());
        messages.send(target, "request-denied-target",
                Map.of("player", requester == null ? "jugador" : requester.getName()));
        messages.send(requester, "request-denied", Map.of("target", target.getName()));
    }

    /** Drops expired requests and tells both players. Runs periodically. */
    void expireRequests() {
        for (TpaRequest request : book.removeExpired(System.currentTimeMillis())) {
            Player sender = Bukkit.getPlayer(request.sender());
            messages.send(sender, "request-expired");
            messages.send(Bukkit.getPlayer(request.target()), "request-expired-target",
                    Map.of("player", sender == null ? "un jugador" : sender.getName()));
        }
    }

    /** Forgets a player who left and tells whoever was waiting on them. */
    void forget(UUID player) {
        TpaRequestBook.Removed removed = book.removeAllFor(player);
        if (removed.outgoing() != null)
            messages.send(Bukkit.getPlayer(removed.outgoing().target()), "request-cancelled-disconnect");
        for (TpaRequest request : removed.incoming())
            messages.send(Bukkit.getPlayer(request.sender()), "target-disconnected");
        teleporter.forget(player);
        cooldowns.clear(player, 0L);
    }

    void shutdown() {
        book.clear();
        cooldowns.clearAll();
        teleporter.shutdown();
    }
}
