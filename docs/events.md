# Event formats

Services never call each other to trigger side effects. They publish events to one SQS queue (`courtbook-events`), and the notification service consumes them.

## How an event travels

1. The booking or tournament service writes the event into its own `outbox_events` table **in the same database transaction** as the change it describes. If the change rolls back, the event never existed.
2. A relay in the same service sends unpublished outbox rows to SQS every 500 ms, oldest first, then marks them published.
3. The notification service long-polls the queue. For each message it writes the notifications, feed lines and a "processed" marker for the event ID in **one DynamoDB transaction**, then deletes the message.
4. If handling fails, the message is not deleted. SQS shows it again after the 30-second visibility timeout. After 5 failed tries it moves to `courtbook-events-dlq`.

Delivery is at-least-once at two points (the relay can resend after a crash, and SQS can deliver twice), so the consumer de-duplicates by `eventId`: the marker write has the condition `attribute_not_exists(eventId)`, and if it fails the whole transaction is cancelled and the message is just deleted.

## Envelope

Every message body has the same shape:

```json
{
  "eventId": "6f1c2a0e-3b7d-4a49-9a51-2f3d1c7e8b90",
  "type": "WaitlistPromoted",
  "occurredAt": "2026-10-21T18:02:11.304Z",
  "source": "booking-service",
  "requestId": "1b9e…",
  "payload": { }
}
```

`requestId` is the ID the gateway gave the HTTP request that caused the event. Searching all logs for it follows one action from the browser to the notification.

The type is also set as the SQS message attribute `type`.

## Booking service

All booking events share one payload:

| Field | Type | Notes |
|---|---|---|
| `bookingId` | number | |
| `slotId` | number | |
| `userId` | number | who holds (or held) the booking |
| `userName` | string | |
| `courtName` | string | e.g. "Court 2" |
| `startsAt`, `endsAt` | ISO-8601 instant | |
| `source` | `DIRECT` \| `WAITLIST` | how the booking was made |

- **`BookingConfirmed`**: someone booked a free slot (or was promoted; see below).
- **`BookingCancelled`**: adds `cancelledBy` (user ID; differs from `userId` when an admin cancelled).
- **`WaitlistPromoted`**: published right after `BookingCancelled` in the same transaction. The payload describes the *new* booking, and `userId` is the member who got the slot.

## Tournament service

- **`TournamentStarted`**: `tournamentId`, `tournamentName`, `playerIds` (number[]).
- **`MatchResultRecorded`**: `tournamentId`, `tournamentName`, `matchId`, `round`, `roundName` ("Final", "Semifinals", …), `winnerId`, `winnerName`, `loserId`, `loserName`, `score` (string or null), `winnerRating`, `loserRating` (Elo after the match).
- **`TournamentCompleted`**: `tournamentId`, `tournamentName`, `championId`, `championName`.

## What the notification service does with them

| Event | Notification to | Club feed |
|---|---|---|
| `BookingConfirmed` (DIRECT) | the member | – |
| `BookingCancelled` | the member | – |
| `WaitlistPromoted` | the promoted member | – |
| `TournamentStarted` | every player | "X has started with N players." |
| `MatchResultRecorded` | winner and loser | "A beat B 21-17, 21-19 (Final, Fall Singles)" |
| `TournamentCompleted` | the champion | "A won Fall Singles!" |

Unknown types are logged and deleted, so a new event can be published before the consumer learns about it.

## Changing an event

Add fields freely; the consumer ignores fields it doesn't use. Never rename or remove a field in place: publish both for a while, update the consumer, then drop the old one.
