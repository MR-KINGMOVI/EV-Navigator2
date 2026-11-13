package com.evnavigator.iran.domain.journey.multistop

import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Dedicated combinatorial sequence solver for intermediate journey waypoints (V12.6 P1-13, P1-14).
 *
 * Provides:
 * 1. Bounded Branch-and-Bound exact solver for $N \le 7$ flexible intermediate stops
 *    (capped at [MultiStopWaypointOptimizer.MAX_BRANCH_EVALUATIONS]).
 * 2. Cheapest-Insertion solver when pinned stops (`isRequired == true`) must remain in fixed relative order.
 * 3. Nearest-Neighbor + 2-Opt local search heuristic for $N > 7$ intermediate stops.
 * 4. Exhaustive Brute-Force permutation solver ([solveExactBruteForceReference]) for unit-test
 *    optimality verification on small instances ($N \le 6$).
 */
object WaypointSequenceSolver {

    data class SequenceSolution(
        val orderedIntermediateIndices: List<Int>,
        val totalCostMeters: Double,
        val statesEvaluated: Int
    )

    suspend fun solveIntermediateOrder(
        costMatrix: Array<DoubleArray>,
        originIdx: Int,
        destIdx: Int,
        intermediateIndices: List<Int>,
        isPinnedIndex: (Int) -> Boolean,
        keepPinnedStopsFixed: Boolean,
        maxExactStops: Int = MultiStopWaypointOptimizer.MAX_EXACT_INTERMEDIATE_STOPS,
        maxBranchEvaluations: Int = MultiStopWaypointOptimizer.MAX_BRANCH_EVALUATIONS
    ): SequenceSolution {
        coroutineContext.ensureActive()
        if (intermediateIndices.isEmpty()) {
            return SequenceSolution(
                orderedIntermediateIndices = emptyList(),
                totalCostMeters = costMatrix[originIdx][destIdx],
                statesEvaluated = 1
            )
        }

        return when {
            keepPinnedStopsFixed -> solveWithPinnedStops(
                costMatrix = costMatrix,
                originIdx = originIdx,
                destIdx = destIdx,
                intermediateIndices = intermediateIndices,
                isPinnedIndex = isPinnedIndex
            )
            intermediateIndices.size <= maxExactStops -> solveBranchAndBound(
                costMatrix = costMatrix,
                originIdx = originIdx,
                destIdx = destIdx,
                intermediateIndices = intermediateIndices,
                maxBranchEvaluations = maxBranchEvaluations
            )
            else -> solveNearestNeighborTwoOpt(
                costMatrix = costMatrix,
                originIdx = originIdx,
                destIdx = destIdx,
                intermediateIndices = intermediateIndices
            )
        }
    }

    private suspend fun solveWithPinnedStops(
        costMatrix: Array<DoubleArray>,
        originIdx: Int,
        destIdx: Int,
        intermediateIndices: List<Int>,
        isPinnedIndex: (Int) -> Boolean
    ): SequenceSolution {
        val pinnedIndices = intermediateIndices.filter(isPinnedIndex)
        val flexibleIndices = intermediateIndices.filterNot(isPinnedIndex).toMutableList()
        var statesEvaluated = 0

        if (flexibleIndices.isEmpty()) {
            val cost = computeSequenceCost(costMatrix, originIdx, destIdx, intermediateIndices)
            return SequenceSolution(intermediateIndices, cost, 1)
        }

        val currentRoute = mutableListOf(originIdx)
        currentRoute.addAll(pinnedIndices)
        currentRoute.add(destIdx)

        while (flexibleIndices.isNotEmpty()) {
            coroutineContext.ensureActive()
            var bestCandListIdx = 0
            var bestInsertPos = 1
            var bestDelta = Double.MAX_VALUE

            for ((candListIdx, candNodeIdx) in flexibleIndices.withIndex()) {
                for (pos in 1 until currentRoute.size) {
                    statesEvaluated++
                    val prevNode = currentRoute[pos - 1]
                    val nextNode = currentRoute[pos]
                    val delta = (costMatrix[prevNode][candNodeIdx] + costMatrix[candNodeIdx][nextNode]) -
                        costMatrix[prevNode][nextNode]
                    if (delta < bestDelta) {
                        bestDelta = delta
                        bestCandListIdx = candListIdx
                        bestInsertPos = pos
                    }
                }
            }
            val chosenNode = flexibleIndices.removeAt(bestCandListIdx)
            currentRoute.add(bestInsertPos, chosenNode)
        }

        val orderedIntermediates = currentRoute.subList(1, currentRoute.size - 1).toList()
        val finalCost = computeSequenceCost(costMatrix, originIdx, destIdx, orderedIntermediates)
        return SequenceSolution(orderedIntermediates, finalCost, statesEvaluated.coerceAtLeast(1))
    }

