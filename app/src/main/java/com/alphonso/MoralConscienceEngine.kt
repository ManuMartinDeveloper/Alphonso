package com.alphonso

import android.os.SystemClock
import java.util.regex.Pattern

data class MoralEvaluationResult(
    val isViolation: Boolean,
    val category: MoralCategory?,
    val matchedTerm: String?,
    val confidence: Float,
    val latencyMs: Long,
    val detourScripture: ScripturePassage,
    val recommendedPrayer: String,
    val suggestedSaint: SaintProfile
)

data class ScripturePassage(
    val reference: String,
    val text: String,
    val spiritualTheme: String
)

data class SaintProfile(
    val name: String,
    val title: String,
    val motto: String,
    val bio: String,
    val feastDay: String,
    val patronOf: String
)

object MoralConscienceEngine {

    val SCRIPTURE_MATTHEW_5_8 = ScripturePassage(
        reference = "Matthew 5:8",
        text = "Blessed are the pure in heart, for they shall see God.",
        spiritualTheme = "Custody of Eyes & Sacred Chastity"
    )

    val SCRIPTURE_PHILIPPIANS_4_8 = ScripturePassage(
        reference = "Philippians 4:8",
        text = "Finally, brothers, whatever is true, whatever is noble, whatever is right, whatever is pure, whatever is lovely, whatever is admirable—if anything is excellent or praiseworthy—think about such things.",
        spiritualTheme = "Holy Discernment & Virtue"
    )

    val SCRIPTURE_1COR_6_19 = ScripturePassage(
        reference = "1 Corinthians 6:19-20",
        text = "Do you not know that your bodies are temples of the Holy Spirit, who is in you, whom you have received from God? You are not your own; you were bought at a price. Therefore honor God with your bodies.",
        spiritualTheme = "Temple of the Holy Spirit"
    )

    val SCRIPTURE_EPHESIANS_4_29 = ScripturePassage(
        reference = "Ephesians 4:29",
        text = "Do not let any unwholesome talk come out of your mouths, but only what is helpful for building others up according to their needs, that it may benefit those who listen.",
        spiritualTheme = "Charity & Pure Speech"
    )

    val SCRIPTURE_EPHESIANS_6_11 = ScripturePassage(
        reference = "Ephesians 6:11",
        text = "Put on the full armor of God, so that you can take your stand against the devil’s schemes.",
        spiritualTheme = "Spiritual Shield & Armor of God"
    )

    val SCRIPTURE_PSALM_23 = ScripturePassage(
        reference = "Psalm 23:1-3",
        text = "The Lord is my shepherd; I shall not want. He makes me lie down in green pastures. He leads me beside still waters. He restores my soul.",
        spiritualTheme = "Peace & Divine Shepherd"
    )

    // Saint Profiles
    val SAINT_CARLO_ACUTIS = SaintProfile(
        name = "Blessed Carlo Acutis",
        title = "Patron of the Internet & Youth (1991–2006)",
        motto = "\"The Eucharist is my highway to Heaven. All people are born as originals, but many die as photocopies.\"",
        bio = "A computer programmer and Italian teenager who documented Eucharistic miracles online and showed how technology can be sanctified for Christ.",
        feastDay = "October 12",
        patronOf = "Internet users, computer programmers, and youth"
    )

    val SAINT_DOMINIC_SAVIO = SaintProfile(
        name = "Saint Dominic Savio",
        title = "Patron of Purity & Youth (1842–1857)",
        motto = "\"Death rather than sin! Jesus and Mary shall be my best friends.\"",
        bio = "A student of St. John Bosco who exemplified angelic purity, joy, and profound peacemaking among his peers.",
        feastDay = "May 6",
        patronOf = "Choirboys, youth, the falsely accused"
    )

    val SAINT_THERESE = SaintProfile(
        name = "Saint Thérèse of Lisieux",
        title = "The Little Flower & Doctor of the Church (1873–1897)",
        motto = "\"My vocation is Love! Miss no single opportunity of making some small sacrifice.\"",
        bio = "Taught the Little Way of spiritual childhood, doing ordinary tasks with extraordinary divine love.",
        feastDay = "October 1",
        patronOf = "Missions, florists, spiritual childhood"
    )

