package com.zyra.app.data.knowledge

data class MathTopic(
    val level: String, // B.Tech, PhD, M.Sc
    val category: String, // Calculus, Algebra
    val title: String,
    val definition: String,
    val formula: String? = null,
    val keywords: List<String>
)

object AdvancedMathematics {
    val topics = listOf(
        // CALCULUS (Engineering/MIT/IIT Level)
        MathTopic(
            "B.Tech/MIT", "Calculus", "Derivative of sin x",
            "The rate of change of sin(x) with respect to x.",
            "d/dx(sin x) = cos x",
            listOf("derivative of sin", "diff sin")
        ),
        MathTopic(
            "B.Tech/MIT", "Calculus", "Integration by Parts",
            "A technique for performing integration, based on the product rule for differentiation.",
            "∫u dv = uv - ∫v du",
            listOf("integration by parts", "integral parts")
        ),
        MathTopic(
            "B.Tech/IIT", "Differential Equations", "Laplace Transform",
            "An integral transform that converts a function of real variable t (time) to a function of complex variable s (frequency).",
            "L{f(t)} = F(s) = ∫[0,∞] e^(-st) f(t) dt",
            listOf("laplace transform", "laplace definition")
        ),
        MathTopic(
            "B.Tech/IIT", "Calculus", "Fourier Transform",
            "Decomposes a function (often a function of time, or a signal) into its constituent frequencies.",
            "F(ξ) = ∫[-∞,∞] f(x)e^(-2πixξ) dx",
            listOf("fourier transform", "fourier analysis")
        ),

        // LINEAR ALGEBRA
        MathTopic(
            "B.Sc/B.Tech", "Linear Algebra", "Eigenvalues and Eigenvectors",
            "Scalars λ and vectors v such that Av = λv.",
            "Av = λv",
            listOf("eigenvalue", "eigenvector", "eigen")
        ),
        MathTopic(
            "B.Sc/B.Tech", "Linear Algebra", "Rank of a Matrix",
            "The maximum number of linearly independent column vectors in the matrix.",
            null,
            listOf("rank of matrix", "matrix rank")
        ),

        // ABSTRACT ALGEBRA (M.Sc)
        MathTopic(
            "M.Sc", "Abstract Algebra", "Group Definition",
            "A set G with an operation • that satisfies Closure, Associativity, Identity, and Invertibility.",
            null,
            listOf("define group", "group theory", "what is a group")
        ),
        MathTopic(
            "M.Sc", "Abstract Algebra", "Ring Definition",
            "A set R equipped with two binary operations + and · satisfying specific axioms (Abelian group under +, Monoid under ·, Distributive).",
            null,
            listOf("define ring", "ring theory")
        ),

        // RESEARCH / PhD Level
        MathTopic(
            "PhD/Research", "Number Theory", "Riemann Hypothesis",
            "A conjecture that the real part of every non-trivial zero of the Riemann zeta function is 1/2. It implies results about the distribution of prime numbers.",
            "ζ(s) = 0 => Re(s) = 1/2",
            listOf("riemann hypothesis", "riemann zeta")
        ),
        MathTopic(
            "PhD/Research", "Number Theory", "Fermat's Last Theorem",
            "No three positive integers a, b, and c satisfy the equation aⁿ + bⁿ = cⁿ for any integer value of n greater than 2.",
            "aⁿ + bⁿ ≠ cⁿ for n > 2",
            listOf("fermat's last theorem", "fermat last theorem")
        ),
        MathTopic(
            "PhD/Research", "Topology", "Manifold",
            "A topological space that locally resembles Euclidean space near each point.",
            null,
            listOf("manifold", "topology manifold")
        ),
        MathTopic(
            "PhD/Research", "Physics/Math", "Schrodinger Equation",
            "A linear partial differential equation that governs the wave function of a quantum-mechanical system.",
            "iħ ∂/∂t Ψ(r,t) = ĤΨ(r,t)",
            listOf("schrodinger equation", "quantum wave equation")
        ),

        // GAME THEORY & LOGIC (For Decision Engine)
        MathTopic(
            "Game Theory", "Strategic Decision", "Nash Equilibrium",
            "A stable state of a system involving the interaction of different participants, in which no participant can gain by a unilateral change of strategy if the strategies of the others remain unchanged.",
            null,
            listOf("nash equilibrium", "nash strategy")
        ),
        MathTopic(
            "Game Theory", "Strategic Decision", "Prisoner's Dilemma",
            "A standard example of a game analyzed in game theory that shows why two completely rational individuals might not cooperate, even if it appears that it is in their best interests to do so.",
            null,
            listOf("prisoner's dilemma", "prisoner dilemma")
        ),
        MathTopic(
            "Logic", "Propositional Logic", "Modus Ponens",
            "If P implies Q, and P is true, then Q must be true.",
            "((P -> Q) ^ P) -> Q",
            listOf("modus ponens", "logical deduction")
        ),

        
        // COMPUTER SCIENCE (IIT/MIT Level)
        MathTopic(
            "CS/Algorithms", "Complexity Theory", "Big O Notation",
            "A mathematical notation that describes the limiting behavior of a function when the argument tends towards a particular value or infinity. Used to classify algorithms by how their run time or space requirements grow.",
            "O(n), O(log n), O(n²)",
            listOf("big o", "time complexity", "space complexity")
        ),
        MathTopic(
            "CS/Algorithms", "Theoretical CS", "Turing Machine",
            "A mathematical model of computation that defines an abstract machine which manipulates symbols on a strip of tape according to a table of rules.",
            null,
            listOf("turing machine", "halting problem")
        ),
        MathTopic(
            "CS/Algorithms", "Complexity Classes", "P vs NP",
            "The question of whether every problem whose solution can be quickly verified (NP) can also be solved quickly (P).",
            "P ?= NP",
            listOf("p vs np", "p equals np")
        )
    )

    fun getResponse(query: String): String? {
        val lower = query.lowercase().trim()
        
        val bestMatch = topics.find { topic -> 
            topic.keywords.any { k -> lower.contains(k) } 
        }

        if (bestMatch != null) {
            val formulaText = if (bestMatch.formula != null) " Formula: ${bestMatch.formula}" else ""
            return "[${bestMatch.level}] ${bestMatch.title}: ${bestMatch.definition}$formulaText"
        }
        
        return null
    }
}
