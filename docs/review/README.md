# Native-speaker course review

The workbook a native teacher marks up, and how what they send back gets applied.

## Building it

```bash
python tools/course/build_review_doc.py hr
# → docs/review/hr-review-workbook.html
```

One self-contained HTML file, no network, no dependencies. It embeds the course JSON and
renders it in the browser a section at a time, which is why 5 MB of content produces a
3.4 MB document rather than a 40 MB one. Re-run it after a round of fixes to produce a
fresh workbook against the corrected content.

Any course works: `build_review_doc.py pt`, `fr`, and so on. Hidden courses build too —
it reads the content folder, not `content/_index.json`.

Only build a workbook when a reviewer is actually lined up, and delete it once their file is
back and applied. A workbook is a snapshot of content that keeps moving, so a stale one in the
repo is worse than no workbook at all: someone eventually hands a reviewer the old one. The
August 2026 `*-content-review.html` set was deleted for exactly that reason.

## What the reviewer gets

Everything the app teaches, in the order it teaches it, with every answer key visible.
Per level: grammar reference → vocabulary packs (in SRS introduction order) → lessons in
plan order → quiz. Then a cross-level group for the placement test, the mock exams and the
Profile > References resource list.

The cheatsheet and the teach-back concepts are deliberately excluded: `CheatsheetScreen` and
`TeachScreen` are no longer reachable from the nav graph, so no learner sees that content and
reviewing it would waste a reviewer's time. Their JSON still ships in the APK, which is a
separate problem. `resources.json` is NOT in that category — Profile still renders it, and a
dead external link has shipped before, so it stays in the workbook.

The design decision that matters: **nothing is ticked by default.** With ~15,000
reviewable items, a workbook that asks for a verdict on each one never gets finished, so
silence means "fine" and the reviewer only touches what is wrong. Coverage is tracked one
level up, by a "I have reviewed this whole section" mark, which is what separates
"checked and fine" from "not looked at yet".

Three verdicts — **✗ Wrong**, **≈ Awkward**, **? Unsure** — each opening a free-text box
for the correction. The correction is the part that can be acted on; a flag without one
still needs a conversation.

Progress autosaves to `localStorage`. **Download review** produces a small JSON file;
**Import…** loads one back, so a review can move between machines or sessions.

## Applying what comes back

The returned file looks like this:

```json
{
  "course": "hr",
  "reviewer": "Ana Horvat",
  "sectionsReviewed": ["day.1", "vocab.survival", "..."],
  "levelNotes": {"A1": "…"},
  "flags": [
    {
      "id": "vocab/survival/bok",
      "section": "vocab.survival",
      "level": "A0",
      "verdict": "wrong",
      "correction": "…what it should say…",
      "hr": "bok",
      "en": "hi / bye (informal)"
    }
  ],
  "summary": {"flagged": 0, "wrong": 0, "awkward": 0, "unsure": 0}
}
```

`id` is a stable path into the content, which is what makes the file directly actionable:

| id shape | points at |
|---|---|
| `vocab/<packId>/<wordId>` | a word in `vocab/*.json` |
| `day/<n>/framing` | that day's objective / paretoFocus / drills / reviewBlock |
| `day/<n>/act<i>/intro` | a `DayActivity.intro` |
| `day/<n>/act<i>/item<j>` | a LEARN item |
| `day/<n>/act<i>/line<j>` | a DIALOGUE line |
| `day/<n>/act<i>/q<j>` | an EXERCISE question |
| `grammar/<level>/<topicId>` | a grammar topic |
| `quiz/<quizId>/q<j>` · `placement/q<j>` | assessment questions |
| `exam/<examId>/<sectionId>/{q,passage,prompt}<j>` | mock-exam parts |
| `cheatsheet/<i>` · `feynman/<id>` · `resource/<name>` | the rest |

**Never rename a vocab `id` when applying a fix.** It is the learner's SRS key
(`Content.kt`), and renaming resets their review history. Correct `hr`, `en`, `pos`,
`note` and `example` freely; the id stays.

After applying a batch, run the validators (`check_batch.py`, `check_hr.py`,
`check_deck_examples.py`, `proctor.py`) and rebuild — a native correction can still break
an app-level invariant, such as a cloze whose blanked token is no longer unambiguous.

Per `docs/error-registry.md`: any defect class the reviewer finds more than once should
gain a check in `tools/course/`, so it can never come back.
