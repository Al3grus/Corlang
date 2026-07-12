# Français fondamental + frequency cross-check (vocabulary anchor)

- **Sources:**
  - **Le Français fondamental** (1er & 2e degré) — the historic official core-French inventory
    commissioned by the French Ministry of Education (Gougenheim et al.), ~1,500 words + core
    grammar defining the minimum for everyday communication. The reference point for A1/A2 core.
  - **A Frequency Dictionary of French** (Lonsdale & Le Bras, Routledge) — a modern, corpus-based
    ranked frequency list (top ~5,000 lemmas with part of speech), the cross-check for
    "is this word actually frequent enough for this level".
- **Role:** the two-sided anchor for `content/fr/vocab/` — coverage is driven by the référentiel
  themes (referentiel-fr.md), and each candidate word is cross-checked against a named frequency
  ranking so the deck stays high-leverage (the Pareto core first). Mirrors how Croatian vocab was
  built against ASOO themes + a frequency cross-check.

## Level targets (words introduced; SRS order = pack order)
- **A1 ≈ 600**  core survival + Français-fondamental 1er degré
- **A2 ≈ 800**  everyday life, work, past narration
- **B1 ≈ 900**  opinion, society, media, abstract-but-common
- **B2 ≈ 900**  argumentation, professional/technical-general, register-marked vocab
- Total **≈ 3,200** through B2.

## Authoring rules (enforced by QA + ContentValidationTest)
- Correct accents, NFC-normalized; `en` gloss; `pos` with **gender on every noun** (n. m. / n. f.)
  and verb group; `example` mandatory at A1/A2 (TTS listening value), encouraged at B1/B2.
- Cross-check each batch against the frequency list; ≥10% random spot-check vs the ranked list.
- Frozen IDs once a batch lands (`src/test/resources/frozen-word-ids-fr.txt`).
