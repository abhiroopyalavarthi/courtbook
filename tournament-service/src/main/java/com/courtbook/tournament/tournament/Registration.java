package com.courtbook.tournament.tournament;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "registrations")
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_name", nullable = false)
    private String userName;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    private Integer seed;

    protected Registration() {
    }

    public Registration(long tournamentId, long userId, String userName, Instant now) {
        this.tournamentId = tournamentId;
        this.userId = userId;
        this.userName = userName;
        this.registeredAt = now;
    }

    public void setSeed(int seed) {
        this.seed = seed;
    }

    public Long getId() {
        return id;
    }

    public Long getTournamentId() {
        return tournamentId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }

    public Integer getSeed() {
        return seed;
    }
}
