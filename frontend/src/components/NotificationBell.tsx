import { useEffect, useRef, useState } from "react";
import type { NotificationPage } from "../api/schemas.ts";
import { useSession } from "../session.tsx";
import { timeAgo } from "../time.ts";

const POLL_MS = 5_000;

export function NotificationBell() {
  const { api } = useSession();
  const [page, setPage] = useState<NotificationPage | null>(null);
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let alive = true;
    const load = () =>
      api
        .notifications()
        .then((p) => alive && setPage(p))
        .catch(() => {
          // the bell is best-effort; the rest of the page still works
        });
    void load();
    const timer = setInterval(load, POLL_MS);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [api]);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", close);
    return () => document.removeEventListener("mousedown", close);
  }, [open]);

  async function toggle() {
    const opening = !open;
    setOpen(opening);
    if (opening && page && page.unreadCount > 0) {
      await api.markAllRead().catch(() => {});
      setPage({ ...page, unreadCount: 0 });
    }
  }

  const unread = page?.unreadCount ?? 0;
  return (
    <div className="bell" ref={ref}>
      <button className="bell-button" onClick={toggle} aria-label={`Notifications, ${unread} unread`}>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
          <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
          <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        </svg>
        {unread > 0 && <span className="badge">{unread > 9 ? "9+" : unread}</span>}
      </button>
      {open && (
        <div className="bell-panel">
          {page?.items.length ? (
            <ul>
              {page.items.map((n) => (
                <li key={n.id} className={n.read ? undefined : "unread"}>
                  <strong>{n.title}</strong>
                  <span>{n.body}</span>
                  <time className="muted small">{timeAgo(n.createdAt)}</time>
                </li>
              ))}
            </ul>
          ) : (
            <p className="muted small">No notifications yet.</p>
          )}
        </div>
      )}
    </div>
  );
}
