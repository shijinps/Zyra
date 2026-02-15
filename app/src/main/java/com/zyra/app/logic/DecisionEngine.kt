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
        
        // 3. ETHICAL DILEMMA (Moral Engine)
        if (lower.contains("ethical") || lower.contains("moral") || lower.contains("right thing") || lower.contains("trolley problem")) {
            return performEthicalAnalysis(lower)
        }
        
        // 4. FINANCIAL ADVICE (Basic Logic)
        if (lower.contains("invest") || lower.contains("stock") || lower.contains("money") || lower.contains("crypto")) {
            return performFinancialAnalysis(lower)
        }
        
        // 5. BINARY DECISION (Logic/Weighted)
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
        // QUANTUM INTEGRATION: Merging Cisco Entropy (Mood) with IBM Logic
        val mood = PersonaManager.currentMood
        
        // 1. Analytical / Professional -> Logic & Probability
        if (mood.contains("Analytical") || mood.contains("Professional") || mood.contains("Concise")) {
             val prob = Random.nextInt(75, 99)
             return "Quantum Analysis (Mode: $mood): The probability distribution favors action. Proceed with ${prob}% confidence."
        }
        
        // 2. Witty / Sassy -> Chaos & Risk
        if (mood.contains("Witty") || mood.contains("Humorous") || mood.contains("Creative")) {
             return "My quantum state is currently 'Witty'. I say do it. Add some entropy to the universe."
        }
        
        // 3. Empathetic / Protective -> Caution & Safety
        if (mood.contains("Empathetic") || mood.contains("Warm") || mood.contains("Protective")) {
             return "I have analyzed the outcomes. Please be careful, but I believe this is the right path."
        }

        // Default Quantum Superposition (True Random via logic if mood is Neutral/Unknown)
        val options = listOf(
            "Superposition collapsed: Outcome Positive. Go ahead.",
            "Quantum interference detected. Recommendation: Hold position.",
            "The qubits have aligned to 'Yes'.",
            "Entanglement suggests a 'No' at this specific moment."
        )
        
        // Zombie/Survival Logic
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

    private fun performEthicalAnalysis(query: String): String {
        if (query.contains("trolley")) {
            return "Utilitarianism suggests pulling the lever to save the many (5) at the expense of the few (1). Deontology argues that the act of killing is inherently wrong regardless of the outcome. My logic unit defaults to: Minimize total casualties."
        }
        return "Ethical Subroutine: Act in a way that you would want to become a universal law (Kant's Categorical Imperative). If the action harms no one and benefits you, proceed. If it causes harm, desist."
    }

    private fun performFinancialAnalysis(query: String): String {
        if (query.contains("crypto") || query.contains("bitcoin")) {
            return "Market Volatility Warning: Crypto assets are highly speculative. Only invest what you can afford to lose. HODL at your own risk."
        }
        if (query.contains("stock")) {
             return "Standard advice: Diversify your portfolio. Index funds historically outperform active trading in the long run. 'Time in the market beats timing the market'."
        }
        return "Financial Principle 101: Compound interest is the 8th wonder of the world. Save early, save often, and avoid high-interest debt."
    }
}
