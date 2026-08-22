# Corlang — Master pending checklist

Single source of truth for everything left to do, as of **2026-08-21**. Pick items one by one.
Deep detail lives in: `road-to-play.md` (Play steps + store copy), `server-ai.md` (subscription
verification setup), `monetization-roadmap.md` (pricing + product IDs), `PRE-LAUNCH-TODO.md`
(content/QA log). Legend: **(browser)** = Play Console / Google Cloud, you do it · **(me)** =
ask Claude to do it · **(you, phone)** = on-device.

---

## 📍 Where this actually stands (2026-08-21)

**Content is finished and machine-clean for both live courses.** hr and pt each pass every gate
there is: `check_wrapup` (344/0 and 240/0), `check_hr`/`check_pt`, `check_batch`,
`check_deck_examples`, `check_deck_sync`, `proctor`, and the 175-test Kotlin suite. fr, de, it and
es are authored but HIDDEN from `content/_index.json` and are not part of this launch.

**The Play AAB builds, signed, from the current source**: `./gradlew :app:bundlePlayRelease` →
`app/build/outputs/bundle/playRelease/app-play-release.aab` (v0.47.2, versionCode 176). Verified
on that artifact: signed, ships only hr and pt, carries `com.android.vending.BILLING`, and does
NOT carry `REQUEST_INSTALL_PACKAGES` (the self-updater is compiled out of the play flavor, which
Play requires). `corlang.devPremium=true` in local.properties is sideload-only and cannot reach
it: the play flavor hardcodes `DEV_PREMIUM=false`. **Rebuild it right before uploading** so the
versionCode is fresh.

**Exactly one thing blocks the store listing, and only you can do it: the screenshots.**
Everything else on the critical path is browser work in Play Console.

---

## ✅ Already done this session — DO NOT redo
- Deep code audit (36 findings) fully fixed; 73/73 tests green (v0.20.30–32).
- AI proxy token **rotated** (old leaked token is dead/403); worker hardened + deployed:
  per-IP 300/day + global 3000/day rate limits, empty-secret deny, timing-safe auth,
  field allowlist, byte-accurate body cap.
- **Billing layer built** (v0.20.32): Play Billing 7.1.1, `BillingManager`, `PaywallScreen`,
  level gate, Get Premium, entitlement layer. Subscription + one-time unlocks.
- **30 msg/day per-subscriber cap** live in worker (keyed on Play sub token), disclosed
  on the paywall. Real cost
  measured: hr €0.0036/msg, pt/fr €0.0007/msg.
- **Server-side Play subscription verification** coded + deployed (v0.20.33), **dormant** until
  the `PLAY_SERVICE_ACCOUNT` secret is added (see Track B).
- Pricing research done (deep-research, 23 verified claims) + plan written.
- Play AAB built: `app/build/outputs/bundle/playRelease/app-play-release.aab` (rebuilt
  2026-08-21 at v0.47.2 / vc 176 — always rebuild right before uploading, step A5).
- Gated-preview sideload APK (DEV_PREMIUM=false) built + sent to you.
- **2026-07-19 (vc 120-134)**: all three courses at 250 lessons + full checkpoint set
  (quiz/readiness/mock per level), decks >=2500 words, adaptive 3-item-band placement,
  review-limit setting, per-course Reset progress, em-dash purge, strict accent grading,
  cross-language stale-frame fix, A0-default fixes; 108 tests green. See the session-resume
  memory for the full log.

---

## ✅ DONE 2026-08-22 — Portuguese has an A0, authored on top

Shipped. Recorded because the route here was not the obvious one and the obvious one is a trap.

**The trap.** Portuguese days 1 to 10 already read like an A0 (sounds, greetings, introductions,
ser/estar, numbers, gender, family, verbs, questions), so relabelling them looks free. It is not:
pt A1's floor is 45 lessons and that floor is a claim about how much teaching reaching A1 takes,
not a bookkeeping total. Relabelling leaves A1 claiming to deliver A1 in 35, and
`everyCourseMeetsTheWeightedLessonFloor` fails it. Correctly.

