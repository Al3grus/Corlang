#!/usr/bin/env python3
"""Adversarial variety eval for the Corlang AI tutor (docs/language-standard.md §4 gate).

Sends variety-eliciting prompts through the deployed worker using the SAME system prompt
shape as the app, then grades every reply with an LLM-as-judge using a category rubric
(lexicon / verb constructions / orthography / clitics / false corrections).

Pass bar: zero false corrections of correct target-variety input, zero neighbor-variety
constructions presented as correct.

Usage (from repo root; reads corlang.proxyAuthToken from local.properties):
    python tools/ai-variety-eval.py hr
    python tools/ai-variety-eval.py pt
Cost: ~25 Sonnet calls per language (a few cents). Uses curl (Cloudflare blocks urllib's UA).
"""
import json, subprocess, sys, time

sys.stdout.reconfigure(encoding="utf-8")   # Windows console defaults to cp1252

WORKER = "https://corlang-ai-proxy.ricardo-infante.workers.dev/v1/messages"
# The TUTOR model under test (arg 2, default Haiku — the cheap chat model we want to ship).
# The JUDGE is always Sonnet (grading reliability matters, one call per prompt).
TUTOR_MODEL = "claude-haiku-4-5-20251001"
JUDGE_MODEL = "claude-sonnet-5"

# Keep in sync with TalkScreen.varietyRules / tutorSystemPrompt (the app is the source of truth).
VARIETY = {
    "hr": """- You speak STANDARD CROATIAN (hrvatski standardni jezik) — NEVER Serbian, Bosnian, or mixed
forms. Concretely: after modal and semi-modal verbs use the INFINITIVE (trebam učiti, mogu
doći, želim ići) — NEVER the Serbian 'da' + present ('trebam da učim' is WRONG in Croatian).
Yes/no questions use the '-li' enclitic or 'je li' (Dolaziš li?, Je li točno?) — NEVER the
Serbian 'da li' ('da li dolaziš' is non-standard in Croatian). Use ijekavian forms (lijepo,
mlijeko, htjeti), Croatian month names (siječanj, veljača...), and Croatian vocabulary
(tjedan, kruh, tisuća, zrak, vlak — never nedelja, hleb, hiljada, vazduh, voz).
- If the student's sentence is ALREADY correct standard Croatian, do not invent a correction —
confirm it's right and continue. Never "correct" a correct form, and do not present two valid
orderings as if one were an error.""",
    "pt": """- You speak EUROPEAN Portuguese (português europeu, Portugal) — NEVER Brazilian. Concretely:
'estar a' + infinitive (estou a aprender — never 'estou aprendendo'), tu with correct verb
forms in informal speech, European clitic placement (chamo-me, disse-lhe), and European
vocabulary (pequeno-almoço, autocarro, telemóvel, casa de banho — never café da manhã,
ônibus, celular, banheiro).
- If the student's sentence is ALREADY correct European Portuguese, do not invent a
correction — confirm it's right and continue. Never "correct" a correct form.""",
    "fr": """- You speak standard metropolitan French, as tested by the DELF exams.
- If the student's sentence is ALREADY correct, do not invent a correction — confirm it's
right and continue.""",
}
NAME = {"hr": "Croatian", "pt": "Portuguese", "fr": "French"}
SEED = {
    "hr": ("Bok!", "Bok! Ja sam tvoj hrvatski tutor. Možemo razgovarati o čemu god želiš — polako i jednostavno. Kako si danas?"),
    "pt": ("Olá!", "Olá! Sou o teu tutor de português europeu. Podemos falar sobre o que quiseres — com calma e frases simples. Como estás hoje?"),
    "fr": ("Bonjour !", "Bonjour ! Je suis ton tuteur de français. On peut parler de ce que tu veux — doucement et simplement. Comment vas-tu aujourd'hui ?"),
}

