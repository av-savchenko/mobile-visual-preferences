package com.pdmi_samsung.android.visual_preferences.db;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by avsavchenko.
 */
public class DetectionResults implements Serializable {
    public List<DetectorData> detections;

    public static double DETECTION_THRESHOLD=0.4;

    public DetectionResults(){}

    public DetectionResults(List<DetectorData> detections){
        this.detections=detections;
    }

    public List<DetectorData> getReliableDetectors(){
        List<DetectorData> res=new ArrayList<>();
        for(DetectorData data : detections){
            if(data.confidence>=DETECTION_THRESHOLD){
                res.add(data);
            }
        }
        return res;
    }
}
