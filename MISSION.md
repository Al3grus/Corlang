# Corlang — what we are and what we refuse to be

The source of truth for every word we publish: store listing, landing page, release notes,
paywall copy, support replies. If a sentence you are about to write is not supported here, it is
either wrong or this document is out of date. Fix one of the two before shipping it.

Last verified against the code and content: **2026-08-24**.

---

## One sentence

Corlang is a serious, CEFR-structured course to B1 in languages the big apps ignore, that you buy
once, that collects nothing about you, and that shows you no advertising.

## Who it is for

Someone learning a language **on purpose**, with a destination: an exam, a move, a partner's
family, work. They want a course, not a game. They will do the work if the work is laid out
properly.

Explicitly **not** for the casual streak-chaser browsing for something to poke at on the bus.
That person is Duolingo's, defended by a billion-dollar brand and 50 million daily users, and
every feature designed to attract them costs us the learner we are actually for.

---

## What we stand for

**1. You buy it, you own it.**
One payment per course, forever, restored on any device. The entire competitive field is
subscriptions. This is our loudest single difference and we do not trade it away.

**2. The app collects nothing.**
No accounts. No sign-up. No email. No analytics. No advertising identifier. Progress lives in a
local database on the phone and goes nowhere. This is not a promise about our intentions, it is a
property of the build: there is no network call that sends learner data anywhere. The only
outbound traffic is the AI tutor's own messages, for subscribers who chose it.

**3. No advertising. Ever.**
Not a growth stage we have not reached yet, a permanent decision. See "Settled decisions".

**4. Content is a course, not a stream of exercises.**
A0 through B1, in order, with real assessment at every level: lesson quizzes, readiness checks,
mock exams. A learner should be able to sit a real exam afterwards and recognise the shape of it.

**5. We say the mechanism, never "science-backed".**
Every competitor claims to be scientifically proven. The phrase is worthless. We name what we
actually run — FSRS spaced repetition, retrieval practice, teach-back explanation, interleaved
review — and any claim we make is checkable in the code.

**6. We serve the languages the giants refuse.**
Duolingo has no Croatian course and is not building one. That is the whole opportunity.

---

## How we differ, with the evidence

| | Corlang | The field |
|---|---|---|
| Payment | Once, per course, €4.99 / €12.99 / €24.99 cumulative | Subscription everywhere: Ling $14.99/mo ($149.99 lifetime), Babbel $15.99/mo ($599 lifetime), Mondly $89.99 lifetime, Busuu $6–15/mo |
| Data collected | None. No account exists | Accounts, email, analytics, ad IDs |
| Advertising | None | Duolingo ~8% of revenue from ads; most free tiers carry them |
| Structure | CEFR A0–B1, quizzes + readiness + mock exams | Themed packs and streaks; CEFR mapping usually loose or absent |
| Croatian depth | 344 lessons, 3,440 words | Ling ~200 lessons (current #1 for Croatian); no CEFR-to-B1 course exists |
| Offline | Full course bundled; only the AI tutor needs a network | Mostly online-first |
| AI tutor | Bounded to the words the learner has actually met | General chatbot that talks over the learner's head |

**Verified course facts** (recount before quoting; `MISSION.md` is checked by hand, not by a test):

- Croatian: 344 lessons, 3,440 deck words, levels A0 A1 A2 B1
- Portuguese: 250 lessons, 2,568 deck words, levels A0 A1 A2 B1
- Free window: the first 10 lessons of every course, which is all of A0
- Hidden and not for sale: French, German, Italian, Spanish

---

## Settled decisions — do not relitigate without new evidence

**No advertising** (2026-08-24). European Android rewarded video is about $5.00 eCPM, so one ad
per lesson at one lesson a day earns **€0.14 per user per month**. Matching a single €24.99 sale
would take roughly 5,000 ad views, about thirteen years from one learner. Reaching €100/month
needs ~20,000 impressions a day. The cost is a GDPR consent dialog, an advertising identifier on
the Play data-safety form, a third-party SDK in the APK, and the deletion of two of our four
differentiators — to buy pennies. Duolingo takes only ~8% of revenue from ads and does it at
50.5M DAU; it is a subscription business that happens to show ads.

**No daily lesson cap for free users** (2026-08-24). One lesson a day gives the whole Croatian
course away inside a year, which makes a one-time purchase pointless. It does not extend our
model, it replaces it with a subscription, and it aims at Duolingo's customer instead of ours.

**No annual AI plan** (2026-07-18). Model costs can move within a year; a sold annual locks us
into serving twelve months at today's economics.

**One-time unlocks are cumulative.** A course is a ladder. Owning A2 without A1 is a state that
should not exist, so the top level's product is the whole course and there is no separate bundle.

---

## Claims we may make

- "Buy once. Yours for good." — true, Play one-time products, restorable.
- "No accounts. No sign-up. Nothing about you leaves the phone." — true of the app.
- "No ads." — true and permanent.
- "A complete CEFR course from beginner to B1." — true for Croatian and Portuguese.
- "Spaced repetition that schedules each word with FSRS." — true, `data/Fsrs.kt`.
- "Works offline." — true of the course; say "the AI tutor needs a connection" whenever the tutor
  is in the same sentence.
- "Prepares you for official exams." — true. We prepare; we award nothing.

## Claims we may never make

- **"Scientifically proven"** or "backed by research" without naming the mechanism. Name FSRS,
  retrieval practice, or teach-back, or say nothing.
- **"Fluent"**, "fluency", or any promise of an outcome the learner controls.
- **Any B2 or C1 claim.** Neither course has a B2 lesson.
- **"Everything is free"** or "the whole course stays free." Ten lessons are free. This sentence
  shipped once inside the app and was false; it is the reason this section exists.
- **A certificate, diploma, or qualification.** We are not an awarding body.
- **Native-speaker-reviewed**, until the native reviews are actually back.
- **Comparative claims about competitors' prices** without re-checking them. The table above is
  dated; prices move.

---

## The tone

Plain, exact, unexcited. We are talking to an adult who has been sold language learning before
and did not finish it. No exclamation marks, no "amazing", no emoji in body copy, no urgency
tricks, no fake scarcity. Short sentences. Concrete numbers instead of adjectives: "344 lessons"
beats "comprehensive". If a claim needs a superlative to land, it was not a claim.

Never disparage a competitor by name. State what we do; the comparison makes itself.
