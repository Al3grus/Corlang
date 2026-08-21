package com.corlang.app.ai

/**
 * The gate every tutor reply passes before a learner ever sees it.
 *
 * Field report, 2026-08-21: asked to produce a sentence, the tutor handed over the sentence:
 * "Can you try saying 'i dont work, i look for a job' using 'ne radim' and 'trazim posao'". The
 * system prompt has told it not to do this since the English-led mode was written, in those
 * words, and it did it anyway. That is the whole argument for this file: a rule the model is
 * asked to follow is not a rule the output obeys.
 *
 * Field report, 2026-08-20: a Croatian tutor reply contained a Russian word and then said
 * "russian word... wait, let me stay in croatian/english properly". Two failures in one message —
 * the model drifted out of the target language, and it narrated its own recovery. Neither is
 * something a learner should ever be shown, and no prompt makes a model perfect: the reply has to
 * be inspected before it is displayed.
 *
 * The model choice already fights drift (hr runs Sonnet WITH thinking because Haiku bled into
 * Serbian ~30% of the time, see TalkScreen.send), and the seeded in-language anchor fights it
 * again. This is the third line: a check on the actual output, with a retry behind it.
 *
 * Deliberately CONSERVATIVE. A false reject costs one silent retry; a false accept puts nonsense
 * in front of a paying learner. But it must not reject ordinary teaching, so every pattern here is
 * one that cannot occur in a well-formed tutor reply, and the borderline cases (a tutor correcting
 * the STUDENT, a tutor explaining a word) are covered by the fixtures in ReplyGuardTest.
 *
 * Pure Kotlin, no Android: it is unit-tested against both real failures and real good replies.
 */
object ReplyGuard {

    sealed interface Verdict {
        data object Ok : Verdict
        /** [reason] is for logs and tests, never shown to the learner. */
        data class Reject(val reason: String) : Verdict
    }

    /**
     * Scripts no course in this app uses. Croatian, Portuguese and French are all Latin, so a
     * single Cyrillic or Greek letter is proof the model left the language entirely — this is the
     * check that would have caught the Russian word on its own.
     */
    private val FOREIGN_SCRIPT = Regex(
        "[\\p{IsCyrillic}\\p{IsGreek}\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}" +
            "\\p{IsHangul}\\p{IsArabic}\\p{IsHebrew}\\p{IsDevanagari}\\p{IsThai}]"
    )

    /**
     * The model narrating its own process. A tutor corrects the STUDENT ("small correction: ...")
     * and explains things ("let me explain the case here"), so neither of those may match: these
     * are only the phrases of a model catching ITSELF mid-mistake.
     */
    private val SELF_TALK = Regex(
        "(?i)\\b(" +
            "wait,\\s*let me|hold on,|oops|scratch that|ignore (that|the above)|" +
            "let me (try (that )?again|start over|redo|correct myself|fix that)|" +
            "my mistake|i made a mistake|that('s| was) (not right|wrong|incorrect) of me|" +
            "i apologi[sz]e|sorry,? i (wrote|said|used)|" +
            "as an ai|i'?m an ai|language model|" +
            "i should (stay|switch back|be writing) in|" +
            "let me stay in|staying in [a-z]+ (now|properly)" +
            ")"
    )

    /**
     * Naming another language is fine when the learner asked about it, and a warning sign when it
     * appears beside a slip. Only rejected TOGETHER with self-talk or a stray script, never alone,
     * so "yes, it is similar to Russian" survives.
     */
    private val OTHER_LANGUAGE = Regex(
        "(?i)\\b(russian|serbian|bulgarian|ukrainian|polish|czech|slovak|slovenian|macedonian)\\b"
    )

    /**
     * Asking the student to produce something and then supplying it. The tell is not the quoting
     * (a tutor quotes constantly: corrections, new words, the English meaning to translate) but
     * the HAND-OVER: a production ask joined to target-language material by "using", "with the
     * words", "start with". Once the words are named, nothing is left to produce.
     *
     * Which side of the quote marks the answer sits on is decided by [looksEnglish], and the
     * asymmetry is deliberate: a fragment that looks English at all is left alone, because
     * "how would you say 'I don't work' ?" is the exercise working correctly, and a wrong reject
     * costs the learner a whole turn (one silent retry, then an error) while a wrong accept only
     * leaves things as they already were.
     *
     * Known gap: the words have to be quoted. "say it using ne radim" is the same defect and is
     * not caught, which would need a target-language lexicon in here. Models quote almost always,
     * and a lexicon is the kind of dependency that turns a guard into a second content pipeline.
     */
    private val PRODUCE_ASK = Regex(
        "(?i)\\b(say|saying|write|writing|type|typing|repeat|repeating|" +
            "answer|answering|reply|replying|translate|translating|tell me|telling me|" +
            "make a sentence|form a sentence|put (it |that )?together)\\b"
    )

    private val HANDOVER = Regex(
        "(?i)\\b(using|use|with the words?|with these words?|" +
            "start(ing)? with|includ(e|ing))\\b"
    )

    /**
     * "Type 'idem u trgovinu'." needs no connector: naming what to TYPE is naming the answer,
     * always, because the student's only remaining move is to copy it. Kept to type/write on
     * purpose. "Say 'dobar dan'" is repeat-after-me, which is real beginner teaching, and a
     * guard that eats it would cost the learner a turn every time the tutor models a greeting.
     */
    private val DIRECT_ASK = Regex("(?i)\\b(type|write|send)\\b")

