import { useEffect, useMemo, useState } from "react";
import type { Slot } from "../api/schemas.ts";
import { useSession } from "../session.tsx";
import { addDays, clubToday, dayLabel, formatTime } from "../time.ts";
import { useAction, useAsync } from "../useAsync.ts";

const DAYS_AHEAD = 14;

export function BookPage() {
  const { api } = useSession();
  const today = clubToday();
  const [date, setDate] = useState(today);
  const slots = useAsync(() => api.slots(date), [api, date]);
  const action = useAction();

  // Someone else may book or cancel while this page is open.
  useEffect(() => {
    const timer = setInterval(() => void slots.reload(), 15_000);
    return () => clearInterval(timer);
  }, [slots.reload]);

  const days = useMemo(() => Array.from({ length: DAYS_AHEAD + 1 }, (_, i) => addDays(today, i)), [today]);

  // Rows are start times, columns are courts.
  const { courts, rows } = useMemo(() => {
    const list = slots.data ?? [];
    const courtNames = [...new Map(list.map((s) => [s.courtId, s.courtName])).entries()].sort((a, b) => a[0] - b[0]);
    const byTime = new Map<string, Map<number, Slot>>();
    for (const s of list) {
      if (!byTime.has(s.startsAt)) byTime.set(s.startsAt, new Map());
      byTime.get(s.startsAt)!.set(s.courtId, s);
    }
    return { courts: courtNames, rows: [...byTime.entries()] };
  }, [slots.data]);

  async function act(fn: () => Promise<unknown>) {
    if (await action.run(fn)) await slots.reload();
  }

  return (
    <section>
      <div className="page-head">
        <h2>Book a court</h2>
        <p className="muted">One-hour slots. Full? Join the waitlist and you'll get the court automatically if it frees up.</p>
      </div>

      <div className="day-strip" role="tablist">
        {days.map((d) => {
          const l = dayLabel(d);
          return (
            <button key={d} role="tab" aria-selected={d === date} className={d === date ? "day active" : "day"} onClick={() => setDate(d)}>
              <span className="day-weekday">{d === today ? "Today" : l.weekday}</span>
              <span className="day-num">{l.day}</span>
            </button>
          );
        })}
      </div>

      {action.error && (
        <p className="error" role="alert">
          {action.error}
        </p>
      )}
      {slots.error && <p className="error">{slots.error}</p>}

      <div className="grid-wrap">
        <table className="slot-grid">
          <thead>
            <tr>
              <th className="time-col">Time</th>
              {courts.map(([id, name]) => (
                <th key={id}>{name}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map(([startsAt, byCourt]) => (
              <tr key={startsAt}>
                <td className="time-col">{formatTime(startsAt)}</td>
                {courts.map(([courtId]) => {
                  const s = byCourt.get(courtId);
                  return <td key={courtId}>{s ? <SlotCell slot={s} busy={action.busy} act={act} /> : null}</td>;
                })}
              </tr>
            ))}
          </tbody>
        </table>
        {slots.loading && !slots.data && <p className="muted">Loading slots…</p>}
      </div>
    </section>
  );
}

function SlotCell({ slot, busy, act }: { slot: Slot; busy: boolean; act: (fn: () => Promise<unknown>) => void }) {
  const { api } = useSession();

  if (slot.started) {
    return <div className="cell past">{slot.status === "BOOKED" ? "Played" : "—"}</div>;
  }
  if (slot.myBookingId != null) {
    const id = slot.myBookingId;
    return (
      <div className="cell mine">
        <strong>Yours</strong>
        <button className="link" disabled={busy} onClick={() => confirm("Cancel this booking?") && act(() => api.cancelBooking(id))}>
          Cancel
        </button>
      </div>
    );
  }
  if (slot.status === "FREE") {
    return (
      <button className="cell free" disabled={busy} onClick={() => act(() => api.book(slot.id))}>
        Book
      </button>
    );
  }
  if (slot.myWaitlistPosition != null) {
    return (
      <div className="cell waiting">
        <span>#{slot.myWaitlistPosition} in line</span>
        <button className="link" disabled={busy} onClick={() => act(() => api.leaveWaitlist(slot.id))}>
          Leave
        </button>
      </div>
    );
  }
  return (
    <div className="cell booked">
      <span>Booked</span>
      <button className="link" disabled={busy} onClick={() => act(() => api.joinWaitlist(slot.id))}>
        Join waitlist{slot.waitlistCount > 0 ? ` (${slot.waitlistCount})` : ""}
      </button>
    </div>
  );
}
