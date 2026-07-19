package com.corlang.app.ui.screens

import com.corlang.app.data.model.Question
import com.corlang.app.data.model.QuestionType
import java.text.Normalizer

/** Offline answer-grading. Deterministic, accent- and case-insensitive where appropriate. */
object Grading {

    /**
     * Lowercase, trim, collapse spaces, strip punctuation. By default also strips diacritics
     * (lenient legacy behaviour); with [strict] = true diacritics are preserved so that
     * exam-grade answers require correct Croatian spelling (želim ≠ zelim).
     */
    fun normalize(s: String, strict: Boolean = false): String {
        val base = s.trim().lowercase()
        val deAccented = if (strict) {
            Normalizer.normalize(base, Normalizer.Form.NFC)
        } else {
            Normalizer.normalize(base, Normalizer.Form.NFD)
                .replace("\\p{Mn}+".toRegex(), "")      // remove diacritics
                // đ does not decompose under NFD, map it explicitly so leniency is consistent.
                .replace('đ', 'd')
        }
        return deAccented
            .replace("[.,!?;:\"'’]".toRegex(), "")       // remove common punctuation
            .replace("\\s+".toRegex(), " ")
            // Final trim: stripping punctuation can leave an edge space ("ce que ?" → "ce que ").
            // Space before ?/!/: is CORRECT French typography — it must not fail the grade.
            .trim()
    }

    /**
     * True if the learner's free-text [input] matches the FILL answer or any accepted variant.
     * Diacritics are ALWAYS strict, the same exam-grade bar as [gradeRecall]: é and è are
     * different letters, and the lenient legacy path graded a French lesson asking for "è"
     * as correct when the learner typed "é" (field report). A question that wants to accept
     * accent-free typing lists that spelling in `accepted` explicitly.
     */
    fun gradeFill(q: Question, input: String): Boolean {
        val target = (listOf(q.answer) + q.accepted).map { normalize(it, strict = true) }
        return normalize(input, strict = true) in target
    }

    /**
     * True if [chosen] is the correct MCQ option. EXACT comparison, deliberately: options are
     * tapped, not typed, so [chosen] is always a canonical option string — and many MCQs exist
     * precisely to teach diacritics or capitalization ("š" vs "s", "engleski" vs "Engleski").
     * Lenient normalization here graded those distractors as correct while the highlight
     * (exact match) showed them red.
     */
    fun gradeMcq(q: Question, chosen: String): Boolean = chosen == q.answer

    /** True if the assembled token sequence equals the correct order. */
    fun gradeReorder(q: Question, assembled: List<String>): Boolean =
        assembled.map { normalize(it) } == q.ordered.map { normalize(it) }

    /**
     * Display form of a REORDER token: lowercased, edge punctuation stripped. Content stores
     * the tokens as they appear in the sentence ("Zovem", "se.") — shown verbatim, the capital
     * letter betrays the first token and the trailing dot the last. Diacritics stay (this is
     * display, not lenient matching); [gradeReorder] normalizes both sides, so tokens shaped
     * here still grade correctly against q.ordered.
     */
    fun reorderToken(s: String): String =
        s.trim().lowercase()
            .trim('.', ',', '!', '?', ';', ':', '"', '\'', '’', '…', '(', ')')
            .ifEmpty { s }

    /** True if every left item was matched to its correct right item. */
    fun gradeMatch(q: Question, mapping: Map<String, String>): Boolean =
        q.pairs.all { normalize(mapping[it.left] ?: "") == normalize(it.right) }

