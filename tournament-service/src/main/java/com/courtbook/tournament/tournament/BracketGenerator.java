package com.courtbook.tournament.tournament;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a seeded single-elimination bracket. No Spring, no database, so the
 * logic is unit tested on its own.
 *
 * <p>With n players the bracket has the next power of two slots. The missing
 * players are "byes", and standard seeding (1 v 16, 8 v 9, ...) puts them
 * against the top seeds, so seed 1 gets the first bye, seed 2 the second, and
 * so on. Seeds 1 and 2 can only meet in the final.
 */
public final class BracketGenerator {

    private BracketGenerator() {
    }

    /** A player in seed order (index 0 = seed 1). */
    public record Entrant(long userId, String name) {
    }

    /**
     * One match. Later-round matches start empty and fill as winners advance.
     * For a bye, player2 is null and the winner is already set.
     */
    public record MatchSpec(int round, int position, Entrant player1, Entrant player2, Entrant winner) {
        public boolean isBye() {
            return winner != null && (player1 == null || player2 == null);
        }
    }

    public static List<MatchSpec> generate(List<Entrant> seeded) {
        int n = seeded.size();
        if (n < 2) {
            throw new IllegalArgumentException("A tournament needs at least 2 players");
        }
        int size = Integer.highestOneBit(n - 1) << 1; // next power of two >= n
        int rounds = Integer.numberOfTrailingZeros(size);
        int[] order = seedOrder(size);

        List<MatchSpec> matches = new ArrayList<>();
        Entrant[] advancing = new Entrant[size / 2];
        for (int p = 0; p < size / 2; p++) {
            Entrant a = entrant(seeded, order[2 * p]);
            Entrant b = entrant(seeded, order[2 * p + 1]);
            // Seeds above n don't exist. Standard seeding never pairs two of them.
            if (a == null) {
                a = b;
                b = null;
            }
            Entrant winner = b == null ? a : null;
            matches.add(new MatchSpec(1, p, a, b, winner));
            advancing[p] = winner;
        }

        for (int round = 2; round <= rounds; round++) {
            int count = size >> round;
            for (int p = 0; p < count; p++) {
                // Byes only happen in round 1, so round 2 is where bye winners land.
                Entrant p1 = round == 2 ? advancing[2 * p] : null;
                Entrant p2 = round == 2 ? advancing[2 * p + 1] : null;
                matches.add(new MatchSpec(round, p, p1, p2, null));
            }
        }
        return matches;
    }

    /** Number of rounds for n players. */
    public static int rounds(int players) {
        int size = Integer.highestOneBit(Math.max(players, 2) - 1) << 1;
        return Integer.numberOfTrailingZeros(size);
    }

    /**
     * Seeds in bracket order. For 8: [1, 8, 4, 5, 2, 7, 3, 6], read in pairs:
     * 1v8, 4v5, 2v7, 3v6. Built by repeatedly pairing each seed s with
     * (2 * current size + 1 - s).
     */
    static int[] seedOrder(int size) {
        int[] order = {1};
        while (order.length < size) {
            int len = order.length * 2;
            int[] next = new int[len];
            for (int i = 0; i < order.length; i++) {
                next[2 * i] = order[i];
                next[2 * i + 1] = len + 1 - order[i];
            }
            order = next;
        }
        return order;
    }

    private static Entrant entrant(List<Entrant> seeded, int seed) {
        return seed <= seeded.size() ? seeded.get(seed - 1) : null;
    }
}
