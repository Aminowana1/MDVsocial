package com.mdvcraft.mdvsocial.tpa;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TpaRequestBookTest {

    private final UUID alex = new UUID(0, 1);
    private final UUID steve = new UUID(0, 2);
    private final UUID target = new UUID(0, 3);
    private final TpaRequestBook book = new TpaRequestBook();

    @Test
    void latestIsTheNewestValidRequest() {
        book.add(alex, target, 1_000, 60_000);
        TpaRequest fromSteve = book.add(steve, target, 2_000, 60_000);
        assertSame(fromSteve, book.latest(target, 3_000));
        assertEquals(List.of(steve, alex), book.incoming(target, 3_000).stream().map(TpaRequest::sender).toList());
    }

    @Test
    void newRequestReplacesTheSendersPreviousOne() {
        TpaRequest first = book.add(alex, target, 1_000, 60_000);
        TpaRequest second = book.add(alex, steve, 2_000, 60_000);
        assertFalse(book.contains(first));
        assertNull(book.find(target, alex, 2_000));
        assertSame(second, book.outgoing(alex));
    }

    @Test
    void resendingMovesTheRequestToTheTop() {
        book.add(alex, target, 1_000, 60_000);
        book.add(steve, target, 2_000, 60_000);
        book.add(alex, target, 3_000, 60_000);
        assertEquals(alex, book.latest(target, 3_000).sender());
    }

    @Test
    void expiredRequestsAreNotOfferedAndAreSwept() {
        book.add(alex, target, 0, 10_000);
        book.add(steve, target, 5_000, 10_000);
        assertNull(book.find(target, alex, 10_000));
        assertEquals(1, book.incoming(target, 10_000).size());

        assertEquals(List.of(), book.removeExpired(10_000), "already dropped by the lookup above");
        assertEquals(1, book.removeExpired(15_000).size());
        assertNull(book.latest(target, 15_000));
    }

    @Test
    void quittingDropsSentAndReceivedRequests() {
        TpaRequest sent = book.add(target, steve, 0, 60_000);
        TpaRequest received = book.add(alex, target, 0, 60_000);
        TpaRequestBook.Removed removed = book.removeAllFor(target);
        assertSame(sent, removed.outgoing());
        assertEquals(List.of(received), removed.incoming());
        assertNull(book.outgoing(alex));
        assertTrue(book.incoming(steve, 0).isEmpty());
    }
}