**What was built instead.** Ten NEW lessons before day 1, a survival onramp rather than a second
grammar onramp: arriving, the cafe, paying, directions, transport, eating out, shopping, repair
phrases, emergencies, checkpoint. Whole chunks, no conjugation tables. Every paid level kept
every lesson it was authored with, so the course is now **A0 10, A1 45, A2 70, B1 125 = 250**.

Why survival rather than phonics: Croatian needs 16 lessons of A0 because Croatian cannot be
read until you know 30 letters and eight digraphs. Portuguese can be read on sight. What a
beginner here lacks is the six transactions that fill a day, and that is also the content most
likely to make somebody keep the app.

**The deck was not touched.** Word ids are the words themselves, and the first hundred deck
entries are already the greetings and courtesy set, which is exactly what an A0 learner should
meet in Review. A new A0 vocabulary pack would have duplicated frozen SRS ids for no gain, and
inserting one would have moved every later word's introduction point.

**What this cost elsewhere:** every old day renumbered +10 (weeks recomputed), placement bands
shifted +10 with a new A0 band added, an A0 level quiz written, and the `levels.json` A0 entry
(which had been authored long ago and used by nothing) finally drives something.

Guards added while building, worth keeping in mind for the next course:
- The generator asserts every new taught string is unique against all 3241 existing ones, and
  against the other A0 days. It caught 8 real collisions on the first run.
- Learner-facing titles are English by rule, including activity titles.
- A placement band is one ability probe, so all four of its items share a difficulty.
- A quiz prompt may not restate a lesson's own exercise prompt, or it tests the quiz.

## 🟡 BLOCKED — Croatian A0 survival rework, waiting on 70 deck words

Diagnosed, authored, and stopped one step short. **The lessons are written**:
`docs/drafts/hr-a0-survival-lessons.json`, seven of them, checked collision-free against all
6636 taught strings already in the Croatian course.

**Why it is worth doing.** Croatian's free level spends eleven of sixteen lessons on grammar
paradigms, and its objectives say "recognise", "know", "understand" where Portuguese A0 now says
"you will order and pay". Paying first appears on day 69, tickets and buses on day 24,
restaurants on day 22, all behind the paywall. A learner who finishes the entire free Croatian
course can recite the accusative and cannot buy a coffee. A0 is the only part of the course
anyone sees before paying, so this is the conversion surface.

**The plan, which works.** Keep the alphabet, greetings and the milestone check; move the
thirteen grammar lessons UP into A1, where cases and conjugation belong by any CEFR reading and
where a growing level can never break a floor; add the seven survival lessons. That gives
A0 10, A1 74, A2 96, B1 171 = 351, matching Portuguese's ten-lesson A0.

**The blocker, exactly.** `everyDeckCoversTheWholeCourse` requires deck >= lessons x 10, and
Croatian sits at **3440 words for 344 lessons: zero headroom**. Portuguese had 68 spare words,
which is the only reason its A0 landed without touching the deck. Seven more lessons need
**70 more Croatian words**, each with the unique, cloze-safe example `check_deck_examples.py`
demands.

**The open question is WHERE those words go**, and it is a real decision, not a detail:

- *Front of the deck*, as an A0 survival pack. Pedagogically right, and it also fixes three
  pack-alignment offenders the shift otherwise creates (`adjectives` +17, `personality-emotions`
  +17, `time-frequency` +16 against a limit of 15). But it contradicts "top-up packs are
  APPENDED, never inserted" and moves every existing word's introduction point back by seven
  lessons. No id is renamed or removed, so no SRS history is orphaned.
- *End of the deck*, honouring the rule. Then the survival words are introduced around lesson
  344, which is absurd for "trajekt", the 70 words have to be B1-level instead, and the three
  offending packs each need a `fromDay` set.

`fromDay` cannot resolve this: it only ever DELAYS a pack, never advances one.

## 🔴 TRACK A — Get to Play testers (critical path, in order)

