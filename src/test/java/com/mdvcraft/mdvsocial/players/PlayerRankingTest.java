package com.mdvcraft.mdvsocial.players;

import com.mdvcraft.mdvsocial.players.PlayerRanking.Candidate;
import com.mdvcraft.mdvsocial.players.PlayerRanking.Ranked;
import com.mdvcraft.mdvsocial.players.PlayerRanking.Relations;
import com.mdvcraft.mdvsocial.players.PlayerRanking.Tier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerRankingTest {

    private final UUID viewer = UUID.randomUUID();
    private final UUID friend = UUID.randomUUID();
    private final UUID partyMate = UUID.randomUUID();
    private final UUID clanMate = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final UUID stranger2 = UUID.randomUUID();
    private final UUID blocked = UUID.randomUUID();

    private final List<Candidate> online = List.of(
            new Candidate(stranger, "zoe"), new Candidate(blocked, "Bad"), new Candidate(clanMate, "Clara"),
            new Candidate(viewer, "Me"), new Candidate(partyMate, "Pablo"), new Candidate(friend, "Fran"),
            new Candidate(stranger2, "Ana"));

    // Pablo is friend and party mate, Clara party and clan mate, Ana only a clan mate.
    private final Relations relations = new Relations(Set.of(friend, partyMate), Set.of(partyMate, clanMate),
            Set.of(clanMate, stranger2), Set.of(blocked));

    private static List<String> names(List<Ranked> ranked) {
        return ranked.stream().map(Ranked::name).toList();
    }

    @Test
    void ordersFriendsPartyClanThenOnline() {
        List<Ranked> ranked = PlayerRanking.rank(viewer, online, relations, null);
        assertEquals(List.of("Fran", "Pablo", "Clara", "Ana", "zoe"), names(ranked));
        // The first matching tier wins: Pablo is a friend, Clara a party mate.
        assertEquals(List.of(Tier.FRIEND, Tier.FRIEND, Tier.PARTY, Tier.CLAN, Tier.ONLINE),
                ranked.stream().map(Ranked::tier).toList());
    }

    @Test
    void leavesOutViewerBlockedAndExcluded() {
        List<Ranked> ranked = PlayerRanking.rank(viewer, online, relations, relations.friends()::contains);
        assertEquals(List.of("Clara", "Ana", "zoe"), names(ranked));
    }

    @Test
    void worksWithoutRelations() {
        assertEquals(List.of("Ana", "Bad", "Clara", "Fran", "Pablo", "zoe"),
                names(PlayerRanking.rank(viewer, online, null, null)));
    }
}
