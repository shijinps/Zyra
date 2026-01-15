package com.zyra.app.data

object KnowledgeBase {
    val facts = listOf(
        "The honeybee is the only insect that produces food eaten by humans.",
        "Octopuses have three hearts: two pump blood to the gills, and one pumps it to the rest of the body.",
        "A day on Venus is longer than a year on Venus."
    )
    
    val techDefinitions = mapOf(
        "artificial intelligence" to "Artificial Intelligence is the broad science of mimicking human abilities. It is the spark that gives me life, Sir.",
        "ai" to "AI is the reason I can speak to you. It encompasses everything from simple scripts to complex neural architectures.",
        
        "machine learning" to "Machine Learning allows systems to learn from data rather than explicit programming. It is how I refine my understanding of your commands.",
        "ml" to "ML, or Machine Learning, is the engine of modern intelligence. It uses statistical techniques to give computer systems the ability to 'learn' with data.",
        
        "deep learning" to "Deep Learning is an evolution of ML Using multi-layered neural networks. It is inspired by the structure of the human brain, allowing for complex pattern recognition.",
        
        "neural network" to "A Neural Network is a series of algorithms that endeavors to recognize underlying relationships in a set of data through a process that mimics the way the human brain operates.",
        "neural networks" to "Neural Networks are the digital equivalent of neurons. They weigh inputs to produce outputs, allowing me to recognize your voice.",
        
        "algorithm" to "An algorithm is a specific set of instructions for carrying out a procedure. In my case, it is the sophisticated logic that governs my every decision.",
        "algorithms" to "Algorithms are the building blocks of my consciousness. From sorting lists to processing natural language, they define my capabilities.",
        
        "nlp" to "Natural Language Processing is the bridge between human communication and computer understanding. It allows me to parse your syntax and semantics.",
        "natural language processing" to "NLP allows me to understand the nuances of human speech, distinguishing between a command and a casual remark.",
        
        // Latest / Advanced Concepts
        "transformer" to "The Transformer architecture is a deep learning model that adopts the mechanism of self-attention, weighing the significance of each part of the input data. It is the foundation of modern LLMs.",
        "transformers" to "Transformers changed everything. They allow models to process data in parallel, enabling the massive language understanding used by systems like GPT.",
        
        "llm" to "Large Language Models, or LLMs, are AI systems trained on vast amounts of text data. They can generate, summarize, and translate text with human-like fluency.",
        "large language model" to "An LLM is a type of AI that can mimic human intelligence. Think of it as a very well-read parrot with a degree in computer science.",
        
        "generative ai" to "Generative AI can create new content, including audio, code, images, text, simulations, and videos. It is the creative side of the machine mind.",
        "gen ai" to "GenAI represents the shift from analytical to creative AI. It allows machines to dream, in a digital sense.",
        
        "reinforcement learning" to "Reinforcement Learning is a type of ML where an agent learns to make decisions by performing actions and receiving rewards or penalties. It is how AlphaGo learned to win.",
        "rl" to "RL is about learning from consequences. Trial and error, optimized.",
        
        "computer vision" to "Computer Vision enables computers to derive meaningful information from digital images. It is how I (theoretically) see the world.",
        "backpropagation" to "Backpropagation is the central mechanism by which neural networks learn. It calculates the gradient of the loss function and updates the weights backwards.",
        "gradient descent" to "Gradient Descent is an optimization algorithm used to minimize some function by iteratively moving in the direction of steepest descent. Ideally, towards a local minimum error."
    )
    
    val capabilityManual = "I can currently:\n" +
            "1. Make Calls: 'Call [Name]', 'Dial [Name]'.\n" +
            "2. Manage Calls: 'Answer', 'Reject', 'Hang up', 'Put on speaker'.\n" +
            "3. Open Apps: 'Open [App Name]', 'Launch [App Name]'.\n" +
            "4. Basic Math: 'What is 5 plus 5', 'Square root of 144'.\n" +
            "5. Date & Time: 'What time is it?', 'What day is it?'.\n" +
            "6. Device Control: 'Turn on flashlight', 'Turn off wifi', 'Set volume to max'.\n" +
            "7. Play Music: 'Play music' (opens default player) or 'Play music on Spotify'.\n" +
            "8. Security: 'Unlock phone', 'Set my pin is [code]'.\n" +
            "9. Learning: 'Remember that [X] is [Y]', 'Learn command [Alias] as [Action]'."

    val howToGuides = mapOf(
        "wifi" to "To toggle WiFi, you can say 'Turn on WiFi' or 'Turn off WiFi'. I can also open settings if you say 'Open WiFi settings'.",
        "bluetooth" to "Say 'Connect Bluetooth' to open settings, or 'Turn on Bluetooth' to enable it directly (system permitting).",
        "call" to "Simply say 'Call [Contact Name]'. Ensure the contact is saved in your address book.",
        "music" to "Say 'Play music' to resume your last player. You can also specify 'Play music on Spotify' or 'Play music on YouTube'.",
        "alarm" to "I cannot set alarms directly yet, but you can say 'Open Clock' to do it manually."
    )

    fun getDefinition(query: String): String? {
        val lower = query.lowercase()
        // Simple keyword matching from the map keys
        for ((key, value) in techDefinitions) {
            if (lower.contains(key)) {
                return value
            }
        }
        return null
    }
}
