package com.pdmi_samsung.android.visual_preferences.db;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Created by avsavchenko.
 */

public class TopCategoriesData implements ClassifierResult,Serializable {
    public String type = "";
    public int highLevelCategory = 0;
    public String[] topCategories = null;
    public float[] topScores = null;

    public TopCategoriesData(){
    }

    public TopCategoriesData(String type, int highLevelCategory, float[] allScores, List<String> labels){
        this.type = type;
        this.highLevelCategory=highLevelCategory;
        float sum = 0;
        for (int j = 0; j < allScores.length; ++j) {
            sum += allScores[j];
        }
        PriorityQueue<Integer> pq =
                new PriorityQueue<>(
                        ImageClassificationData.MAX_RESULTS,
                        new Comparator<Integer>() {
                            @Override
                            public int compare(Integer lhs, Integer rhs) {
                                // Intentionally reversed to put high confidence at the head of the queue.
                                return Float.compare(allScores[rhs], allScores[lhs]);
                            }
                        });
        for (int j = 0; j < allScores.length; ++j) {
            float probab= allScores[j]/sum;
            if (probab > 0.25) {
                pq.add(j);
            }
        }
        int recognitionsSize = Math.min(pq.size(), ImageClassificationData.MAX_RESULTS);
        if(recognitionsSize>0) {
            this.topCategories = new String[recognitionsSize];
            this.topScores = new float[recognitionsSize];
            for (int i = 0; i < recognitionsSize; ++i) {
                int breedIndex = pq.poll();
                topCategories[i] = labels.get(breedIndex);
                topScores[i] = allScores[breedIndex]/sum;
            }
        }    }

    public String getBestCategory(){
        return (topCategories==null || topCategories.length==0)?null:topCategories[0];
    }
    public String toString(){
        StringBuilder res=new StringBuilder();
        if(topCategories!=null) {
            //res.append(type).append(" ");
            for(int i = 0; i < topCategories.length; ++i){
                res.append(topCategories[i]).append(String.format("(%.2f)", topScores[i]));
                if (i < topCategories.length-1)
                    res.append(", ");
                else
                    res.append("\n");
            }
        }
        return res.toString();
    }
}
