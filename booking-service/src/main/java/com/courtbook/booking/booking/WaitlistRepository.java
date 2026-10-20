package com.courtbook.booking.booking;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    Optional<WaitlistEntry> findFirstBySlotIdAndStatusOrderByCreatedAtAscIdAsc(long slotId,
            WaitlistEntry.Status status);

    Optional<WaitlistEntry> findBySlotIdAndUserIdAndStatus(long slotId, long userId, WaitlistEntry.Status status);

    /** 1-based place in line: how many waiting entries joined at or before this one. */
    @Query("""
            select count(w) from WaitlistEntry w
            where w.slotId = :slotId and w.status = :status
              and (w.createdAt < :createdAt or (w.createdAt = :createdAt and w.id <= :id))""")
    long positionOf(@Param("slotId") long slotId, @Param("status") WaitlistEntry.Status status,
            @Param("createdAt") Instant createdAt, @Param("id") long id);
}
