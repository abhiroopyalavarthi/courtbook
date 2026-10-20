// End-to-end test against the whole app running in Docker Compose.
//
//   docker compose -f deploy/docker-compose.yml up -d --build --wait
//   cd e2e && npm ci && npm run journey
//
// It uses the frontend's own API client, so every response is parsed with the
// frontend's zod schemas. A field renamed in any service fails this run: that
// is the contract check between the frontend and the services.

import assert from "node:assert/strict";
import { ApiError, createApi, type Api } from "../frontend/src/api/client.ts";

const BASE = process.env.API_URL ?? "http://localhost:8080/api";
const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? "admin@courtbook.local";
const run = Date.now().toString(36);

type Player = { api: Api; id: number; name: string };

function client(): { api: Api; setToken(t: string): void } {
  let token: string | null = null;
  const api = createApi(BASE, { get: () => token, clear: () => (token = null) });
  return { api, setToken: (t) => (token = t) };
}

async function signUp(email: string, name: string): Promise<Player> {
  const { api, setToken } = client();
  const password = "e2e-password-123";
  try {
    await api.register(email, name, password);
  } catch (e) {
    // The admin account survives between local runs; anything else is a failure.
    if (!(e instanceof ApiError && e.status === 409 && email === ADMIN_EMAIL)) throw e;
  }
  const res = await api.login(email, password);
  setToken(res.accessToken);
  return { api, id: res.user.id, name };
}

async function expectStatus(status: number, call: Promise<unknown>) {
  try {
    await call;
  } catch (e) {
    assert.ok(e instanceof ApiError, `expected ApiError, got ${e}`);
    assert.equal(e.status, status, e.message);
    return;
  }
  assert.fail(`expected HTTP ${status}, but the call succeeded`);
}

async function eventually<T>(what: string, fetch: () => Promise<T>, done: (v: T) => boolean, timeoutMs = 10_000) {
  const start = Date.now();
  for (;;) {
    const v = await fetch();
    if (done(v)) return { value: v, ms: Date.now() - start };
    if (Date.now() - start > timeoutMs) assert.fail(`timed out after ${timeoutMs} ms waiting for: ${what}`);
    await new Promise((r) => setTimeout(r, 250));
  }
}

function step(name: string) {
  console.log(`\n▸ ${name}`);
}

function tomorrow(): string {
  const d = new Date(Date.now() + 36 * 3600_000);
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/New_York" }).format(d);
}

async function main() {
  step("register and log in");
  const admin = await signUp(ADMIN_EMAIL, "Club Admin");
  const me = await admin.api.me();
  assert.equal(me.role, "ADMIN", `${ADMIN_EMAIL} should be an admin (check COURTBOOK_ADMIN_EMAILS)`);
  const alice = await signUp(`alice-${run}@e2e.test`, "Alice");
  const bob = await signUp(`bob-${run}@e2e.test`, "Bob");
  const cara = await signUp(`cara-${run}@e2e.test`, "Cara");
  console.log("  4 accounts ready");

  step("book a court");
  const slots = await alice.api.slots(tomorrow());
  assert.ok(slots.length > 0, "tomorrow should have slots");
  const slot = slots.find((s) => s.status === "FREE" && s.waitlistCount === 0);
  assert.ok(slot, "need a free slot");
  const booking = await alice.api.book(slot.id);
  assert.equal(booking.status, "ACTIVE");
  console.log(`  Alice booked ${slot.courtName} at ${slot.startsAt}`);

  step("the same slot can't be booked twice");
  await expectStatus(409, bob.api.book(slot.id));

  step("join the waitlist");
  const pos = await bob.api.joinWaitlist(slot.id);
  assert.equal(pos.position, 1);
  const caraPos = await cara.api.joinWaitlist(slot.id);
  assert.equal(caraPos.position, 2);

  step("cancel → first in line gets the court and a notification");
  await alice.api.cancelBooking(booking.id);
  const bobBookings = await bob.api.myBookings();
  const promoted = bobBookings.find((b) => b.slotId === slot.id && b.status === "ACTIVE");
  assert.ok(promoted, "Bob should now hold the slot");
  assert.equal(promoted.source, "WAITLIST");
  const note = await eventually(
    "Bob's 'You got the court!' notification",
    () => bob.api.notifications(),
    (p) => p.items.some((n) => n.title === "You got the court!"),
  );
  console.log(`  notification arrived after ${note.ms} ms`);
  assert.ok(note.ms < 10_000);

  const caraView = (await cara.api.slots(tomorrow())).find((s) => s.id === slot.id);
  assert.equal(caraView?.myWaitlistPosition, 1, "Cara moves up to #1");

  step("mark notifications read");
  await bob.api.markAllRead();
  assert.equal((await bob.api.notifications()).unreadCount, 0);

  step("run a tournament with an odd number of players");
  const deadline = new Date(Date.now() + 3 * 86400_000).toISOString();
  const created = await admin.api.createTournament(`E2E Cup ${run}`, tomorrow(), deadline);
  const tid = created.tournament.id;
  for (const p of [alice, bob, cara]) await p.api.signUp(tid);
  await expectStatus(403, alice.api.startTournament(tid));
  let detail = await admin.api.startTournament(tid);
  assert.equal(detail.rounds.length, 2, "3 players → 4-slot bracket → 2 rounds");
  assert.equal(detail.rounds[0].matches.filter((m) => m.bye).length, 1, "top seed gets a bye");

  while (detail.tournament.status !== "COMPLETED") {
    const ready = detail.rounds.flatMap((r) => r.matches).find((m) => m.status === "READY");
    assert.ok(ready, "there should always be a playable match until the end");
    await admin.api.recordResult(ready.id, ready.player1Id!, "21-17, 21-15");
    detail = await admin.api.tournament(tid);
  }
  console.log(`  champion: ${detail.tournament.championName}`);

  step("results reach the club feed and rankings");
  await eventually(
    "the final in the club feed",
    () => alice.api.feed(),
    (f) => f.some((i) => i.text.includes(`E2E Cup ${run}`) && i.text.includes("won")),
  );
  const rankings = await alice.api.rankings();
  assert.ok(rankings.length >= 3);

  step("admin stats");
  const [users, bookings, tournaments] = await Promise.all([
    admin.api.userStats(),
    admin.api.bookingStats(),
    admin.api.tournamentStats(),
  ]);
  assert.ok(users.members >= 3 && bookings.promotedFromWaitlist >= 1 && tournaments.completed >= 1);
  await expectStatus(403, alice.api.userStats());

  console.log("\n✔ journey passed");
}

main().catch((e) => {
  console.error("\n✘ journey failed:", e);
  process.exit(1);
});
