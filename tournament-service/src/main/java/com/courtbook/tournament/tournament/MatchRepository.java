package com.courtbook.tournament.tournament;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByTournamentIdOrderByRoundAscPositionAsc(long tournamentId);

    Optional<Match> findByTournamentIdAndRoundAndPosition(long tournamentId, int round, int position);

    long countByCompletedAtIsNotNullAndByeFalse();
}
