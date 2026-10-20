# Backlog

Starting user stories for the GitHub Projects board. Paste each into a new issue with the "User story" template, estimate in the first planning meeting, and let the product owner (a club officer) order them.

`scripts/create-issues.sh` creates all of them as issues with the `gh` CLI if you'd rather not paste by hand.

| # | Story | Done when |
|---|---|---|
| 1 | As a member, I want to create an account with my email so that I can book courts. | Registering with a new email works; a reused email is refused; passwords under 8 characters are refused. |
| 2 | As a member, I want to log in so that the app knows who I am. | Correct password returns a token; wrong email or password gives the same error. |
| 3 | As a member, I want to see every court's free and booked slots for a day so that I can pick a time. | Day view shows all courts × hourly slots from 8 AM to 10 PM, Eastern time. |
| 4 | As a member, I want to book a free slot in one click so that I get the court. | Booking shows as mine immediately; others see it as booked. |
| 5 | As the club, I want it to be impossible for two members to get the same slot so that nobody shows up to a taken court. | 100 simultaneous requests for one slot produce exactly 1 booking (race test in CI). |
| 6 | As a member, I want to cancel my booking so that someone else can play. | Cancelled slot shows as free (or goes to the waitlist); I can't cancel other people's bookings. |
| 7 | As a member, I want to join a waitlist for a full slot so that I get the court if someone cancels. | Joining shows my place in line; when the booking ahead of me is cancelled, I'm promoted and notified within 10 seconds. |
| 8 | As a member, I want to leave a waitlist so that I'm not given a court I no longer want. | After leaving, a cancellation doesn't promote me. |
| 9 | As the club, I want a limit on upcoming bookings per member so that a few people can't hold every court. | A 4th upcoming booking is refused with a clear message. |
| 10 | As a member, I want a bell with my notifications so that I see waitlist promotions and results. | Unread count shows on the bell; opening it marks them read. |
| 11 | As an admin, I want to create a tournament with a sign-up deadline so that members can enter. | Tournament appears for everyone with its deadline. |
| 12 | As a member, I want to sign up for (and withdraw from) a tournament before the deadline. | Sign-up closes at the deadline; I can't sign up twice. |
| 13 | As an admin, I want to draw a seeded bracket so that the strongest players don't meet early. | Works for any player count ≥ 2; byes go to the top seeds; seeds 1 and 2 are in opposite halves. |
| 14 | As an admin, I want to record match results so that winners move on. | Winner advances to the next round; the final sets the champion. |
| 15 | As a member, I want to see results in a club feed so that I can follow tournaments. | Each result appears in the feed within seconds. |
| 16 | As a member, I want rankings based on match results so that I know where I stand. | Elo ratings update after every match; table sorted by rating. |
| 17 | As an admin, I want usage numbers so that we can report how the app is used. | Admin page shows members, bookings, waitlist promotions, tournaments run. |
| 18 | As an admin, I want to make another officer an admin so that I'm not the only one. | Role change takes effect at their next login. |
| 19 | As the club, I want the app at a public HTTPS address so that members can use it from their phones. | Deployed on AWS behind HTTPS; layout works on a phone. |
| 20 | As the team, I want CI on every pull request so that main always works. | Build, unit, integration, race and e2e tests must pass before merge. |

## Ideas for later (not committed)

- Email notifications through Amazon SES
- Doubles tournaments
- Recurring weekly bookings for club practice
- "Find me a partner" for a slot
