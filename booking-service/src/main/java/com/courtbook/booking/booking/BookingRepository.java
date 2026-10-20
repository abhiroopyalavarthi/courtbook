package com.courtbook.booking.booking;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findBySlotIdAndStatus(long slotId, Booking.Status status);

    long countBySlotIdAndStatus(long slotId, Booking.Status status);

    @Query("""
            select count(b) from Booking b, Slot s
            where s.id = b.slotId and b.userId = :userId
              and b.status = :status and s.startsAt > :now""")
    long countUpcoming(@Param("userId") long userId, @Param("status") Booking.Status status,
            @Param("now") Instant now);
}
