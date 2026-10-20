import { Shuttle } from "./components/Shuttle.tsx";
import { NotificationBell } from "./components/NotificationBell.tsx";
import { AdminPage } from "./pages/AdminPage.tsx";
import { BookPage } from "./pages/BookPage.tsx";
import { FeedPage } from "./pages/FeedPage.tsx";
import { LoginPage } from "./pages/LoginPage.tsx";
import { MyBookingsPage } from "./pages/MyBookingsPage.tsx";
import { RankingsPage } from "./pages/RankingsPage.tsx";
import { TournamentPage } from "./pages/TournamentPage.tsx";
import { TournamentsPage } from "./pages/TournamentsPage.tsx";
import { useRoute, type Route } from "./router.ts";
import { useSession } from "./session.tsx";

const NAV: { href: string; label: string; pages: Route["page"][]; adminOnly?: boolean }[] = [
  { href: "#/book", label: "Book a court", pages: ["book"] },
  { href: "#/bookings", label: "My bookings", pages: ["bookings"] },
  { href: "#/tournaments", label: "Tournaments", pages: ["tournaments", "tournament"] },
  { href: "#/rankings", label: "Rankings", pages: ["rankings"] },
  { href: "#/feed", label: "Club feed", pages: ["feed"] },
  { href: "#/admin", label: "Admin", pages: ["admin"], adminOnly: true },
];

export function App() {
  const { user, logout } = useSession();
  const route = useRoute();

  if (!user) return <LoginPage />;
  const isAdmin = user.role === "ADMIN";

  return (
    <div className="shell">
      <header className="topbar">
        <a className="brand" href="#/book">
          <span className="brand-mark">
            <Shuttle />
          </span>
          CourtBook
        </a>
        <nav className="nav">
          {NAV.filter((n) => !n.adminOnly || isAdmin).map((n) => (
            <a key={n.href} href={n.href} className={n.pages.includes(route.page) ? "active" : undefined}>
              {n.label}
            </a>
          ))}
        </nav>
        <div className="topbar-right">
          <NotificationBell />
          <span className="who">{user.name}</span>
          <button className="link" onClick={logout}>
            Log out
          </button>
        </div>
      </header>
      <main className="page">{renderPage(route, isAdmin)}</main>
      <footer className="footer">SBU Badminton Club · times shown in Eastern Time</footer>
    </div>
  );
}

function renderPage(route: Route, isAdmin: boolean) {
  switch (route.page) {
    case "book":
      return <BookPage />;
    case "bookings":
      return <MyBookingsPage />;
    case "tournaments":
      return <TournamentsPage />;
    case "tournament":
      return <TournamentPage id={route.id} />;
    case "rankings":
      return <RankingsPage />;
    case "feed":
      return <FeedPage />;
    case "admin":
      return isAdmin ? <AdminPage /> : <p>Admins only.</p>;
  }
}
