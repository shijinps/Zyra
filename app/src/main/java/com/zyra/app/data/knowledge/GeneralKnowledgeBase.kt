package com.zyra.app.data.knowledge

object GeneralKnowledgeBase {
    
    // Simple Q&A Map for foundational knowledge
    private val facts = mapOf(
        // Geography (India/Kerala)
        "capital of india" to "The capital of India is New Delhi.",
        "capital of kerala" to "The capital of Kerala is Thiruvananthapuram.",
        "districts in kerala" to "There are 14 districts in Kerala.",
        "longest river in india" to "The Ganga (Ganges) is the longest river in India.",
        "highest peak in india" to "Kangchenjunga is the highest peak in India.",
        
        // Science (10th Levl)
        "newton's first law" to "Newton's First Law states that an object will remain at rest or in uniform motion unless acted upon by an external force. Also known as the Law of Inertia.",
        "newton's second law" to "Newton's Second Law states that Force equals mass times acceleration (F = ma).",
        "newton's third law" to "Newton's Third Law states that for every action, there is an equal and opposite reaction.",
        "photosynthesis" to "Photosynthesis is the process by which green plants define food using sunlight, carbon dioxide, and water.",
        "mitochondria" to "Mitochondria is known as the powerhouse of the cell.",
        "atomic number of carbon" to "The atomic number of Carbon is 6.",
        "speed of light" to "The speed of light in a vacuum is approximately 299,792,458 meters per second.",
        
        // History
        "independence day of india" to "India gained independence on August 15, 1947.",
        "father of the nation" to "Mahatma Gandhi is considered the Father of the Nation in India.",
        "first prime minister of india" to "Jawaharlal Nehru was the first Prime Minister of India."
    )

    fun getGKResponse(query: String): String? {
        val lower = query.lowercase().trim()
        
        // Direct Map Lookup (Simple Fuzzy)
        // Check if any key is contained in the query or vice-versa
        // "what is the capital of kerala" -> contains "capital of kerala"
        
        val bestMatch = facts.entries.find { (key, _) -> 
            lower.contains(key) 
        }
        
        if (bestMatch != null) {
            return bestMatch.value
        }
        
        return null
    }
}
