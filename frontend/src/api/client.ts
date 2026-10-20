import { z } from "zod";
import * as S from "./schemas.ts";

/** An error response from the API, carrying its ProblemDetail fields. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly title: string,
    detail: string,
  ) {
    super(detail);
  }
}

export type TokenStore = {
  get(): string | null;
  clear(): void;
};

/**
 * A thin fetch wrapper. Works in the browser and in Node (the e2e tests use
 * it too), so it takes the base URL and token store as arguments.
 */
export function createApi(baseUrl: string, tokens: TokenStore, onUnauthorized: () => void = () => {}) {
  async function request<T>(method: string, path: string, schema: z.ZodType<T> | null, body?: unknown): Promise<T> {
    const headers: Record<string, string> = { Accept: "application/json" };
    const token = tokens.get();
    if (token) headers.Authorization = `Bearer ${token}`;
    if (body !== undefined) headers["Content-Type"] = "application/json";

    const res = await fetch(baseUrl + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });

    if (res.status === 401 && token) {
      tokens.clear();
      onUnauthorized();
    }
    if (!res.ok) {
      const text = await res.text();
      let problem: z.infer<typeof S.Problem> = {};
      try {
        problem = S.Problem.parse(JSON.parse(text));
      } catch {
        // not JSON (e.g. a 429 from the gateway); fall through
      }
      throw new ApiError(
        res.status,
        problem.title ?? res.statusText,
        problem.detail ?? (res.status === 429 ? "Too many requests. Wait a minute and try again." : `Request failed (${res.status})`),
      );
    }
    if (schema === null || res.status === 204) return undefined as T;

    const json: unknown = await res.json();
    const parsed = schema.safeParse(json);
    if (!parsed.success) {
      // A contract break between the frontend and a service. Make it obvious.
      console.error(`Unexpected response shape from ${method} ${path}`, parsed.error.issues, json);
      throw new ApiError(res.status, "Unexpected response", `The server's response to ${path} didn't match what this page expects.`);
    }
    return parsed.data;
  }

  const get = <T>(path: string, schema: z.ZodType<T>) => request("GET", path, schema);
  const post = <T>(path: string, schema: z.ZodType<T> | null, body?: unknown) => request("POST", path, schema, body);
  const put = <T>(path: string, schema: z.ZodType<T>, body?: unknown) => request("PUT", path, schema, body);
  const del = (path: string) => request<void>("DELETE", path, null);

  return {
    // users
    register: (email: string, name: string, password: string) => post("/users", S.User, { email, name, password }),
    login: (email: string, password: string) => post("/auth/login", S.TokenResponse, { email, password }),
    me: () => get("/users/me", S.User),
    allUsers: () => get("/admin/users", z.array(S.User)),
    setRole: (userId: number, role: "MEMBER" | "ADMIN") => put(`/admin/users/${userId}/role`, S.User, { role }),

    // bookings
    courts: () => get("/courts", z.array(S.Court)),
    slots: (date: string) => get(`/slots?date=${date}`, z.array(S.Slot)),
    book: (slotId: number) => post("/bookings", S.Booking, { slotId }),
    cancelBooking: (bookingId: number) => del(`/bookings/${bookingId}`),
    myBookings: () => get("/bookings/me", z.array(S.Booking)),
    joinWaitlist: (slotId: number) => post(`/slots/${slotId}/waitlist`, S.WaitlistPosition),
    leaveWaitlist: (slotId: number) => del(`/slots/${slotId}/waitlist`),

    // tournaments
    tournaments: () => get("/tournaments", z.array(S.TournamentSummary)),
    tournament: (id: number) => get(`/tournaments/${id}`, S.TournamentDetail),
    createTournament: (name: string, startsOn: string, signupDeadline: string) =>
      post("/tournaments", S.TournamentDetail, { name, startsOn, signupDeadline }),
    signUp: (id: number) => post(`/tournaments/${id}/registrations`, S.TournamentDetail),
    withdraw: (id: number) => del(`/tournaments/${id}/registrations/me`),
    startTournament: (id: number) => post(`/tournaments/${id}/start`, S.TournamentDetail),
    recordResult: (matchId: number, winnerId: number, score: string) =>
      post(`/matches/${matchId}/result`, S.Match, { winnerId, score }),
    rankings: () => get("/rankings", z.array(S.Ranking)),

    // notifications
    notifications: () => get("/notifications", S.NotificationPage),
    markAllRead: () => post<void>("/notifications/read", null),
    feed: () => get("/feed", z.array(S.FeedItem)),

    // admin stats
    userStats: () => get("/admin/stats/users", S.UserStats),
    bookingStats: () => get("/admin/stats/bookings", S.BookingStats),
    tournamentStats: () => get("/admin/stats/tournaments", S.TournamentStats),
  };
}

export type Api = ReturnType<typeof createApi>;
