package com.courtbook.tournament.tournament;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "players")
public class Player {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false)
    private int wins;

    @Column(nullable = false)
    private int losses;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Player() {
    }

    public Player(long userId, String name, Instant now) {
        this.userId = userId;
        this.name = name;
        this.rating = Elo.STARTING_RATING;
        this.updatedAt = now;
    }

    public void rename(String name, Instant now) {
        this.name = name;
        this.updatedAt = now;
    }

    public void won(int newRating, Instant now) {
        this.rating = newRating;
        this.wins++;
        this.updatedAt = now;
    }

    public void lost(int newRating, Instant now) {
        this.rating = newRating;
        this.losses++;
        this.updatedAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public int getRating() {
        return rating;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }
}
