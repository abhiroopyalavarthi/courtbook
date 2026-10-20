import { useEffect, useState } from "react";

/** Hash routes: #/book, #/tournaments/12, ... No server config needed. */
export type Route =
  | { page: "book" }
  | { page: "bookings" }
  | { page: "tournaments" }
  | { page: "tournament"; id: number }
  | { page: "rankings" }
  | { page: "feed" }
  | { page: "admin" };

export function parseRoute(hash: string): Route {
  const parts = hash.replace(/^#\/?/, "").split("/");
  switch (parts[0]) {
    case "bookings":
      return { page: "bookings" };
    case "tournaments": {
      const id = Number(parts[1]);
      return Number.isInteger(id) && id > 0 ? { page: "tournament", id } : { page: "tournaments" };
    }
    case "rankings":
      return { page: "rankings" };
    case "feed":
      return { page: "feed" };
    case "admin":
      return { page: "admin" };
    default:
      return { page: "book" };
  }
}

export function useRoute(): Route {
  const [route, setRoute] = useState(() => parseRoute(window.location.hash));
  useEffect(() => {
    const onChange = () => setRoute(parseRoute(window.location.hash));
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);
  return route;
}

export function go(path: string) {
  window.location.hash = path;
}
