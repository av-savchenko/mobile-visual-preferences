package com.pdmi_samsung.android.visual_preferences.db;


import java.io.Serializable;

public class DetectorData implements Serializable {
    /**
     * Display name for the recognition.
     */
    public final String title;

    /**
     * A sortable score for how good the recognition is relative to others. Higher should be better.
     */
    public final Float confidence;

    /**
     * Optional location within the source image for the location of the recognized object.
     */
    public RectFloat location;

    public DetectorData(final String title, final Float confidence, final RectFloat location) {
        this.title = title;
        this.confidence = confidence;
        this.location = location;
    }

    public String toString(){
        return String.format("%s (%.2f)\n", title, confidence);
    }
}
