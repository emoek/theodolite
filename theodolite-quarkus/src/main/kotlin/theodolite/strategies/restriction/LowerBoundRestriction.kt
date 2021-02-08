package theodolite.strategies.restriction

import theodolite.util.Results
import theodolite.util.LoadDimension
import theodolite.util.Resource

/**
 * The Lower Bound Restriction sets the lower bound of the resources to be examined to the value
 * needed to successfully execute the next smaller load.
 *
 * @param results Result object used as a basis to restrict the resources.
 */
class LowerBoundRestriction(results: Results) : RestrictionStrategy(results) {
    override fun next(load: LoadDimension, resources: List<Resource>): List<Resource> {
            val maxLoad: LoadDimension? = this.results.getMaxBenchmarkedLoad(load)
            var lowerBound: Resource? = this.results.getMinRequiredInstances(maxLoad)
            if(lowerBound == null) {
                lowerBound = resources.get(0)
            }
            return resources.filter{x -> x.get() >= lowerBound.get()}
    }
}