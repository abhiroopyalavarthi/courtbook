package com.courtbook.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The headline guarantee: 100 people press "Book" on the same court at the
 * same moment, and exactly one gets it. Repeated 10 times so a rare timing
 * bug can't hide behind one lucky run.
 */
class DoubleBookingRaceTest extends IntegrationTestBase {

    private static final int RACERS = 100;

    @Autowired
    JdbcTemplate jdbc;

    @RepeatedTest(10)
    void exactlyOneOfAHundredSimultaneousBookingsSucceeds() throws Exception {
        long slotId = freshSlot();
        CountDownLatch ready = new CountDownLatch(RACERS);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(RACERS)) {
            for (int i = 0; i < RACERS; i++) {
                long userId = newUser();
                Callable<Integer> racer = () -> {
                    ready.countDown();
                    go.await();
                    return book(userId, slotId).getStatusCode().value();
                };
                results.add(pool.submit(racer));
            }
            ready.await();
            go.countDown(); // everyone at once
        }

        int created = 0;
        int conflicts = 0;
        for (Future<Integer> f : results) {
            int status = f.get();
            if (status == 201) {
                created++;
            } else if (status == 409) {
                conflicts++;
            }
        }
        assertThat(created).as("successful bookings").isEqualTo(1);
        assertThat(conflicts).as("409 Conflict responses").isEqualTo(RACERS - 1);

        Integer active = jdbc.queryForObject(
                "select count(*) from bookings where slot_id = ? and status = 'ACTIVE'", Integer.class, slotId);
        assertThat(active).isEqualTo(1);
    }
}
