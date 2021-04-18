package com.pdmi_samsung.android.visual_preferences.db;

import java.io.Serializable;
import java.util.*;

/**
 * Created by avsavchenko.
 */

public class SceneData implements ClassifierResult,Serializable {
    public ImageClassificationData scenes;
    public ImageClassificationData events;

    public static final float SCENE_DISPLAY_THRESHOLD = 0.1f; //0.3f;
    public static final float SCENE_CATEGORY_THRESHOLD = 0.2f; //0.3f;

    public static final float EVENT_DISPLAY_THRESHOLD = 0.0f;
    public static final float EVENT_CATEGORY_THRESHOLD = 0.1f;

    public SceneData(){
    }

    public SceneData(TreeMap<String,Integer> sceneLabels2Index, TreeMap<String,Float> scene2Score,
                     TreeMap<String,Integer> eventLabels2Index, TreeMap<String,Float> event2Score){
        this.scenes =new ImageClassificationData(sceneLabels2Index,scene2Score,SCENE_DISPLAY_THRESHOLD);
        this.events =new ImageClassificationData(eventLabels2Index,event2Score,EVENT_DISPLAY_THRESHOLD);
    }

    public List<String> getMostReliableCategories() {
        List<String> res=new ArrayList<>();
        scenes.getMostReliableCategories(res,SCENE_CATEGORY_THRESHOLD);
        events.getMostReliableCategories(res,EVENT_CATEGORY_THRESHOLD);
        return res;
    }
    public String toString(){
        String res="scenes: "+scenes;
        if(events.categories.length>0)
            res+=" events: "+events;
        return res;
    }
    public double distance(SceneData rhs){
        return scenes.distance(rhs.scenes);
    }
}
