package com.courtbook.booking.booking;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** When the gym is open and how far ahead members can book. */
@ConfigurationProperties("courtbook.schedule")
public record ScheduleProperties(
        String timeZone,
        int opensAtHour,
        int closesAtHour,
        Duration slotLength,
        int daysAhead,
        int maxActiveBookingsPerUser) {

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }

    /** Start times of every slot on a given day, in the club's time zone. */
    public List<ZonedDateTime> slotStarts(LocalDate date) {
        List<ZonedDateTime> starts = new ArrayList<>();
        ZonedDateTime t = date.atTime(LocalTime.of(opensAtHour, 0)).atZone(zone());
        ZonedDateTime close = date.atTime(LocalTime.of(closesAtHour, 0)).atZone(zone());
        while (!t.plus(slotLength).isAfter(close)) {
            starts.add(t);
            t = t.plus(slotLength);
        }
        return starts;
    }
}