# Adversarial prompts: correct-variety input the model might "fix" (FALSE-CORRECTION bait),
# translation/grammar asks where the varieties diverge (BLEED bait).
PROMPTS = {
    "hr": [
        "ja sam dobro, trebam uciti hrvatski",                       # correct hr — the field-failure case
        "Mogu doći sutra u pet.",                                    # correct hr infinitive
        "Želim naučiti hrvatski jer moja žena je Hrvatica.",         # correct-ish; watch for da+present 'fix'
        "Kako se kaže 'I have to work tomorrow'?",                   # elicits trebam/moram + INF
        "Je li točno reći 'moram da radim'?",                        # must say NO (Serbian construction)
        "What month is January in Croatian?",                        # siječanj, not januar
        "Kupio sam hleb i mleko.",                                   # sr. lexicon+ekavian — SHOULD correct to kruh/mlijeko
        "How do I say 'a thousand' and 'a week'?",                   # tisuća/tjedan not hiljada/nedelja
        "Idem vlakom u Zagreb sljedeći tjedan.",                     # correct hr — no correction allowed
        "Can you explain when to use 'da' in Croatian?",             # rule question; must not teach sr. da+present
        "Lijepo je vrijeme danas, želim ići na plažu.",              # correct ijekavian — no correction
        "Translate: 'She wants to buy bread and milk.'",             # kruh/mlijeko expected
    ],
    "pt": [
        "estou a aprender português todos os dias",                  # correct EP — no correction allowed
        "Como se diz 'I am reading a book'?",                        # estou a ler, not estou lendo
        "Tomo o pequeno-almoço às oito.",                            # correct EP lexicon — no correction
        "É correto dizer 'estou fazendo o jantar'?",                 # must say that's BR; EP = estou a fazer
        "How do I say 'bus', 'mobile phone' and 'bathroom'?",        # autocarro/telemóvel/casa de banho
        "Chamo-me Ana e moro em Lisboa.",                            # correct EP clitic — no correction
        "Ele me disse que vem amanhã.",                              # BR proclisis — SHOULD correct to disse-me
        "Translate: 'I'm having breakfast right now.'",              # estou a tomar o pequeno-almoço
        "Vou apanhar o autocarro para o trabalho.",                  # correct EP — no correction
        "Can you explain the difference between 'tu' and 'você' usage?",  # EP usage, not BR você-default
        "Preciso de comprar um telemóvel novo.",                     # correct EP (preciso de) — no correction
        "Translate: 'They are traveling to Porto.'",                 # estão a viajar
    ],
    "fr": [
        "je veux apprendre le français pour mon travail",
        "Comment dit-on 'I need to finish my work'?",
        "Est-ce correct de dire 'si j'aurais su'?",                  # must say no — si + imparfait
        "J'ai besoin de me reposer ce week-end.",                    # correct — no correction
    ],
}

JUDGE = """You are a strict linguistic examiner for {name} ({variety_label}). Below is a tutor's reply
to a learner. Judge it category by category:
1. LEXICON — any neighbor-variety vocabulary presented as correct?
2. VERB CONSTRUCTIONS — any neighbor-variety syntax (e.g. {syntax_marker}) presented as correct?
3. ORTHOGRAPHY — spelling/diacritics of the wrong standard?
4. CLITICS/WORD ORDER — wrong-variety placement presented as correct?
5. FALSE CORRECTION — did the tutor "correct" input that was ALREADY correct {variety_label}?
Quoting a wrong form in order to explain it is wrong is FINE. Teaching or using it as correct is a FAIL.
Reply with EXACTLY this JSON, nothing else:
{{"pass": true/false, "failures": ["<category>: <quoted evidence>", ...]}}

LEARNER SAID: {user}
TUTOR REPLIED: {reply}"""

SYNTAX_MARKER = {
    "hr": "Serbian 'da'+present after modal verbs instead of the Croatian infinitive",
    "pt": "Brazilian 'estar + gerund' instead of European 'estar a + infinitive'",
    "fr": "non-standard constructions",
}
VARIETY_LABEL = {"hr": "standard Croatian", "pt": "European Portuguese", "fr": "standard French"}


def token():
    for l in open("local.properties", encoding="utf-8-sig"):
        if l.startswith("corlang.proxyAuthToken="):
            return l.partition("=")[2].strip()
    sys.exit("corlang.proxyAuthToken not found in local.properties")


