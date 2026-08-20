package com.corlang.app.ai

/**
 * The gate every tutor reply passes before a learner ever sees it.
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
            "alphabet. Produce only the finished reply."
}
