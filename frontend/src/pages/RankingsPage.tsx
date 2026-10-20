import { useSession, useUser } from "../session.tsx";
import { useAsync } from "../useAsync.ts";

export function RankingsPage() {
  const { api } = useSession();
  const me = useUser();
  const rankings = useAsync(() => api.rankings(), [api]);

  return (
    <section>
      <div className="page-head">
        <h2>Rankings</h2>
        <p className="muted">Elo ratings from tournament matches. Everyone starts at 1200.</p>
      </div>
      {rankings.error && <p className="error">{rankings.error}</p>}
      {rankings.data?.length === 0 && <p className="muted">No matches played yet.</p>}
      {rankings.data && rankings.data.length > 0 && (
        <table className="table">
          <thead>
            <tr>
              <th>#</th>
              <th>Player</th>
              <th className="num">Rating</th>
              <th className="num">W–L</th>
            </tr>
          </thead>
          <tbody>
            {rankings.data.map((r) => (
              <tr key={r.userId} className={r.userId === me.id ? "me" : undefined}>
                <td>{r.rank}</td>
                <td>{r.name}</td>
                <td className="num">{r.rating}</td>
                <td className="num">
                  {r.wins}–{r.losses}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
