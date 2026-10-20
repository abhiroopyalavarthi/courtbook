package com.courtbook.booking.booking;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Request and response bodies for the booking service's REST API. */
public final class BookingDtos {

    private BookingDtos() {
    }

    public record CourtView(long id, String name) {
    }

    public record SlotView(
            long id,
            long courtId,
            String courtName,
            Instant startsAt,
            Instant endsAt,
            /** FREE or BOOKED. */
            String status,
            /** Set only when the caller holds this slot. */
            Long myBookingId,
            int waitlistCount,
            /** The caller's place in line (1 = next), or null if not waiting. */
            Integer myWaitlistPosition,
            boolean started) {
    }

    public record CreateBookingRequest(@NotNull Long slotId) {
    }

    public record BookingView(
            long id,
            long slotId,
            String courtName,
            Instant startsAt,
            Instant endsAt,
            String status,
            String source,
            Instant createdAt) {
    }

    public record WaitlistView(long slotId, int position) {
    }

    public record BookingStats(
            long totalBookings,
            long upcomingActive,
            long cancelled,
            long promotedFromWaitlist,
            long membersWhoBooked) {
    }
}
