package com.courtbook.tournament.tournament;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    List<Player> findAllByOrderByRatingDescWinsDescNameAsc();
}
