package com.mdvcraft.mdvsocial.tpa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pending teleport requests, without any server dependency (time is passed in).
 * A sender has at most one request out; a target can have several in, kept in
 * arrival order.
 */
public final class TpaRequestBook {

    /** What {@link #removeAllFor} dropped, so the other players can be told. */
    public record Removed(TpaRequest outgoing, List<TpaRequest> incoming) {
    }

    private final Map<UUID, TpaRequest> outgoing = new HashMap<>();
    private final Map<UUID, LinkedHashMap<UUID, TpaRequest>> incoming = new HashMap<>();

    /**
     * Stores a new request, replacing the sender's previous one.
     *
     * @return the new request
     */
    public TpaRequest add(UUID sender, UUID target, long now, long lifetimeMillis) {
        remove(outgoing.get(sender));
        TpaRequest request = new TpaRequest(sender, target, now, now + lifetimeMillis);
        outgoing.put(sender, request);
        incoming.computeIfAbsent(target, id -> new LinkedHashMap<>()).put(sender, request);
        return request;
    }

    public TpaRequest outgoing(UUID sender) {
        return outgoing.get(sender);
    }

    /** The still valid request from {@code sender} to {@code target}, or null. */
    public TpaRequest find(UUID target, UUID sender, long now) {
        Map<UUID, TpaRequest> requests = incoming.get(target);
        TpaRequest request = requests == null ? null : requests.get(sender);
        if (request == null)
            return null;
        if (request.isExpired(now)) {
            remove(request);
            return null;
        }
        return request;
    }

    /** The most recent valid request received by {@code target}, or null. */
    public TpaRequest latest(UUID target, long now) {
        List<TpaRequest> valid = incoming(target, now);
        return valid.isEmpty() ? null : valid.get(0);
    }

    /** Valid requests received by {@code target}, newest first. */
    public List<TpaRequest> incoming(UUID target, long now) {
        Map<UUID, TpaRequest> requests = incoming.get(target);
        if (requests == null)
            return List.of();
        List<TpaRequest> valid = new ArrayList<>();
        for (TpaRequest request : List.copyOf(requests.values())) {
            if (request.isExpired(now))
                remove(request);
            else
                valid.add(0, request);
        }
        return valid;
    }

    public boolean contains(TpaRequest request) {
        return request != null && outgoing.get(request.sender()) == request;
    }

    public void remove(TpaRequest request) {
        if (request == null)
            return;
        outgoing.remove(request.sender(), request);
        Map<UUID, TpaRequest> requests = incoming.get(request.target());
        if (requests != null) {
            requests.remove(request.sender(), request);
            if (requests.isEmpty())
                incoming.remove(request.target());
        }
    }

    /** Removes and returns every expired request. */
    public List<TpaRequest> removeExpired(long now) {
        List<TpaRequest> expired = new ArrayList<>();
        for (TpaRequest request : List.copyOf(outgoing.values())) {
            if (request.isExpired(now)) {
                remove(request);
                expired.add(request);
            }
        }
        return expired;
    }

    /** Drops everything sent or received by {@code player} (e.g. on quit). */
    public Removed removeAllFor(UUID player) {
        TpaRequest sent = outgoing.get(player);
        remove(sent);
        Map<UUID, TpaRequest> received = incoming.remove(player);
        List<TpaRequest> dropped = received == null ? List.of() : List.copyOf(received.values());
        dropped.forEach(this::remove);
        return new Removed(sent, dropped);
    }

    public void clear() {
        outgoing.clear();
        incoming.clear();
    }
}
