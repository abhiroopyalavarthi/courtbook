// Booking latency at 50 concurrent users. Each user looks at a day, books a
// free court if there is one, and cancels it again (to stay under the club's
// per-member limit). Run it locally, not on AWS, to save credits.
//
//   COURTBOOK_RATE_LIMIT=100000 COURTBOOK_LOGIN_RATE_LIMIT=100000 \
//     docker compose -f deploy/docker-compose.yml up -d --build --wait
//   k6 run load/booking-load.js
import http from "k6/http";
import { check, sleep } from "k6";
import { Trend } from "k6/metrics";
import { BASE, auth, clubDate, makeUser } from "./common.js";

const USERS = 50;
const bookLatency = new Trend("booking_latency", true);

export const options = {
  scenarios: {
    members: { executor: "constant-vus", vus: USERS, duration: "2m" },
  },
  thresholds: {
    booking_latency: ["p(50)<100", "p(99)<500"],
    http_req_failed: ["rate<0.01"],
  },
  summaryTrendStats: ["avg", "p(50)", "p(95)", "p(99)", "max"],
};

export function setup() {
  const tokens = [];
  for (let i = 0; i < USERS; i++) tokens.push(makeUser("load", i));
  return { tokens };
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const day = clubDate(1 + Math.floor(Math.random() * 13));
  const slots = http.get(`${BASE}/slots?date=${day}`, auth(token));
  check(slots, { "slots 200": (r) => r.status === 200 });
  const free = (slots.json() || []).filter((s) => s.status === "FREE" && !s.started);
  if (free.length === 0) return;

  const pick = free[Math.floor(Math.random() * free.length)];
  const res = http.post(`${BASE}/bookings`, JSON.stringify({ slotId: pick.id }), {
    ...auth(token),
    // 409 is a correct answer when another user got there first.
    responseCallback: http.expectedStatuses(201, 409),
  });
  bookLatency.add(res.timings.duration);
  if (res.status === 201) {
    http.del(`${BASE}/bookings/${res.json("id")}`, null, auth(token));
  }
  sleep(0.5 + Math.random());
}
