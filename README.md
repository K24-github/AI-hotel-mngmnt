# AI-Assisted Hotel Management System

<img width="1677" height="1010" alt="image" src="https://github.com/user-attachments/assets/8bc03f14-af22-4038-973f-bdcfd4a0a68b" />


This is a front-desk app for a small hotel, written in Java Swing. A receptionist
picks a room off the grid, checks a guest in, extends or upgrades the stay, checks them
out. There's a dashboard for occupancy and revenue.

It started as a final exam project at university. The point of the work since then is
practising putting AI into a codebase that already exists, instead of building
something around a model from scratch. The rules and the screens were already there and
the model had to fit around them, which turned out to be most of the work.

The part added since is a text box above the room grid. Instead of reading a room
number off a note, finding the tile and filling the form in by hand, you type the
booking the way you'd say it out loud and the form arrives filled in. It works, and
most of this README is the checking that it works.

It needs a model because what someone types at a front desk is a mess. Any word order,
English or Indonesian or both at once, whatever abbreviations that person uses, a name
nobody has seen before, a phone number sitting in the middle of it. There's no format
to agree on in advance. But what has to come out is fixed and small, six values and no
more, so it's an open input mapped onto a closed output.

The closed half is what makes it safe. The reply gets constrained to that schema,
anything that doesn't fit is binned, and everything past those six values stays in
plain Java with tests behind it. The model proposes and gets no further.

It runs on the machine at the desk, so guest details never leave the building and a
check-in doesn't wait on a network. Gemma 4 E2B (`gemma4:e2b-it-qat`) through Ollama,
picked by measuring it against the others instead of guessing.

## Structured information extraction with Gemma 4 E2B through Ollama

The receptionist types:

> `Make a booking at room 201 deluxe for 2 guests for 2 nights, breakfast included`

and the check-in dialog opens with room 201 selected, guests set to 2, nights set to
2, and breakfast ticked. The clerk checks it over, fixes anything wrong, and presses
OK. Nothing is saved before that.

They can also ignore the box entirely, click room 201, and type everything by hand.
That path stays exactly as it was. If the parsing fails the dialog doesn't open and
the status line says so, which leaves them where they'd have been anyway. The feature
can save typing but it can't get in the way of a booking.

What Gemma 4 E2B is doing here is information extraction. It reads one unstructured
sentence and returns six typed fields: room number, tier, guest name, guests, nights,
breakfast. That is the whole job. It doesn't decide anything, doesn't look anything up,
and doesn't talk to the rest of the app.

`breakfast` isn't a word anywhere in the sentence, it's true or false depending on
whether a refusal word sits next to sarapan. `tier` comes back as Deluxe whether you
typed deluxe or DELUXE. So the model is writing the values, not pointing at words it
found, which is the generative side of information extraction.

It runs under Ollama as a separate local process and the app talks to it through
LangChain4j. The call is one interface with one method on it, `BookingExtractor`.
LangChain4j builds the JSON schema from the `BookingDraft` record, sends it to Ollama
as an output constraint, and gives back the filled record, so there's no request to
build and no JSON to unpack. Ollama and LangChain4j both call this structured output.
Underneath it is constrained decoding, which masks out any token that would break the
schema before the model can pick it. `temperature` is 0 and thinking is off. The reply
can't come back as prose or broken JSON. It can still be wrong about a value, it just
can't be wrong about the format.

The schema LangChain4j writes on its own didn't work here. It marks nothing as
required and lets no field be null, so Gemma just skipped things. `kamar 201 2 orang 2
malam` came back with guests missing from the JSON entirely. `Hana jo 5 nights studio
breakfast` came back with roomNumber set to the word null, as a string.

So the request goes through a transformer first. Every field goes back in as required,
and every field is allowed to be null. Now it has to answer all six, and null counts as
an answer. That's `everyFieldOrNull` in `LangChainBookingParser`.

Before the reply becomes a draft it goes through an output guardrail that checks both
counts against the sentence. No guest word, no guest count, and the same for nights.
That's the only place JSON is still handled by hand, because the guardrail sees the
reply before LangChain4j has turned it into a record.

This isn't parsing in the grammar sense. A parser runs rules written by hand, so you
can point at the rule that produced a field. Here the model writes the values and
there's no rule to point at. So the two steps that can be reasoned about sit either
side of it, a regex before and a validator after.

What you get for that is not caring how it's phrased. Same six fields back whether the
order changes, whether it's Indonesian or English or both, whether it's abbreviated
down to something nobody would put in a manual. All of these fill the form:

