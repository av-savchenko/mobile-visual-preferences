package com.pdmi_samsung.android.visual_preferences;

import android.content.res.AssetManager;
import android.util.Log;

import com.pdmi_samsung.android.visual_preferences.db.TopCategoriesData;
import com.pdmi_samsung.android.visual_preferences.db.ClassifierResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by avsavchenko.
 */
public class CarsTfLiteClassifier extends TfLiteClassifier{

    /** Tag for the {@link Log}. */
    private static final String TAG = "CarsTfLite";

    private static final String MODEL_FILE =
            "comp_cars_mobilenet_v2_adam02-0.859.tflite";

    private static final String LABELS_FILE = "comp_cars_labels.txt";
    private List<String> labels = new ArrayList<String>();

    public CarsTfLiteClassifier(final AssetManager assetManager) throws IOException {
        super(assetManager,MODEL_FILE);

        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(assetManager.open(LABELS_FILE)));
            String line;
            while ((line = br.readLine()) != null) {
                labels.add(line);
            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Problem reading cars label file!" , e);
        }
    }

    protected ClassifierResult getResults(float[][][] outputs) {
        //3: high level category for cars
        TopCategoriesData carData=new TopCategoriesData("car",3,outputs[0][0], labels);
        return carData;
    }
}