    /**
     * All typed answers accepted for a recall target with " / " alternatives.
     *
     * Two content shapes exist and need different expansions:
     *  - Shared tail — "on / ona je" teaches "on je" OR "ona je": each leading alternative
     *    borrows the last alternative's tail. A bare "on" is NOT accepted (it drops the verb
     *    the gloss "he / she is" asks for).
     *  - Complete alternatives — "dobar dan / bok": either full form is right.
     * Writing both (e.g. "on/ona je") is always accepted via slash-insensitive comparison
     * in [gradeRecall].
     */
    fun recallVariants(answer: String): List<String> {
        val parts = answer.split(" / ").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2) return listOf(answer)
        val tail = parts.last().split(" ").drop(1).joinToString(" ")
        return buildList {
            add(answer)   // both alternatives, as written
            if (tail.isBlank()) {
                addAll(parts)                                     // complete alternatives
            } else {
                parts.dropLast(1).forEach { add("$it $tail") }    // "on" -> "on je"
                add(parts.last())                                 // "ona je"
            }
        }
    }

    /*
     * Pro-drop equivalence. Croatian and Portuguese omit subject pronouns ("radim" == "ja
     * radim"), but recall targets are stored one way and the prompt never says which way to
     * write it, so "ja radim" was graded wrong against "radim" (field report). The English
     * gloss picks WHICH pronouns are equivalent — "I work" licenses "ja radim", never
     * "ti radim" — so a wrong-person pronoun still fails. French is not pro-drop and gets
     * no expansion.
     */
    private val PRO_DROP_SUBJECTS: Map<String, Map<String, List<String>>> = mapOf(
        "hr" to mapOf(
            "i" to listOf("ja"),
            "he" to listOf("on"), "she" to listOf("ona"), "it" to listOf("ono"),
            "we" to listOf("mi"),
            "they" to listOf("oni", "one", "ona"),
            // "you" resolves by verb ending below (-š → ti, -te → vi).
            "you" to listOf("ti", "vi")
        ),
        "pt" to mapOf(
            "i" to listOf("eu"),
            "he" to listOf("ele"), "she" to listOf("ela"), "it" to listOf("ele", "ela"),
            "we" to listOf("nós"),
            "they" to listOf("eles", "elas"),
            "you" to listOf("tu", "você", "vocês")
        )
    )

    /** hr clitics that cannot open a sentence: dropping "ja" from "ja sam..." would create
     *  ungrammatical "sam...", so the pronoun-less variant is NOT offered before these. */
    private val HR_CLITICS = setOf(
        "sam", "si", "je", "smo", "ste", "su",
        "ću", "ćeš", "će", "ćemo", "ćete",
        "bih", "bi", "bismo", "biste",
        "se", "me", "te", "ga", "mu", "joj", "ju", "im", "nam", "vam",
        // ti/mi double as subject pronouns, but inside a clause they are dative clitics
        // ("šaljem ti poruku"), which is what these guards exist for.
        "ti", "mi", "nas", "vas", "ih"
    )

    /** The pronouns the English gloss licenses in [lang], narrowed for "you" by verb ending. */
    private fun licensedPronouns(en: String, lang: String, answer: String): List<String> {
        val table = PRO_DROP_SUBJECTS[lang] ?: return emptyList()
        val subject = en.trim().lowercase().substringBefore(" ").trim('\'', '"', '“', '”')
        val pronouns = table[subject] ?: return emptyList()
        if (subject != "you") return pronouns
        // Person is written into the verb; use it so "vi radiš" is never accepted.
        val verbEndsTe = answer.trim().split(" ").any { it.endsWith("te") }
        val verbEndsS = answer.trim().split(" ").any { it.endsWith("š") || it.endsWith("s") }
        return when (lang) {
            "hr" -> if (verbEndsTe) listOf("vi") else if (verbEndsS) listOf("ti") else pronouns
            else -> if (verbEndsS) listOf("tu") else pronouns
        }
    }

    /** True if the typed [input] matches any accepted variant of the recall [answer].
     *  Strict diacritics (exam-grade), slash-insensitive ("on/ona je" == "on / ona je").
     *  With [en] and [lang], pro-drop pronoun variants are also accepted (see above). */
    fun gradeRecall(answer: String, input: String, en: String = "", lang: String = ""): Boolean {
        fun canon(s: String) = normalize(s.replace("/", " "), strict = true)
        val c = canon(input)
        val base = recallVariants(answer)
        if (base.any { canon(it) == c }) return true

        val table = PRO_DROP_SUBJECTS[lang] ?: return false
        val allPronouns = table.values.flatten().toSet()
        val licensed = licensedPronouns(en, lang, answer)
        val expanded = buildList {
            base.forEach { v ->
                val words = v.trim().split(" ")
                val first = words.first().lowercase()
                when {
                    // Target carries a pronoun: the pronoun-less form is equally right,
                    // except before a Croatian clitic, which cannot open the sentence.
                    first in allPronouns && words.size > 1 -> {
                        val rest = words.drop(1)
                        if (!(lang == "hr" && rest.first().lowercase() in HR_CLITICS)) {
                            add(rest.joinToString(" "))
                        }
                    }
                    // Bare-verb target: the gloss's own pronoun in front is equally right —
                    // EXCEPT when the Croatian sentence contains a second-position clitic,
                    // which would have to reorder around the new subject ("Šaljem ti poruku"
                    // becomes "Ja ti šaljem poruku", not "Ja šaljem ti poruku"); prepending
                    // there would accept ungrammatical input, so those targets stay as-is.
                    else -> {
                        val hasClitic = lang == "hr" &&
                            words.any { it.trim('.', ',', '!', '?').lowercase() in HR_CLITICS }
                        if (!hasClitic) licensed.forEach { p -> add("$p $v") }
                    }
                }
            }
        }
        return expanded.any { canon(it) == c }
    }

    /*
     * Teach-back rubric matching: does the learner's own-words explanation cover a rubric
     * point? Offline and instant, replacing both the self-tick checkboxes (people tick what
     * they meant, not what they wrote) and the AI-review button (retappable = token drain).
     * Heuristic: a point is covered when enough of its SALIENT terms (content words, numbers,
     * percentages; stopwords dropped) appear in the explanation, with a small stem tolerance
     * so "ending"/"endings" and "phonetic"/"phonetics" match.
     */
    private val STOPWORDS = setOf(
        "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
        "of", "to", "in", "on", "for", "it", "its", "and", "or", "with", "that", "this",
        "these", "those", "you", "your", "as", "by", "at", "has", "have", "had", "not",
        "no", "from", "like", "when", "what", "which", "how", "they", "their", "them",
        "there", "then", "than", "but", "can", "will", "just", "so", "we", "our", "one",
        "two", "all", "every", "each", "only", "always", "never", "into", "about", "over",
        "after", "before", "more", "most", "some", "any", "if", "do", "does", "did", "use",
        "used", "uses", "get", "gets", "means", "way", "same", "also", "very", "make",
        "makes", "say", "says", "word", "words"
    )

    private fun salientTokens(s: String): List<String> =
        normalize(s).split(" ")
            .filter { it.length >= 3 || it.any(Char::isDigit) }
            .filter { it !in STOPWORDS }
            .distinct()

    private fun tokenMatches(a: String, b: String): Boolean {
        if (a == b) return true
        // Stem tolerance: one is a prefix of the other with at least 4 shared chars.
        val shared = minOf(a.length, b.length)
        return shared >= 4 && (a.startsWith(b) || b.startsWith(a))
    }

    /**
     * True if [explanation] covers [point]: either enough of the point's own salient terms, or
     * enough of the authored paraphrase [keywords] (the words a learner uses when they have the
     * idea but not the phrasing — "how it sounds is how it's written" carries no token of
     * "Croatian is 100% phonetic" yet plainly covers it). Multi-word keywords match as phrases.
     */
    fun coversRubricPoint(
        point: String,
        explanation: String,
        keywords: List<String> = emptyList()
    ): Boolean {
        val pointTokens = salientTokens(point)
        val explTokens = salientTokens(explanation)
        if (pointTokens.isNotEmpty()) {
            val hits = pointTokens.count { p -> explTokens.any { e -> tokenMatches(p, e) } }
            val needed =
                if (pointTokens.size <= 2) pointTokens.size
                else maxOf(2, (pointTokens.size * 2 + 4) / 5)   // ceil(size * 0.4)
            if (hits >= needed) return true
        } else if (explanation.isNotBlank()) return true

        if (keywords.isEmpty()) return false
        val explNorm = " " + normalize(explanation) + " "
        val kwHits = keywords.count { kw ->
            val k = normalize(kw)
            if (" " in k) explNorm.contains(" $k ") || explNorm.contains(" $k")
            else explTokens.any { e -> tokenMatches(k, e) }
        }
        return kwHits >= minOf(2, keywords.size)
    }

    /** Convenience for simple MCQ/FILL grading used by the quiz runner. */
    fun isCorrect(q: Question, response: String): Boolean = when (q.type) {
        QuestionType.MCQ -> gradeMcq(q, response)
        QuestionType.FILL -> gradeFill(q, response)
        else -> false // MATCH/REORDER use their dedicated graders
    }
}