> `booking kamar 201 untuk 2 orang 2 malam`
> `2 nights 2 pax room 201 include breakfast`
> `kamar 212 utk bpk Hendra, 1 mlm`
> `room 305 Ms Lim 3 nights 2 pax`
> `3 malam di 305, sarapan included`

There's no rule anywhere for `utk` or `a/n`, and the room number can be first, last or
in the middle. The guest name is the field that earns the model its place. Getting
`bpk Hendra` or `Ms Lim` out of a sentence with nothing marking where the name starts
is the one thing here with no tidy rule behind it.

The catch is that when it's wrong you can't predict it from reading the code.
`Hana jo 5 nights deluxe breakfast` came back with the guest count set to five, copied
off the nights. No deluxe holds five people so the booking got refused. Nothing in that
sentence asked for five guests. That's why a count now needs a unit word behind it
before the resolver will touch it.

Two things aren't the model's job. Write `note:`, `notes:`, `catatan:` or `cttn:`
anywhere and everything after it goes to the notes field. And the phone number gets
pulled out by a regex before the model ever sees the sentence, because a country code
like `+61...` kept coming back as a guest count.

Limits built in:

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

That last one is `BookingResolver` and it's where most of the work went. `findRoom`
checks the room exists, so if the model invents room 999 it goes nowhere. Capacity
catches four people in a studio. A guest name only survives if it appears in what was
actually typed, so a name the model made up gets dropped. If the room number is wrong
but the tier is fine, it falls back to the first free room in that tier instead of
giving up.

Availability is checked outside the resolver, once in `MainFrame` before the dialog
opens and again in `HotelManager` when the booking is saved. Same rules that reject a
bad manual entry. A room that's taken, or booked later in the stay, never reaches a
saved booking.

## Does it actually work?

101 sentences were written down with the JSON expected back from each, then measured
rather than guessed at. They're in
`src/test/resources/eval/booking-sentences.json`, and `./gradlew evalModels` scores a
model against all of them.

The row that matters is the last one in each table. Field accuracy is useful for
working out what's broken, but what actually reaches the receptionist is whether they
got the right room or a correct refusal. That's what "right room" counts.

Gemma 4 E2B, the same 101 sentences on both devices:

| | GPU (RTX 3060) | CPU only (Ryzen 7 5800H) |
|---|---|---|
| latency p50 | 693 ms | 4750 ms |
| latency p95 | 775 ms | 4975 ms |
| no reply | 0 | 0 |
| room number | 93.1% | 93.1% |
| tier | 95.0% | 93.1% |
| guests | 98.0% | 98.0% |
| nights | 95.0% | 95.0% |
| breakfast | 100% | 100% |
| guest name | 94.1% | 94.1% |
| right room | 101 / 101 | 101 / 101 |

Accuracy is the same either way. Five of the six fields come out identical and only
tier moves, by two sentences. That's what you'd expect, the hardware doesn't change the
answer, only how fast it turns up. Seven times faster on the GPU is the whole
difference.

Most of the field misses never reach anybody. The resolver fixes tier capitalisation,
drops a guest count with no guest word behind it, drops a name that wasn't typed, and
falls back to the tier when the room number is wrong. That's why the right-room row
stays at 101 while the individual fields sit in the low nineties.

Three models over the same 101 sentences, on the GPU:

| model | p50 | p95 | right room |
|---|---|---|---|
| gemma4:e2b-it-qat | 693 ms | 775 ms | 101 / 101 |
| qwen3.5:2b-q8_0 | 798 ms | 1167 ms | 57 / 101 |
| qwen3.5:0.8b | 535 ms | 706 ms | 92 / 101 |

And CPU only, which is the configuration a front desk PC would actually run:

| model | p50 | p95 | right room |
|---|---|---|---|
| gemma4:e2b-it-qat | 4750 ms | 4975 ms | 101 / 101 |
| qwen3.5:2b-q8_0 | 7031 ms | 7365 ms | 60 / 101 |
| qwen3.5:0.8b | 2930 ms | 3194 ms | 92 / 101 |

The bigger Qwen is slower and worse at the same time, so that one needs no arguing
about. The small one is the fastest thing here on a CPU and it gets nights right 8
times out of 101, so you can't buy speed with it either. Gemma sits just under five
seconds on a CPU at both p50 and p95, close enough to the limit that it's listed as an
open problem further down. Each model gets evicted from memory and given an untimed
warm-up before its rows, so none of them is measured while another one is holding
VRAM.

