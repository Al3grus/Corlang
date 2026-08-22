package com.corlang.app.ai

/**
 * Stops a message that is asking the tutor for live world information BEFORE it costs an API call.
 *
 * Field report, 2026-08-22: the learner probed with the price of bitcoin, the latest Trump news
 * and news from the Strait of Hormuz. The tutor answered honestly every time ("I don't have that
 * data, so I won't guess"), which is the right answer and still the wrong outcome: it is a paid
 * round trip, and it invites another one.
 *
 * ## What this does NOT do
 *
 * It does not decide whether a message is "about language learning". Almost nothing a learner
 * says is, on its face: "I went to the market yesterday", "my sister lives in Split", "what is
 * your favourite food" are the CONTENT of conversation practice, and a guard that refused them
 * would break the feature it is meant to protect. So the target is much narrower and much safer:
 * requests for facts that change by the day and that the tutor could not know.
 *
 * The tutor has no web access at all — the worker allowlists request fields and drops `tools` —
 * so there is no research happening behind these questions, only a normal message being paid for.
 *
 * ## Why it is deliberately timid
 *
 * A false positive refuses a real learner and there is no way for them to appeal it. A false
 * negative costs one round trip and an honest answer, which is what happens today. So a match
 * needs a volatile-topic cue AND a request shape, except for a very short list of cues that
 * cannot appear in ordinary practice at any level. Everything borderline is left to the model,
 * and the system prompt carries a matching rule so a miss still gets one short sentence rather
 * than a paragraph.
 */
object OffTopicGuard {

    /**
     * Topics whose answer is different tomorrow. Deliberately excludes weather: "what is the
     * weather like" is A1 vocabulary in every course this app ships.
     */
    private val VOLATILE = Regex(
        "(?i)\\b(" +
            "bitcoin|ethereum|crypto(currency)?|blockchain|nasdaq|s&p|dow jones|" +
            "stock (market|price)|share price|exchange rate|" +
            "news|headlines|breaking|current events|" +
            "election|war in|invasion|ceasefire|sanctions|" +
            "world cup|premier league|champions league" +
            ")\\b"
    )

    /** The same, in the target languages, so the probe does not simply switch language. */
    private val VOLATILE_LOCAL = Regex(
        "(?i)\\b(" +
            "vijesti|novosti|" +                       // hr
            "not[íi]cias|manchetes|" +                 // pt
            "actualit[ée]s|nouvelles du jour|" +       // fr
            "nachrichten|schlagzeilen|" +              // de
            "notizie|" +                               // it
            "noticias" +                               // es
            ")\\b"
    )

    /** A message shaped like a request for information rather than a sentence of practice. */
    private val ASKING = Regex(
        "(?i)(" +
            "\\?|" +
            "\\bwhat('s| is| are)\\b|\\bhow much\\b|\\bhow many\\b|\\bwho (won|is winning)\\b|" +
            "\\btell me\\b|\\bgive me\\b|\\bdo you know\\b|\\bcan you (tell|find|check|look up)\\b|" +
            "\\blatest\\b|\\bcurrent(ly)?\\b|\\bright now\\b|\\btoday'?s\\b|\\bupdate on\\b|" +
            "\\bwhat happened\\b|\\bwhat'?s happening\\b|" +
            "\\bkoliko\\b|\\bšto se\\b|\\bquanto custa\\b|\\bo que aconteceu\\b" +
            ")"
    )

    /**
     * Cues strong enough on their own: no learner practising a language types these as content.
     * "Price of bitcoin" is the field report; the rest are the same shape.
     */
    private val ALWAYS = Regex(
        "(?i)(" +
            "\\b(price|value|worth|rate) of \\w+|" +
            "\\b\\w+ price\\b|" +
            "\\blatest news\\b|\\bany news\\b|\\bnews (about|on|from)\\b|" +
            "\\bsearch (the web|online|for)\\b|\\bgoogle (it|this|that)\\b|\\blook (it|this) up\\b|" +
            "\\bwrite (me )?(a|some) (code|python|javascript|sql)\\b|" +
            "\\bcijena \\w+|\\bpre[çc]o d[eo] \\w+" +
            ")"
    )

    /**
     * True when [message] is asking for live world information.
     *
     * Cheap on purpose: it runs on the UI thread before every send, and it is a handful of
     * regexes over one short string.
     */
    fun isOffTopic(message: String): Boolean {
        val text = message.trim()
        if (text.length < 3) return false
        if (ALWAYS.containsMatchIn(text)) return true
        val volatileTopic = VOLATILE.containsMatchIn(text) || VOLATILE_LOCAL.containsMatchIn(text)
        return volatileTopic && ASKING.containsMatchIn(text)
    }

    /**
     * What the learner sees instead. One short sentence, then straight back to the lesson, and
     * identical every time: somebody testing the edges should find a wall, not an argument.
     *
     * English even when the learner is working inside the target language. This is the app
     * speaking, not the tutor teaching, and a refusal is the one thing that must not be
     * misunderstood.
     */
    fun refusal(languageName: String): String =
        "I can't help with that. I'm your $languageName tutor, so let's keep going: ask me about " +
            "a word, or tell me something about your day."
}
