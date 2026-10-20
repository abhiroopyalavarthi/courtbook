package com.courtbook.booking.booking;

import com.courtbook.booking.booking.BookingDtos.WaitlistView;
import com.courtbook.booking.events.EventRecorder;
import com.courtbook.booking.web.ApiException;
import com.courtbook.booking.web.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The write side of bookings and waitlists. Two guarantees matter here:
 *
 * <ol>
 *   <li>A slot never has two active bookings. The partial unique index
 *       bookings_one_active_per_slot enforces it; this class only turns the
 *       violation into a 409.</li>
 *   <li>Cancelling and promoting the next person in line commit together,
 *       and anything else that changes the line (join, leave) bumps the
 *       slot's version, so two of these racing on one slot can't both win.</li>
 * </ol>
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final SlotRepository slots;
    private final BookingRepository bookings;
    private final WaitlistRepository waitlist;
    private final SlotQueries queries;
    private final EventRecorder events;
    private final ScheduleProperties schedule;

    public BookingService(SlotRepository slots, BookingRepository bookings, WaitlistRepository waitlist,
            SlotQueries queries, EventRecorder events, ScheduleProperties schedule) {
        this.slots = slots;
        this.bookings = bookings;
        this.waitlist = waitlist;
        this.queries = queries;
        this.events = events;
        this.schedule = schedule;
    }

    @Transactional
    public Booking book(CurrentUser user, long slotId) {
        Instant now = now();
        Slot slot = slots.findById(slotId).orElseThrow(() -> ApiException.notFound("No slot with id " + slotId));
        if (slot.hasStarted(now)) {
            throw ApiException.conflict("This slot has already started");
        }
        long upcoming = bookings.countUpcoming(user.id(), Booking.Status.ACTIVE, now);
        if (upcoming >= schedule.maxActiveBookingsPerUser()) {
            throw ApiException.conflict("You already have " + upcoming + " upcoming bookings, the club maximum");
        }
        Booking booking;
        try {
            booking = bookings.saveAndFlush(new Booking(slotId, user.id(), user.name(), Booking.Source.DIRECT, now));
        } catch (DataIntegrityViolationException e) {
            // Lost the race: another request's booking for this slot got in first.
            throw ApiException.conflict("This slot is already booked");
        }
        events.record("BookingConfirmed", payload(booking, slot));
        log.info("User {} booked slot {} (booking {})", user.id(), slotId, booking.getId());
        return booking;
    }

    @Transactional
    public void cancel(CurrentUser user, long bookingId) {
        Instant now = now();
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("No booking with id " + bookingId));
        if (!booking.getUserId().equals(user.id()) && !user.admin()) {
            throw ApiException.forbidden("You can only cancel your own bookings");
        }
        if (!booking.isActive()) {
            throw ApiException.conflict("This booking is already cancelled");
        }
        Slot slot = slots.findAndBumpVersion(booking.getSlotId()).orElseThrow();
        if (slot.hasStarted(now)) {
            throw ApiException.conflict("This slot has already started and can't be cancelled");
        }

        booking.cancel(now);
        // Flush now: Hibernate runs inserts before updates, and the promoted
        // booking below must not be inserted while this one still looks ACTIVE.
        bookings.saveAndFlush(booking);
        Map<String, Object> cancelled = payload(booking, slot);
        cancelled.put("cancelledBy", user.id());
        events.record("BookingCancelled", cancelled);

        Optional<WaitlistEntry> next = waitlist
                .findFirstBySlotIdAndStatusOrderByCreatedAtAscIdAsc(slot.getId(), WaitlistEntry.Status.WAITING);
        if (next.isPresent()) {
            WaitlistEntry entry = next.get();
            entry.promote();
            Booking promoted = bookings.save(
                    new Booking(slot.getId(), entry.getUserId(), entry.getUserName(), Booking.Source.WAITLIST, now));
            bookings.flush();
            events.record("WaitlistPromoted", payload(promoted, slot));
            log.info("Booking {} cancelled; promoted user {} from the waitlist (booking {})",
                    bookingId, entry.getUserId(), promoted.getId());
        } else {
            log.info("Booking {} cancelled; slot {} is free again", bookingId, slot.getId());
        }
    }

    @Transactional
    public WaitlistView joinWaitlist(CurrentUser user, long slotId) {
        Instant now = now();
        Slot slot = slots.findAndBumpVersion(slotId)
                .orElseThrow(() -> ApiException.notFound("No slot with id " + slotId));
        if (slot.hasStarted(now)) {
            throw ApiException.conflict("This slot has already started");
        }
        Booking holder = bookings.findBySlotIdAndStatus(slotId, Booking.Status.ACTIVE)
                .orElseThrow(() -> ApiException.conflict("This slot is free, so book it instead"));
        if (holder.getUserId().equals(user.id())) {
            throw ApiException.conflict("You already have this slot");
        }
        if (waitlist.findBySlotIdAndUserIdAndStatus(slotId, user.id(), WaitlistEntry.Status.WAITING).isPresent()) {
            throw ApiException.conflict("You're already on the waitlist for this slot");
        }
        WaitlistEntry entry = waitlist.saveAndFlush(new WaitlistEntry(slotId, user.id(), user.name(), now));
        int position = (int) waitlist.positionOf(slotId, WaitlistEntry.Status.WAITING,
                entry.getCreatedAt(), entry.getId());
        return new WaitlistView(slotId, position);
    }

    @Transactional
    public void leaveWaitlist(CurrentUser user, long slotId) {
        slots.findAndBumpVersion(slotId).orElseThrow(() -> ApiException.notFound("No slot with id " + slotId));
        WaitlistEntry entry = waitlist.findBySlotIdAndUserIdAndStatus(slotId, user.id(), WaitlistEntry.Status.WAITING)
                .orElseThrow(() -> ApiException.notFound("You're not on the waitlist for this slot"));
        entry.leave();
    }

    /** Members can look back a week and book up to daysAhead days out. */
    public void checkBookableDate(LocalDate date) {
        LocalDate today = LocalDate.now(schedule.zone());
        if (date.isBefore(today.minusDays(7)) || date.isAfter(today.plusDays(schedule.daysAhead()))) {
            throw ApiException.badRequest("Pick a date between a week ago and "
                    + schedule.daysAhead() + " days from today");
        }
    }

    private Map<String, Object> payload(Booking booking, Slot slot) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("bookingId", booking.getId());
        p.put("slotId", slot.getId());
        p.put("userId", booking.getUserId());
        p.put("userName", booking.getUserName());
        p.put("courtName", queries.courtName(slot.getId()));
        p.put("startsAt", slot.getStartsAt().toString());
        p.put("endsAt", slot.getEndsAt().toString());
        p.put("source", booking.getSource().name());
        return p;
    }

    /** Postgres keeps microseconds; truncating keeps in-memory and stored times equal. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
