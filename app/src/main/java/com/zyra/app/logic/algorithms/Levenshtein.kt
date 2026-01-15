package com.zyra.app.logic.algorithms

import kotlin.math.min

object Levenshtein {
    fun calculate(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length

        var cost = Array(lhsLen + 1) { it }
        var newCost = Array(lhsLen + 1) { 0 }

        for (j in 1..rhsLen) {
            newCost[0] = j
            for (i in 1..lhsLen) {
                val match = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                val costReplace = cost[i - 1] + match
                val costInsert = cost[i] + 1
                val costDelete = newCost[i - 1] + 1
                newCost[i] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[lhsLen]
    }
    
    fun calculateSimilarity(s1: String, s2: String): Int {
        val dist = calculate(s1, s2)
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 100
        return ((1.0 - dist.toDouble() / maxLen) * 100).toInt()
    }

    fun findBestMatch(query: String, candidates: List<String>): String? {
        val matches = findAllMatches(query, candidates)
        return matches.firstOrNull()
    }

    fun findAllMatches(query: String, candidates: List<String>): List<String> {
        val matches = mutableListOf<Pair<String, Int>>()
        val threshold = (query.length * 0.4).toInt().coerceAtLeast(1) // Allow 40% error rate

        for (candidate in candidates) {
            val dist = calculate(query.lowercase(), candidate.lowercase())
            if (dist <= threshold) {
                matches.add(candidate to dist)
            }
        }
        // Sort by distance (closest first)
        return matches.sortedBy { it.second }.map { it.first }
    }
}