    fun solveBranchAndBound(
        costMatrix: Array<DoubleArray>,
        originIdx: Int,
        destIdx: Int,
        intermediateIndices: List<Int>,
        maxBranchEvaluations: Int = MultiStopWaypointOptimizer.MAX_BRANCH_EVALUATIONS
    ): SequenceSolution {
        val m = intermediateIndices.size
        val used = BooleanArray(m)
        val currentPath = IntArray(m)
        var bestCost = Double.MAX_VALUE
        var bestPath = intermediateIndices.toIntArray()
        var statesEvaluated = 0

        fun branchAndBound(depth: Int, prevNodeIdx: Int, accumulatedCost: Double) {
            if (statesEvaluated >= maxBranchEvaluations) return
            if (accumulatedCost >= bestCost) return

            if (depth == m) {
                statesEvaluated++
                val totalCost = accumulatedCost + costMatrix[prevNodeIdx][destIdx]
                if (totalCost < bestCost) {
                    bestCost = totalCost
                    bestPath = currentPath.clone()
                }
                return
            }

            for (i in 0 until m) {
                if (!used[i]) {
                    val nextNodeIdx = intermediateIndices[i]
                    val stepCost = costMatrix[prevNodeIdx][nextNodeIdx]
                    if (accumulatedCost + stepCost < bestCost) {
                        used[i] = true
                        currentPath[depth] = nextNodeIdx
                        branchAndBound(depth + 1, nextNodeIdx, accumulatedCost + stepCost)
                        used[i] = false
                    }
                }
            }
        }

        branchAndBound(0, originIdx, 0.0)
        val resultList = bestPath.toList()
        val resolvedCost = if (bestCost < Double.MAX_VALUE) {
            bestCost
        } else {
            computeSequenceCost(costMatrix, originIdx, destIdx, resultList)
        }
        return SequenceSolution(resultList, resolvedCost, statesEvaluated.coerceAtLeast(1))
    }

    private suspend fun solveNearestNeighborTwoOpt(
        costMatrix: Array<DoubleArray>,
        originIdx: Int,
        destIdx: Int,
        intermediateIndices: List<Int>
    ): SequenceSolution {
        var statesEvaluated = 0
        val remaining = intermediateIndices.toMutableList()
        val ordered = ArrayList<Int>(intermediateIndices.size)
        var currentIdx = originIdx

        while (remaining.isNotEmpty()) {
            var bestRemIdx = 0
            var minCost = Double.MAX_VALUE
            for (rIdx in remaining.indices) {
                statesEvaluated++
                val candidateNode = remaining[rIdx]
                val c = costMatrix[currentIdx][candidateNode]
                if (c < minCost) {
                    minCost = c
                    bestRemIdx = rIdx
                }
            }
            val chosenNode = remaining.removeAt(bestRemIdx)
            ordered.add(chosenNode)
            currentIdx = chosenNode
        }

        var improved = true
        var iteration = 0
        while (improved && iteration < 50) {
            coroutineContext.ensureActive()
            improved = false
            iteration++
            for (i in 0 until ordered.size - 1) {
                for (k in i + 1 until ordered.size) {
                    statesEvaluated++
                    val nodeA = if (i == 0) originIdx else ordered[i - 1]
                    val nodeB = ordered[i]
                    val nodeC = ordered[k]
                    val nodeD = if (k == ordered.size - 1) destIdx else ordered[k + 1]

                    val currentEdgeCost = costMatrix[nodeA][nodeB] + costMatrix[nodeC][nodeD]
                    val swappedEdgeCost = costMatrix[nodeA][nodeC] + costMatrix[nodeB][nodeD]

                    if (swappedEdgeCost < currentEdgeCost - 1.0) {
                        ordered.subList(i, k + 1).reverse()
                        improved = true
                    }
                }
            }
        }

        val totalCost = computeSequenceCost(costMatrix, originIdx, destIdx, ordered)
        return SequenceSolution(ordered, totalCost, statesEvaluated.coerceAtLeast(1))
    }

    /**
     * Exhaustive unpruned brute-force permutation solver for $N \le 6$ intermediate stops.
     * Used in unit tests to verify that [solveBranchAndBound] finds the exact global minimum.
     */
    fun solveExactBruteForceReference(
        costMatrix: Array<DoubleArray>,
        originIdx: Int,
        destIdx: Int,
        intermediateIndices: List<Int>
    ): SequenceSolution {
        require(intermediateIndices.size <= 7) {
            "Brute-force reference solver is restricted to at most 7 intermediate stops"
        }
        if (intermediateIndices.isEmpty()) {
            return SequenceSolution(emptyList(), costMatrix[originIdx][destIdx], 1)
        }
        val m = intermediateIndices.size
        val used = BooleanArray(m)
        val perm = IntArray(m)
        var bestCost = Double.MAX_VALUE
        var bestPerm = intermediateIndices.toIntArray()
        var permutationsChecked = 0

        fun permute(depth: Int) {
            if (depth == m) {
                permutationsChecked++
                var cost = costMatrix[originIdx][perm[0]]
                for (i in 0 until m - 1) {
                    cost += costMatrix[perm[i]][perm[i + 1]]
                }
                cost += costMatrix[perm[m - 1]][destIdx]
                if (cost < bestCost) {
                    bestCost = cost
                    bestPerm = perm.clone()
                }
                return
            }
            for (i in 0 until m) {
                if (!used[i]) {
                    used[i] = true
                    perm[depth] = intermediateIndices[i]
                    permute(depth + 1)
                    used[i] = false
                }
            }
        }

        permute(0)
        return SequenceSolution(bestPerm.toList(), bestCost, permutationsChecked)
    }

    fun computeSequenceCost(
        costMatrix: Array<DoubleArray>,
        originIdx: Int,
        destIdx: Int,
        orderedIntermediates: List<Int>
    ): Double {
        if (orderedIntermediates.isEmpty()) return costMatrix[originIdx][destIdx]
        var sum = costMatrix[originIdx][orderedIntermediates.first()]
        for (i in 0 until orderedIntermediates.size - 1) {
            sum += costMatrix[orderedIntermediates[i]][orderedIntermediates[i + 1]]
        }
        sum += costMatrix[orderedIntermediates.last()][destIdx]
        return sum
    }
}
