package com.courtbook.tournament.tournament;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request and response bodies for the tournament service's REST API. */
public final class TournamentDtos {

    private TournamentDtos() {
    }

    public record CreateTournamentRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull LocalDate startsOn,
            @NotNull Instant signupDeadline) {
    }

    public record RecordResultRequest(@NotNull Long winnerId, @Size(max = 64) String score) {
    }

    public record TournamentSummary(
            long id,
            String name,
            LocalDate startsOn,
            Instant signupDeadline,
            String status,
            boolean signupOpen,
            long playerCount,
            boolean registeredByMe,
            Long championId,
            String championName) {
    }

    public record RegistrationView(long userId, String name, Integer seed, int rating) {
    }

    public record MatchView(
            long id,
            int round,
            int position,
            Long player1Id,
            String player1Name,
            Long player2Id,
            String player2Name,
            Long winnerId,
            String score,
            boolean bye,
            /** WAITING (a player still to come), READY, or COMPLETED. */
            String status) {
    }

    public record RoundView(int round, String name, List<MatchView> matches) {
    }

    public record TournamentDetail(
            TournamentSummary tournament,
            List<RegistrationView> players,
            List<RoundView> rounds) {
    }

    public record RankingView(int rank, long userId, String name, int rating, int wins, int losses) {
    }

    public record TournamentStats(long completed, long inProgress, long openForSignup, long matchesPlayed,
            long playersEntered) {
    }
}
