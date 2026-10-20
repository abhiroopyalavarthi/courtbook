package com.courtbook.booking.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "slots")
public class Slot {

    @Id
    private Long id;

    @Column(name = "court_id", nullable = false)
    private Long courtId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Version
    private long version;

    protected Slot() {
    }

    public Long getId() {
        return id;
    }

    public Long getCourtId() {
        return courtId;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public long getVersion() {
        return version;
    }

    public boolean hasStarted(Instant now) {
        return !now.isBefore(startsAt);
    }
}
