package com.pdmi_samsung.android.visual_preferences.db;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by avsavchenko.
 */
public class DetectionResultsWithFaces extends DetectionResults {
    //faces
    public List<RectFloat> faceRects=null;
    public List<FaceData> faces=null;

    public DetectionResultsWithFaces(){}

    public DetectionResultsWithFaces(List<DetectorData> detections, List<RectFloat> faceRects, List<FaceData> faces){
        super(detections);
        this.faceRects=faceRects;
        this.faces=faces;
    }

}