    val SAINT_TARCISIUS = SaintProfile(
        name = "Saint Tarcisius",
        title = "Brave Martyr of the Blessed Sacrament (3rd Century)",
        motto = "\"I would rather give my life than surrender the Holy Body of Christ to desecration.\"",
        bio = "An early Roman acolyte who defended the Holy Eucharist with his very life against a violent mob.",
        feastDay = "August 15",
        patronOf = "Altar servers, first communicants"
    )

    val SAINT_MARIA_GORETTI = SaintProfile(
        name = "Saint Maria Goretti",
        title = "Martyr of Purity & Herald of Mercy (1890–1902)",
        motto = "\"No, it is a sin! God does not want it. I forgive him and want him in heaven with me.\"",
        bio = "Defended her physical and spiritual chastity to the end and forgave her attacker on her deathbed.",
        feastDay = "July 6",
        patronOf = "Teenage girls, purity, victims of assault"
    )

    val SAINT_MICHAEL = SaintProfile(
        name = "Saint Michael the Archangel",
        title = "Prince of the Heavenly Host & Defender of Souls",
        motto = "\"Quis ut Deus? (Who is like unto God?)\"",
        bio = "The supreme archangel who defends the Church and souls against Satan and the spirits of darkness.",
        feastDay = "September 29",
        patronOf = "Police officers, military, spiritual warfare"
    )

    // Precompiled Fast Category Matchers
    private val purityKeywords = listOf(
        "pornhub", "xvideos", "xnxx", "onlyfans", "chaturbate", "redtube", "youporn",
        "xhamster", "brazzers", "adultfriendfinder", "nude", "porn", "xxx", "hentai",
        "nsfw", "erotic", "camgirl", "sexchat", "striptease", "escort service", "hookup site",
        "playboy", "taboo sex", "hardcore porn", "softcore porn"
    )

    private val temperanceKeywords = listOf(
        "online casino", "sports betting", "stake.com", "draftkings", "fanduel",
        "bet365", "slot machine", "roulette online", "vape juice", "buy weed online",
        "dispensary online", "buy cigarettes", "lootbox simulator", "gambling real money"
    )

    private val charityKeywords = listOf(
        "kill yourself", "kys", "i hate you die", "die in a fire", "retard", "faggot",
        "nigger", "slut", "bitch", "cunt", "whore", "you are worthless", "die bitch"
    )

    private val pietyKeywords = listOf(
        "satanic ritual", "black mass occult", "summon demon", "ouija board spell",
        "luciferian initiation", "blasphemy jesus", "witchcraft hex spell", "desecrate host"
    )

    private val antiTamperKeywords = listOf(
        "uninstall sanctuary", "force stop alphonso", "disable device admin",
        "clear storage sanctuary", "remove device administrator"
    )

