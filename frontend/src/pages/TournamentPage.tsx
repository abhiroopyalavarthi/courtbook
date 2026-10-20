import { useState } from "react";
import type { Match } from "../api/schemas.ts";
import { useSession, useUser } from "../session.tsx";
import { formatWhen } from "../time.ts";
import { useAction, useAsync } from "../useAsync.ts";

export function TournamentPage({ id }: { id: number }) {
  const { api } = useSession();
  const user = useUser();
  const isAdmin = user.role === "ADMIN";
  const detail = useAsync(() => api.tournament(id), [api, id]);
  const action = useAction();

  async function act(fn: () => Promise<unknown>) {
    if (await action.run(fn)) await detail.reload();
  }

  if (detail.error) return <p className="error">{detail.error}</p>;
  if (!detail.data) return <p className="muted">Loading…</p>;
  const { tournament: t, players, rounds } = detail.data;

  return (
    <section>
      <p>
        <a href="#/tournaments">← All tournaments</a>
      </p>
      <div className="page-head">
        <h2>{t.name}</h2>
        <p className="muted">
          {t.startsOn} · {t.playerCount} players
          {t.status === "SIGNUP" && <> · sign up by {formatWhen(t.signupDeadline)}</>}
        </p>
      </div>
      {t.championName && <p className="champion">🏆 Champion: {t.championName}</p>}
      {action.error && <p className="error">{action.error}</p>}

      <div className="row">
        {t.signupOpen && !t.registeredByMe && (
          <button className="primary" disabled={action.busy} onClick={() => act(() => api.signUp(id))}>
            Sign up
          </button>
        )}
        {t.status === "SIGNUP" && t.registeredByMe && (
          <button className="secondary" disabled={action.busy} onClick={() => act(() => api.withdraw(id))}>
            Withdraw
          </button>
        )}
        {isAdmin && t.status === "SIGNUP" && (
          <button
            className="primary"
            disabled={action.busy || t.playerCount < 2}
            onClick={() => confirm("Close sign-up and draw the bracket?") && act(() => api.startTournament(id))}
          >
            Draw bracket
          </button>
        )}
      </div>

      {rounds.length > 0 ? (
        <div className="bracket">
          {rounds.map((r) => (
            <div key={r.round} className="bracket-round">
              <h4>{r.name}</h4>
              <div className="bracket-matches">
                {r.matches.map((m) => (
                  <MatchCard key={m.id} match={m} canRecord={isAdmin && t.status === "IN_PROGRESS"} busy={action.busy} act={act} />
                ))}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <>
          <h3>Players</h3>
          {players.length === 0 ? (
            <p className="muted">Nobody yet. Be the first.</p>
          ) : (
            <ul className="list">
              {players.map((p) => (
                <li key={p.userId} className="list-row">
                  <span>{p.name}</span>
                  <span className="muted small">rating {p.rating}</span>
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  );
}

function MatchCard({ match: m, canRecord, busy, act }: { match: Match; canRecord: boolean; busy: boolean; act: (fn: () => Promise<unknown>) => void }) {
  const { api } = useSession();
  const [score, setScore] = useState("");

  const line = (playerId: number | null | undefined, name: string | null | undefined) => {
    const won = m.winnerId != null && m.winnerId === playerId;
    return (
      <div className={`match-player${won ? " won" : ""}${playerId == null ? " empty" : ""}`}>
        <span>{name ?? (m.bye && playerId == null ? "bye" : "—")}</span>
        {canRecord && m.status === "READY" && playerId != null && (
          <button className="link small" disabled={busy} onClick={() => act(() => api.recordResult(m.id, playerId, score))}>
            won
          </button>
        )}
      </div>
    );
  };

  return (
    <div className={`match match-${m.status.toLowerCase()}`}>
      {line(m.player1Id, m.player1Name)}
      {line(m.player2Id, m.player2Name)}
      {m.score && <div className="match-score">{m.score}</div>}
      {canRecord && m.status === "READY" && (
        <input className="score-input" placeholder="Score, e.g. 21-18, 21-15" value={score} onChange={(e) => setScore(e.target.value)} />
      )}
    </div>
  );
}
