package com.courtbook.tournament.tournament;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tournaments")
public class Tournament {

    public enum Status { SIGNUP, IN_PROGRESS, COMPLETED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "signup_deadline", nullable = false)
    private Instant signupDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "champion_user_id")
    private Long championUserId;

    @Version
    private long version;

    protected Tournament() {
    }

    public Tournament(String name, LocalDate startsOn, Instant signupDeadline, long createdBy, Instant now) {
        this.name = name;
        this.startsOn = startsOn;
        this.signupDeadline = signupDeadline;
        this.createdBy = createdBy;
        this.status = Status.SIGNUP;
        this.createdAt = now;
    }

    public boolean signupOpen(Instant now) {
        return status == Status.SIGNUP && now.isBefore(signupDeadline);
    }

    public void start() {
        this.status = Status.IN_PROGRESS;
    }

    public void complete(long championUserId) {
        this.status = Status.COMPLETED;
        this.championUserId = championUserId;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public Instant getSignupDeadline() {
        return signupDeadline;
    }

    public Status getStatus() {
        return status;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getChampionUserId() {
        return championUserId;
    }
}
