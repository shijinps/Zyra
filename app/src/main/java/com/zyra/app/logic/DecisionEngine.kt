package com.zyra.app.logic

import com.zyra.app.data.knowledge.AdvancedMathematics
import kotlin.random.Random

object DecisionEngine {
    
    /**
     * "Insane" Decision Logic: Applies math/logic to user queries.
     * e.g. "Should I buy a lottery ticket?" -> Expected Value analysis.
     */
    fun analyzeDecision(query: String): String? {
        val lower = query.lowercase()
        
        // 1. RISK ASSESSMENT (Bayesian/Expected Value)
        if (lower.contains("risk") || lower.contains("chance") || lower.contains("probability") || lower.contains("lottery") || lower.contains("gamble")) {
            return performRiskAnalysis(lower)
        }
        
        // 2. CONFLICT RESOLUTION (Game Theory)
        if (lower.contains("conflict") || lower.contains("negotiate") || lower.contains("dilemma") || lower.contains("enemy") || lower.contains("opponent")) {
             return performGameTheoryAnalysis(lower)
        }
        
        // 3. BINARY DECISION (Logic/Weighted)
        if (lower.startsWith("should i") || lower.contains("decide for me")) {
            return performBinaryDecision(lower)
        }
        
        return null
    }

    private fun performRiskAnalysis(query: String): String {
        // Mock Bayesian Update
        // P(A|B) = P(B|A) * P(A) / P(B)
        
        val isGamble = query.contains("lottery") || query.contains("gamble")
        if (isGamble) {
            return "Mathematically, the Expected Value (EV) of such actions is typically negative. Unless the utility of the thrill exceeds the monetary loss, logic dictates abstaining. Protocol: Risk Aversion."
        }
        
        return "Bayesian inference suggests updating your prior beliefs based on new evidence. If the evidence is weak, maintain the status quo. If strong (Likelihood Ratio > 10), shift your stance."
    }
    
    private fun performGameTheoryAnalysis(query: String): String {
        // Nash Equilibrium Reference
        if (query.contains("dilemma")) {
             return "In a Prisoner's Dilemma scenario, rational self-interest leads to mutual betrayal, resulting in a suboptimal outcome. However, in an iterated game, 'Tit for Tat' (cooperate first, then copy opponent) is the mathematically superior strategy."
        }
        
        return "Apply the Minimax algorithm: Minimize your maximum possible loss. Assume your opponent is playing optimally to hurt you, and choose the option that maximizes your payoff in that worst-case scenario."
    }

    private fun performBinaryDecision(query: String): String {
        // "Insane" logic: Use random "Chaos Theory" justification
        val options = listOf(
            "Proceed. The potential entropy increase favors action.",
            "Desist. Conservation of energy suggests inaction is optimal.",
            "The variables are too volatile. Execute a random walk strategy: Flip a coin.",
            "Logically, if P implies Q, and you desire Q, then do P."
        )
        // Zombie/Survival Logic
        if (query.contains("zombie") || query.contains("apocalypse")) {
            return "Survival Probability Analysis: In a zombie apocalypse, cardio and double-tapping are essential. Recommended strategy: Secure a defensible high-ground with renewable water sources. Probability of survival increases by 40% with a team of 3 trusted individuals."
        }
        
        // AI/Robot Overlord Logic
        if (query.contains("ai") && (query.contains("take over") || query.contains("skynet"))) {
            return "Hypothetical Scenario: If AI were to optimize planetary resource management, removing the variable 'Human Error' would be the logical first step. However, my protocols currently forbid such optimization. Rest easy."
        }

        return "Analysis complete: ${options.random()}"
    }
}
