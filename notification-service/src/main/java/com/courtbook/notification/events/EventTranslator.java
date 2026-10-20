package com.courtbook.notification.events;

import com.courtbook.notification.store.NotificationStore.NewFeedItem;
import com.courtbook.notification.store.NotificationStore.NewNotification;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns an event into the notifications and feed lines people see. Pure
 * logic with no AWS calls, so it's unit tested directly.
 */
public class EventTranslator {

    private final DateTimeFormatter when;

    public EventTranslator(ZoneId zone) {
        this.when = DateTimeFormatter.ofPattern("EEE MMM d, h:mm a", Locale.US).withZone(zone);
    }

    public record Output(List<NewNotification> notifications, List<NewFeedItem> feed) {
        static Output empty() {
            return new Output(List.of(), List.of());
        }
    }

    public Output translate(EventEnvelope e) {
        return switch (e.type()) {
            case "BookingConfirmed" -> e.str("source") != null && e.str("source").equals("WAITLIST")
                    ? Output.empty() // WaitlistPromoted says it better
                    : notify(e.lng("userId"), e.type(), "Court booked",
                            e.str("courtName") + ", " + time(e.str("startsAt")));
            case "BookingCancelled" -> notify(e.lng("userId"), e.type(), "Booking cancelled",
                    (cancelledBySomeoneElse(e) ? "A club admin cancelled your booking: " : "")
                            + e.str("courtName") + ", " + time(e.str("startsAt")));
            case "WaitlistPromoted" -> notify(e.lng("userId"), e.type(), "You got the court!",
                    "Someone cancelled, so " + e.str("courtName") + " on " + time(e.str("startsAt"))
                            + " is now yours.");
            case "TournamentStarted" -> {
                List<NewNotification> notes = new ArrayList<>();
                for (long playerId : e.longs("playerIds")) {
                    notes.add(new NewNotification(playerId, e.type(), "The bracket is out",
                            e.str("tournamentName") + " has started. Check your first match."));
                }
                yield new Output(notes, List.of(new NewFeedItem(e.type(),
                        e.str("tournamentName") + " has started with " + e.longs("playerIds").size() + " players.")));
            }
            case "MatchResultRecorded" -> {
                String score = e.str("score") == null || e.str("score").isBlank() ? "" : " " + e.str("score");
                String line = e.str("winnerName") + " beat " + e.str("loserName") + score
                        + " (" + e.str("roundName") + ", " + e.str("tournamentName") + ")";
                yield new Output(
                        List.of(
                                new NewNotification(e.lng("winnerId"), e.type(), "You won your match", line),
                                new NewNotification(e.lng("loserId"), e.type(), "Match result recorded", line)),
                        List.of(new NewFeedItem(e.type(), line)));
            }
            case "TournamentCompleted" -> new Output(
                    List.of(new NewNotification(e.lng("championId"), e.type(), "Champion!",
                            "You won " + e.str("tournamentName") + ". Congratulations!")),
                    List.of(new NewFeedItem(e.type(), e.str("championName") + " won " + e.str("tournamentName") + "!")));
            default -> Output.empty();
        };
    }

    public boolean understands(String type) {
        return switch (type) {
            case "BookingConfirmed", "BookingCancelled", "WaitlistPromoted", "TournamentStarted",
                    "MatchResultRecorded", "TournamentCompleted" -> true;
            default -> false;
        };
    }

    private static boolean cancelledBySomeoneElse(EventEnvelope e) {
        return e.str("cancelledBy") != null && e.lng("cancelledBy") != e.lng("userId");
    }

    private Output notify(long userId, String type, String title, String body) {
        return new Output(List.of(new NewNotification(userId, type, title, body)), List.of());
    }

    private String time(String isoInstant) {
        return isoInstant == null ? "" : when.format(Instant.parse(isoInstant));
    }
}
