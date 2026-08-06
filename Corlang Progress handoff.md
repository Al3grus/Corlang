# Corlang: Progress screen + light theme ("Umber on paper")

Handoff for implementation. Kotlin + Jetpack Compose + Material 3.
Constraints that still apply: every colour lands in a Material 3 role, no external
assets, no em dashes or en dashes in any shipped string, content is JSON driven so
no container gets a fixed height.

---

## 1. Light theme colour scheme

```kotlin
val UmberOnPaper = lightColorScheme(
    primary                = Color(0xFF6B4F32),
    onPrimary              = Color(0xFFFFFFFF),
    primaryContainer       = Color(0xFFEADCC6),
    onPrimaryContainer     = Color(0xFF2A1D0C),
    inversePrimary         = Color(0xFFD9BE99),

    secondary              = Color(0xFF9A4A31),
    onSecondary            = Color(0xFFFFFFFF),
    secondaryContainer     = Color(0xFFF7DCD0),
    onSecondaryContainer   = Color(0xFF4A1B0C),

    tertiary               = Color(0xFF785A12),
    onTertiary             = Color(0xFFFFFFFF),
    tertiaryContainer      = Color(0xFFF1E2B4),
    onTertiaryContainer    = Color(0xFF382A00),

    background             = Color(0xFFF6F0E6),
    onBackground           = Color(0xFF2B2118),
    surface                = Color(0xFFFFFBF3),
    onSurface              = Color(0xFF2B2118),
    surfaceVariant         = Color(0xFFEADFCC),
    onSurfaceVariant       = Color(0xFF574A38),
    surfaceTint            = Color(0xFF6B4F32),
    inverseSurface         = Color(0xFF302518),
    inverseOnSurface       = Color(0xFFF8F1E6),

    error                  = Color(0xFFA02F26),
    onError                = Color(0xFFFFFFFF),
    errorContainer         = Color(0xFFF8DDD8),
    onErrorContainer       = Color(0xFF410E0B),

    outline                = Color(0xFF8A7357),
    outlineVariant         = Color(0xFFD5C7AE),
    scrim                  = Color(0xFF000000),

    surfaceContainerLowest  = Color(0xFFFFFFFF),
    surfaceContainerLow     = Color(0xFFF9F3E9),
    surfaceContainer        = Color(0xFFF2EADC),
    surfaceContainerHigh    = Color(0xFFECE3D3),
    surfaceContainerHighest = Color(0xFFE5DBC9),
    surfaceBright           = Color(0xFFFFFBF3),
    surfaceDim              = Color(0xFFE6DFD1),
)
```

Feedback colours, light. These sit outside the role system and are the one
documented exception. Expose them through a `CorlangFeedback` object on the theme,
passed down, not reached for globally.

| token | hex |
|---|---|
| correct | `#2E6B36` |
| correctContainer | `#DDEBD9` |
| onCorrectContainer | `#17351C` |
| wrong | `#9E332A` |
| wrongContainer | `#F7DDD8` |
| onWrongContainer | `#43110D` |

Brand mark stays fixed in both themes and is not restyled: ring `#2F7FAE`,
core `#C8402C`. Blue appears nowhere else in this theme.

### Two rules that matter more than the hex values

1. `outlineVariant` (`#D5C7AE`) measures **1.61:1** against surface. It is legal for
   dividers and the edge of a non interactive card. It is never sufficient as the
   only boundary of something tappable. Everything interactive takes `outline`
   (`#8A7357`, **4.36:1**).
2. A surface container tone step measures **1.28:1**. Tone is hierarchy, not an edge.
   In the light theme a card on background also gets a 1dp `outlineVariant` hairline,
   because cream on paper separates on a monitor and vanishes outdoors.

Measured pairings used on this screen: `onSurface`/`surface` 15.26:1,
`onSurfaceVariant`/`surface` 8.33:1, `primary`/`background` 6.64:1,
`tertiary`/`background` 5.67:1.

---

## 2. Progress screen

360 x 800dp. Bottom navigation, Progress tab selected. No top bar back button,
it is a root destination. Nothing on this screen needs a network call.

**Only figures the device already holds.** No accounts, no analytics, so no
comparisons to other learners, no percentile, no "you are ahead of" anything.

### Layout

```
statusBar        28dp
title            56dp   headlineMedium "Progress", 16dp gutter
content          fill   16dp gutter, 12dp gap between cards, vertical scroll
bottomNav        80dp
```

Content order:

1. **Course card.** radiusLg 16dp, `surfaceContainer` fill + 1dp `outlineVariant`,
   16dp padding. Row: titleMedium course name + bodySmall current level, right
   aligned. Then a 6dp linear progress on `surfaceVariant` track with `primary`
   indicator. Then bodySmall percentage complete.
2. **Stat tiles.** Two equal tiles, 12dp gap, radiusMd, `surfaceContainer` +
   hairline. Each is headlineMedium figure in Fraunces over bodySmall label.
   Words known, day streak.
3. **Month calendar card.** Spec below.
4. **Review load ahead.** radiusMd card. titleSmall overline, then a seven bar
   row, `primary` bars with 4dp top corners, bodySmall weekday label under each.
   Bars are scaled to the largest value in the window, so an empty week does not
   produce a full height bar.

