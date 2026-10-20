package com.courtbook.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.courtbook.notification.events.EventEnvelope;
import com.courtbook.notification.events.EventTranslator;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventTranslatorTest {

    private final EventTranslator translator = new EventTranslator(ZoneId.of("America/New_York"));

    @Test
    void waitlistPromotionNotifiesThePromotedMember() {
        EventTranslator.Output out = translator.translate(event("WaitlistPromoted", Map.of(
                "userId", 42, "courtName", "Court 2", "startsAt", "2026-10-21T23:00:00Z")));
        assertThat(out.notifications()).singleElement().satisfies(n -> {
            assertThat(n.userId()).isEqualTo(42);
            assertThat(n.title()).isEqualTo("You got the court!");
            assertThat(n.body()).contains("Court 2").contains("Wed Oct 21, 7:00 PM");
        });
        assertThat(out.feed()).isEmpty();
    }

    @Test
    void bookingsFromTheWaitlistDontNotifyTwice() {
        EventTranslator.Output out = translator.translate(event("BookingConfirmed", Map.of(
                "userId", 42, "courtName", "Court 2", "startsAt", "2026-10-21T23:00:00Z", "source", "WAITLIST")));
        assertThat(out.notifications()).isEmpty();
    }

    @Test
    void adminCancellationsSaySo() {
        EventTranslator.Output out = translator.translate(event("BookingCancelled", Map.of(
                "userId", 42, "cancelledBy", 1, "courtName", "Court 1", "startsAt", "2026-10-21T23:00:00Z")));
        assertThat(out.notifications().getFirst().body()).startsWith("A club admin cancelled");
    }

    @Test
    void matchResultsGoToBothPlayersAndTheFeed() {
        EventTranslator.Output out = translator.translate(event("MatchResultRecorded", Map.of(
                "winnerId", 1, "winnerName", "Abhi", "loserId", 2, "loserName", "Sam",
                "score", "21-17, 21-19", "roundName", "Final", "tournamentName", "Fall Singles")));
        assertThat(out.notifications()).extracting(n -> n.userId()).containsExactly(1L, 2L);
        assertThat(out.feed()).singleElement()
                .satisfies(f -> assertThat(f.text()).isEqualTo("Abhi beat Sam 21-17, 21-19 (Final, Fall Singles)"));
    }

    @Test
    void tournamentStartNotifiesEveryPlayer() {
        EventTranslator.Output out = translator.translate(event("TournamentStarted", Map.of(
                "tournamentName", "Fall Singles", "playerIds", List.of(1, 2, 3))));
        assertThat(out.notifications()).hasSize(3);
        assertThat(out.feed()).hasSize(1);
    }

    @Test
    void unknownEventsProduceNothing() {
        assertThat(translator.understands("SomethingNew")).isFalse();
    }

    private static EventEnvelope event(String type, Map<String, Object> payload) {
        return new EventEnvelope("evt-1", type, "2026-10-19T12:00:00Z", "test", null, payload);
    }
}
