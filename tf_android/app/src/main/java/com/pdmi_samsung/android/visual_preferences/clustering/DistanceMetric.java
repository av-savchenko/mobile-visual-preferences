package com.pdmi_samsung.android.visual_preferences.clustering;

import java.util.ArrayList;

/**
 * Interface for the implementation of distance metrics.
 * 
 * @author <a href="mailto:cf@christopherfrantz.org>Christopher Frantz</a>
 *
 * @param <V> Value type to which distance metric is applied.
 */
public interface DistanceMetric<V> {
	double calculateDistance(V val1, V val2);

	ArrayList<V> getNeighbours(final V inputValue);
}
