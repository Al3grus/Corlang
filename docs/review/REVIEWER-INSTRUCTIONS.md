# Corlang native-speaker review — reviewer instructions

Thank you for helping check the [LANGUAGE] course in Corlang! Almost all of this content was
machine-authored, and it has never been read by a native speaker. You don't need any teaching
background — just flag anything that sounds off to you.

## What you're looking at

Open **`[lang]-content-review.html`** in any browser (double-click it, no install needed). It's
one long page with five sections:

1. **Vocabulary** — words and short phrases, grouped by topic and level (A0 = absolute beginner
   → B1/B2 = intermediate/upper-intermediate).
2. **Grammar** — explanations and example sentences.
3. **Cheatsheet** — a condensed reference.
4. **Quizzes** — practice questions with the answers already shown.
5. **Dialogues** — two-person practice conversations from the day-by-day lessons. **This is the
   part that's most likely to have never been checked**, so if you're short on time, prioritize
   this section. You don't need to read every single line — skim for anything a real person
   would never say, the wrong level of formality, or an unnatural word order.

## What to flag

Only three things matter:
- **Sounds unnatural** — technically correct but not how a real person would say it.
- **Wrong register** — too formal/informal, or inconsistent (e.g. switching between "tu" and
  "vous"-equivalent mid-conversation without reason).
- **Factual/grammatical error** — a wrong translation, wrong gender/conjugation, wrong fact.

Don't worry about typos in the English gloss column — those are easy for me to catch separately.
Ignore anything that's just "not how I'd personally phrase it" if it's still natural and correct —
the goal is fixing errors, not rewriting to one person's style.

## How to send it back

Please don't try to edit the HTML file itself. Just reply with a plain list, one line per issue,
in this format:

```
[Section] — [where] — [what's wrong] — [your suggested fix]
```

For example:
```
Vocab A1 — "kći" (daughter) — note is outdated — kćerka is the everyday form now, kći sounds literary
Dialogue — Day 12, Partner's line "Bem, obrigado" — wrong if the partner is a woman — should be "obrigada"
Grammar A2 — passato prossimo table — "ho andato" is wrong — should be "sono andato" (verb of movement)
```

Any format is fine as long as I can tell *where* in the document you mean (section + level/day/word)
and *what* the fix is — a WhatsApp message, email, or comments in a shared doc all work equally well.

## Pacing (so this doesn't feel like homework)

No deadline — do it in whatever chunks fit your schedule. If it helps, split it by level:

| Chunk | Covers |
|---|---|
| A0/A1 | absolute-beginner material — easiest and quickest to check |
| A2 | early intermediate |
| B1 (/B2) | most advanced — the least urgent, since most learners won't reach it for a while |

Even partial feedback (just the dialogues, just one level) is genuinely useful — this isn't
all-or-nothing.

---

## Per-language file map (for me, not the reviewer)

| Lang | File | Levels | Dialogue days | Suggested reviewer |
|---|---|---|---|---|
| hr | `hr-content-review.html` | A0-B1 | 344 | friend (Croatian) |
| fr | `fr-content-review.html` | A1-B2 | 418 | friend (French) |
| pt | `pt-content-review.html` | A1-B1 | 170 | sister (Portuguese) |
| de | `de-content-review.html` | A0-B1 | 285 | reviewer found |
| it | `it-content-review.html` | A1-B1 | 245 | **unassigned — need an Italian-speaking reviewer** |

Regenerate all five after any content change: `python docs/review/generate_review_docs.py`.
