package com.courtbook.tournament.tournament;

import com.courtbook.tournament.tournament.TournamentDtos.CreateTournamentRequest;
import com.courtbook.tournament.tournament.TournamentDtos.MatchView;
import com.courtbook.tournament.tournament.TournamentDtos.RankingView;
import com.courtbook.tournament.tournament.TournamentDtos.RecordResultRequest;
import com.courtbook.tournament.tournament.TournamentDtos.TournamentDetail;
import com.courtbook.tournament.tournament.TournamentDtos.TournamentStats;
import com.courtbook.tournament.tournament.TournamentDtos.TournamentSummary;
import com.courtbook.tournament.web.ApiException;
import com.courtbook.tournament.web.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TournamentController {

    private final TournamentService service;

    public TournamentController(TournamentService service) {
        this.service = service;
    }

    @GetMapping("/v1/tournaments")
    public List<TournamentSummary> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.from(jwt).id());
    }

    @PostMapping("/v1/tournaments")
    public ResponseEntity<TournamentDetail> create(@Valid @RequestBody CreateTournamentRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = requireAdmin(jwt);
        Tournament t = service.create(user, req);
        return ResponseEntity.created(URI.create("/v1/tournaments/" + t.getId()))
                .body(service.detail(t.getId(), user.id()));
    }

    @GetMapping("/v1/tournaments/{id}")
    public TournamentDetail detail(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        return service.detail(id, CurrentUser.from(jwt).id());
    }

    @PostMapping("/v1/tournaments/{id}/registrations")
    public ResponseEntity<TournamentDetail> register(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        service.register(user, id);
        return ResponseEntity.status(201).body(service.detail(id, user.id()));
    }

    @DeleteMapping("/v1/tournaments/{id}/registrations/me")
    public ResponseEntity<Void> withdraw(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        service.withdraw(CurrentUser.from(jwt), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/v1/tournaments/{id}/start")
    public TournamentDetail start(@PathVariable long id, @AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = requireAdmin(jwt);
        service.start(id);
        return service.detail(id, user.id());
    }

    @PostMapping("/v1/matches/{id}/result")
    public MatchView recordResult(@PathVariable long id, @Valid @RequestBody RecordResultRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        requireAdmin(jwt);
        return service.recordResult(id, req.winnerId(), req.score());
    }

    @GetMapping("/v1/rankings")
    public List<RankingView> rankings() {
        return service.rankings();
    }

    @GetMapping("/v1/admin/stats/tournaments")
    public TournamentStats stats() {
        return service.stats();
    }

    private static CurrentUser requireAdmin(Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        if (!user.admin()) {
            throw ApiException.forbidden("Only club admins can do this");
        }
        return user;
    }
}
