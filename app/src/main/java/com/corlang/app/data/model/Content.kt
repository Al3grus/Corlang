package com.corlang.app.data.model

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
    val canDo: List<String>,     // "can-do" statements (short form)
    /** Verbatim CEFR self-assessment grid descriptors per skill (source: cefr-grid). */
    val skills: List<SkillCanDo> = emptyList(),
    /** The official exam this level unlocks (B1 = NN 100/2021 exam), if any. */
    val exam: ExamMilestone? = null
)

@Serializable
data class SkillCanDo(
    val skill: String,           // "Listening", "Reading", "Spoken interaction", ...
    val descriptors: List<String>
)

@Serializable
data class ExamMilestone(
    val name: String,            // "Ispit iz poznavanja hrvatskoga jezika i latiničnog pisma (B1)"
    val description: String,
    val sections: List<String>,
    val passRule: String,
    val sources: List<String> = emptyList()
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
    val reviewBlock: ReviewBlock, // the 15-minute end-of-session review
    /**
     * The day's embedded lesson: the ACTUAL content to learn and practice, so no step is
     * ever a content-less instruction. Days without activities fall back to text steps.
     */
    val activities: List<DayActivity> = emptyList()
)

@Serializable
enum class ActivityKind {
    @SerialName("LEARN") LEARN,        // material to read + listen to (words, phrases, tables)
    @SerialName("EXERCISE") EXERCISE,  // interactive practice (MCQ/FILL/REORDER, graded)
    @SerialName("DIALOGUE") DIALOGUE   // conversation script to act out with a partner
}

@Serializable
data class DayActivity(
    val type: ActivityKind,
    val title: String,
    val intro: String = "",
    val items: List<LearnItem> = emptyList(),     // LEARN: the content itself
    val questions: List<Question> = emptyList(),  // EXERCISE: graded with the quiz engine
    val lines: List<DialogueLine> = emptyList(),  // DIALOGUE: the script
    val sources: List<String> = emptyList()
)

@Serializable
data class LearnItem(
    val hr: String,
    val en: String,
    val note: String? = null
)

@Serializable
data class DialogueLine(
    val speaker: String,   // "Me" (the learner's lines) or "Partner"
    val hr: String,
    val en: String
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
    val explanation: String,                   // always shown after answering
    /**
     * Listening mode: when set, this text is spoken via TTS and hidden from the learner —
     * the question is answered from audio alone. The underlying type (MCQ/FILL) is unchanged,
     * so grading needs no special case.
     */
    val audioText: String? = null,
    /** Exam-honest grading: diacritics must be typed correctly (želim ≠ zelim). */
    val strictDiacritics: Boolean = false
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

// ---------- Vocabulary (spaced-repetition deck) ----------

@Serializable
data class VocabSet(
    val packs: List<VocabPack>
)

@Serializable
data class VocabPack(
    val id: String,
    val title: String,
    val level: String,           // rough CEFR level of the pack
    val words: List<VocabWord>,
    /** Provenance: source keys from docs/sources/README.md. */
    val sources: List<String> = emptyList()
)

@Serializable
data class VocabWord(
    val id: String,              // stable key used by the SRS review table, NEVER rename
    val hr: String,              // target-language side (named for the primary language)
    val en: String,              // English gloss
    val note: String? = null,    // optional usage/grammar hint shown on the back
    val pos: String? = null,     // part of speech + gender/aspect shorthand, e.g. "n. f.", "v. pf."
    val example: Example? = null // example sentence (target + gloss); spoken via TTS
)

// ---------- Grammar syllabus (per-level reference, source-anchored) ----------

@Serializable
data class GrammarSyllabus(
    val levels: List<GrammarLevel>
)

@Serializable
data class GrammarLevel(
    val levelId: String,             // "A1", "A2", "B1"
    val intro: String = "",          // what this level's grammar unlocks
    val topics: List<GrammarTopic>,
    val sources: List<String> = emptyList()
)

@Serializable
data class GrammarTopic(
    val id: String,
    val title: String,
    val summary: String,             // plain-English explanation
    /** Reference tables rendered as monospace diagrams (same as CheatSection.diagram). */
    val tables: List<String> = emptyList(),
    val examples: List<Example> = emptyList(),
    val sources: List<String> = emptyList()
)

// ---------- Exam specs (mock exam mirroring NN 100/2021 / Croaticum format) ----------

@Serializable
enum class ExamSectionKind {
    @SerialName("LISTENING") LISTENING,
    @SerialName("READING") READING,
    @SerialName("GRAMMAR") GRAMMAR,
    @SerialName("WRITING") WRITING,
    @SerialName("SPEAKING") SPEAKING
}

@Serializable
data class ExamSpec(
    val id: String,                  // "hr-b1-mock"
    val levelId: String,             // "B1"
    val title: String,
    val description: String = "",
    /** Human-readable pass rule, applied programmatically by the runner. */
    val passRule: String,
    val sections: List<ExamSection>,
    val sources: List<String> = emptyList()
)

@Serializable
data class ExamSection(
    val id: String,                  // "slusanje", "citanje", ...
    val kind: ExamSectionKind,
    val title: String,
    val instructions: String = "",
    /** Scored sections (LISTENING/READING/GRAMMAR) need ≥ this %, null for pass/fail sections. */
    val passPercent: Int? = null,
    val passages: List<Passage> = emptyList(),
    val questions: List<Question> = emptyList(),
    val prompts: List<OpenPrompt> = emptyList()  // WRITING / SPEAKING tasks
)

@Serializable
data class Passage(
    val id: String,
    val title: String = "",
    val text: String,
    /** true = listening passage: spoken via TTS, transcript hidden during the section. */
    val audioOnly: Boolean = false
)

/** Open writing/speaking task, self-checked against a rubric + model answer. */
@Serializable
data class OpenPrompt(
    val prompt: String,
    val modelAnswer: String,
    val rubric: List<String>
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
