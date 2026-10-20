package com.courtbook.booking.booking;

import com.courtbook.booking.booking.BookingDtos.BookingStats;
import com.courtbook.booking.booking.BookingDtos.BookingView;
import com.courtbook.booking.booking.BookingDtos.CourtView;
import com.courtbook.booking.booking.BookingDtos.CreateBookingRequest;
import com.courtbook.booking.booking.BookingDtos.SlotView;
import com.courtbook.booking.booking.BookingDtos.WaitlistView;
import com.courtbook.booking.web.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {

    private final BookingService service;
    private final SlotQueries queries;

    public BookingController(BookingService service, SlotQueries queries) {
        this.service = service;
        this.queries = queries;
    }

    @GetMapping("/v1/courts")
    public List<CourtView> courts() {
        return queries.courts();
    }

    @GetMapping("/v1/slots")
    public List<SlotView> slots(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Jwt jwt) {
        service.checkBookableDate(date);
        queries.ensureSlots(date);
        return queries.slotsOn(date, CurrentUser.from(jwt).id(), Instant.now());
    }

    @PostMapping("/v1/bookings")
    public ResponseEntity<BookingView> book(@Valid @RequestBody CreateBookingRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        Booking booking = service.book(user, req.slotId());
        BookingView view = queries.booking(booking.getId());
        return ResponseEntity.created(URI.create("/v1/bookings/" + booking.getId())).body(view);
    }

    @DeleteMapping("/v1/bookings/{id}")
    public ResponseEntity<Void> cancel(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        service.cancel(CurrentUser.from(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/bookings/me")
    public List<BookingView> myBookings(@AuthenticationPrincipal Jwt jwt) {
        return queries.bookingsOf(CurrentUser.from(jwt).id());
    }

    @PostMapping("/v1/slots/{slotId}/waitlist")
    public ResponseEntity<WaitlistView> joinWaitlist(@PathVariable long slotId, @AuthenticationPrincipal Jwt jwt) {
        WaitlistView view = service.joinWaitlist(CurrentUser.from(jwt), slotId);
        return ResponseEntity.status(201).body(view);
    }

    @DeleteMapping("/v1/slots/{slotId}/waitlist")
    public ResponseEntity<Void> leaveWaitlist(@PathVariable long slotId, @AuthenticationPrincipal Jwt jwt) {
        service.leaveWaitlist(CurrentUser.from(jwt), slotId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/admin/stats/bookings")
    public BookingStats stats() {
        return queries.stats(Instant.now());
    }
}
