package com.mdvcraft.mdvsocial.players;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Orders the players offered by every "choose a player" list:
 * <ol>
 * <li>friends,</li>
 * <li>adventure party members,</li>
 * <li>clan members,</li>
 * <li>everyone else online,</li>
 * </ol>
 * alphabetically inside each tier. The viewer, players the viewer blocked and
 * the ones the caller excludes (already friends, already in the party...) are
 * left out. No Bukkit types, so it is unit tested directly.
 */
public final class PlayerRanking {

    /** Why a player is offered where it is; the first matching tier wins. */
    public enum Tier { FRIEND, PARTY, CLAN, ONLINE }

    /** The viewer's relations with other players. */
    public record Relations(Set<UUID> friends, Set<UUID> party, Set<UUID> clan, Set<UUID> blocked) {

        public static final Relations NONE = new Relations(Set.of(), Set.of(), Set.of(), Set.of());

        public Relations {
            friends = friends == null ? Set.of() : Set.copyOf(friends);
            party = party == null ? Set.of() : Set.copyOf(party);
            clan = clan == null ? Set.of() : Set.copyOf(clan);
            blocked = blocked == null ? Set.of() : Set.copyOf(blocked);
        }

        Tier tierOf(UUID uuid) {
            if (friends.contains(uuid))
                return Tier.FRIEND;
            if (party.contains(uuid))
                return Tier.PARTY;
            if (clan.contains(uuid))
                return Tier.CLAN;
            return Tier.ONLINE;
        }
    }

    /** An online player the viewer can see. */
    public record Candidate(UUID uuid, String name) {
    }

    public record Ranked(UUID uuid, String name, Tier tier) {
    }

    private static final Comparator<Ranked> ORDER = Comparator.comparing(Ranked::tier)
            .thenComparing(Ranked::name, String.CASE_INSENSITIVE_ORDER);

    private PlayerRanking() {
    }

    /**
     * @param exclude players this list must not offer (redundant for its purpose); may be null
     */
    public static List<Ranked> rank(UUID viewer, Collection<Candidate> online, Relations relations,
            Predicate<UUID> exclude) {
        Relations rel = relations == null ? Relations.NONE : relations;
        List<Ranked> out = new ArrayList<>();
        for (Candidate candidate : online) {
            UUID uuid = candidate.uuid();
            if (uuid == null || uuid.equals(viewer) || rel.blocked().contains(uuid)
                    || (exclude != null && exclude.test(uuid)))
                continue;
            out.add(new Ranked(uuid, candidate.name(), rel.tierOf(uuid)));
        }
        out.sort(ORDER);
        return out;
    }
}
