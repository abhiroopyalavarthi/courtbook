import { useSession } from "../session.tsx";
import { timeAgo } from "../time.ts";
import { useAsync } from "../useAsync.ts";

export function FeedPage() {
  const { api } = useSession();
  const feed = useAsync(() => api.feed(), [api]);

  return (
    <section>
      <div className="page-head">
        <h2>Club feed</h2>
        <p className="muted">Results and tournament news.</p>
      </div>
      {feed.error && <p className="error">{feed.error}</p>}
      {feed.data?.length === 0 && <p className="muted">Nothing here yet. Results show up as matches are played.</p>}
      <ul className="feed">
        {feed.data?.map((f) => (
          <li key={f.id}>
            <span>{f.text}</span>
            <time className="muted small">{timeAgo(f.createdAt)}</time>
          </li>
        ))}
      </ul>
    </section>
  );
}
