package com.zyra.app.data.knowledge

data class LawSection(
    val section: String,
    val act: String,
    val description: String,
    val punishment: String,
    val keywords: List<String>
)

object LegalKnowledgeBase {
    
    val laws = listOf(
        // Information Technology Act, 2000
        LawSection(
            "Section 65", "IT Act, 2000",
            "Tampering with computer source documents.",
            "Imprisonment up to 3 years, or fine up to ₹2 lakh, or both.",
            listOf("tamper", "source code", "65")
        ),
        LawSection(
            "Section 66", "IT Act, 2000",
            "Computer Related Offences (Hacking, Data Theft).",
            "Imprisonment up to 3 years, or fine up to ₹5 lakh, or both.",
            listOf("hacking", "hack", "data theft", "66")
        ),
        LawSection(
            "Section 66A", "IT Act, 2000",
            "Punishment for sending offensive messages through communication service (Struck down by SC, but historically relevant).",
            "Declared unconstitutional by Supreme Court in Shreya Singhal case (2015).",
            listOf("offensive messages", "66a", "trolling")
        ),
        LawSection(
            "Section 66B", "IT Act, 2000",
            "Punishment for dishonestly receiving stolen computer resource or communication device.",
            "Imprisonment up to 3 years, or fine up to ₹1 lakh, or both.",
            listOf("stolen computer", "stolen phone", "66b")
        ),
        LawSection(
            "Section 66C", "IT Act, 2000",
            "Punishment for identity theft (using others' password/digital signature).",
            "Imprisonment up to 3 years and fine up to ₹1 lakh.",
            listOf("identity theft", "password theft", "impersonation", "66c")
        ),
        LawSection(
            "Section 66D", "IT Act, 2000",
            "Punishment for cheating by personation by using computer resource.",
            "Imprisonment up to 3 years and fine up to ₹1 lakh.",
            listOf("cheating", "personation", "phishing", "66d")
        ),
        LawSection(
            "Section 66E", "IT Act, 2000",
            "Punishment for violation of privacy (capturing/publishing private images).",
            "Imprisonment up to 3 years, or fine up to ₹2 lakh, or both.",
            listOf("privacy violation", "nudes", "private pictures", "66e")
        ),
        LawSection(
            "Section 66F", "IT Act, 2000",
            "Punishment for cyber terrorism.",
            "Imprisonment for life.",
            listOf("cyber terrorism", "terrorism", "66f")
        ),
        LawSection(
            "Section 67", "IT Act, 2000",
            "Punishment for publishing or transmitting obscene material in electronic form.",
            "Imprisonment up to 3 years and fine up to ₹5 lakh (First conviction).",
            listOf("obscene", "pornography", "67")
        ),
        
        // IPC Specifics (Cyber Context)
        LawSection(
            "Section 420", "Indian Penal Code",
            "Cheating and dishonestly inducing delivery of property.",
            "Imprisonment up to 7 years and fine.",
            listOf("420", "fraud", "scam")
        ),
        LawSection(
            "Section 379", "Indian Penal Code",
            "Punishment for theft (applicable to data theft in some contexts).",
            "Imprisonment up to 3 years, or fine, or both.",
            listOf("theft", "stealing", "379")
        ),
        LawSection(
            "Section 419", "Indian Penal Code",
            "Punishment for cheating by personation.",
            "Imprisonment up to 3 years, or fine, or both.",
            listOf("419", "impersonation")
        )
    )

    fun getLawResponse(query: String): String? {
        val lower = query.lowercase()
        
        // 1. Direct Section Match
        val sectionMatch = laws.find { lower.contains(it.section.lowercase()) || (lower.contains("section ${it.keywords.last()}")) }
        if (sectionMatch != null) {
            return "${sectionMatch.section} of ${sectionMatch.act}: ${sectionMatch.description} Punishment: ${sectionMatch.punishment}"
        }
        
        // 2. Keyword Match
        val keywordMatch = laws.find { law -> law.keywords.any { k -> lower.contains(k) } }
        if (keywordMatch != null) {
             return "Regarding your query on '${keywordMatch.keywords.first()}', ${keywordMatch.section} of ${keywordMatch.act} covers this. It states: ${keywordMatch.description} The penalty includes ${keywordMatch.punishment}"
        }
        
        return null
    }
}