The prompt got measured the same way, back when this set was 96 sentences. One bug,
`sarapan tidak usah` was booking breakfast instead of refusing it, took three goes to
fix. The version that got thrown away actually scored better on three of the six
fields. It had also started copying night counts into guest counts, which made the
resolver refuse a perfectly good booking. Prompt edits aren't local, and reading the
prompt would never have caught that.

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
it work on an 8 GB front desk PC with no graphics card. Nothing else to set up, no
Modelfile, no config. The prompt and the JSON schema live in the code, so the model is
used exactly as it comes.

Gemma 4 E2B is the default, not a requirement. Any model Ollama can serve will do,
because the prompt and the schema go out with every request instead of being baked into
the model:

```bash
./gradlew run -Dhotel.ai.model=qwen3.5:0.8b
```

Nothing gets recompiled and nothing else changes. `./gradlew evalModels
-Dhotel.ai.models=<a>,<b>` scores several of them against the same 101 sentences, which
is how the tables above were made. In PowerShell you have to quote the argument,
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
│   ├── LangChainBookingParser the live one, wires up LangChain4j and Ollama
│   ├── BookingExtractor       the one call to the model, declared not assembled
│   ├── BookingDraft           what the model said, untrusted
│   ├── CountsBackedBySentence guardrail, drops a count the sentence never gave
│   ├── SentenceEvidence       what words the typed sentence actually contains
│   ├── BookingResolver        turns a draft into a real room, or says why not
│   ├── BookingProposal        what survived
│   ├── PhoneNumbers, Notes    the parts the model doesn't get to see
│   ├── ModelHealth            is a model answering, and what to say if not
│   └── OllamaConfig           model, endpoint, prompt
└── ui/
    ├── MainFrame.java         wiring and drawing only
    ├── CheckInDialog.java     the check-in form
    ├── DateField.java
    └── UiTheme.java           colours, fonts, button styling on top of FlatLaf
```

It only points one way. `ui` uses `service`, `service` uses `model`, and `model`
doesn't know either of them exist. That's why the tests run without opening a window.
`ai` sits beside the UI and calls the same `HotelManager` methods a button click
already calls, so nothing in `model` had to change for any of this.

`BookingParser` is one method, so the LangChain4j one can be swapped out. The tests use
a scripted parser with no model behind it at all, and the AI checkbox swaps in one that
returns nothing.

## Things fixed after the first version

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
broken the moment a floor was added, and it reads the actual floor numbers now. The room
and booking lists were handed out as the live internal lists, so anything holding one
could scramble the state. The "one booking per room" rule sat in `HotelManager` while
`Room` would happily let you overwrite a booking, so that moved into `Room`.
`MainFrame` was 609 lines with the whole check-in form buried inside an event
handler, and that's split out now.

Buttons also came out white on Windows for a while. The Windows look-and-feel paints
its own button backgrounds and ignores `setBackground`. The app runs on FlatLaf now,
which paints its own buttons and takes whatever colour you set, so the tiles come out
the same green on every platform.

Refusing a booking used to look like failing to read it. `5 mlm Hana Jo 4 guests
5551231 deluxe` got told it could not be read, when actually every field came back
right and the problem was that no deluxe holds four people. It now says
`A Deluxe holds 3, not 4. A Suite holds 5.` The sentence was fine, the hotel just
doesn't have a room that big, and the old message was blaming the sentence for it.

## Still missing

- It's slow without a GPU. 4750 ms at p50 and 4975 ms at p95, so under five seconds but
  not by much, and the test laptop is faster than a real front desk PC would be. On the
  GPU the same model answers in 693 ms. Fine for a demo, marginal for a real desk. Running
  Ollama on a better PC in the back office would fix it, which is why the endpoint check
  allows a LAN address.
- It hasn't been run on the actual 8 GB desktop yet. Every number in here is from one
  laptop.
- The first request after a quiet spell takes far longer than any of the numbers
  above. 11 seconds turned up once with the model already loaded, because the GPU had
  dropped to idle and had to spin back up.
- The argument in here is that regex would be a losing fight against mixed Indonesian
  and English. That's probably true, but no regex parser has been written and scored on
  the same 101 sentences. Until one is, it's a claim and not a result.
- The GUI has only been tested on Windows.
- Dates work in whole days, so "nights stayed" ticks over at midnight rather than at
  a proper check-out time.
- Booking IDs count up from a static counter, so they're only unique per run.