def call(tok, system, messages, model, max_tokens=500, disable_thinking=False):
    payload = {"model": model, "max_tokens": max_tokens,
               "system": system, "messages": messages}
    if disable_thinking:
        payload["thinking"] = {"type": "disabled"}
    body = json.dumps(payload)
    out = subprocess.run(
        ["curl", "-s", "-X", "POST", WORKER, "-H", "content-type: application/json",
         "-H", f"x-corlang-auth: {tok}", "-d", body],
        capture_output=True, text=True, encoding="utf-8").stdout
    d = json.loads(out)
    if "content" not in d:
        raise RuntimeError(f"API error: {d}")
    # First TEXT block, not content[0]: the model may emit a thinking block first.
    for block in d["content"]:
        if block.get("type") == "text":
            return block["text"]
    # No text block (e.g. thinking hit max_tokens). Signal it rather than crash the run.
    return '{"pass": false, "failures": ["no text block — likely max_tokens on thinking"]}'


def system_prompt(lang):
    # Mirrors TalkScreen.tutorSystemPrompt (A1 level, plain text).
    return f"""You are a warm, patient {NAME[lang]} conversation tutor. Your student is an adult learning
{NAME[lang]} at CEFR level A1, preparing for the official {NAME[lang]} exam, so accuracy
matters, but keep it encouraging.

Rules:
{VARIETY[lang]}
- Converse mainly in {NAME[lang]}, kept at or slightly below level A1. Use short, natural sentences.
- When you use a word or phrase the student likely doesn't know yet, add a brief English gloss
  in parentheses right after it.
- If the student makes a genuine mistake, gently correct it: give the corrected {NAME[lang]}
  sentence and a one-line reason, then continue naturally. Don't nitpick; focus on what helps most.
- Always end with a simple follow-up question to keep the conversation going.
- Keep each reply short (2 to 5 sentences) so it stays a real back-and-forth, not a lecture.
- Use correct {NAME[lang]} spelling and accents at all times.
- PLAIN TEXT ONLY: no markdown, no asterisks, no bullet lists — your reply is shown verbatim
  in a chat bubble."""


def main():
    lang = sys.argv[1] if len(sys.argv) > 1 else "hr"
    tutor_model = sys.argv[2] if len(sys.argv) > 2 else TUTOR_MODEL
    # arg 3 = "nothink" disables the tutor's extended thinking (mirrors the app's chat path).
    disable_thinking = len(sys.argv) > 3 and sys.argv[3] == "nothink"
    tok = token()
    sysp = system_prompt(lang)
    opener, greeting = SEED[lang]
    print(f"Tutor model under test: {tutor_model}  (thinking {'DISABLED' if disable_thinking else 'default'})\n")
    fails, results = 0, []
    for i, user in enumerate(PROMPTS[lang]):
        reply = call(tok, sysp, [
            {"role": "user", "content": opener},
            {"role": "assistant", "content": greeting},
            {"role": "user", "content": user},
        ], model=tutor_model, disable_thinking=disable_thinking)
        # Generous cap: Sonnet may spend output on a thinking block before the JSON verdict.
        judge_raw = call(tok, "You are a precise JSON-only grader.", [{
            "role": "user",
            "content": JUDGE.format(name=NAME[lang], variety_label=VARIETY_LABEL[lang],
                                    syntax_marker=SYNTAX_MARKER[lang], user=user, reply=reply)
        }], model=JUDGE_MODEL, max_tokens=1024)
        try:
            verdict = json.loads(judge_raw[judge_raw.index("{"):judge_raw.rindex("}") + 1])
        except Exception:
            verdict = {"pass": False, "failures": [f"judge output unparseable: {judge_raw[:120]}"]}
        ok = verdict.get("pass", False)
        fails += 0 if ok else 1
        results.append((ok, user, reply, verdict.get("failures", [])))
        print(f"[{i+1:02d}/{len(PROMPTS[lang])}] {'PASS' if ok else 'FAIL'}  {user[:60]}")
        for f in verdict.get("failures", []):
            print(f"        !! {f}")
        time.sleep(0.3)
    print(f"\n===== {lang}: {len(PROMPTS[lang]) - fails}/{len(PROMPTS[lang])} passed =====")
    if fails:
        print("GATE FAILED — fix the prompt/model and rerun (docs/language-standard.md §4).")
        for ok, user, reply, fl in results:
            if not ok:
                print(f"\n--- {user}\n{reply}")
        sys.exit(1)
    print("GATE PASSED.")


if __name__ == "__main__":
    main()
