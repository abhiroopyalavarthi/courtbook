package com.courtbook.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class BookingApiIntegrationTest extends IntegrationTestBase {

    private static final ZoneId CLUB = ZoneId.of("America/New_York");

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aDayHasFourteenHourlySlotsPerCourt() {
        LocalDate date = LocalDate.now(CLUB).plusDays(5);
        List<Map<String, Object>> slots = slotsOn(date, newUser());
        assertThat(slots).hasSize(56);
        assertThat(slots).allSatisfy(s -> assertThat(s.get("courtName")).asString().startsWith("Court "));
    }

    @Test
    void bookingShowsAsMineToMeAndAsBookedToOthers() {
        long me = newUser();
        long other = newUser();
        long slotId = freshSlot();
        ResponseEntity<Map<String, Object>> res = book(me, slotId);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody()).containsEntry("status", "ACTIVE").containsEntry("source", "DIRECT");

        LocalDate date = dateOf(slotId);
        assertThat(slot(slotId, date, me)).containsEntry("status", "BOOKED").containsKey("myBookingId");
        assertThat(slot(slotId, date, me).get("myBookingId")).isNotNull();
        assertThat(slot(slotId, date, other).get("myBookingId")).isNull();
        assertThat(myBookings(me)).extracting(b -> b.get("slotId")).contains((int) slotId);
    }

    @Test
    void secondBookingForTheSameSlotIsAConflict() {
        long slotId = freshSlot();
        assertThat(book(newUser(), slotId).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ResponseEntity<Map<String, Object>> second = book(newUser(), slotId);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody()).containsEntry("detail", "This slot is already booked");
    }

    @Test
    void onlyTheOwnerOrAnAdminCanCancel() {
        long owner = newUser();
        long slotId = freshSlot();
        long bookingId = id(book(owner, slotId));

        assertThat(cancel(TestTokens.member(newUser()), bookingId).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(cancel(TestTokens.member(owner), bookingId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(cancel(TestTokens.member(owner), bookingId).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(slot(slotId, dateOf(slotId), owner)).containsEntry("status", "FREE");

        long another = id(book(owner, slotId));
        assertThat(cancel(TestTokens.admin(newUser()), another).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void cancellingPromotesTheFirstPersonInLine() {
        long holder = newUser();
        long first = newUser();
        long second = newUser();
        long slotId = freshSlot();
        long bookingId = id(book(holder, slotId));

        assertThat(joinWaitlist(first, slotId).getBody()).containsEntry("position", 1);
        assertThat(joinWaitlist(second, slotId).getBody()).containsEntry("position", 2);
        assertThat(slot(slotId, dateOf(slotId), holder)).containsEntry("waitlistCount", 2);

        assertThat(cancel(TestTokens.member(holder), bookingId).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Map<String, Object> promoted = myBookings(first).stream()
                .filter(b -> ((Number) b.get("slotId")).longValue() == slotId)
                .findFirst().orElseThrow();
        assertThat(promoted).containsEntry("status", "ACTIVE").containsEntry("source", "WAITLIST");

        Map<String, Object> view = slot(slotId, dateOf(slotId), second);
        assertThat(view).containsEntry("status", "BOOKED").containsEntry("myWaitlistPosition", 1);

        List<String> types = jdbc.queryForList(
                "select type from outbox_events where payload like ? order by created_at", String.class,
                "%\"slotId\":" + slotId + ",%");
        assertThat(types).containsSubsequence("BookingConfirmed", "BookingCancelled", "WaitlistPromoted");
    }

    @Test
    void cannotJoinTheWaitlistForAFreeSlotOrYourOwnSlot() {
        long me = newUser();
        long slotId = freshSlot();
        assertThat(joinWaitlist(me, slotId).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        book(me, slotId);
        assertThat(joinWaitlist(me, slotId).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        long other = newUser();
        assertThat(joinWaitlist(other, slotId).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(joinWaitlist(other, slotId).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void leavingTheWaitlistMeansYouAreNotPromoted() {
        long holder = newUser();
        long quitter = newUser();
        long slotId = freshSlot();
        long bookingId = id(book(holder, slotId));
        joinWaitlist(quitter, slotId);

        ResponseEntity<Void> left = http.delete().uri("/v1/slots/{id}/waitlist", slotId)
                .header("Authorization", "Bearer " + TestTokens.member(quitter))
                .retrieve().toBodilessEntity();
        assertThat(left.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        cancel(TestTokens.member(holder), bookingId);
        assertThat(slot(slotId, dateOf(slotId), quitter)).containsEntry("status", "FREE");
    }

    @Test
    void membersHaveAMaximumNumberOfUpcomingBookings() {
        long me = newUser();
        for (int i = 0; i < 3; i++) {
            assertThat(book(me, freshSlot()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        ResponseEntity<Map<String, Object>> fourth = book(me, freshSlot());
        assertThat(fourth.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void pastSlotsCannotBeBooked() {
        LocalDate yesterday = LocalDate.now(CLUB).minusDays(1);
        long slotId = ((Number) slotsOn(yesterday, newUser()).getFirst().get("id")).longValue();
        assertThat(book(newUser(), slotId).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void datesOutsideTheBookingWindowAreRejected() {
        ResponseEntity<String> res = http.get().uri("/v1/slots?date={d}", LocalDate.now(CLUB).plusDays(60))
                .header("Authorization", "Bearer " + TestTokens.member(newUser()))
                .retrieve().toEntity(String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * The same cancellation sent twice at once (a double-click, or a retry)
     * must not promote the next person twice or hand out the slot twice.
     */
    @Test
    void simultaneousCancellationsPromoteExactlyOnce() throws Exception {
        long holder = newUser();
        long next = newUser();
        long slotId = freshSlot();
        long bookingId = id(book(holder, slotId));
        joinWaitlist(next, slotId);
        joinWaitlist(newUser(), slotId);

        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    return cancel(TestTokens.member(holder), bookingId).getStatusCode().value();
                }));
            }
            go.countDown();
        }
        long succeeded = 0;
        for (Future<Integer> f : results) {
            if (f.get() == 204) {
                succeeded++;
            }
        }
        assertThat(succeeded).isEqualTo(1);

        Integer active = jdbc.queryForObject(
                "select count(*) from bookings where slot_id = ? and status = 'ACTIVE'", Integer.class, slotId);
        assertThat(active).isEqualTo(1);
        Integer nextActive = jdbc.queryForObject(
                "select count(*) from bookings where slot_id = ? and user_id = ? and status = 'ACTIVE'",
                Integer.class, slotId, next);
        assertThat(nextActive).isEqualTo(1);
    }

    @Test
    void adminStatsRequireTheAdminRole() {
        ResponseEntity<String> member = http.get().uri("/v1/admin/stats/bookings")
                .header("Authorization", "Bearer " + TestTokens.member(newUser()))
                .retrieve().toEntity(String.class);
        assertThat(member.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map<String, Object>> admin = http.get().uri("/v1/admin/stats/bookings")
                .header("Authorization", "Bearer " + TestTokens.admin(newUser()))
                .retrieve().toEntity(JSON);
        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).containsKeys("totalBookings", "membersWhoBooked");
    }

    private LocalDate dateOf(long slotId) {
        java.sql.Timestamp start = jdbc.queryForObject("select starts_at from slots where id = ?",
                java.sql.Timestamp.class, slotId);
        return start.toInstant().atZone(CLUB).toLocalDate();
    }
}
