package com.courtbook.tournament.tournament;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    List<Registration> findByTournamentIdOrderByRegisteredAtAscIdAsc(long tournamentId);

    Optional<Registration> findByTournamentIdAndUserId(long tournamentId, long userId);

    long countByTournamentId(long tournamentId);

    @Query("select count(distinct r.userId) from Registration r")
    long countDistinctPlayers();
}
