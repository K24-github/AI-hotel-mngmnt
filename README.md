# AI-Assisted Hotel Management System

<img width="1919" height="1020" alt="image" src="https://github.com/user-attachments/assets/da596a5c-23dd-4caa-ac1b-945d0ac508a7" />

This is a front-desk app for a small hotel, written in Java Swing. A receptionist
picks a room off a grid, checks a guest in, extends or upgrades the stay, and checks
them out, with a dashboard tracking occupancy and revenue.

The plan is to turn it into something where the receptionist can just type what they
want in plain language and the app fills the form for them. That part isn't built
yet. Everything described under "What it does now" works today.

## What it does now

32 rooms over three floors, in three tiers (Studio, Deluxe, Suite) with different
rates and capacities. Click a room to see who's in it and what they owe. Green tiles
are free, red ones are taken.

Four actions: check in, extend a stay, move a guest to a better room, check out. The
dashboard shows how many rooms are free, occupancy percentage, money owed by guests
still in-house, and money already collected.

## Running it

You need Java 17 or newer. From the project folder:

```bash
./gradlew run
```

Tests:

```bash
./gradlew runTests
```

The suite is hand-rolled rather than JUnit, so it runs as a plain Java program and
exits non-zero on failure. Nothing to install — the Gradle wrapper fetches what it
needs on first run.

## Where this is going

The idea is a text box above the room grid. The receptionist types:

> `Make a booking at room 201 deluxe for 2 guests for 2 nights, breakfast included`

and the check-in dialog opens with room 201 selected, guests set to 2, nights set to
2, and breakfast ticked. Name and phone are still blank because the app can't know
those. The clerk checks it over and presses OK. Nothing is saved before that.

They can also ignore the box entirely, click room 201, and type everything by hand.
That path stays exactly as it is now. If the parsing fails or the sentence is
confusing, the dialog just opens blank, which is the same as the manual flow. The
feature can save typing but it can't get in the way of a booking.

Rules I'm holding myself to:

- Nothing gets booked, changed or closed unless a person presses a button. The AI
  fills in a form, it doesn't submit it.
- Every screen has to work with the AI switched off. A setting, not a rebuild.
  Someone on the night shift with the internet down still needs to check people in.
- The model runs locally on the front-desk machine, so guest details never leave the
  building and there's no server call sitting between the clerk and a check-in.
- Money is never calculated by a model. Rates and bills stay in plain Java with
  tests behind them.
- Whatever the model returns is treated like it came from a stranger. It goes
  through the same checks as a manual entry before it reaches the form.

I'm planning on Gemma 4 E2B through Ollama. The job isn't reasoning, it's pulling
five fields out of a sentence:

```json
{ "roomNumber": "201", "tier": "Deluxe", "guests": 2, "nights": 2, "notes": "Breakfast requested" }
```

A small model is fine for that, especially with the output constrained to a fixed
JSON schema so it can't reply with anything else. The reason I'm not doing this with
regex is language. People here type Indonesian, English, or both in the same
sentence — "booking kamar 201 untuk 2 orang 2 malam", "2 nights 2 pax room 201
include breakfast" — and writing rules for all of that is a losing fight. A model
handles it without me listing every phrasing.

Then `findRoom` checks the room actually exists, the occupancy check catches a room
that's already taken, and capacity catches four people in a studio. Same code that
already rejects a bad manual entry. If the model invents room 999 it goes nowhere.

Before writing any of it I want 50-100 real sentences written down with the JSON I'd
expect back from each, so I can measure whether E2B is good enough instead of
guessing. Moving up to E4B is a one-line change if it isn't.

Persistence has to come first though. Forecasting and anything to do with guest
history is pointless while the data disappears when the window closes.

Other things I'd like eventually: room suggestions based on who's staying and how
full the hotel is, quiet-night forecasting to help with rates, and summarising a
repeat guest's notes into something short the clerk can read at a glance.

## How the code is organised

```
src/hotel/
├── Main.java                  entry point
├── model/                     the rules — no Swing in here at all
│   ├── Person (abstract) ← Guest
│   ├── Priceable (interface)
│   ├── Room (abstract) ← StudioRoom | DeluxeRoom | SuiteRoom
│   ├── StaySegment            nights charged at one room's rate
│   ├── Booking                a stay is a list of segments
│   └── BookingStatus
├── service/HotelManager.java  the rooms and the booking ledger
├── ui/
│   ├── MainFrame.java         wiring and drawing only
│   ├── CheckInDialog.java     the check-in form
│   └── UiTheme.java           colours, fonts, button styling
└── test/HotelTests.java
```

It only points one way: `ui` uses `service`, `service` uses `model`, and `model`
doesn't know either of them exist. That's why the tests run without opening a
window, and it's also where the AI layer will slot in — beside the UI, calling the
same `HotelManager` methods a button click already calls. Nothing in `model` has to
change for it.

## Things I fixed after the first version

**Revenue disappeared at check-out.** Only active bookings were counted, so a
completed stay vanished off the dashboard. Check-out now freezes the amount on the
booking, and the dashboard shows collected and in-house money separately.

**Upgrades re-charged the whole stay.** The bill was the current room's rate times
total nights, so someone moving to a suite on night 4 of 5 got charged suite rates
for the studio nights too. A booking is now a list of `StaySegment`s, one per room,
so an upgrade only affects nights that haven't happened yet. Same-day upgrades still
re-price everything, which is correct.

**Crash on the upgrade list.** `getAvailableUpgradeOptions` read the booking of a
room without checking there was one. It only worked because the UI happened to guard
the call. Returns an empty list now.

Smaller things: the floor filter was tied to the dropdown's index, so it would have
broken the moment I added a floor — it reads the actual floor numbers now. The room
and booking lists were handed out as the live internal lists, so anything holding one
could scramble the state. The "one booking per room" rule sat in `HotelManager` while
`Room` would happily let you overwrite a booking, so that moved into `Room`.
`MainFrame` was 609 lines with the whole check-in form buried inside an event
handler, and that's split out now.

Buttons also came out white on Windows for a while. The Windows look-and-feel paints
its own button backgrounds and ignores `setBackground`, so they're drawn with
`BasicButtonUI` instead, which behaves the same everywhere.

## Still missing

- **No saving.** Close the window and everything's gone. JSON on exit is the next
  job, and it blocks most of the AI work.
- The GUI hasn't been tested on every platform, only Windows. The logic suite passes
  and it compiles clean with `-Xlint:all`.
- Dates work in whole days, so "nights stayed" ticks over at midnight rather than at
  a proper check-out time.
- Booking IDs count up from a static counter, so they're only unique per run. Saving
  to disk means seeding it from the highest stored ID.