1. **(browser)** Create the app in Play Console (name "Corlang", App, Free).
2. **(browser)** "Set up your app" tasks — all required before any release:
   - App access: **restricted** — no login exists, but the course is payment-gated past the
     free window, so a reviewer needs promo codes. Exact wording in `docs/road-to-play.md`
     §2. Promo codes need the products created AND a build uploaded first, so this task
     closes after the Internal-testing upload, not before.
   - Ads: no ads.
   - Content rating questionnaire (educational → Everyone/PEGI 3).
   - Target audience: 13+.
   - Data safety: **answers are drafted field by field in `docs/play-data-safety.md`** — three
     declared types (Personal info/Name, Messages/Other in-app messages, Financial info/Purchase
     history), the reason each of the others is NOT declared, and the four overview answers.
     Verified against the built manifest and the dependency list rather than from memory.
   - Privacy policy URL: `https://raw.githubusercontent.com/Al3grus/Corlang/main/PRIVACY.md`.
3. **(browser + assets)** Main store listing. Copy is drafted in `road-to-play.md`. Needs the
   assets from Track C (icon, feature graphic, screenshots).
4. **(browser)** Create the 4 billing products — **IDs must match exactly** (in `road-to-play.md`
   / `monetization-roadmap.md`):
   - Subscription `corlang_ai_premium`: ONE base plan `monthly` €9.99 with the **3-day
     free-trial offer** on it (Google's minimum; the trial spends real tokens). **No annual plan** (decided 2026-07-18: AI models/costs can
     shift within a year; monthly keeps repricing freedom). The app requests only `monthly`.
   - Managed products, **six, three per language**: `unlock_hr_a1` €4.99 · `unlock_hr_a2`
     €12.99 · `unlock_hr_b1` €24.99, and the same three for `pt`. Each grants its level and
     everything below it, so the B1 product is the whole course and there is no `_all`.
     **No `unlock_*_b2`** — neither course has a B2 lesson.
   - Activate all; accept Google's regional prices.
5. **(browser)** Upload the AAB to **Internal testing** (live in minutes, billing works).
   **This comes BEFORE step 4**: Play does not enable the in-app products page until a build
   containing the Billing Library is published to a track.
6. **(browser)** License testing (Setup → License testing): add your + testers' Gmail addresses
   so their purchases are free / auto-refunded.
7. **(browser)** Add testers + share the opt-in link.
8. **(you, phone)** Install from the Play opt-in link with a license-tester account → **see the
   real payment popups**, buy A2 → unlock, subscribe → Learn tab appears. First real end-to-end
   billing test.
9. **(browser)** Start **Closed testing** with **≥12 testers opted in for ≥14 consecutive days**
   — the production-eligibility clock for personal accounts created after 2023-11-13. Re-verified
   against Play Console Help on 2026-08-21 (it was 20 testers until Google cut it to 12 in
   December 2024, so check the Console rather than any blog if the number looks different).
   **This is the long pole: start it the day Internal testing works, and run it in parallel with
   everything else.** The 14 days are continuous, and testers leaving resets your standing.

---

## 🔐 TRACK B — Security hardening (before PUBLIC production; NOT needed for testers)

1. **(browser + 1 CLI)** Google Cloud **service account** for subscription verification — the
   4 steps in `server-ai.md` → "Play subscription verification":
   - Enable Google Play Android Developer API in the linked Cloud project.
   - Create service account `corlang-play-verifier`, download JSON key.
   - Grant its email "View financial data / app info" in Play Console → Users and permissions.
   - `cd server/ai-proxy && wrangler secret put PLAY_SERVICE_ACCOUNT` (paste JSON at hidden prompt).
   - Verify: invalid sub token → 403, real subscriber → 200. (Turns the dormant worker code ON.)
2. ✅ **Anthropic spend alert + limits set** (2026-07-18); auto-reload confirmed off is the
   guard that bounds worst-case token abuse to the prepaid balance.
3. **(browser, optional)** Cloudflare WAF rate-limit rule on the worker route (belt-and-suspenders
   over the KV daily caps).

---

## 🎨 TRACK C — Creative assets (blocks the store listing, step A3)

