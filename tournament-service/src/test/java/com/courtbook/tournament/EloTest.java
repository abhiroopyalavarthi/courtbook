package com.courtbook.tournament;

import static org.assertj.core.api.Assertions.assertThat;

import com.courtbook.tournament.tournament.Elo;
import org.junit.jupiter.api.Test;

class EloTest {

    @Test
    void evenMatchMovesSixteenPoints() {
        Elo.Result r = Elo.afterMatch(1200, 1200);
        assertThat(r.winnerRating()).isEqualTo(1216);
        assertThat(r.loserRating()).isEqualTo(1184);
    }

    @Test
    void upsetsMoveMoreThanExpectedWins() {
        Elo.Result upset = Elo.afterMatch(1100, 1400);
        Elo.Result expected = Elo.afterMatch(1400, 1100);
        assertThat(upset.winnerRating() - 1100).isGreaterThan(expected.winnerRating() - 1400);
    }

    @Test
    void pointsAreConserved() {
        Elo.Result r = Elo.afterMatch(1337, 1250);
        assertThat(r.winnerRating() + r.loserRating()).isEqualTo(1337 + 1250);
    }
}
