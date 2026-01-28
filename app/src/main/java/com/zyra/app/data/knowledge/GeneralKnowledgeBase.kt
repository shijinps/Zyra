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
        "capital of karnataka" to "Bengaluru (Bangalore) is the capital of Karnataka.",
        "capital of tamil nadu" to "Chennai is the capital of Tamil Nadu.",
        "capital of maharashtra" to "Mumbai is the capital of Maharashtra.",
        
        // World Geography
        "capital of usa" to "Washington, D.C. is the capital of the United States.",
        "capital of uk" to "London is the capital of the United Kingdom.",
        "capital of japan" to "Tokyo is the capital of Japan.",
        "largest ocean" to "The Pacific Ocean is the largest ocean on Earth.",
        "highest mountain" to "Mount Everest is the highest mountain in the world.",

        // Science (10th Levl)
        "newton's first law" to "Newton's First Law (Law of Inertia) states that an object remains at rest or in uniform motion unless acted upon by an external force.",
        "newton's second law" to "Newton's Second Law states that F = ma (Force equals mass times acceleration).",
        "newton's third law" to "Newton's Third Law states that for every action, there is an equal and opposite reaction.",
        "photosynthesis" to "Photosynthesis is the process by which green plants create food using sunlight, carbon dioxide, and water.",
        "mitochondria" to "Mitochondria is the powerhouse of the cell, generating chemical energy.",
        "atomic number of carbon" to "The atomic number of Carbon is 6.",
        "speed of light" to "The speed of light in a vacuum is approximately 300,000 kilometers per second (299,792,458 m/s).",
        "boiling point of water" to "Water boils at 100 degrees Celsius (212 degrees Fahrenheit) at standard pressure.",
        "freezing point of water" to "Water freezes at 0 degrees Celsius (32 degrees Fahrenheit).",
        "nearest planet" to "Mercury is the closest planet to the Sun, but Venus is often the closest to Earth.",
        "red planet" to "Mars is known as the Red Planet due to iron oxide on its surface.",

        // History (India)
        "independence day of india" to "India gained independence on August 15, 1947.",
        "republic day of india" to "India became a Republic on January 26, 1950.",
        "father of the nation" to "Mahatma Gandhi is revered as the Father of the Nation.",
        "first prime minister of india" to "Jawaharlal Nehru was the first Prime Minister of India.",
        "iron man of india" to "Sardar Vallabhbhai Patel is known as the Iron Man of India.",
        "constitution of india" to "The Constitution of India was adopted on November 26, 1949, and came into effect on January 26, 1950. Dr. B.R. Ambedkar was the chairman of the drafting committee.",
        
        // General Tech
        "founder of google" to "Google was founded by Larry Page and Sergey Brin.",
        "founder of microsoft" to "Microsoft was founded by Bill Gates and Paul Allen.",
        "ceo of google" to "Sundar Pichai is the CEO of Google (Alphabet Inc).",
        "what is android" to "Android is a mobile operating system based on a modified version of the Linux kernel, developed by the Open Handset Alliance and commercially sponsored by Google."
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
