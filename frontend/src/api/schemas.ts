import { z } from "zod";

// Every API response is parsed with one of these. If a backend renames or
// drops a field, parsing fails loudly instead of the page quietly showing
// "undefined". The e2e tests parse real responses with these same schemas,
// so CI catches the mismatch before a member ever does.

const timestamp = z.string();
const id = z.number().int();

export const Role = z.enum(["MEMBER", "ADMIN"]);

export const User = z.object({
  id,
  email: z.string(),
  name: z.string(),
  role: Role,
  createdAt: timestamp,
});

export const TokenResponse = z.object({
  accessToken: z.string(),
  tokenType: z.literal("Bearer"),
  expiresAt: timestamp,
  user: User,
});

export const Problem = z.object({
  title: z.string().nullish(),
  detail: z.string().nullish(),
  status: z.number().nullish(),
});

export const Court = z.object({ id, name: z.string() });

export const Slot = z.object({
  id,
  courtId: id,
  courtName: z.string(),
  startsAt: timestamp,
  endsAt: timestamp,
  status: z.enum(["FREE", "BOOKED"]),
  myBookingId: id.nullish(),
  waitlistCount: z.number().int(),
  myWaitlistPosition: z.number().int().nullish(),
  started: z.boolean(),
});

export const Booking = z.object({
  id,
  slotId: id,
  courtName: z.string(),
  startsAt: timestamp,
  endsAt: timestamp,
  status: z.enum(["ACTIVE", "CANCELLED"]),
  source: z.enum(["DIRECT", "WAITLIST"]),
  createdAt: timestamp,
});

export const WaitlistPosition = z.object({ slotId: id, position: z.number().int() });

export const TournamentSummary = z.object({
  id,
  name: z.string(),
  startsOn: z.string(),
  signupDeadline: timestamp,
  status: z.enum(["SIGNUP", "IN_PROGRESS", "COMPLETED"]),
  signupOpen: z.boolean(),
  playerCount: z.number().int(),
  registeredByMe: z.boolean(),
  championId: id.nullish(),
  championName: z.string().nullish(),
});

export const Entrant = z.object({
  userId: id,
  name: z.string(),
  seed: z.number().int().nullish(),
  rating: z.number().int(),
});

export const Match = z.object({
  id,
  round: z.number().int(),
  position: z.number().int(),
  player1Id: id.nullish(),
  player1Name: z.string().nullish(),
  player2Id: id.nullish(),
  player2Name: z.string().nullish(),
  winnerId: id.nullish(),
  score: z.string().nullish(),
  bye: z.boolean(),
  status: z.enum(["WAITING", "READY", "COMPLETED"]),
});

export const Round = z.object({
  round: z.number().int(),
  name: z.string(),
  matches: z.array(Match),
});

export const TournamentDetail = z.object({
  tournament: TournamentSummary,
  players: z.array(Entrant),
  rounds: z.array(Round),
});

export const Ranking = z.object({
  rank: z.number().int(),
  userId: id,
  name: z.string(),
  rating: z.number().int(),
  wins: z.number().int(),
  losses: z.number().int(),
});

export const Notification = z.object({
  id: z.string(),
  type: z.string(),
  title: z.string(),
  body: z.string(),
  createdAt: timestamp,
  read: z.boolean(),
});

export const NotificationPage = z.object({
  unreadCount: z.number().int(),
  items: z.array(Notification),
});

export const FeedItem = z.object({
  id: z.string(),
  type: z.string(),
  text: z.string(),
  createdAt: timestamp,
});

export const UserStats = z.object({
  members: z.number().int(),
  admins: z.number().int(),
  joinedLast7Days: z.number().int(),
});

export const BookingStats = z.object({
  totalBookings: z.number().int(),
  upcomingActive: z.number().int(),
  cancelled: z.number().int(),
  promotedFromWaitlist: z.number().int(),
  membersWhoBooked: z.number().int(),
});

export const TournamentStats = z.object({
  completed: z.number().int(),
  inProgress: z.number().int(),
  openForSignup: z.number().int(),
  matchesPlayed: z.number().int(),
  playersEntered: z.number().int(),
});

export type User = z.infer<typeof User>;
export type TokenResponse = z.infer<typeof TokenResponse>;
export type Court = z.infer<typeof Court>;
export type Slot = z.infer<typeof Slot>;
export type Booking = z.infer<typeof Booking>;
export type TournamentSummary = z.infer<typeof TournamentSummary>;
export type TournamentDetail = z.infer<typeof TournamentDetail>;
export type Match = z.infer<typeof Match>;
export type Round = z.infer<typeof Round>;
export type Ranking = z.infer<typeof Ranking>;
export type Notification = z.infer<typeof Notification>;
export type NotificationPage = z.infer<typeof NotificationPage>;
export type FeedItem = z.infer<typeof FeedItem>;
export type UserStats = z.infer<typeof UserStats>;
export type BookingStats = z.infer<typeof BookingStats>;
export type TournamentStats = z.infer<typeof TournamentStats>;
