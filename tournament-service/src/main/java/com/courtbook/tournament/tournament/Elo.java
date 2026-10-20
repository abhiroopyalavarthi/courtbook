package com.courtbook.tournament.tournament;

/**
 * Elo ratings: beating a stronger player gains more than beating a weaker
 * one, and the loser drops by the same amount the winner gains.
 */
public final class Elo {

    public static final int STARTING_RATING = 1200;
    static final int K = 32;

    private Elo() {
    }

    public record Result(int winnerRating, int loserRating) {
    }

    public static Result afterMatch(int winnerRating, int loserRating) {
        double expectedWin = 1.0 / (1.0 + Math.pow(10, (loserRating - winnerRating) / 400.0));
        int change = (int) Math.round(K * (1.0 - expectedWin));
        return new Result(winnerRating + change, loserRating - change);
    }
}
