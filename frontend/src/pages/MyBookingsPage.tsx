import type { Booking } from "../api/schemas.ts";
import { useSession } from "../session.tsx";
import { formatTime, formatWhen } from "../time.ts";
import { useAction, useAsync } from "../useAsync.ts";

export function MyBookingsPage() {
  const { api } = useSession();
  const bookings = useAsync(() => api.myBookings(), [api]);
  const action = useAction();

  const now = Date.now();
  const all = bookings.data ?? [];
  const upcoming = all
    .filter((b) => b.status === "ACTIVE" && new Date(b.startsAt).getTime() > now)
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  const history = all.filter((b) => !upcoming.includes(b));

  async function cancel(b: Booking) {
    if (!confirm(`Cancel ${b.courtName} on ${formatWhen(b.startsAt)}?`)) return;
    if (await action.run(() => api.cancelBooking(b.id))) await bookings.reload();
  }

  return (
    <section>
      <div className="page-head">
        <h2>My bookings</h2>
      </div>
      {action.error && <p className="error">{action.error}</p>}
      {bookings.error && <p className="error">{bookings.error}</p>}

      <h3>Upcoming</h3>
      {upcoming.length === 0 ? (
        <p className="muted">
          Nothing booked. <a href="#/book">Find a court</a>.
        </p>
      ) : (
        <ul className="list">
          {upcoming.map((b) => (
            <li key={b.id} className="list-row">
              <div>
                <strong>{b.courtName}</strong> · {formatWhen(b.startsAt)}–{formatTime(b.endsAt)}
                {b.source === "WAITLIST" && <span className="tag">from waitlist</span>}
              </div>
              <button className="secondary" disabled={action.busy} onClick={() => cancel(b)}>
                Cancel
              </button>
            </li>
          ))}
        </ul>
      )}

      <h3>History</h3>
      {history.length === 0 ? (
        <p className="muted">No past bookings yet.</p>
      ) : (
        <ul className="list">
          {history.map((b) => (
            <li key={b.id} className="list-row dim">
              <div>
                {b.courtName} · {formatWhen(b.startsAt)}
              </div>
              <span className="tag">{b.status === "CANCELLED" ? "cancelled" : "played"}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