The streak figure and the calendar are the same data. They must reconcile: if the
grid shows a gap, the streak cannot span it.

### Month calendar

One month at a time, stepped with arrows. Replaces the earlier rolling 12 week
heatmap, because a month is the unit people actually reason about and it lets a
learner look back at a specific week rather than a sliding window.

```
card            radiusMd 12dp, surfaceContainer + 1dp outlineVariant, 16dp padding
header row      prev arrow | month label + count | next arrow
                arrows are 48dp square targets, negative 14dp vertical margin so
                they overlap the card padding without growing the card
                label: titleMedium Fraunces, month + year, centre aligned
                count: bodySmall, "<banked> of <settled> days"
grid            7 columns, 5dp gap
                weekday header row: labelSmall, onSurfaceVariant, M T W T F S S
                leading blanks for the offset of the 1st (Monday first week)
                cells: 22dp tall, radius 4dp, labelSmall day number centred
```

Cell states:

| state | fill | number | edge |
|---|---|---|---|
| banked | `primary` | `onPrimary` | none |
| missed | `outlineVariant` | `onSurfaceVariant` | none |
| today, not yet banked | none | `primary` | 2dp `primary` |
| future | none | `outline` | 1dp `outlineVariant` |
| leading blank | none | none | none |

Dark theme equivalents, for the same five states in order: `#8CBAD2` on `#06293D`;
`#3A4650` with `#B8C4CE`; 2dp `#8CBAD2` with `#8CBAD2`; 1dp `#3A4650` with `#7E8A95`.

Behaviour:

- Today is **pending**, never banked. It joins neither the numerator nor the
  denominator of the count until the day is complete. This is the same statement
  the grey streak flame makes on Learn, and the two must not disagree.
- The count denominator is settled days only, so a month in progress reads
  "5 of 5 days" rather than "5 of 31".
- Back steps indefinitely, stopping at the first month with any history.
  Forward is disabled at the current month and its arrow drops to 38 percent alpha.
- Gaps are `outlineVariant`. No red, no warning icon, no apology copy. A history
  that flinches at every missed day teaches the learner not to open it.
- Month label and weekday initials come from the locale, not hardcoded strings.

Compose sketch of the state:

```kotlin
data class DayCell(val day: Int?, val state: DayState)
enum class DayState { Blank, Banked, Missed, Today, Future }

// monthOffset: 0 is the current month, negative goes back
val shown = today.withDayOfMonth(1).plusMonths(monthOffset.toLong())
val lead  = (shown.dayOfWeek.value + 6) % 7      // Monday first
val cells = List(lead) { DayCell(null, DayState.Blank) } +
    (1..shown.lengthOfMonth()).map { d ->
        val date = shown.withDayOfMonth(d)
        DayCell(d, when {
            date.isEqual(today)  -> DayState.Today
            date.isAfter(today)  -> DayState.Future
            history.banked(date) -> DayState.Banked
            else                 -> DayState.Missed
        })
    }
val settled = cells.count { it.state == DayState.Banked || it.state == DayState.Missed }
val banked  = cells.count { it.state == DayState.Banked }
```

### Type

| element | slot | value |
|---|---|---|
| screen title | headlineMedium | Fraunces 28sp / 36sp, w600 |
| stat figure | headlineMedium | Fraunces 28sp / 36sp, w600 |
| card title, month label | titleMedium | Fraunces 18sp / 24sp, w600, +0.1 |
| overline | titleSmall | system sans 14sp / 20sp, w600 |
| supporting text, count | bodySmall | system sans 13sp / 18sp |
| day number, weekday initial | labelSmall | system sans 11sp / 16sp, w600 |

### Motion

- Month step: 200ms, `standardDecelerate`, 12dp horizontal slide plus fade on the
  grid only. The header label crossfades. Nothing tweens a colour role.
- Progress advance: 400ms `standard`, forward only, never animating backwards.
- Reduce motion: both collapse to 0ms. No state on this screen depends on an
  animation completing.

### Accessibility

- Arrows are 48dp targets with content descriptions "Previous month" and
  "Next month". Disabled forward arrow keeps its size and is marked disabled
  rather than removed, so the control does not move between months.
- The grid is a single semantic group. Each cell exposes a text description,
  for example "6 August, not yet complete", so state is never colour only.
- The count line is the accessible summary of the grid. Screen reader users do
  not have to traverse 31 cells to learn the month.
- At 200 percent font scale: day numbers can be dropped from the cells before the
  cells shrink, the weekday header goes with them, and the card becomes a plain
  heatmap. The count line and the month label stay. Cards grow, the screen scrolls,
  nothing is given a fixed height.
- Bottom nav labels may drop at large scale. The 48dp icon targets do not.

---

## 3. Proposals, not implemented

- **Planned pause.** A learner declared pause, set in advance, that keeps the count
  intact and renders as a distinct cell state. Needs no data the device lacks.
- **Tapping a day** to see what was studied. Only worth it if lesson history is
  already persisted per date.