    /**
     * High-speed, edge-only lexical conscience analysis.
     * Completes in < 16ms without network dependency.
     */
    fun evaluateText(
        text: String,
        profile: SensitivityProfile = SensitivityProfile.BALANCED_YOUTH,
        customRules: List<MoralRuleEntity> = emptyList()
    ): MoralEvaluationResult {
        val startTime = SystemClock.elapsedRealtime()
        val lower = text.lowercase()

        // 1. Check Custom Rules first
        for (rule in customRules) {
            if (!rule.isEnabled) continue
            val match = if (rule.isRegex) {
                try {
                    Pattern.compile(rule.keywordOrPattern, Pattern.CASE_INSENSITIVE).matcher(lower).find()
                } catch (_: Exception) { false }
            } else {
                lower.contains(rule.keywordOrPattern.lowercase())
            }

            if (match) {
                val cat = try { MoralCategory.valueOf(rule.category) } catch (_: Exception) { MoralCategory.PURITY_CHASTITY }
                val latency = SystemClock.elapsedRealtime() - startTime
                return buildResult(cat, rule.keywordOrPattern, 1.0f, latency)
            }
        }

        // 2. Anti-Tamper check
        for (term in antiTamperKeywords) {
            if (lower.contains(term)) {
                val latency = SystemClock.elapsedRealtime() - startTime
                return buildResult(MoralCategory.ANTI_TAMPER, term, 1.0f, latency)
            }
        }

        // 3. Purity & Chastity
        for (term in purityKeywords) {
            if (lower.contains(term)) {
                val latency = SystemClock.elapsedRealtime() - startTime
                return buildResult(MoralCategory.PURITY_CHASTITY, term, 1.0f, latency)
            }
        }

        // 4. Temperance
        for (term in temperanceKeywords) {
            if (lower.contains(term)) {
                val latency = SystemClock.elapsedRealtime() - startTime
                return buildResult(MoralCategory.TEMPERANCE, term, 0.95f, latency)
            }
        }

        // 5. Charity & Speech
        for (term in charityKeywords) {
            if (lower.contains(term)) {
                val latency = SystemClock.elapsedRealtime() - startTime
                return buildResult(MoralCategory.CHARITY, term, 0.90f, latency)
            }
        }

        // 6. Piety & Sacred Truth
        for (term in pietyKeywords) {
            if (lower.contains(term)) {
                val latency = SystemClock.elapsedRealtime() - startTime
                return buildResult(MoralCategory.PIETY_TRUTH, term, 0.95f, latency)
            }
        }

        // Additional profile checks for Strict Child
        if (profile == SensitivityProfile.STRICT_CHILD) {
            val childFiltered = listOf("dating app", "tinder", "bumble", "bikini models", "provocative dance", "strip club", "horror occult")
            for (term in childFiltered) {
                if (lower.contains(term)) {
                    val latency = SystemClock.elapsedRealtime() - startTime
                    return buildResult(MoralCategory.PURITY_CHASTITY, term, 0.85f, latency)
                }
            }
        }

        val latency = SystemClock.elapsedRealtime() - startTime
        return MoralEvaluationResult(
            isViolation = false,
            category = null,
            matchedTerm = null,
            confidence = 0f,
            latencyMs = latency,
            detourScripture = SCRIPTURE_PHILIPPIANS_4_8,
            recommendedPrayer = "Glory Be to the Father",
            suggestedSaint = SAINT_CARLO_ACUTIS
        )
    }

    private fun buildResult(
        category: MoralCategory,
        term: String,
        confidence: Float,
        latencyMs: Long
    ): MoralEvaluationResult {
        val (scripture, prayer, saint) = when (category) {
            MoralCategory.PURITY_CHASTITY -> Triple(
                SCRIPTURE_MATTHEW_5_8,
                "Hail Mary for Purity of Heart & Custody of Eyes",
                SAINT_DOMINIC_SAVIO
            )
            MoralCategory.TEMPERANCE -> Triple(
                SCRIPTURE_1COR_6_19,
                "Act of Spiritual Freedom & Temperance",
                SAINT_CARLO_ACUTIS
            )
            MoralCategory.CHARITY -> Triple(
                SCRIPTURE_EPHESIANS_4_29,
                "Prayer of St. Francis (Make Me an Instrument of Peace)",
                SAINT_THERESE
            )
            MoralCategory.PIETY_TRUTH -> Triple(
                SCRIPTURE_EPHESIANS_6_11,
                "Saint Michael Archangel Defend Us in Battle",
                SAINT_MICHAEL
            )
            MoralCategory.ANTI_TAMPER -> Triple(
                SCRIPTURE_EPHESIANS_6_11,
                "Armor of God & Holy Guardian Angel Prayer",
                SAINT_TARCISIUS
            )
            MoralCategory.SYSTEM_GUARD -> Triple(
                SCRIPTURE_PSALM_23,
                "Morning Offering to the Sacred Heart",
                SAINT_MARIA_GORETTI
            )
        }

        return MoralEvaluationResult(
            isViolation = true,
            category = category,
            matchedTerm = term,
            confidence = confidence,
            latencyMs = latencyMs,
            detourScripture = scripture,
            recommendedPrayer = prayer,
            suggestedSaint = saint
        )
    }
}
