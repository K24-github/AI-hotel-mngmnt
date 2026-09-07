# AI-Assisted Hotel Management System

<img width="1919" height="1020" alt="image" src="https://github.com/user-attachments/assets/da596a5c-23dd-4caa-ac1b-945d0ac508a7" />

This is a front-desk app for a small hotel, written in Java Swing. A receptionist
picks a room off a grid, checks a guest in, extends or upgrades the stay, and checks
them out, with a dashboard tracking occupancy and revenue.

The app itself started as my final exam project at university. What I'm doing now is
using it to practise putting AI into a codebase that already exists, rather than
building something around a model from the start. The rules and the screens were
already there and the model had to fit around them, and that turned out to be most of
the work.

There's also a text box above the room grid. The receptionist types what they want in
plain language and the check-in form opens with the fields already filled in. That
part works now, and most of this README is about how I checked that it works.

The model is Gemma 4 E2B (`gemma4:e2b-it-qat`), running locally through Ollama. I
picked it because it's light enough for the machine it has to run on, a front desk PC
with 8 GB of RAM and no graphics card. It takes about 3.9 GB of RAM once loaded,
which leaves room to spare on an 8 GB machine. The surprise was that it also beat the
smaller models I tried it against, including a Qwen half its size, so I didn't have to
trade accuracy for it fitting. The numbers are further down.

## What it does now

32 rooms over three floors, in three tiers (Studio, Deluxe, Suite) with different
rates and capacities. Click a room to see who's in it and what they owe. Green tiles
are free, amber ones are reserved, red ones are taken.

Six actions: check in, reserve future dates, extend a stay, move a guest to a better
room, cancel a reservation, check out. The dashboard shows how many rooms are free,
occupancy percentage, money owed by guests still in-house, and money already
collected. Everything saves to `hotel-bookings.json` and loads again on startup.

## Running it

You need Java 17 or newer. From the project folder:

```bash
./gradlew run
```

Tests:

```bash
./gradlew test
```

Nothing to install, the Gradle wrapper fetches what it needs on first run.

