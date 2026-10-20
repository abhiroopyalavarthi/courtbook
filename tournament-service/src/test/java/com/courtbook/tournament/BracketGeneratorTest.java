package com.courtbook.tournament;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.courtbook.tournament.tournament.BracketGenerator;
import com.courtbook.tournament.tournament.BracketGenerator.Entrant;
import com.courtbook.tournament.tournament.BracketGenerator.MatchSpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BracketGeneratorTest {

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 5, 8, 13, 32})
    void bracketHasTheRightShape(int players) {
        int size = Integer.highestOneBit(players - 1) << 1;
        List<MatchSpec> matches = BracketGenerator.generate(entrants(players));

        assertThat(matches).hasSize(size - 1); // single elimination: one loser per match
        assertThat(round(matches, 1)).hasSize(size / 2);
        assertThat(matches.stream().mapToInt(MatchSpec::round).max().orElseThrow())
                .isEqualTo(Integer.numberOfTrailingZeros(size));
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 5, 8, 13, 32})
    void everyPlayerAppearsExactlyOnceInRoundOne(int players) {
        List<Long> seen = new ArrayList<>();
        for (MatchSpec m : round(BracketGenerator.generate(entrants(players)), 1)) {
            assertThat(m.player1()).as("no match is two byes").isNotNull();
            seen.add(m.player1().userId());
            if (m.player2() != null) {
                seen.add(m.player2().userId());
            }
        }
        assertThat(seen).doesNotHaveDuplicates().hasSize(players);
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 5, 13})
    void byesGoToTheTopSeeds(int players) {
        int size = Integer.highestOneBit(players - 1) << 1;
        int byes = size - players;
        Set<Long> byeWinners = new HashSet<>();
        for (MatchSpec m : round(BracketGenerator.generate(entrants(players)), 1)) {
            if (m.isBye()) {
                byeWinners.add(m.winner().userId());
            }
        }
        // Seed s has userId s in these tests.
        Set<Long> topSeeds = new HashSet<>();
        for (long s = 1; s <= byes; s++) {
            topSeeds.add(s);
        }
        assertThat(byeWinners).isEqualTo(topSeeds);
    }

    @Test
    void byeWinnersAreAlreadySeatedInRoundTwo() {
        // 5 players in an 8 bracket: seeds 1, 2, 3 get byes.
        List<MatchSpec> matches = BracketGenerator.generate(entrants(5));
        List<Long> seatedInRoundTwo = new ArrayList<>();
        for (MatchSpec m : round(matches, 2)) {
            if (m.player1() != null) {
                seatedInRoundTwo.add(m.player1().userId());
            }
            if (m.player2() != null) {
                seatedInRoundTwo.add(m.player2().userId());
            }
        }
        assertThat(seatedInRoundTwo).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void eightPlayersUseStandardSeeding() {
        List<MatchSpec> first = round(BracketGenerator.generate(entrants(8)), 1);
        assertThat(first).extracting(m -> m.player1().userId() + "v" + m.player2().userId())
                .containsExactly("1v8", "4v5", "2v7", "3v6");
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 8, 13, 32})
    void topTwoSeedsAreInOppositeHalves(int players) {
        List<MatchSpec> first = round(BracketGenerator.generate(entrants(players)), 1);
        int half = first.size() / 2;
        int seedOneMatch = indexOf(first, 1);
        int seedTwoMatch = indexOf(first, 2);
        assertThat(seedOneMatch < half).isNotEqualTo(seedTwoMatch < half);
    }

    @Test
    void needsAtLeastTwoPlayers() {
        assertThatThrownBy(() -> BracketGenerator.generate(entrants(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundsForPlayerCounts() {
        assertThat(BracketGenerator.rounds(2)).isEqualTo(1);
        assertThat(BracketGenerator.rounds(5)).isEqualTo(3);
        assertThat(BracketGenerator.rounds(8)).isEqualTo(3);
        assertThat(BracketGenerator.rounds(13)).isEqualTo(4);
        assertThat(BracketGenerator.rounds(32)).isEqualTo(5);
    }

    private static List<Entrant> entrants(int n) {
        return IntStream.rangeClosed(1, n).mapToObj(i -> new Entrant(i, "Seed " + i)).toList();
    }

    private static List<MatchSpec> round(List<MatchSpec> matches, int round) {
        return matches.stream().filter(m -> m.round() == round).toList();
    }

    private static int indexOf(List<MatchSpec> matches, long userId) {
        for (int i = 0; i < matches.size(); i++) {
            MatchSpec m = matches.get(i);
            if ((m.player1() != null && m.player1().userId() == userId)
                    || (m.player2() != null && m.player2().userId() == userId)) {
                return i;
            }
        }
        throw new AssertionError("seed " + userId + " not found");
    }
}
