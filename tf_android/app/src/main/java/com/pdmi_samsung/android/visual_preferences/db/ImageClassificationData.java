package com.pdmi_samsung.android.visual_preferences.db;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

/**
 * Created by avsavchenko.
 */

public class ImageClassificationData implements ClassifierResult,Serializable {
    public String[] categories =null;
    public float[] scores =null;
    public int[] topIndices =null;

    // Only return this many results with at least this confidence.
    public static final int MAX_RESULTS = 3;

    public ImageClassificationData(){
    }

    public ImageClassificationData(TreeMap<String,Integer> categoryLabels2Index, TreeMap<String,Float> category2Score,
                                   float displayThreshold){
        this.scores = new float[category2Score.size()];
        // Find the best classifications.
        PriorityQueue<String> pq =
                new PriorityQueue<>(
                        MAX_RESULTS,
                        new Comparator<String>() {
                            @Override
                            public int compare(String lhs, String rhs) {
                                // Intentionally reversed to put high confidence at the head of the queue.
                                return Float.compare(category2Score.get(rhs), category2Score.get(lhs));
                            }
                        });
        for(Map.Entry<String,Float> entry:category2Score.entrySet()){
            if (entry.getValue() > displayThreshold) {
                pq.add(entry.getKey());
            }
            scores[categoryLabels2Index.get(entry.getKey())]=entry.getValue();
        }
        int recognitionsSize = Math.min(pq.size(), MAX_RESULTS);
        this.categories =new String[recognitionsSize];
        this.topIndices =new int[recognitionsSize];
        for (int i = 0; i < recognitionsSize; ++i) {
            categories[i]=pq.poll();
            //topScores[i]=category2Score.get(topCategories[i]);
            topIndices[i]=categoryLabels2Index.get(categories[i]);
        }
    }

    public void getMostReliableCategories(List<String> reliableCategories,float profileThreshold) {
        for (int i = 0; i < categories.length; ++i){
            if(scores[topIndices[i]]>= profileThreshold)
                reliableCategories.add(categories[i]);
        }
    }
    public String toString(){
        StringBuilder str=new StringBuilder();
        for (int i = 0; i< categories.length; ++i){
            str.append(String.format("%s (%.2f); ", categories[i], scores[topIndices[i]]));
        }
        return str.toString();
    }
    public double distance(ImageClassificationData rhs){
        double d=0;
        for (int i = 0; i< scores.length; ++i) {
            float otherScore = rhs.scores[i];
            if ((scores[i] + otherScore) > 0.0001) {
                d += (scores[i] - otherScore) * (scores[i] - otherScore) / (scores[i] + otherScore);
            }
        }
        return d/scores.length;
    }
}
