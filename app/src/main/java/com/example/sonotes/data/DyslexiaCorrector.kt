package com.example.sonotes.data

import java.text.Normalizer
import kotlin.math.min

data class DyslexiaCorrectionResult(
    val originalWord: String,
    val correctedWord: String,
    val isCorrected: Boolean,
    val alternatives: List<String>
)

object DyslexiaCorrector {

    private val GREEK_DICTIONARY = setOf(
        "επειδή", "αγάπη", "ξέρω", "καλημέρα", "ευχαριστώ", "σημείωση", "σκέψη", "σημαντικό",
        "γιατί", "όταν", "μετά", "πώς", "είναι", "έχω", "κάνω", "θέλω", "βλέπω", "λέω",
        "μέρα", "νύχτα", "σχολείο", "βιβλίο", "παιδί", "άνθρωπος", "φίλος", "οικογένεια",
        "δουλειά", "χρόνος", "ώρα", "σπίτι", "νερό", "ψωμί", "φαγητό", "ζωή", "κόσμος",
        "αλήθεια", "ελπίδα", "χαρά", "ειρήνη", "ελευθερία", "δύναμη", "ομορφιά", "γνώμη",
        "ιδέα", "πράγμα", "πρόβλημα", "λύση", "αρχή", "τέλος", "σήμερα", "αύριο", "χθες",
        "τώρα", "πάντα", "ποτέ", "ίσως", "μαζί", "μόνο", "πολύ", "λίγο", "καλά", "κακά",
        "μεγάλος", "μικρός", "όμορφος", "καλός", "κακός", "νέος", "παλιός", "πρώτος",
        "τελευταίος", "έτσι", "τότε", "εκεί", "εδώ", "αλλά", "όμως", "επίσης", "ακόμα",
        "ξανά", "σχεδόν", "πάλι", "μέχρι", "χωρίς", "μέσα", "έξω", "πάνω", "κάτω", "μπροστά",
        "πίσω", "κοντά", "μακριά", "παντού", "πουθενά", "τίποτα", "όλα", "κάτι", "κάποιος",
        "κανένας", "όλος", "άλλος", "ίδιος", "μόνος", "έτοιμος", "σίγουρος", "δυνατός",
        "αδύνατος", "εύκολος", "δύσκολος", "γρήγορος", "αργός", "ζεστός", "κρύος", "καθαρός"
    )

    private val ENGLISH_DICTIONARY = setOf(
        "because", "friend", "believe", "receive", "beautiful", "people", "together",
        "thought", "through", "different", "important", "government", "necessary",
        "definitely", "separate", "accommodation", "embarrass", "rhythm", "environment",
        "tomorrow", "yesterday", "always", "never", "maybe", "sometimes", "without",
        "between", "against", "during", "before", "after", "above", "below", "behind",
        "something", "anything", "nothing", "everything", "someone", "anyone", "everyone",
        "problem", "solution", "question", "answer", "example", "information", "knowledge",
        "family", "business", "company", "system", "program", "service", "product"
    )

    private val accentRegex = Regex("\\p{M}+")

    private fun stripAccents(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
        return normalized.replace(accentRegex, "").lowercase().replace('ς', 'σ')
    }

    private fun phoneticGreek(input: String): String {
        var s = stripAccents(input)
        s = s.replace("ει", "ι")
             .replace("οι", "ι")
             .replace("υ", "ι")
             .replace("η", "ι")
             .replace("αι", "ε")
             .replace("ω", "ο")
             .replace("ου", "υ")
        return s
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }

    fun correctWord(input: String, languageCode: String): DyslexiaCorrectionResult {
        val trimmed = input.trim()
        if (trimmed.length < 3) {
            return DyslexiaCorrectionResult(trimmed, trimmed, false, listOf(trimmed))
        }

        val dictionary = if (languageCode.startsWith("el")) GREEK_DICTIONARY else ENGLISH_DICTIONARY
        val strippedInput = stripAccents(trimmed)
        val isGreek = languageCode.startsWith("el")

        val exactMatch = dictionary.firstOrNull { stripAccents(it) == strippedInput }
        if (exactMatch != null) {
            val preservedCase = preserveCase(trimmed, exactMatch)
            return DyslexiaCorrectionResult(trimmed, preservedCase, false, listOf(preservedCase))
        }

        var bestCandidate: String? = null
        var minScore = Int.MAX_VALUE

        val inputPhonetic = if (isGreek) phoneticGreek(trimmed) else strippedInput

        for (dictWord in dictionary) {
            val dictStripped = stripAccents(dictWord)
            val dictPhonetic = if (isGreek) phoneticGreek(dictWord) else dictStripped

            val distance = levenshteinDistance(inputPhonetic, dictPhonetic)

            if (distance < minScore) {
                minScore = distance
                bestCandidate = dictWord
            }
        }

        val isCorrectionFound = bestCandidate != null && minScore <= 2
        val corrected = if (bestCandidate != null && minScore <= 2) preserveCase(trimmed, bestCandidate) else trimmed

        val altList = mutableListOf<String>()
        if (isCorrectionFound) {
            altList.add(corrected)
            altList.add(trimmed)
        } else {
            altList.add(trimmed)
        }

        return DyslexiaCorrectionResult(
            originalWord = trimmed,
            correctedWord = corrected,
            isCorrected = isCorrectionFound,
            alternatives = altList.distinct()
        )
    }

    private fun preserveCase(original: String, target: String): String {
        if (original.isEmpty()) return target
        return if (original[0].isUpperCase()) {
            target.replaceFirstChar { it.uppercase() }
        } else {
            target.lowercase()
        }
    }
}
