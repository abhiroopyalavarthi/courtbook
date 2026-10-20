package com.courtbook.booking.booking;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    /**
     * Loads the slot and bumps its version when the transaction commits.
     * If another transaction bumped it first, this one fails with an
     * optimistic lock error instead of silently overwriting.
     */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select s from Slot s where s.id = :id")
    Optional<Slot> findAndBumpVersion(@Param("id") long id);
}
