# AI-Assisted Hotel Management System

<img width="1677" height="1010" alt="image" src="https://github.com/user-attachments/assets/8bc03f14-af22-4038-973f-bdcfd4a0a68b" />


This is a front-desk app for a small hotel, written in Java Swing. A receptionist
picks a room off a grid, checks a guest in, extends or upgrades the stay, and checks
them out, with a dashboard tracking occupancy and revenue.

The app itself started as my final exam project at university. What I'm doing now is
using it to practise putting AI into a codebase that already exists, rather than
building something around a model from the start. The rules and the screens were
already there and the model had to fit around them, and that turned out to be most of
the work.

The part I added is a text box above the room grid. The receptionist types the booking
in plain language and the check-in form opens with the fields already filled in. That
works now, and most of this README is about how I checked that it works.

The point of the integration is to take one job off the receptionist, not to hand the
app over to a model. A check-in normally means reading a room number off a note,
finding the tile, opening the form and filling it in by hand. Here they type the
booking the way they'd say it out loud and the form arrives filled in.

The reason it needs a model is the shape of the problem. The input is open and the
output is closed. What someone types at a front desk is an unstructured sentence
produced on the fly, under time pressure, with a guest waiting: any word order, either
language or both at once, whatever abbreviations that person happens to use, a name
nobody has seen before, a phone number sitting in the middle of it. Nobody can write
that side down in advance, because there is no format to agree on. What the model has
to produce is fixed and small: six values, no more, no less. So the job is mapping an
unbounded input onto a fixed schema, which is the thing a language model is actually
good for, and the closed output is what makes it safe to constrain the reply to that
schema and throw away anything that doesn't fit.

Everything after that mapping stays in plain Java with tests behind it. Which room,
whether it is free, what it costs, whether anything is saved at all. The model
proposes six values and gets no further.

It runs on the machine at the desk, so guest details never leave the building and a
check-in doesn't wait on a network. Gemma 4 E2B (`gemma4:e2b-it-qat`) through Ollama,
picked by measuring it against the alternatives rather than by guessing.

The next two sections are that feature and the numbers behind it. Everything after
them is the app it was built into.

## Structured extraction with Gemma 4 E2B through Ollama

The receptionist types:

> `Make a booking at room 201 deluxe for 2 guests for 2 nights, breakfast included`

and the check-in dialog opens with room 201 selected, guests set to 2, nights set to
2, and breakfast ticked. The clerk checks it over, fixes anything wrong, and presses
OK. Nothing is saved before that.

They can also ignore the box entirely, click room 201, and type everything by hand.
That path stays exactly as it was. If the parsing fails the dialog doesn't open and
the status line says so, which leaves them where they'd have been anyway. The feature
can save typing but it can't get in the way of a booking.

What Gemma 4 E2B is doing here is slot filling, or structured extraction. It reads one
unstructured sentence and returns six typed slots: room number, tier, guest name,
guests, nights, breakfast. That is the whole job. It doesn't decide anything, doesn't
look anything up, and doesn't talk to the rest of the app.

It runs under Ollama as a separate local process, and the app reaches it over HTTP at
`localhost:11434/api/generate` using the HTTP client that already ships with Java, so
no library was added for it. Each request carries the prompt, `temperature` 0, and a
JSON schema in the `format` field, which constrains decoding to that shape. The reply
cannot come back as prose or as broken JSON. It can still be wrong about a value, it
just can't be wrong about the format.

Worth being precise: this isn't parsing in the grammar sense. A parser applies rules
you wrote and you can point at the rule that produced each field. The model generates
the values, and there's no rule to point at. The two deterministic steps sit either
side of it, a regex before and a validator after, so the part that can't be reasoned
about is sandwiched between two parts that can.

What that buys is tolerance of phrasing. The same six slots come back whether the
fields arrive in a different order, in Indonesian, in English, in both at once, or
abbreviated down to something nobody would write in a manual. All of these fill the
form:

> `booking kamar 201 untuk 2 orang 2 malam`
> `2 nights 2 pax room 201 include breakfast`
> `kamar 212 utk bpk Hendra, 1 mlm`
> `room 305 Ms Lim 3 nights 2 pax`
> `3 malam di 305, sarapan included`

Nobody wrote a rule for `utk` or `a/n`, and the room can appear first, last or in the
middle. The guest name is the slot that earns the model its place: pulling
`bpk Hendra` or `Ms Lim` out of a sentence with no marker in front of it is the one
thing here that has no tidy rule behind it.

The price is that when it is wrong, it is wrong in ways you can't predict from reading
the code. `Hana jo 5 nights deluxe breakfast` came back with the guest count set to
five, copied off the nights, and no deluxe holds five people, so the booking was
refused. Nothing in the sentence asked for five guests. That is why counts now have to
be backed by a unit word before the resolver will use them.

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
Capacity catches four people in a studio. Availability is checked outside the
resolver, once in `MainFrame` before the dialog opens and again in `HotelManager` when
the booking is saved, by the same rules that reject a bad manual entry, so a room
that is taken or booked later in the stay never reaches a saved booking. A guest
name is only kept if it appears in what the clerk typed, so a name the model made up
gets dropped.
If a room number doesn't exist but a tier does, it falls back to the first free room
in that tier instead of giving up.

