# Corlang — design brief

Paste the block below into [claude.ai/design](https://claude.ai/design) as the opening prompt.
Everything after the horizontal rule is the prompt itself; this header is not part of it.

Keep this file in step with reality: it states the current tokens, and a design produced against
stale tokens is a design that has to be re-mapped by hand. When `ui/theme/Theme.kt` or `Dimens.kt`
changes, update the tables here in the same commit.

---

I'm building an Android app called **Corlang** and I want you to design its visual system. Research first, then design. Read the whole brief before starting.

## What Corlang is

A serious language-learning app for adults. Six courses (Croatian, French, German, Italian, Portuguese, Spanish), each running from absolute beginner to CEFR B2 — the level real employers, universities and citizenship applications ask for. It teaches through daily structured lessons, spaced-repetition vocabulary review, quizzes, mock exams in the official exam formats, and an optional AI conversation tutor.

It is **not** a gamified toy. No mascot, no cartoon characters, no hearts/lives, no XP economy, no leaderboards. The nearest emotional reference points are a well-made reading app, a good notebook, or a serious study tool — something a working adult opens every day for years. It does keep exactly two celebratory elements, both already built: a streak flame that stays grey until the day's lesson is banked, and a single confetti burst when a day is completed.

Built with Kotlin + Jetpack Compose + **Material 3**. Everything you design must express itself through the Material 3 role system (see Deliverables).

## Who uses it and where

One adult self-learner, aiming from B1 exam level toward near-native fluency. The defining usage context is **short micro-sessions, frequently at the gym between sets**:

- one-handed, thumb-reach only, phone held at arm's length
- interrupted constantly; a session may be 90 seconds long
- sometimes sweaty or imprecise fingers

So: generous tap targets, no fine-precision gestures, no dense tap clusters, primary actions inside the bottom third of the screen, and text that survives being read quickly at a distance. Daily consistency is the product thesis — the design's job is to make returning tomorrow feel obvious and low-friction.

## What already exists (evolve it; don't discard it)

**Brand mark — do not restyle.** "Orbit Core": two concentric arcs with a molten core dot. Ring `#2F7FAE`, core `#C8402C`. In the wordmark, the mark *is* the "o" in Corlang. These two colors are fixed in both themes.

**Typography.** Fraunces (variable serif, OFL licensed) for display/headline/title, and also for reading prose — a serif for lesson content is the single biggest signal of "book, not toy". System sans for UI chrome and body. Current reading style is 18sp with 29sp line-height; the focal sentence of a lesson card is 22sp/32sp.

**Shape and space.** Corner radius 8 / 12 / 16dp (chips & inline boxes / option rows & list cards / hero cards & flashcards). Spacing scale 4 / 8 / 12 / 16 / 24dp.

**Two themes, and the learner picks one explicitly.** The app deliberately ignores the OS light/dark setting — it asks once on first run and offers a switch in Settings. Both themes are first-class and must be designed together. Current values, which you should treat as the starting point to refine rather than gospel:

Dark, "Adriatic on ink":

| role | hex | | role | hex |
|---|---|---|---|---|
| primary | `#8CBAD2` | | background | `#0F1418` |
| onPrimary | `#06293D` | | onBackground | `#E0E3E6` |
| primaryContainer | `#123F5A` | | surface | `#161B20` |
| onPrimaryContainer | `#C9E1F0` | | onSurface | `#E0E3E6` |
| secondary | `#E7AE9D` | | surfaceVariant | `#29323B` |
| onSecondary | `#48160B` | | onSurfaceVariant | `#B8C4CE` |
| secondaryContainer | `#5A281B` | | outline | `#7E8A95` |
| onSecondaryContainer | `#F9DAD0` | | outlineVariant | `#3A4650` |
| tertiary | `#DBC271` | | error | `#FFB4AB` |
| onTertiary | `#362D00` | | onError | `#690005` |
| tertiaryContainer | `#4E4300` | | errorContainer | `#93000A` |
| onTertiaryContainer | `#F5E4AF` | | onErrorContainer | `#FFDAD6` |

Light, "Adriatic on paper" — warm beige and brown, deliberately **not** a grey/white inversion:

| role | hex | | role | hex |
|---|---|---|---|---|
| primary | `#2A6183` | | background | `#F6F0E6` |
| onPrimary | `#FFFFFF` | | onBackground | `#2B2118` |
| primaryContainer | `#CFE2EE` | | surface | `#FFFBF3` |
| onPrimaryContainer | `#0E3549` | | onSurface | `#2B2118` |
| secondary | `#9A4A31` | | surfaceVariant | `#EADFCC` |
| onSecondary | `#FFFFFF` | | onSurfaceVariant | `#574A38` |
| secondaryContainer | `#F7DCD0` | | outline | `#8A7357` |
| onSecondaryContainer | `#4A1B0C` | | outlineVariant | `#D5C7AE` |
| tertiary | `#785A12` | | error | `#A02F26` |
| onTertiary | `#FFFFFF` | | onError | `#FFFFFF` |
| tertiaryContainer | `#F1E2B4` | | errorContainer | `#F8DDD8` |
| onTertiaryContainer | `#382A00` | | onErrorContainer | `#410E0B` |

Plus semantic right/wrong feedback colors that live outside the Material roles (quiz grading, match highlights): correct accent / correct container / on-correct-container, and the same three for wrong.

## The screens (the real inventory — design for these, not for imagined ones)

Bottom navigation, five tabs:

1. **Learn** — the daily dashboard. Today's lesson, streak, what's due, the entry point into everything.
2. **Review** — spaced-repetition flashcards. Tap or swipe to rate Hard / Medium / Easy.
3. **Tutor** — AI conversation practice, chat-shaped, plus written feedback.
4. **Progress** — stats, streak history, level completion.
5. **Profile** — the learner's own page and the way into Settings.

Beyond the tabs: a branded loading screen; a 7-step onboarding flow; a first-run theme picker; the lesson player (which steps through LEARN, EXERCISE and DIALOGUE activities); quizzes; level quizzes; full mock exams; an exam-readiness checklist; a placement test; a grammar reference; a cheatsheet; a level journey map with checkpoint milestones; a teach-back ("explain it in your own words") screen; a paywall; and Settings.

## What I want from you

**First, research and tell me what you found** — briefly, before you design:

- evidence-based habit and streak design that doesn't manipulate (what actually sustains daily use versus what merely punishes absence)
- spaced-repetition UI conventions: what the rating step should look like, what belongs on a card front versus back
- how study and exam-prep products signal credibility and seriousness visually
- one-handed reach zones on a ~6.5" phone, and what that implies for where primary actions sit
- pitfalls when a single design must hold in both a dark and a warm-light theme — what commonly breaks, and how warm-neutral palettes behave for contrast

**Then design, and deliver in this exact shape**, because I transcribe it straight into Kotlin:

1. **Color tokens as a plain table**, both themes, one hex per Material 3 role: primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary, secondary, onSecondary, secondaryContainer, onSecondaryContainer, tertiary, onTertiary, tertiaryContainer, onTertiaryContainer, background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant, surfaceTint, inverseSurface, inverseOnSurface, error, onError, errorContainer, onErrorContainer, outline, outlineVariant, scrim, plus the surfaceContainer family (lowest/low/base/high/highest), surfaceBright, surfaceDim. Then the six feedback colors, both themes.
2. **Type scale** mapped onto the Material 3 typography slots (displaySmall, headlineLarge/Medium/Small, titleLarge/Medium/Small, bodyLarge/Medium/Small, labelLarge/Medium/Small): family, size, weight, line-height, letter-spacing. Say explicitly which slots are Fraunces and which are system sans, and give the separate reading/prose style used for lesson content.
3. **Spacing, radius and elevation scales**, as named steps with dp values.
4. **Component specs** with every state (default, pressed, disabled, selected, error, loading, empty): buttons (filled/outlined/text), cards, list and option rows, segmented controls, chips, linear progress, the review flashcard, bottom navigation, top bar, dialogs, text fields, snackbars.
5. **Motion**: durations and easing curves per interaction class. One constraint you must respect — theme switching is implemented as a freeze-frame crossfade (the screen is captured, the theme snaps underneath, the frozen frame fades out over ~420ms), because animating color roles forced a full recomposition each frame and looked like the app was repainting itself in pieces. Don't design a transition that requires per-role color tweening.
6. **Screen mocks at 360×800dp, each shown in BOTH themes**: Learn (daily dashboard), Review (flashcard, front and back), a lesson EXERCISE activity, Progress, Settings, one onboarding step.
7. **Accessibility notes**: contrast ratios stated per critical pairing (target 4.5:1 for body text, 3:1 for large text and UI boundaries), tap targets ≥48dp, and how the layout behaves at 200% font scale and with reduce-motion enabled.
8. **A one-line rationale per significant decision.** I care as much about why as what.

## Hard constraints

- **Every color must land in a Material 3 role.** If a color can't be named as a role, it can't exist in the app. The one exception already carved out is the semantic correct/wrong feedback pair.
- **No external assets.** No CDN, no web fonts beyond Fraunces, no photography, no bitmap illustrations, no icon set beyond Material Icons Extended. Vector shapes, type and color only. The app is fully offline.
- **Don't invent features.** Design only for the screens listed above. The app collects no analytics, has no accounts, and stores nothing off-device, so anything needing data it doesn't have cannot ship. If you think something is genuinely missing, put it in a separate "proposals" section — don't bake it into the mocks.
- **Don't restyle the brand mark or wordmark.**
- **Both themes, always.** A component spec that only shows dark is incomplete.
- **Warm light theme.** Beige and brown, in the same family as the existing terracotta and ochre accents. A grey/white light mode would read as a different app.

Structure the output as a design-system project: a tokens page, a components page, and a screens page, so the whole thing can be synced into the codebase in one pass.
