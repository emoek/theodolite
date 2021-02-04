package theodolite.strategies.searchstrategy

import theodolite.execution.BenchmarkExecutor
import theodolite.util.LoadDimension
import theodolite.util.Resource
import theodolite.util.Results
import java.lang.IllegalArgumentException

class BinarySearch(benchmarkExecutor: BenchmarkExecutor, results: Results) : SearchStrategy(benchmarkExecutor, results) {
    override fun findSuitableResource(load: LoadDimension, resources: List<Resource>): Resource? {
        val result =  search(load, resources, 0, resources.size - 1)
        if( result == -1 ) {
            return null;
        }
        return resources[result]
    }

    private fun search (load: LoadDimension, resources: List<Resource>, lower: Int, upper: Int): Int {
        if (lower > upper) {
            throw IllegalArgumentException()
        }
        // special case:  length == 1 or 2
        if (lower == upper ) {
            if (this.benchmarkExecutor.runExperiment(load, resources[lower])) return lower
            else {
                if (lower + 1 == resources.size) return - 1
                return lower + 1
            }
        } else {
            // apply binary search for a list with length > 2 and adjust upper and lower depending on the result for `resources[mid]`
            val mid = (upper + lower) / 2
            if (this.benchmarkExecutor.runExperiment(load, resources[mid])) {
                if (mid == lower) {
                    return lower
                }
                return search(load, resources, lower, mid - 1 )
            } else {
              return search(load, resources, mid + 1 , upper)
            }
        }
    }

}