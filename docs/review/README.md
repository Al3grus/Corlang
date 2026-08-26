# Native-speaker course review

The workbook a native teacher marks up, and how what they send back gets applied.

## Building it

```bash
python tools/course/build_review_doc.py hr
# → docs/review/hr-review-workbook.html
```

Hosted at **https://corlang-review.pages.dev** (see `server/review-site/README.md`), and the
same file also works opened straight from disk.

One self-contained HTML file, no dependencies. It reaches the network only when opened with a
`?k=<token>` link, and then only to mirror what it has already written locally. It embeds the course JSON and
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

**Organised by lesson, in the order the app plays it.** The layout mirrors
`buildSessionSteps` in `SessionPlayer.kt`, which is the only thing that decides what a
learner actually meets:

```
intro          title + objective + "Why this matters"
1 Recall       the new words this lesson introduces
2 Input        every LEARN activity
3 Practice     every EXERCISE activity
4 Output       every DIALOGUE activity
```

Note the phase sort: the app groups **all** LEARN before **all** EXERCISE before **all**
DIALOGUE, regardless of the order they sit in the JSON. Presenting them in authored order
would have a reviewer checking a sequence no learner ever sees. A level runs lessons first,
then its quiz, then its mock exam last — nobody starts a level by being tested on it — and
the placement test opens the workbook because it is offered before Lesson 1. The sidebar
lists sections in that same order; listing the non-lessons first put the quiz above Lesson 1
and read as "start here".

The grammar syllabus is excluded. It is still in the app, behind a Profile button, but it is
reference material rather than something the course serves in sequence, and this workbook
follows the sequence.

`drills` and `reviewBlock` are excluded: `buildSessionSteps` only falls back to them when a
day has no activities at all, and every day in every shipped course has activities, so no
learner ever sees them. Nothing else is added and nothing else is left out.

Which words belong to which lesson is computed by replicating `data/DeckOrder.kt`: deck slots
`[(n-1)*10, n*10)` are lesson `n`'s, honouring every pack's `fromDay` gate. **If DeckOrder.kt
or buildSessionSteps changes, the generator must change with it**, or the workbook stops
matching the app. For Croatian the deck comes out exact — 3,440 words, 10 per lesson, 344
lessons, 344 plan days, no orphans. Each word still shows its deck pack, because some defects
are only visible across a set (one colour glossed unlike the other nine).

The design decision that matters: **nothing is ticked by default.** With ~15,000
reviewable items, a workbook that asks for a verdict on each one never gets finished, so
silence means "fine" and the reviewer only touches what is wrong. Coverage is tracked one
level up, by a "I have reviewed this whole section" mark, which is what separates
"checked and fine" from "not looked at yet".

Three verdicts — **✗ Wrong**, **≈ Awkward**, **? Unsure** — each opening a free-text box
for the correction. The correction is the part that can be acted on; a flag without one
still needs a conversation.

Two standing pages sit above the course in the sidebar: **Instructions**, and **Overall
notes** — a page of free-text boxes, one per level plus one for the course as a whole, for
anything that is not about a single item. The notes used to live at the foot of the
instructions, which meant reaching them was "open the instructions and scroll past
everything you have already read".

Each section ends with its own footer: **← Previous**, **Skip for now →**, and **✓ Mark
reviewed and continue**, which marks and advances in one click. Nothing at the top asks for
a verdict before the reviewer has read anything; a "Reviewed ✓" chip in the heading shows
the state, and the footer offers the undo.

Progress autosaves to `localStorage`. **Save my work** produces the small JSON file that is
both the reviewer's backup and the thing they send back.

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
