package com.courtbook.tournament.tournament;

import com.courtbook.tournament.events.EventRecorder;
import com.courtbook.tournament.tournament.BracketGenerator.Entrant;
import com.courtbook.tournament.tournament.BracketGenerator.MatchSpec;
import com.courtbook.tournament.tournament.TournamentDtos.CreateTournamentRequest;
import com.courtbook.tournament.tournament.TournamentDtos.MatchView;
import com.courtbook.tournament.tournament.TournamentDtos.RankingView;
import com.courtbook.tournament.tournament.TournamentDtos.RegistrationView;
import com.courtbook.tournament.tournament.TournamentDtos.RoundView;
import com.courtbook.tournament.tournament.TournamentDtos.TournamentDetail;
import com.courtbook.tournament.tournament.TournamentDtos.TournamentStats;
import com.courtbook.tournament.tournament.TournamentDtos.TournamentSummary;
import com.courtbook.tournament.web.ApiException;
import com.courtbook.tournament.web.CurrentUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TournamentService {

    private final TournamentRepository tournaments;
    private final RegistrationRepository registrations;
    private final MatchRepository matches;
    private final PlayerRepository players;
    private final EventRecorder events;

    public TournamentService(TournamentRepository tournaments, RegistrationRepository registrations,
            MatchRepository matches, PlayerRepository players, EventRecorder events) {
        this.tournaments = tournaments;
        this.registrations = registrations;
        this.matches = matches;
        this.players = players;
        this.events = events;
    }

    @Transactional
    public Tournament create(CurrentUser admin, CreateTournamentRequest req) {
        Instant now = now();
        if (!req.signupDeadline().isAfter(now)) {
            throw ApiException.badRequest("The sign-up deadline must be in the future");
        }
        return tournaments.save(new Tournament(req.name().trim(), req.startsOn(), req.signupDeadline(), admin.id(), now));
    }

    @Transactional(readOnly = true)
    public List<TournamentSummary> list(long viewerId) {
        Instant now = now();
        return tournaments.findAllByOrderByStartsOnDescIdDesc().stream()
                .map(t -> summary(t, viewerId, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public TournamentDetail detail(long id, long viewerId) {
        Tournament t = find(id);
        Map<Long, Integer> ratings = players.findAll().stream()
                .collect(Collectors.toMap(Player::getUserId, Player::getRating));
        List<RegistrationView> entrants = registrations.findByTournamentIdOrderByRegisteredAtAscIdAsc(id).stream()
                .map(r -> new RegistrationView(r.getUserId(), r.getUserName(), r.getSeed(),
                        ratings.getOrDefault(r.getUserId(), Elo.STARTING_RATING)))
                .sorted(Comparator.comparing((RegistrationView r) -> r.seed() == null ? Integer.MAX_VALUE : r.seed()))
                .toList();

        Map<Integer, List<MatchView>> byRound = new TreeMap<>();
        for (Match m : matches.findByTournamentIdOrderByRoundAscPositionAsc(id)) {
            byRound.computeIfAbsent(m.getRound(), r -> new ArrayList<>()).add(matchView(m));
        }
        int totalRounds = byRound.size();
        List<RoundView> rounds = byRound.entrySet().stream()
                .map(e -> new RoundView(e.getKey(), roundName(e.getKey(), totalRounds), e.getValue()))
                .toList();
        return new TournamentDetail(summary(t, viewerId, now()), entrants, rounds);
    }

    @Transactional
    public void register(CurrentUser user, long tournamentId) {
        Instant now = now();
        Tournament t = find(tournamentId);
        if (!t.signupOpen(now)) {
            throw ApiException.conflict("Sign-up for this tournament is closed");
        }
        if (registrations.findByTournamentIdAndUserId(tournamentId, user.id()).isPresent()) {
            throw ApiException.conflict("You're already signed up");
        }
        try {
            registrations.saveAndFlush(new Registration(tournamentId, user.id(), user.name(), now));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("You're already signed up");
        }
        players.findById(user.id()).ifPresentOrElse(
                p -> p.rename(user.name(), now),
                () -> players.save(new Player(user.id(), user.name(), now)));
    }

    @Transactional
    public void withdraw(CurrentUser user, long tournamentId) {
        Tournament t = find(tournamentId);
        if (t.getStatus() != Tournament.Status.SIGNUP) {
            throw ApiException.conflict("The bracket is already drawn");
        }
        Registration r = registrations.findByTournamentIdAndUserId(tournamentId, user.id())
                .orElseThrow(() -> ApiException.notFound("You're not signed up for this tournament"));
        registrations.delete(r);
    }

    /** Seeds players by rating (ties go to whoever signed up first) and draws the bracket. */
    @Transactional
    public void start(long tournamentId) {
        Instant now = now();
        Tournament t = find(tournamentId);
        if (t.getStatus() != Tournament.Status.SIGNUP) {
            throw ApiException.conflict("This tournament has already started");
        }
        List<Registration> regs = registrations.findByTournamentIdOrderByRegisteredAtAscIdAsc(tournamentId);
        if (regs.size() < 2) {
            throw ApiException.conflict("A tournament needs at least 2 players");
        }
        Map<Long, Integer> ratings = players.findAllById(regs.stream().map(Registration::getUserId).toList())
                .stream().collect(Collectors.toMap(Player::getUserId, Player::getRating));
        // Stable sort, so equal ratings keep sign-up order.
        List<Registration> seeded = regs.stream()
                .sorted(Comparator.comparing((Registration r) -> ratings.getOrDefault(r.getUserId(),
                        Elo.STARTING_RATING)).reversed())
                .toList();
        List<Entrant> entrants = new ArrayList<>();
        for (int i = 0; i < seeded.size(); i++) {
            seeded.get(i).setSeed(i + 1);
            entrants.add(new Entrant(seeded.get(i).getUserId(), seeded.get(i).getUserName()));
        }

        for (MatchSpec spec : BracketGenerator.generate(entrants)) {
            Match m = new Match(tournamentId, spec.round(), spec.position());
            if (spec.player1() != null) {
                m.seat(1, spec.player1().userId(), spec.player1().name());
            }
            if (spec.player2() != null) {
                m.seat(2, spec.player2().userId(), spec.player2().name());
            }
            if (spec.isBye()) {
                m.recordBye(now);
            }
            matches.save(m);
        }
        t.start();
        tournaments.saveAndFlush(t);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tournamentId", t.getId());
        payload.put("tournamentName", t.getName());
        payload.put("playerIds", seeded.stream().map(Registration::getUserId).toList());
        events.record("TournamentStarted", payload);
    }

    /**
     * Records a result, moves the winner into their next match, updates both
     * players' ratings, and finishes the tournament after the final.
     */
    @Transactional
    public MatchView recordResult(long matchId, long winnerId, String score) {
        Instant now = now();
        Match match = matches.findById(matchId).orElseThrow(() -> ApiException.notFound("No match with id " + matchId));
        Tournament t = find(match.getTournamentId());
        if (t.getStatus() != Tournament.Status.IN_PROGRESS) {
            throw ApiException.conflict("This tournament isn't in progress");
        }
        if (match.isCompleted()) {
            throw ApiException.conflict("This match already has a result");
        }
        if (!match.isReady()) {
            throw ApiException.conflict("This match is still waiting for a player");
        }
        if (!match.involves(winnerId)) {
            throw ApiException.badRequest("The winner must be one of the two players in this match");
        }
        match.recordResult(winnerId, score == null ? null : score.trim(), now);
        long loserId = match.loserId();

        Player winner = player(winnerId, match.nameOf(winnerId), now);
        Player loser = player(loserId, match.nameOf(loserId), now);
        Elo.Result elo = Elo.afterMatch(winner.getRating(), loser.getRating());
        winner.won(elo.winnerRating(), now);
        loser.lost(elo.loserRating(), now);

        int totalRounds = BracketGenerator.rounds((int) registrations.countByTournamentId(t.getId()));
        if (match.getRound() < totalRounds) {
            Match next = matches.findByTournamentIdAndRoundAndPosition(t.getId(), match.getRound() + 1,
                    match.getPosition() / 2).orElseThrow();
            next.seat(match.getPosition() % 2 == 0 ? 1 : 2, winnerId, match.nameOf(winnerId));
        } else {
            t.complete(winnerId);
        }
        matches.flush();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tournamentId", t.getId());
        payload.put("tournamentName", t.getName());
        payload.put("matchId", match.getId());
        payload.put("round", match.getRound());
        payload.put("roundName", roundName(match.getRound(), totalRounds));
        payload.put("winnerId", winnerId);
        payload.put("winnerName", match.nameOf(winnerId));
        payload.put("loserId", loserId);
        payload.put("loserName", match.nameOf(loserId));
        payload.put("score", match.getScore());
        payload.put("winnerRating", elo.winnerRating());
        payload.put("loserRating", elo.loserRating());
        events.record("MatchResultRecorded", payload);

        if (t.getStatus() == Tournament.Status.COMPLETED) {
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("tournamentId", t.getId());
            done.put("tournamentName", t.getName());
            done.put("championId", winnerId);
            done.put("championName", match.nameOf(winnerId));
            events.record("TournamentCompleted", done);
        }
        return matchView(match);
    }

    @Transactional(readOnly = true)
    public List<RankingView> rankings() {
        List<Player> all = players.findAllByOrderByRatingDescWinsDescNameAsc();
        List<RankingView> out = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Player p = all.get(i);
            out.add(new RankingView(i + 1, p.getUserId(), p.getName(), p.getRating(), p.getWins(), p.getLosses()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public TournamentStats stats() {
        return new TournamentStats(
                tournaments.countByStatus(Tournament.Status.COMPLETED),
                tournaments.countByStatus(Tournament.Status.IN_PROGRESS),
                tournaments.countByStatus(Tournament.Status.SIGNUP),
                matches.countByCompletedAtIsNotNullAndByeFalse(),
                registrations.countDistinctPlayers());
    }

    static String roundName(int round, int totalRounds) {
        int fromEnd = totalRounds - round;
        return switch (fromEnd) {
            case 0 -> "Final";
            case 1 -> "Semifinals";
            case 2 -> "Quarterfinals";
            default -> "Round " + round;
        };
    }

    private TournamentSummary summary(Tournament t, long viewerId, Instant now) {
        long count = registrations.countByTournamentId(t.getId());
        boolean mine = registrations.findByTournamentIdAndUserId(t.getId(), viewerId).isPresent();
        String championName = t.getChampionUserId() == null ? null
                : players.findById(t.getChampionUserId()).map(Player::getName).orElse(null);
        return new TournamentSummary(t.getId(), t.getName(), t.getStartsOn(), t.getSignupDeadline(),
                t.getStatus().name(), t.signupOpen(now), count, mine, t.getChampionUserId(), championName);
    }

    private static MatchView matchView(Match m) {
        String status = m.isCompleted() ? "COMPLETED" : m.isReady() ? "READY" : "WAITING";
        return new MatchView(m.getId(), m.getRound(), m.getPosition(), m.getPlayer1Id(), m.getPlayer1Name(),
                m.getPlayer2Id(), m.getPlayer2Name(), m.getWinnerId(), m.getScore(), m.isBye(), status);
    }

    private Tournament find(long id) {
        return tournaments.findById(id).orElseThrow(() -> ApiException.notFound("No tournament with id " + id));
    }

    private Player player(long userId, String name, Instant now) {
        return players.findById(userId).orElseGet(() -> players.save(new Player(userId, name, now)));
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
