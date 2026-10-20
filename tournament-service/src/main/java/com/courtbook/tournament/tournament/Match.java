package com.courtbook.tournament.tournament;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "matches")
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    @Column(nullable = false)
    private int round;

    @Column(nullable = false)
    private int position;

    @Column(name = "player1_id")
    private Long player1Id;

    @Column(name = "player1_name")
    private String player1Name;

    @Column(name = "player2_id")
    private Long player2Id;

    @Column(name = "player2_name")
    private String player2Name;

    @Column(name = "winner_id")
    private Long winnerId;

    private String score;

    @Column(nullable = false)
    private boolean bye;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    protected Match() {
    }

    public Match(long tournamentId, int round, int position) {
        this.tournamentId = tournamentId;
        this.round = round;
        this.position = position;
    }

    /** Puts a player into slot 1 or 2. Winners of even positions go to slot 1. */
    public void seat(int slot, long userId, String name) {
        if (slot == 1) {
            this.player1Id = userId;
            this.player1Name = name;
        } else {
            this.player2Id = userId;
            this.player2Name = name;
        }
    }

    public void recordBye(Instant now) {
        this.bye = true;
        this.winnerId = player1Id;
        this.completedAt = now;
    }

    public void recordResult(long winnerId, String score, Instant now) {
        this.winnerId = winnerId;
        this.score = score;
        this.completedAt = now;
    }

    public boolean isReady() {
        return player1Id != null && player2Id != null;
    }

    public boolean isCompleted() {
        return winnerId != null;
    }

    public boolean involves(long userId) {
        return Long.valueOf(userId).equals(player1Id) || Long.valueOf(userId).equals(player2Id);
    }

    public long loserId() {
        return winnerId.equals(player1Id) ? player2Id : player1Id;
    }

    public String nameOf(long userId) {
        return Long.valueOf(userId).equals(player1Id) ? player1Name : player2Name;
    }

    public Long getId() {
        return id;
    }

    public Long getTournamentId() {
        return tournamentId;
    }

    public int getRound() {
        return round;
    }

    public int getPosition() {
        return position;
    }

    public Long getPlayer1Id() {
        return player1Id;
    }

    public String getPlayer1Name() {
        return player1Name;
    }

    public Long getPlayer2Id() {
        return player2Id;
    }

    public String getPlayer2Name() {
        return player2Name;
    }

    public Long getWinnerId() {
        return winnerId;
    }

    public String getScore() {
        return score;
    }

    public boolean isBye() {
        return bye;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
