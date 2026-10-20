import { describe, expect, it } from "vitest";
import { Slot, TournamentDetail } from "./schemas.ts";

describe("API schemas", () => {
  it("accept a slot exactly as the booking service sends it", () => {
    const slot = {
      id: 1, courtId: 2, courtName: "Court 2",
      startsAt: "2026-10-21T23:00:00Z", endsAt: "2026-10-22T00:00:00Z",
      status: "BOOKED", myBookingId: null, waitlistCount: 2, myWaitlistPosition: 1, started: false,
    };
    expect(Slot.parse(slot).myWaitlistPosition).toBe(1);
  });

  it("reject a renamed field instead of letting it through as undefined", () => {
    const renamed = {
      id: 1, court_id: 2, courtName: "Court 2", startsAt: "x", endsAt: "y",
      status: "FREE", waitlistCount: 0, started: false,
    };
    expect(Slot.safeParse(renamed).success).toBe(false);
  });

  it("accept a tournament that hasn't been drawn yet", () => {
    const detail = {
      tournament: {
        id: 3, name: "Fall Singles", startsOn: "2026-11-02", signupDeadline: "2026-10-30T02:00:00Z",
        status: "SIGNUP", signupOpen: true, playerCount: 0, registeredByMe: false, championId: null, championName: null,
      },
      players: [],
      rounds: [],
    };
    expect(TournamentDetail.parse(detail).tournament.name).toBe("Fall Singles");
  });
});
