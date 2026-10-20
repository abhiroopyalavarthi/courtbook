import { describe, expect, it } from "vitest";
import { parseRoute } from "./router.ts";

describe("parseRoute", () => {
  it("defaults to the booking page", () => {
    expect(parseRoute("")).toEqual({ page: "book" });
    expect(parseRoute("#/nonsense")).toEqual({ page: "book" });
  });

  it("reads tournament ids", () => {
    expect(parseRoute("#/tournaments/12")).toEqual({ page: "tournament", id: 12 });
    expect(parseRoute("#/tournaments/abc")).toEqual({ page: "tournaments" });
  });
});