That gets you the whole app apart from the text box. For that you also need
[Ollama](https://ollama.com) 0.33.3 or newer, and one command:

```bash
ollama pull gemma4:e2b-it-qat
```

It's a 4.3 GB download. There's nothing else to set up, no Modelfile and no config.
The prompt and the JSON schema live in the code, so the model is used exactly as it
comes.

If Ollama isn't installed the app still runs, the box just won't fill anything in.
There's also an AI checkbox to turn it off and fill every form by hand.

## The text box

The receptionist types:

> `Make a booking at room 201 deluxe for 2 guests for 2 nights, breakfast included`

and the check-in dialog opens with room 201 selected, guests set to 2, nights set to
2, and breakfast ticked. The clerk checks it over, fixes anything wrong, and presses
OK. Nothing is saved before that.

They can also ignore the box entirely, click room 201, and type everything by hand.
That path stays exactly as it was. If the parsing fails the dialog doesn't open and
the status line says so, which leaves them where they'd have been anyway. The feature
can save typing but it can't get in the way of a booking.

People here type Indonesian, English, or both in the same sentence, so all of these
work:

> `booking kamar 201 untuk 2 orang 2 malam`
> `2 nights 2 pax room 201 include breakfast`
> `kamar 212 utk bpk Hendra, 1 mlm`
> `room 305 Ms Lim 3 nights 2 pax`

Two things aren't the model's job. Write `note:` or `catatan:` anywhere and everything
after it goes into the notes field. And a phone number gets pulled out by a regex
before the model ever sees the sentence, because a country code like `+61...` kept
being read as a guest count.

Limits i've put:

- Nothing gets booked, changed or closed unless a person presses a button. The AI
  fills in a form, it doesn't submit it.
- Every screen has to work with the AI switched off. A setting, not a rebuild.
  Someone on the night shift with the internet down still needs to check people in.
- The model runs locally, so guest details never leave the building. The endpoint is
  checked in `OllamaConfig`, and a public address is refused rather than warned about.
- Money is never calculated by a model. Rates and bills stay in plain Java with
  tests behind them.
- Whatever the model returns is treated like it came from a stranger. It goes
  through the same checks as a manual entry before it reaches the form.

That last one is `BookingResolver`, and it's where most of the work went. `findRoom`
checks the room actually exists, so if the model invents room 999 it goes nowhere.
Capacity catches four people in a studio. A room that's already taken, or free
tonight but booked later in the stay, gets caught before the dialog opens and again
when the booking is saved, by the same rules that reject a bad manual entry. A guest
name is only
kept if it appears in what the clerk typed, so a name the model made up gets dropped.
If a room number doesn't exist but a tier does, it falls back to the first free room
in that tier instead of giving up.

## Does it actually work?

I wrote down 96 sentences with the JSON I expected back from each, then measured,
because I didn't want to guess. They're in
`src/test/resources/eval/booking-sentences.json`, and `./gradlew evalModels` scores a
model against all of them.

Gemma 4 E2B on CPU only, which is what a front desk PC would have. Room number
95.8%, tier 91.7%, guests 92.7%, nights 100%, breakfast 100%, guest name 91.7%.

Same model on my RTX 3060, where it sits entirely in VRAM. Room number 94.8%, tier
92.7%, guests 93.8%, nights 100%, breakfast 100%, guest name 92.7%. Near enough
identical, which is what you'd expect since the hardware doesn't change the answer,
only how fast it arrives. Speed is the real difference: 701 ms on the GPU against
5590 ms on the CPU (Ryzen 7 5800H).

Field accuracy isn't really the number I care about though. What matters is whether
the clerk ended up with the right room, or a correct refusal, and that's 96 out of 96
both ways. Most of the field misses never reach anybody, because the resolver fixes
tier capitalisation, ignores a guest count the sentence never stated, and falls back
when a room number is wrong.

I ran two other models over the same 96 sentences. On CPU:

- `gemma4:e2b-it-qat`, 5590 ms, right room 96 times out of 96
- `qwen3.5:2b-q8_0`, 8560 ms, right room 77 times out of 96
- `qwen3.5:0.8b`, 4133 ms, right room 86 times out of 96

On the GPU the order is the same. Gemma takes 701 ms, the 2b Qwen 852 ms and drops to
80 out of 96, the 0.8b Qwen 635 ms and 85 out of 96.

So the bigger Qwen is slower and worse at the same time, which at least makes that
choice easy. The small one is the only model fast enough on a CPU, and it gets nights
right twice out of 96, so that's not an option either.

The prompt got measured the same way. Fixing one bug, `sarapan tidak usah` was booking
breakfast instead of refusing it, took three attempts, and the version that scored
better on three of the six fields is the one I threw away. It had also started copying night
counts into guest counts, which made the resolver refuse a booking I'd typed myself.
Prompt edits aren't local, and I wouldn't have caught that by reading it.

## How the code is organised

```
src/main/java/hotel/
├── Main.java                  entry point
├── model/                     the rules, no Swing in here at all
│   ├── Person (abstract) ← Guest
│   ├── Priceable (interface)
│   ├── Room (abstract) ← StudioRoom | DeluxeRoom | SuiteRoom
│   ├── StaySegment            nights charged at one room's rate
│   ├── Booking                a stay is a list of segments
│   └── BookingStatus
├── service/HotelManager.java  the rooms and the booking ledger
├── store/                     saving and loading bookings as JSON
├── ai/
│   ├── BookingParser          the interface, a sentence in and six guesses out
│   ├── OllamaBookingParser    the live one, talks to Ollama
│   ├── BookingDraft           what the model said, untrusted
│   ├── BookingResolver        turns a draft into a real room, or refuses
│   ├── BookingProposal        what survived
│   ├── PhoneNumbers, Notes    the parts the model doesn't get to see
│   └── OllamaConfig           model, endpoint, prompt
└── ui/
    ├── MainFrame.java         wiring and drawing only
    ├── CheckInDialog.java     the check-in form
    ├── DateField.java
    └── UiTheme.java           colours, fonts, button styling
```

It only points one way: `ui` uses `service`, `service` uses `model`, and `model`
doesn't know either of them exist. That's why the tests run without opening a window.
`ai` sits beside the UI and calls the same `HotelManager` methods a button click
already calls, so nothing in `model` had to change for any of it.

`BookingParser` is a single method, which means the Ollama one can be swapped out. The
tests use a scripted parser with no model behind it at all, and the AI checkbox swaps
in one that returns nothing.

## Things I fixed after the first version

Revenue disappeared at check-out. Only active bookings were counted, so a
completed stay vanished off the dashboard. Check-out now freezes the amount on the
booking, and the dashboard shows collected and in-house money separately.

Upgrades re-charged the whole stay. The bill was the current room's rate times
total nights, so someone moving to a suite on night 4 of 5 got charged suite rates
for the studio nights too. A booking is now a list of `StaySegment`s, one per room,
so an upgrade only affects nights that haven't happened yet. Same-day upgrades still
re-price everything, which is correct.

Crash on the upgrade list. `getAvailableUpgradeOptions` read the booking of a
room without checking there was one. It only worked because the UI happened to guard
the call. Returns an empty list now.

A country code read as a guest count. `Hana Malmo 6 nights studio +6143567382`
asked for six guests in a studio and got refused. The phone is cut out of the sentence
before the model reads it now.

Booking a room that's free tonight but taken later in the stay. The clerk filled
in the whole form and only then got told the room wasn't available. It's checked
before the dialog opens.

Smaller things: the floor filter was tied to the dropdown's index, so it would have
broken the moment I added a floor, and it reads the actual floor numbers now. The room
and booking lists were handed out as the live internal lists, so anything holding one
could scramble the state. The "one booking per room" rule sat in `HotelManager` while
`Room` would happily let you overwrite a booking, so that moved into `Room`.
`MainFrame` was 609 lines with the whole check-in form buried inside an event
handler, and that's split out now.

Buttons also came out white on Windows for a while. The Windows look-and-feel paints
its own button backgrounds and ignores `setBackground`, so they're drawn with
`BasicButtonUI` instead, which behaves the same everywhere.

## Still missing

- It's slow without a GPU. 5590 ms on CPU, and the PC I tested on is faster
  than a real front desk PC would be. On my GPU the same model answers in 701 ms. Fine
  for a demo, marginal for a real desk. Running Ollama on a better PC in the back
  office would fix it, which is why the endpoint check allows a LAN address.
- I haven't run it on the actual 8 GB desktop yet. Every number in here is from my
  own PC.
- The first request after a quiet spell takes far longer than any of the numbers
  above. I measured 11 seconds once with the model already loaded, because the GPU had
  dropped to idle and had to spin back up.
- I've argued in here that regex would be a losing fight against mixed Indonesian and
  English, and I do believe it, but I haven't written a regex parser and scored it on
  the same 96 sentences. Until I do, that's a claim and not a result.
- The GUI has only been tested on Windows.
- Dates work in whole days, so "nights stayed" ticks over at midnight rather than at
  a proper check-out time.
- Booking IDs count up from a static counter, so they're only unique per run.
