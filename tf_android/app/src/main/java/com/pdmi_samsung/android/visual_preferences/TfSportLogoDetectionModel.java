package com.pdmi_samsung.android.visual_preferences;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import com.pdmi_samsung.android.visual_preferences.db.DetectorData;
import com.pdmi_samsung.android.visual_preferences.db.RectFloat;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * github.com/tensorflow/models/tree/master/research/object_detection

 * Created by avsavchenko.
 */
public class TfSportLogoDetectionModel {
    private static final String TAG = "TfSportLogoDetectionModel";

    // Only return this many results.
    private static final int MAX_RESULTS = 100;

    // Config values.
    private String inputName1,inputName2;
    private int inputSize;

    // Pre-allocated buffers.
    private ArrayList<String> labels = new ArrayList<String>();

    private int[] intValues;
    private float[] floatValues;
    private float[] imageSizes;
    private String[] outputNames;

    private TensorFlowInferenceInterface inferenceInterface;

    private static final String MODEL_FILE = "file:///android_asset/nhl_nba_logo_detector.pb";
    private static final String LABELS_FILE ="nhl_nba_classes.txt";


    public TfSportLogoDetectionModel(final AssetManager assetManager) throws IOException {
        InputStream labelsInput = assetManager.open(LABELS_FILE);
        BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
        String line;
        while ((line = br.readLine()) != null) {
            line=line.toLowerCase().split("#")[0].trim();
            labels.add(line);
        }
        br.close();


        inferenceInterface = new TensorFlowInferenceInterface(assetManager, MODEL_FILE);

        inputName1 = "input_1";
        inputName2 = "Placeholder_243";
        outputNames = new String[] {"concat_11", "concat_12", "concat_13"};
        inputSize = 288;
        intValues = new int[inputSize * inputSize];
        floatValues = new float[inputSize * inputSize * 3];
        imageSizes=new float[]{inputSize,inputSize};
    }

    public List<DetectorData> recognizeImage(final Bitmap bitmap) {

        Bitmap resizedBitmap=resizeBitmap(bitmap);
        //saveImage(resizedBitmap);
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.getWidth(), 0, 0, resizedBitmap.getWidth(), resizedBitmap.getHeight());

        for (int i = 0; i < intValues.length; ++i) {
            floatValues[i * 3 + 2] = (intValues[i] & 0xFF)/255.f;
            floatValues[i * 3 + 1] = ((intValues[i] >> 8) & 0xFF)/255.f;
            floatValues[i * 3 + 0] = ((intValues[i] >> 16) & 0xFF)/255.f;
        }

        inferenceInterface.feed(inputName1, floatValues, 1, resizedBitmap.getHeight(), resizedBitmap.getWidth(), 3);
        inferenceInterface.feed(inputName2, imageSizes, imageSizes.length);
        // Run the inference call.
        inferenceInterface.run(outputNames, false);
        float[] outputScores = new float[MAX_RESULTS];
        float[] outputLocations = new float[MAX_RESULTS * 4];
        int[] outputClasses = new int[MAX_RESULTS];

        inferenceInterface.fetch(outputNames[0], outputLocations);
        inferenceInterface.fetch(outputNames[1], outputScores);
        inferenceInterface.fetch(outputNames[2], outputClasses);

        // Scale them back to the input size.
        final ArrayList<DetectorData> recognitions = new ArrayList<DetectorData>();
        for (int i = 0; i < outputScores.length; ++i) {

            if (outputScores[i]>0) {
                final RectFloat detection =
                        new RectFloat(
                                outputLocations[4 * i + 1]/inputSize,
                                outputLocations[4 * i]/inputSize,
                                outputLocations[4 * i + 3]/inputSize,
                                outputLocations[4 * i + 2]/inputSize);
                recognitions.add(
                        new DetectorData(labels.get((int) outputClasses[i]), outputScores[i], detection));

                Log.i(TAG, String.format("Detected bounded box=[%.3f,%.3f,%.3f,%.3f] for %d score %.3f",
                        outputLocations[4 * i + 1],outputLocations[4 * i],outputLocations[4 * i + 3],
                        outputLocations[4 * i + 2],(int)outputClasses[i],outputScores[i])+" "+recognitions.get(recognitions.size()-1));

            }
        }

        return recognitions;
    }

    public void close() {
        inferenceInterface.close();
    }

    public Bitmap resizeBitmap(Bitmap bitmap) {
        Bitmap resizedBitmap=bitmap;
        int newWidth=inputSize,newHeight=inputSize;
        if(bitmap.getWidth()!=newWidth || bitmap.getHeight()!=newHeight){
            resizedBitmap=Bitmap.createScaledBitmap(bitmap,newWidth, newHeight,false);
        }
        return resizedBitmap;
    }

}
