package com.zyra.app.logic.ml

import com.zyra.app.logic.IntentType
import java.util.Locale

// Handles the bridging between Text and Neural Network probabilities
class IntentClassifier(private val context: android.content.Context) {

    // Vocabulary for Bag-of-Words (Significantly expanded for "Extreme" intelligence)
    private val vocabulary = listOf(
        "call", "dial", "phone", "contact", "make", "ring",
        "time", "clock", "current", "hour", "minute",
        "date", "day", "today", "calendar", "month", "year",
        "joke", "funny", "laugh", "comedy", "entertain", "hilarious",
        "battery", "charge", "power", "level", "percentage",
        "hello", "hi", "hey", "greetings", "zyra", "morning", "evening",
        "flashlight", "torch", "light", "dark", "on", "off",
        "wifi", "internet", "connection", "connect",
        "bluetooth", "pair", "device", "headset"
    )
    
    // Map intent to index
    private val intents = listOf(
        IntentType.CALL,
        IntentType.TIME,
        IntentType.DATE,
        IntentType.JOKE,
        IntentType.BATTERY,
        IntentType.GREETING,
        IntentType.DEVICE_CONTROL,
        IntentType.UNKNOWN
    )

    private val nn: NeuralNetwork

    init {
        // Input: Vocab Size
        // Hidden: 64 neurons (Deep learning capacity)
        // Output: Number of Intents
        nn = NeuralNetwork(vocabulary.size, 64, intents.size, learningRate = 0.5)
        
        // "Extreme" On-Device Training
        // We train the network immediately on startup on a small "Core Knowledge" dataset
        trainNetwork()
    }

    private fun trainNetwork() {
        // Training Data: (Phrase, IntentIndex)
        val trainingData = mutableListOf(
            // CALL
            "call mom" to 0, "dial dad" to 0, "phone contact" to 0, "make a call" to 0,
            // TIME
            "what time is it" to 1, "current time" to 1, "tell me the clock" to 1,
            // DATE
            "what is the date" to 2, "what day is today" to 2, "calendar check" to 2,
            // JOKE
            "tell me a joke" to 3, "make me laugh" to 3, "say something funny" to 3,
            // BATTERY
            "battery level" to 4, "how much charge" to 4, "power status" to 4,
            // GREETING
            "hello zyra" to 5, "hi" to 5, "good morning" to 5, "hey there" to 5,
            // DEVICE_CONTROL (Flashlight/Wifi/BT)
            "turn on flashlight" to 6, "enable wifi" to 6, "connect bluetooth" to 6, "torch on" to 6, "internet off" to 6
        )

        // Load Persistent Data
        val savedData = IntentPersistence.load(context)
        for ((text, type) in savedData) {
            val idx = intents.indexOf(type)
            if (idx != -1) {
                trainingData.add(text to idx)
            }
        }

        // Train for 2000 epochs (Fast on modern mobile CPUs)
        repeat(2000) {
            trainingData.forEach { (phrase, intentIdx) ->
                val params = textToVector(phrase)
                val targets = DoubleArray(intents.size) { 0.0 }
                targets[intentIdx] = 1.0 // One-hot encoding
                nn.train(params, targets)
            }
        }
    }

    fun predict(text: String): IntentType {
        val inputVector = textToVector(text)
        
        // Check for empty input to avoid noise
        if (inputVector.all { it == 0.0 }) return IntentType.UNKNOWN
        
        val probabilities = nn.predict(inputVector)
        
        // Find max probability
        var maxIdx = -1
        var maxProb = -1.0
        
        for (i in probabilities.indices) {
            if (probabilities[i] > maxProb) {
                maxProb = probabilities[i]
                maxIdx = i
            }
        }
        
        // Threshold
        if (maxProb < 0.4) return IntentType.UNKNOWN 
        
        return intents.getOrElse(maxIdx) { IntentType.UNKNOWN }
    }
    
    private fun textToVector(text: String): DoubleArray {
        val lower = text.lowercase(Locale.getDefault())
        val vector = DoubleArray(vocabulary.size)
        
        vocabulary.forEachIndexed { index, word ->
            if (lower.contains(word)) {
                vector[index] = 1.0
            }
        }
        return vector
    }

    // Dynamic Learning: Update weights based on new examples
    fun trainDynamic(text: String, type: IntentType) {
        val idx = intents.indexOf(type)
        if (idx == -1) return
        
        // PERSISTENCE SAVE
        IntentPersistence.save(context, text, type)

        val params = textToVector(text)
        
        // Skip if vocabulary doesn't cover this phrase at all (to avoid zero-learning)
        if (params.all { it == 0.0 }) return
        
        val targets = DoubleArray(intents.size) { 0.0 }
        targets[idx] = 1.0
        
        // Train partially (e.g. 50 iterations to reinforce)
        repeat(50) {
            nn.train(params, targets)
        }
    }
}
