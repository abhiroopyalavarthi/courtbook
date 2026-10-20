import { useSession, useUser } from "../session.tsx";
import { formatDay } from "../time.ts";
import { useAction, useAsync } from "../useAsync.ts";

/** Usage numbers for the README and resume, plus member role management. */
export function AdminPage() {
  const { api } = useSession();
  const me = useUser();
  const stats = useAsync(
    async () => {
      const [users, bookings, tournaments] = await Promise.all([api.userStats(), api.bookingStats(), api.tournamentStats()]);
      return { users, bookings, tournaments };
    },
    [api],
  );
  const users = useAsync(() => api.allUsers(), [api]);
  const action = useAction();

  return (
    <section>
      <div className="page-head">
        <h2>Admin</h2>
        <p className="muted">Real usage, straight from the services.</p>
      </div>
      {stats.error && <p className="error">{stats.error}</p>}
      {stats.data && (
        <div className="stats">
          <Stat label="Members" value={stats.data.users.members + stats.data.users.admins} note={`${stats.data.users.joinedLast7Days} joined this week`} />
          <Stat label="Bookings made" value={stats.data.bookings.totalBookings} note={`${stats.data.bookings.upcomingActive} upcoming`} />
          <Stat label="Members who booked" value={stats.data.bookings.membersWhoBooked} />
          <Stat label="Waitlist promotions" value={stats.data.bookings.promotedFromWaitlist} note={`${stats.data.bookings.cancelled} cancellations`} />
          <Stat label="Tournaments run" value={stats.data.tournaments.completed} note={`${stats.data.tournaments.inProgress} in progress`} />
          <Stat label="Matches played" value={stats.data.tournaments.matchesPlayed} note={`${stats.data.tournaments.playersEntered} players entered`} />
        </div>
      )}

      <h3>Members</h3>
      {action.error && <p className="error">{action.error}</p>}
      {users.data && (
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Joined</th>
              <th>Role</th>
            </tr>
          </thead>
          <tbody>
            {users.data.map((u) => (
              <tr key={u.id}>
                <td>{u.name}</td>
                <td>{u.email}</td>
                <td>{formatDay(u.createdAt)}</td>
                <td>
                  {u.id === me.id ? (
                    u.role.toLowerCase()
                  ) : (
                    <select
                      value={u.role}
                      disabled={action.busy}
                      onChange={async (e) => {
                        const role = e.target.value as "MEMBER" | "ADMIN";
                        if (await action.run(() => api.setRole(u.id, role))) await users.reload();
                      }}
                    >
                      <option value="MEMBER">member</option>
                      <option value="ADMIN">admin</option>
                    </select>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function Stat({ label, value, note }: { label: string; value: number; note?: string }) {
  return (
    <div className="stat">
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
      {note && <div className="muted small">{note}</div>}
    </div>
  );
}
