package com.pdmi_samsung.android.visual_preferences;

import android.content.res.AssetManager;
import android.util.Log;

import com.pdmi_samsung.android.visual_preferences.db.ClassifierResult;
import com.pdmi_samsung.android.visual_preferences.db.TopCategoriesData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Created by avsavchenko.
 */
public class PetBreedsTfLiteClassifier extends TfLiteClassifier{

    /** Tag for the {@link Log}. */
    private static final String TAG = "PetBreedsTfLite";

    private static final String MODEL_FILE =
            "cat_dog_breeds.tflite";

    private static final String CATS_LABELS_FILE = "cats.txt";
    private List<String> catsLabels = new ArrayList<String>();

    private static final String DOGS_LABELS_FILE = "dogs.txt";
    private List<String> dogsLabels = new ArrayList<String>();

    private boolean isDogRecognized=true;

    public PetBreedsTfLiteClassifier(final AssetManager assetManager) throws IOException {
        super(assetManager,MODEL_FILE);

        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(assetManager.open(CATS_LABELS_FILE)));
            String line;
            while ((line = br.readLine()) != null) {
                //line=line.split("#")[0].trim();
                catsLabels.add(line);
            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Problem reading cats label file!" , e);
        }
        try {
            br = new BufferedReader(new InputStreamReader(assetManager.open(DOGS_LABELS_FILE)));
            String line;
            while ((line = br.readLine()) != null) {
                //line=line.split("#")[0].trim();
                dogsLabels.add(line);
            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Problem reading dogs label file!" , e);
        }
    }

    /*
    @param isDogRecognized: true if dog should be recognized; false if cat should be recognized
     */
    public void setDogRecognized(boolean isDogRecognized){
        this.isDogRecognized=isDogRecognized;
    }

    protected ClassifierResult getResults(float[][][] outputs) {
        TopCategoriesData petData=null;
        //12: high level category for Pets/animals
        if(isDogRecognized)
            petData=new TopCategoriesData("dog",12,outputs[0][0],dogsLabels);
        else
            petData=new TopCategoriesData("cat",12,outputs[1][0],catsLabels);
        return petData;
    }
}
