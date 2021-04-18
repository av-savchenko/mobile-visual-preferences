package com.pdmi_samsung.android.visual_preferences.db;

import java.io.Serializable;

/**
 * Created by avsavchenko.
 */
public class ImageAnalysisResults implements Serializable {
    public String filename=null;
    public SceneData scene=null,originalScene=null;
    public String text=null;
    public DetectionResultsWithFaces objects=null, serverObjects=null;
    public ClassificationForDetectedObjects objectTypes=null;
    public DetectionResults logos;
    public EXIFData locations=null;

    public ImageAnalysisResults() {}

    public ImageAnalysisResults(String filename, SceneData scene, SceneData originalScene, String text, DetectionResultsWithFaces objects, DetectionResultsWithFaces serverObjects, ClassificationForDetectedObjects objectTypes, DetectionResults logos, EXIFData locations){
        this.filename=filename;
        this.scene=scene;
        this.originalScene=originalScene;
        this.text=text;
        this.objects=objects;
        this.serverObjects=serverObjects;
        this.objectTypes=objectTypes;
        this.logos=logos;
        this.locations=locations;
    }
    public ImageAnalysisResults(SceneData scene,  DetectionResultsWithFaces serverObjects){
        this.scene=scene;
        this.serverObjects=serverObjects;
    }
}
