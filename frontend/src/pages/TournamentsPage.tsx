import { useState, type FormEvent } from "react";
import { go } from "../router.ts";
import { useSession, useUser } from "../session.tsx";
import { addDays, clubToday, formatWhen } from "../time.ts";
import { useAction, useAsync } from "../useAsync.ts";

const STATUS_LABEL = { SIGNUP: "Sign-up open", IN_PROGRESS: "In progress", COMPLETED: "Finished" } as const;

export function TournamentsPage() {
  const { api } = useSession();
  const user = useUser();
  const list = useAsync(() => api.tournaments(), [api]);

  return (
    <section>
      <div className="page-head">
        <h2>Tournaments</h2>
        <p className="muted">Single elimination, seeded by rating.</p>
      </div>
      {user.role === "ADMIN" && <CreateTournament onCreated={(id) => go(`/tournaments/${id}`)} />}
      {list.error && <p className="error">{list.error}</p>}
      {list.data?.length === 0 && <p className="muted">No tournaments yet.</p>}
      <div className="cards">
        {list.data?.map((t) => (
          <a key={t.id} className="card" href={`#/tournaments/${t.id}`}>
            <div className="card-top">
              <span className={`status status-${t.status.toLowerCase()}`}>{STATUS_LABEL[t.status]}</span>
              {t.registeredByMe && <span className="tag">you're in</span>}
            </div>
            <h3>{t.name}</h3>
            <p className="muted small">
              {t.startsOn} · {t.playerCount} players
            </p>
            {t.status === "SIGNUP" && <p className="small">Sign up by {formatWhen(t.signupDeadline)}</p>}
            {t.championName && <p className="small">🏆 {t.championName}</p>}
          </a>
        ))}
      </div>
    </section>
  );
}

function CreateTournament({ onCreated }: { onCreated: (id: number) => void }) {
  const { api } = useSession();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [startsOn, setStartsOn] = useState(addDays(clubToday(), 14));
  const [deadline, setDeadline] = useState(`${addDays(clubToday(), 10)}T22:00`);
  const action = useAction();

  async function submit(e: FormEvent) {
    e.preventDefault();
    await action.run(async () => {
      // datetime-local has no zone; the browser reads it as local time.
      const created = await api.createTournament(name, startsOn, new Date(deadline).toISOString());
      onCreated(created.tournament.id);
    });
  }

  if (!open) {
    return (
      <button className="primary" onClick={() => setOpen(true)}>
        New tournament
      </button>
    );
  }
  return (
    <form className="panel stack" onSubmit={submit}>
      <label>
        Name
        <input required maxLength={100} value={name} onChange={(e) => setName(e.target.value)} placeholder="Fall Singles" />
      </label>
      <div className="row">
        <label>
          Play date
          <input type="date" required value={startsOn} onChange={(e) => setStartsOn(e.target.value)} />
        </label>
        <label>
          Sign-up deadline
          <input type="datetime-local" required value={deadline} onChange={(e) => setDeadline(e.target.value)} />
        </label>
      </div>
      {action.error && <p className="error">{action.error}</p>}
      <div className="row">
        <button className="primary" disabled={action.busy}>
          Create
        </button>
        <button type="button" className="secondary" onClick={() => setOpen(false)}>
          Cancel
        </button>
      </div>
    </form>
  );
}