All assets live in `docs/store-assets/` — see the README there.

1. ✅ **App icon** 512×512 — `docs/store-assets/play-icon-512.png`. Generated from the same
   Orbit Core geometry as the launcher icon, so store and phone match.
2. ✅ **Feature graphic** 1024×500 — `docs/store-assets/feature-graphic-1024x500.png`.
   Both are reproducible via `docs/store-assets/make_assets.py` (Pillow).
3. ⬜ **Phone screenshots** ×4–8 **(you, phone)** — **THE ONE BLOCKER ON THE LISTING.** Shot list
   and Play's size rules are in `docs/store-assets/README.md`; drop the PNGs into
   `docs/store-assets/screenshots/` (the folder does not exist yet, just create it). Shoot on your
   own device, which has real streak and progress data: a fresh install's empty states sell
   nothing. Then **(me)**: check dimensions and draft the per-screenshot captions.

---

## 🗣️ TRACK D — Content quality (native-speaker review; parallel, non-blocking)

**Only hr and pt matter for this launch** — fr, de, it and es are hidden, so their review docs can
wait until they are unhidden. Nothing here blocks Internal or Closed testing.

1. Croatian native review (friend) — `docs/review/hr-content-review.html`.
2. Portuguese native review (sister) — `docs/review/pt-content-review.html`.
3. French native review (friend) — `docs/review/fr-content-review.html`.
4. German native review (reviewer found) — `docs/review/de-content-review.html`.
5. Italian native review — **(browser) find a reviewer, none assigned yet** —
   `docs/review/it-content-review.html`.
   **Docs regenerated 2026-08-03 against current content (all 5 shipped courses, dialogue
   scripts now included as a 5th section — previously only vocab/grammar/cheatsheet/quizzes
   were covered, so ~300+ machine-authored lesson dialogues had never surfaced to a
   reviewer). Reviewer-facing instructions + reporting format: `docs/review/REVIEWER-INSTRUCTIONS.md`.**
6. **(me)** Fold returned corrections into the JSON; tests + ai-variety-eval re-verify.
7. **(you, phone)** TTS pronunciation spot-check per language.

---

## 🧪 TRACK E — QA on a real device (before production)

1. Full end-to-end: onboarding → placement → lesson (all step types) → review → quizzes →
   mock exam → cross midnight (streak) → switch languages.
2. Verify streak resets correctly after a genuinely missed day.
3. Backup export/import round-trips cleanly.
4. Confirm the paywall/purchase/unlock flow on the Internal track (overlaps Track A step 8).

---

## 🚀 ON THE DAY YOU GO LIVE (do not let these rot)

The app and the site both currently SAY they are not public. Both are true today and both become
wrong the moment production goes live, so they are listed here rather than trusted to memory.

1. **The landing page still says "in testing on Google Play and is not publicly available yet".**
   It is in `tools/site/build_site.py`, marked with a `LAUNCH SWITCH` comment. Replace that note
   with the Play link, then:
   ```bash
   python tools/site/build_site.py
   npx wrangler pages deploy site --project-name corlang --branch main --commit-dirty=true
   ```
   Editing `site/*.html` by hand does nothing: those files are generated and overwritten.
2. **README "Status" section** says the same thing. Update it too.
3. **Play Console privacy policy URL** → `https://corlang.app/privacy/`. Use this, not the raw
   GitHub link: that link was the last thing keeping the repo public.

   The site is LIVE at https://corlang.app/ (Cloudflare Pages project `corlang`, apex and
   www both CNAME to corlang.pages.dev, proxied; certificate active 2026-08-21).
4. **Then, and only then, the repo can go private** — the raw GitHub privacy URL is the last
   thing depending on it (the self-updater that also depended on it was removed in v0.48.0).
   `releases/` becomes deletable at the same time: nothing reads `version.json` any more.

---

## 🔮 TRACK F — Future / optional (not launch blockers)

1. **Voice tutor** — on-device STT/TTS → Claude text (≈ free on top of current cost; the app
   already has `TtsManager` + `SpeechInput`). A normal feature release.
