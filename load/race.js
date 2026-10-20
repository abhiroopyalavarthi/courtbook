// The double-booking demo: 100 users press "Book" on the same court at the same moment.
// Exactly one must get 201; the other 99 must get 409.
//
//   COURTBOOK_RATE_LIMIT=100000 COURTBOOK_LOGIN_RATE_LIMIT=100000 \
//     docker compose -f deploy/docker-compose.yml up -d --build --wait
//   k6 run load/race.js
import http from "k6/http";
import { check } from "k6";
import { Counter } from "k6/metrics";
import { BASE, auth, clubDate, makeUser } from "./common.js";

const RACERS = 100;
const created = new Counter("bookings_created");
const conflicts = new Counter("bookings_conflict");
const other = new Counter("bookings_other_status");

export const options = {
  scenarios: {
    race: { executor: "per-vu-iterations", vus: RACERS, iterations: 1, maxDuration: "1m" },
  },
  thresholds: {
    bookings_created: ["count==1"],
    bookings_conflict: [`count==${RACERS - 1}`],
    bookings_other_status: ["count==0"],
  },
};

export function setup() {
  const tokens = [];
  for (let i = 0; i < RACERS; i++) tokens.push(makeUser("racer", i));
  const slots = http.get(`${BASE}/slots?date=${clubDate(3)}`, auth(tokens[0])).json();
  const free = slots.find((s) => s.status === "FREE" && !s.started);
  if (!free) throw new Error("no free slot to race for");
  return { tokens, slotId: free.id, startAt: Date.now() + 3000 };
}

export default function (data) {
  const token = data.tokens[__VU - 1];
  // Everyone waits for the same instant, then fires.
  while (Date.now() < data.startAt) {}
  const res = http.post(`${BASE}/bookings`, JSON.stringify({ slotId: data.slotId }), {
    ...auth(token),
    responseCallback: http.expectedStatuses(201, 409),
  });
  if (res.status === 201) created.add(1);
  else if (res.status === 409) conflicts.add(1);
  else other.add(1);
  check(res, { "201 or 409": (r) => r.status === 201 || r.status === 409 });
}
