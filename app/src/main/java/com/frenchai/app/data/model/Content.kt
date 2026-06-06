package com.frenchai.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Content schema for one language. Every field maps directly to a JSON file under
 * `assets/content/<lang>/`. Adding a new language = adding a folder of these JSON files;
 * no UI/code changes required. This is the core scalability lever of the app.
 */

@Serializable
data class LanguageMeta(
    val code: String,            // "fr", "hr"
    val name: String,            // "French"
    val nativeName: String,      // "Français"
    val flagEmoji: String,       // "🇫🇷"
    val blurb: String,
    val paretoSummary: String    // one-paragraph "the 20% that drives 80%"
)

// ---------- Cheatsheet (the 5-minute review page) ----------

@Serializable
data class Cheatsheet(
    val title: String,
    val sections: List<CheatSection>
)

@Serializable
data class CheatSection(
    val title: String,
    val bullets: List<String> = emptyList(),
    /** Optional monospace "diagram": a table or text figure rendered in a mono box. */
    val diagram: String? = null,
    /** Worked examples: target-language phrase + English gloss. */
    val examples: List<Example> = emptyList()
)

@Serializable
data class Example(
    val target: String,          // "Je suis fatigué."
    val gloss: String            // "I am tired."
)

// ---------- CEFR ladder ----------

@Serializable
data class Levels(
    val levels: List<Level>
)

@Serializable
data class Level(
    val id: String,              // "A0", "A1", ... "C1"
    val title: String,
    val milestone: String,       // the clear, single milestone for this level
    val canDo: List<String>      // "can-do" statements
)

// ---------- Study plan ----------

@Serializable
data class StudyPlan(
    val title: String,
    val days: List<StudyDay>
)

@Serializable
data class StudyDay(
    val day: Int,
    val week: Int,
    val phase: String,           // "Phase 1: A0 → A1"
    val level: String,           // CEFR level this day belongs to
    val title: String,
    val objective: String,
    val paretoFocus: String,     // why this is high-leverage
    val resources: List<String> = emptyList(),  // resource names referenced from resources.json
    val drills: List<String> = emptyList(),
    val reviewBlock: ReviewBlock // the 15-minute end-of-session review
)

@Serializable
data class ReviewBlock(
    val minutes: Int = 15,
    val items: List<String>
)

// ---------- Quizzes ----------

@Serializable
data class QuizSet(
    val quizzes: List<Quiz>
)

@Serializable
data class Quiz(
    val id: String,
    val levelId: String,
    val title: String,
    val questions: List<Question>
)

@Serializable
enum class QuestionType {
    @SerialName("MCQ") MCQ,
    @SerialName("FILL") FILL,
    @SerialName("MATCH") MATCH,
    @SerialName("REORDER") REORDER
}

@Serializable
data class Question(
    val type: QuestionType,
    val prompt: String,
    val difficulty: Int,                       // 1..10, used to order the 10 questions
    val options: List<String> = emptyList(),   // MCQ choices / REORDER tokens
    val answer: String = "",                   // canonical answer (MCQ option text, FILL text)
    val accepted: List<String> = emptyList(),  // FILL: other accepted spellings (normalized match)
    val pairs: List<Pair2> = emptyList(),      // MATCH: left/right correct pairings
    val ordered: List<String> = emptyList(),   // REORDER: correct token order
    val explanation: String                    // always shown after answering
)

/** A matching pair. Named Pair2 to avoid clashing with kotlin.Pair in serialization. */
@Serializable
data class Pair2(val left: String, val right: String)

// ---------- Feynman teach-back ----------

@Serializable
data class FeynmanSet(
    val concepts: List<FeynmanConcept>
)

@Serializable
data class FeynmanConcept(
    val id: String,
    val levelId: String,
    val title: String,
    val simpleExplanation: String,   // plain-English explanation
    val analogy: String,             // a concrete analogy
    val rubricPoints: List<RubricPoint>
)

@Serializable
data class RubricPoint(
    val point: String,               // the idea the learner should have covered
    val reTeach: String              // shown if they say they missed it
)

// ---------- Resources ----------

@Serializable
data class ResourceList(
    val resources: List<Resource>
)

@Serializable
data class Resource(
    val rank: Int,                   // 1..5
    val name: String,
    val type: String,                // book | video | course | person | app | podcast
    val url: String? = null,
    val why: String                  // why it's worth your time
)