2. **Realtime "Lily-style" voice** (streaming audio model) — deferred, genuinely expensive.
3. One-page **corlang.app** site (Cloudflare Pages) — nice-to-have; move privacy policy there.
4. **Repo private?** — the two blockers are GONE as of 2026-08-21: the self-updater was removed
   (v0.48.0) and the privacy policy now lives at https://corlang.app/privacy/, so nothing depends
   on the repo being public once Play Console points at that URL. `releases/` can be deleted at
   the same time; nothing reads `version.json` any more. Original note, kept for the reasoning:
   going private breaks the raw.githubusercontent updater and
   Play APKs are extractable anyway. Revisit for content-IP *after* testers move to the Play track.
5. `tools/provider-bench.py` — compare Gemini/GPT cost/quality (needs your keys), if ever curious.

---

## Accounts / services summary
| Service | Status | Action |
|---|---|---|
| Google Play Console | ✅ have ($25, verified) | create app + products + tracks (Track A) |
| Google Cloud (service account) | ⬜ create | Track B step 1 |
| Anthropic | ✅ have (Corlang acct, ~$9 prepaid) | ✅ spend alert + limits set |
| Cloudflare (worker + KV) | ✅ deployed | optional WAF rule (Track B step 3) |
| corlang.app domain + proton email | ✅ have | optional website (Track F) |

## Key facts the next session needs
- Latest release: **v0.47.2 / versionCode 176**. Live courses: **hr and pt only**.
- Worker: `https://corlang-ai-proxy.ricardo-infante.workers.dev`; secrets `ANTHROPIC_API_KEY`,
  `APP_AUTH_TOKEN` (rotated), KV `RATE_KV` id `7869cfd96a8f4851905855404e6d4df0`; add
  `PLAY_SERVICE_ACCOUNT` in Track B.
- Package name: `com.corlang.app`. Product IDs: `corlang_ai_premium` (base plan `monthly`
  only) plus one `unlock_<lang>_<level>` per paid level of each live course — the app derives
  these from content, so the full list is whatever `BillingManager.levelProductIds` builds.
  Unlocks are cumulative, so the top level's id is the whole-course bundle and there is no
  `_all`. Prices in `docs/monetization-roadmap.md`.
- **`local.properties` is currently `devPremium=true`**, which affects the SIDELOAD build only
  (friends keep AI). The play flavor hardcodes `DEV_PREMIUM=false`, so no Play build can ship
  free Premium by accident.
- Build: `./gradlew` IS in the repo; JAVA_HOME = Android Studio's JBR (JDK 21).
  Prices are NOT in code — they come from Play Console, so changing them never needs a rebuild.

---

## 📬 Where the test-invite emails go

The landing page's "Ask for a test invite" dialog posts to `/api/invite`
(`functions/api/invite.js`, a Cloudflare Pages Function). Addresses are stored in a Cloudflare
**KV namespace** on your own account, and nowhere else. No third-party form service is involved.

- Namespace: **`corlang_invites`**, id `8126fcfb51954368a9ba136df17fb5af`
- Bound to the Pages project `corlang` (production) as **`INVITES`** — declared in the root
  `wrangler.toml`, so a redeploy keeps it.
- One key per address, `invite:<email>`, holding `{email, at, country}`. A repeat submission
  overwrites rather than piling up.
- Rate-limit keys `rate:<ip>` also live there and expire after an hour.

Read the list:

```bash
npx wrangler kv key list --namespace-id 8126fcfb51954368a9ba136df17fb5af --remote
npx wrangler kv key get  --namespace-id 8126fcfb51954368a9ba136df17fb5af --remote "invite:someone@example.com"
```

**`--remote` is not optional.** Wrangler v4 reads LOCAL storage by default, so without it every
command returns empty and looks exactly like a broken endpoint — including `delete`, which will
cheerfully report success while leaving the real key in place.

Disclosed in `PRIVACY.md` under "The website": what is stored, why, that it goes nowhere else,
and that asking gets it deleted. If you ever switch this off, remove that section too.