## Does it actually work?

I wrote down 101 sentences with the JSON I expected back from each, then measured,
because I didn't want to guess. They're in
`src/test/resources/eval/booking-sentences.json`, and `./gradlew evalModels` scores a
model against all of them.

The number I care about is the last row of each table. Field accuracy is diagnostic,
but what reaches the receptionist is whether they got the right room, or a correct
refusal, and that is what "right room" counts.

Gemma 4 E2B, the same 101 sentences on both devices:

| | GPU (RTX 3060) | CPU only (Ryzen 7 5800H) |
|---|---|---|
| latency p50 | 695 ms | 4719 ms |
| latency p95 | 804 ms | 5701 ms |
| no reply | 0 | 0 |
| room number | 95.0% | 96.0% |
| tier | 91.1% | 90.1% |
| guests | 93.1% | 91.1% |
| nights | 100% | 100% |
| breakfast | 100% | 100% |
| guest name | 92.1% | 91.1% |
| right room | 101 / 101 | 101 / 101 |

Accuracy is the same either way, within a sentence or two on every field, which is
what you would expect because the hardware doesn't change the answer, only how fast it
arrives. Seven times faster on the GPU is the whole difference.

Most of the field misses never reach anybody. The resolver canonicalises tier
capitalisation, drops a guest count with no guest word behind it, drops a name that
doesn't appear in what was typed, and falls back to the tier when a room number is
wrong. That is why the right-room row stays at 101 while individual fields sit in the
low nineties.

Three models over the same 101 sentences, on the GPU:

| model | p50 | p95 | right room |
|---|---|---|---|
| gemma4:e2b-it-qat | 695 ms | 804 ms | 101 / 101 |
| qwen3.5:2b-q8_0 | 834 ms | 942 ms | 85 / 101 |
| qwen3.5:0.8b | 613 ms | 729 ms | 90 / 101 |

And CPU only, which is the configuration a front desk PC would actually run:

| model | p50 | p95 | right room |
|---|---|---|---|
| gemma4:e2b-it-qat | 4719 ms | 5701 ms | 101 / 101 |
| qwen3.5:2b-q8_0 | 7196 ms | 7700 ms | 82 / 101 |
| qwen3.5:0.8b | 3510 ms | 3803 ms | 91 / 101 |

The bigger Qwen is slower and worse at the same time, so that choice needs no
argument. The small one is the fastest thing here on a CPU and it scores 2.0% on
nights, two correct out of 101, so buying speed with it is not an option. Gemma sits
either side of five seconds on a CPU, under it at p50 and over it at p95, which is
close enough to the limit that it is listed as an open problem further down. Each model is evicted
from memory and given an untimed warm-up before its rows, so none of them is measured
while another is holding VRAM.

The prompt got measured the same way, on the 96-sentence version of this set. Fixing
one bug, `sarapan tidak usah` was booking breakfast instead of refusing it, took three
attempts, and the version that scored better on three of the six fields is the one I
threw away. It had also started copying night counts into guest counts, which made the
resolver refuse a booking I'd typed myself. Prompt edits aren't local, and I wouldn't
have caught that by reading it.

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

It's a 4.3 GB download and takes about 3.9 GB of RAM once loaded, which is what makes
it viable on an 8 GB front desk PC with no graphics card. There's nothing else to set
up, no Modelfile and no config. The prompt and the JSON schema live in the code, so
the model is used exactly as it comes.

Gemma 4 E2B is the default, not a requirement. Any model Ollama can serve will do,
because the prompt and the schema are sent with every request rather than baked into
the model:

```bash
./gradlew run -Dhotel.ai.model=qwen3.5:0.8b
```

Nothing is recompiled and nothing else changes. `./gradlew evalModels
-Dhotel.ai.models=<a>,<b>` scores several against the same 101 sentences, which is how
the tables above were produced. In PowerShell the argument needs quoting,
`"-Dhotel.ai.model=..."`, or the shell splits it.

If Ollama isn't installed the app still runs, the box just won't fill anything in.
There's also an AI checkbox to turn it off and fill every form by hand.

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
│   ├── ModelHealth            is a model answering, and what to say if not
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

- It's slow without a GPU. Around 4700 ms on CPU, and it moves by several hundred
  milliseconds between runs, so it sits either side of the 5 second mark rather than
  safely under it. The PC I tested on is also faster than a real front desk PC would
  be. On my GPU the same model answers in 695 ms. Fine
  for a demo, marginal for a real desk. Running Ollama on a better PC in the back
  office would fix it, which is why the endpoint check allows a LAN address.
- I haven't run it on the actual 8 GB desktop yet. Every number in here is from my
  own PC.
- The first request after a quiet spell takes far longer than any of the numbers
  above. I measured 11 seconds once with the model already loaded, because the GPU had
  dropped to idle and had to spin back up.
- I've argued in here that regex would be a losing fight against mixed Indonesian and
  English, and I do believe it, but I haven't written a regex parser and scored it on
  the same 101 sentences. Until I do, that's a claim and not a result.
- The GUI has only been tested on Windows.
- Dates work in whole days, so "nights stayed" ticks over at midnight rather than at
  a proper check-out time.
- Booking IDs count up from a static counter, so they're only unique per run.
