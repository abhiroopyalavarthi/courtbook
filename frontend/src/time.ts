// The club is in Stony Brook, so times are always shown in New York time,
// whatever time zone the viewer's laptop is set to.
export const CLUB_ZONE = "America/New_York";

const timeFmt = new Intl.DateTimeFormat("en-US", { timeZone: CLUB_ZONE, hour: "numeric", minute: "2-digit" });
const dayFmt = new Intl.DateTimeFormat("en-US", { timeZone: CLUB_ZONE, weekday: "short", month: "short", day: "numeric" });
const isoDayFmt = new Intl.DateTimeFormat("en-CA", { timeZone: CLUB_ZONE, year: "numeric", month: "2-digit", day: "2-digit" });

export const formatTime = (iso: string) => timeFmt.format(new Date(iso));
export const formatDay = (iso: string) => dayFmt.format(new Date(iso));
export const formatWhen = (iso: string) => `${formatDay(iso)}, ${formatTime(iso)}`;

/** Today's date in the club's time zone, as YYYY-MM-DD. */
export function clubToday(): string {
  return isoDayFmt.format(new Date());
}

/** Adds days to a YYYY-MM-DD date. */
export function addDays(date: string, days: number): string {
  const d = new Date(`${date}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

/** "Tue 21" style label for a YYYY-MM-DD date. */
export function dayLabel(date: string): { weekday: string; day: string } {
  const d = new Date(`${date}T12:00:00Z`);
  return {
    weekday: d.toLocaleDateString("en-US", { weekday: "short", timeZone: "UTC" }),
    day: d.toLocaleDateString("en-US", { day: "numeric", timeZone: "UTC" }),
  };
}

export function timeAgo(iso: string): string {
  const seconds = Math.round((Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return "just now";
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return formatDay(iso);
}
