import http from "k6/http";

export const BASE = __ENV.API_URL || "http://localhost:8080/api";
const JSON_HEADERS = { "Content-Type": "application/json" };

export function makeUser(prefix, i) {
  const email = `${prefix}-${i}-${Date.now()}@load.test`;
  const password = "load-test-password";
  http.post(`${BASE}/users`, JSON.stringify({ email, name: `${prefix} ${i}`, password }), { headers: JSON_HEADERS });
  const res = http.post(`${BASE}/auth/login`, JSON.stringify({ email, password }), { headers: JSON_HEADERS });
  if (res.status !== 200) throw new Error(`login failed for ${email}: ${res.status} ${res.body}`);
  return res.json("accessToken");
}

export function auth(token) {
  return { headers: { ...JSON_HEADERS, Authorization: `Bearer ${token}` } };
}

/** A date N days from today in the club's time zone, as YYYY-MM-DD. */
export function clubDate(daysAhead) {
  const d = new Date(Date.now() + daysAhead * 86400000);
  // k6's JS runtime has no Intl time zones; New York is UTC-4/-5, so shift by 5h.
  return new Date(d.getTime() - 5 * 3600000).toISOString().slice(0, 10);
}
