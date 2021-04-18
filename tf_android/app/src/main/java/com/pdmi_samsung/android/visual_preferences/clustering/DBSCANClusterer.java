package com.pdmi_samsung.android.visual_preferences.clustering;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

/**
 * Implementation of density-based clustering algorithm DBSCAN.
 * 
 * Publication: 
 * Ester, Martin; Kriegel, Hans-Peter; Sander, J�rg; Xu, Xiaowei (1996). 
 * Simoudis, Evangelos; Han, Jiawei; Fayyad, Usama M., eds. 
 * A density-based algorithm for discovering clusters in large spatial 
 * databases with noise. Proceedings of the Second International Conference 
 * on Knowledge Discovery and Data Mining (KDD-96). AAAI Press. pp. 226�231
 * 
 * Usage:
 * - Identify type of input values.
 * - Implement metric for input value type using DistanceMetric interface.
 * - Instantiate using {@link #DBSCANClusterer(Collection, int, double, DistanceMetric)}.
 * - Invoke {@link #performClustering()}.
 *
 * 
 * @author <a href="mailto:cf@christopherfrantz.org>Christopher Frantz</a>
 *
 * @param <V> Input value type
 */
public class DBSCANClusterer<V> {

	/** maximum distance of values to be considered as cluster */
	private double epsilon = 1f;

	/** minimum number of members to consider cluster */
	private int minimumNumberOfClusterMembers = 2;

	/** distance metric applied for clustering **/
	private DistanceMetric<V> metric = null;

	/** internal list of input values to be clustered */
	private ArrayList<V> inputValues = null;

	/** index maintaining visited points */
	private HashSet<V> visitedPoints = new HashSet<V>();

	/**
	 * Creates a DBSCAN clusterer instance. 
	 * Upon instantiation, call {@link #performClustering()} 
	 * to perform the actual clustering.
	 * 
	 * @param inputValues Input values to be clustered
	 * @param minNumElements Minimum number of elements to constitute cluster
	 * @param maxDistance Maximum distance of elements to consider clustered
	 * @param metric Metric implementation to determine distance
	 */
	public DBSCANClusterer(final Collection<V> inputValues, int minNumElements, double maxDistance, DistanceMetric<V> metric){
		setInputValues(inputValues);
		setMinimalNumberOfMembersForCluster(minNumElements);
		setMaximalDistanceOfClusterMembers(maxDistance);
		setDistanceMetric(metric);
	}

	/**
	 * Sets the distance metric
	 * 
	 * @param metric
	 */
	public void setDistanceMetric(final DistanceMetric<V> metric){
		this.metric = metric;
	}

	/**
	 * Sets a collection of input values to be clustered.
	 * Repeated call overwrite the original input values.
	 * 
	 * @param collection
	 */
	public void setInputValues(final Collection<V> collection){
		this.inputValues = new ArrayList<V>(collection);
	}

	/**
	 * Sets the minimal number of members to consider points of close proximity
	 * clustered.
	 * 
	 * @param minimalNumberOfMembers
	 */
	public void setMinimalNumberOfMembersForCluster(final int minimalNumberOfMembers) {

		if (minimumNumberOfClusterMembers < 2) {
			minimumNumberOfClusterMembers=2;
		}

		this.minimumNumberOfClusterMembers = minimalNumberOfMembers;
	}

	/**
	 * Sets the maximal distance members of the same cluster can have while
	 * still be considered in the same cluster.
	 * 
	 * @param maximalDistance
	 */
	public void setMaximalDistanceOfClusterMembers(final double maximalDistance) {
		this.epsilon = maximalDistance;
	}

	/**
	 * Determines the neighbours of a given input value.
	 * 
	 * @param inputValue Input value for which neighbours are to be determined
	 * @return list of neighbours
	 */
	private ArrayList<V> getNeighbours(final V inputValue){
		/*ArrayList<V> neighbours = new ArrayList<V>();
		for(int i=0; i<inputValues.size(); i++) {
			V candidate = inputValues.get(i);
			if (metric.calculateDistance(inputValue, candidate) <= epsilon) {
				neighbours.add(candidate);
			}
		}
		return neighbours;*/
		return metric.getNeighbours(inputValue);
	}

	/**
	 * Merges the elements of the right collection to the left one and returns
	 * the combination.
	 * 
	 * @param neighbours1 left collection
	 * @param neighbours2 right collection
	 * @return Modified left collection
	 */
	private ArrayList<V> mergeRightToLeftCollection(final ArrayList<V> neighbours1,
			final ArrayList<V> neighbours2) {
		for (V tempPt : neighbours2) {
			if (!neighbours1.contains(tempPt)) {
				neighbours1.add(tempPt);
			}
		}
		return neighbours1;
	}

	/**
	 * Applies the clustering and returns a collection of clusters (i.e. a list
	 * of lists of the respective cluster members).
	 * 
	 * @return
	 */
	public ArrayList<ArrayList<V>> performClustering() {
		ArrayList<ArrayList<V>> resultList = new ArrayList<ArrayList<V>>();

		if (inputValues == null || inputValues.isEmpty() || inputValues.size() < minimumNumberOfClusterMembers) {
			return resultList;
		}

		visitedPoints.clear();

		ArrayList<V> neighbours;
		int index = 0;

		while (inputValues.size() > index) {
			V p = inputValues.get(index);
			if (!visitedPoints.contains(p)) {
				visitedPoints.add(p);
				neighbours = getNeighbours(p);

				if (neighbours.size() >= minimumNumberOfClusterMembers) {
					int ind = 0;
					while (neighbours.size() > ind) {
						V r = neighbours.get(ind);
						if (!visitedPoints.contains(r)) {
							visitedPoints.add(r);
							ArrayList<V> individualNeighbours = getNeighbours(r);
							if (individualNeighbours.size() >= minimumNumberOfClusterMembers) {
								neighbours = mergeRightToLeftCollection(
										neighbours,
										individualNeighbours);
							}
						}
						ind++;
					}
					resultList.add(neighbours);
				}
			}
			index++;
		}
		return resultList;
	}

}
