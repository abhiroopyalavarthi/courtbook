package com.courtbook.tournament.tournament;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    List<Tournament> findAllByOrderByStartsOnDescIdDesc();

    long countByStatus(Tournament.Status status);
}