    /**
     * Three shapes that quote target-language material without asking for it, and so must stay
     * out of the guard's way: reporting what the student already wrote, offering an option, and
     * describing the language ("in Croatian we write 'ije' here"). Bare "you write 'X'" is
     * deliberately NOT here, because that one really is a copying exercise.
     */
    private val HEDGED = Regex(
        "(?i)\\b(you (already |just )?(wrote|said|typed)|" +
            "(you|we) (can|could|may|might) (write|type|say|use)|" +
            "we (write|say|use|spell|type|call|pronounce)|" +
            "for example|e\\.g\\.|instead of)"
    )

    /**
     * A quoted fragment. The apostrophe is a quote mark AND an English letter ("don't"), so an
     * opening mark may not sit against a letter and a closing one may not be followed by one.
     */
    private val QUOTED = Regex(
        "(?<!\\p{L})[\"“‘']([^\"“”‘’'\n]{2,80})[\"”’'](?!\\p{L})"
    )

    /**
     * Common English, generously listed ON PURPOSE. Every word added here makes the guard
     * quieter, never louder: a fragment judged English is never rejected. That is also why the
     * overlaps are harmless (hr "i" is "and", hr "a" is "but", fr/es "a"), since the worst they
     * do is let one leak through.
     */
    private val ENGLISH_MARKERS = setOf(
        "the", "a", "an", "i", "you", "your", "my", "me", "we", "us", "they", "them", "he", "she",
        "it", "is", "am", "are", "was", "were", "be", "been", "do", "dont", "does", "doesnt",
        "did", "didnt", "have", "has", "had", "will", "wont", "would", "can", "cant", "could",
        "should", "want", "wants", "like", "likes", "need", "needs", "go", "going", "goes",
        "went", "get", "got", "say", "said", "tell", "know", "think", "live", "lives", "work",
        "works", "working", "look", "looking", "for", "and", "but", "or", "not", "no", "yes",
        "with", "from", "to", "of", "in", "on", "at", "this", "that", "these", "those", "there",
        "here", "what", "where", "when", "why", "who", "how", "much", "many", "please", "thanks",
        "thank", "hello", "good", "morning", "evening", "night", "today", "tomorrow",
        "yesterday", "job", "coffee", "water", "bread", "tired", "hungry", "house", "home",
        "name", "old", "very", "some", "any", "more", "one", "two", "three"
    )

    private val WORD = Regex("[\\p{L}']+")

    /** Half or more of the tokens being ordinary English is enough to call the fragment English. */
    private fun looksEnglish(fragment: String): Boolean {
        val tokens = WORD.findAll(fragment.lowercase())
            .map { it.value.replace("'", "") }
            .filter { it.isNotEmpty() }
            .toList()
        if (tokens.isEmpty()) return true
        return tokens.count { it in ENGLISH_MARKERS } * 2 >= tokens.size
    }

    /** The handed-over fragment, or null when the sentence hands nothing over. */
    private fun handedOverAnswer(sentence: String): String? {
        if (HEDGED.containsMatchIn(sentence)) return null
        val viaConnector =
            if (PRODUCE_ASK.containsMatchIn(sentence)) HANDOVER.find(sentence)?.range?.last
            else null
        val direct = DIRECT_ASK.find(sentence)?.range?.last
        val askEnd = listOfNotNull(viaConnector, direct).minOrNull() ?: return null
        return QUOTED.findAll(sentence)
            .filter { it.range.first > askEnd }
            .map { it.groupValues[1] }
            .firstOrNull { !looksEnglish(it) }
    }

    /** Left-over scaffolding from a model that started composing in the wrong register. */
    private val LEAKED_MARKUP = Regex(
        "(?i)(^|\\n)\\s*(system:|assistant:|user:|<\\|)|\\[/?INST]|</?thinking>"
    )

    fun inspect(reply: String, lang: String): Verdict {
        val text = reply.trim()
        if (text.isEmpty()) return Verdict.Reject("empty reply")

        FOREIGN_SCRIPT.find(text)?.let {
            return Verdict.Reject("foreign script '${it.value}' in a $lang reply")
        }
        SELF_TALK.find(text)?.let {
            return Verdict.Reject("model narrating itself: '${it.value}'")
        }
        LEAKED_MARKUP.find(text)?.let {
            return Verdict.Reject("leaked prompt scaffolding: '${it.value.trim()}'")
        }
        // Per sentence: the ask and the answer have to be handed over together to count.
        text.split(Regex("(?<=[.?!])\\s+")).forEach { sentence ->
            handedOverAnswer(sentence)?.let {
                return Verdict.Reject("handed the student the answer to produce: '$it'")
            }
        }
        // A bare mention of a neighbouring language is legitimate; a mention plus a hedge about
        // which language it is writing in is the drift pattern.
        if (OTHER_LANGUAGE.containsMatchIn(text) &&
            Regex("(?i)\\b(in (croatian|portuguese|french)|back to|switch(ing)? to)\\b")
                .containsMatchIn(text) &&
            Regex("(?i)\\b(sorry|wait|actually|hmm|let me)\\b").containsMatchIn(text)
        ) {
            return Verdict.Reject("hedging about which language it is writing in")
        }
        return Verdict.Ok
    }

    /** Appended to the system prompt on a retry, naming the exact failure. */
    fun retryNudge(reason: String, languageName: String): String =
        "\n\nCRITICAL: your previous attempt was discarded before the student saw it ($reason). " +
            "Write the reply again, cleanly, in $languageName and English only. Never narrate " +
            "your own process, never correct yourself mid-message, never use another language's " +
            "alphabet, and never name the $languageName words you are asking the student to " +
            "produce. Produce only the finished reply."
}
