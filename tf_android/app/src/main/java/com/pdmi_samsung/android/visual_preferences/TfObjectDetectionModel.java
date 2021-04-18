package com.pdmi_samsung.android.visual_preferences;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Environment;

import com.pdmi_samsung.android.visual_preferences.db.*;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * github.com/tensorflow/models/tree/master/research/object_detection

 * Created by avsavchenko.
 */
public class TfObjectDetectionModel {
    private static final String TAG = "TFObjectDetectionModel";

    // Only return this many results.
    private static final int MAX_RESULTS = 100;

    // Config values.
    private String inputName;
    private int inputWidth, inputHeight;

    // Pre-allocated buffers.
    private ArrayList<String> labels = new ArrayList<String>();
    private Map<String,Integer> labels2HighLevelCategories = new HashMap<>();

    private int[] intValues;
    private byte[] byteValues;
    private String[] outputNames;

    private TensorFlowInferenceInterface inferenceInterface;

    private static final String[] TF_OD_API_MODEL_FILES = {
            "file:///android_asset/ssd_oid_optimized.pb",
            "file:///android_asset/fasterrcnn_inception_optimized.pb"
    };
    private static final String[] TF_OD_API_LABELS_FILES ={
            "oid_classes.txt",
            "all_classes.txt"
    };


    public TfObjectDetectionModel(final AssetManager assetManager, boolean detectOidCategories) throws IOException {
        int index=detectOidCategories?0:1;
        InputStream labelsInput = assetManager.open(TF_OD_API_LABELS_FILES[index]);
        BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
        String line;
        while ((line = br.readLine()) != null) {
            line=line.toLowerCase().split("#")[0].trim();
            String[] categoryInfo=line.split("=");
            String category=categoryInfo[0];
            labels.add(category);

            int highLevelCategory=Integer.parseInt(categoryInfo[1]);
            labels2HighLevelCategories.put(category,highLevelCategory);
        }
        br.close();


        inferenceInterface = new TensorFlowInferenceInterface(assetManager, TF_OD_API_MODEL_FILES[index]);

        inputName = "image_tensor";
        // The inputName node has a shape of [N, H, W, C], where
        // N is the batch size
        // H, W are the height and width
        // C is the number of channels (3 for our purposes - RGB)
        // The outputScoresName node has a shape of [N, NumLocations], where N
        // is the batch size.
        outputNames = new String[] {"detection_boxes", "detection_scores", "detection_classes"};
        if(detectOidCategories)
            inputWidth = inputHeight = 300;
        else{
            inputWidth = 600;inputHeight = 800;
        }
        intValues = new int[inputWidth * inputHeight];
        byteValues = new byte[inputWidth * inputHeight * 3];
    }

    public List<DetectorData> recognizeImage(final Bitmap bitmap) {

        Bitmap resizedBitmap=resizeBitmap(bitmap);
        //saveImage(resizedBitmap);
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.getWidth(), 0, 0, resizedBitmap.getWidth(), resizedBitmap.getHeight());

        for (int i = 0; i < intValues.length; ++i) {
            byteValues[i * 3 + 2] = (byte) (intValues[i] & 0xFF);
            byteValues[i * 3 + 1] = (byte) ((intValues[i] >> 8) & 0xFF);
            byteValues[i * 3 + 0] = (byte) ((intValues[i] >> 16) & 0xFF);
        }

        inferenceInterface.feed(inputName, byteValues, 1, resizedBitmap.getHeight(), resizedBitmap.getWidth(), 3);
        // Run the inference call.
        inferenceInterface.run(outputNames, false);

        float[] outputLocations = new float[MAX_RESULTS * 4];
        float[] outputScores = new float[MAX_RESULTS];
        float[] outputClasses = new float[MAX_RESULTS];
        inferenceInterface.fetch(outputNames[0], outputLocations);
        inferenceInterface.fetch(outputNames[1], outputScores);
        inferenceInterface.fetch(outputNames[2], outputClasses);

        // Scale them back to the input size.
        final ArrayList<DetectorData> recognitions = new ArrayList<DetectorData>();
        for (int i = 0; i < outputScores.length; ++i) {
            /*
            Log.i(TAG, String.format("Detected bounded box=[%.3f,%.3f,%.3f,%.3f] for %d score %.3f",
                  outputLocations[4 * i + 1],outputLocations[4 * i],outputLocations[4 * i + 3],outputLocations[4 * i + 2],(int)outputClasses[i],outputScores[i]));
             */
            if (outputScores[i]>0) {
                final RectFloat detection =
                        new RectFloat(
                                outputLocations[4 * i + 1],
                                outputLocations[4 * i],
                                outputLocations[4 * i + 3],
                                outputLocations[4 * i + 2]);
                recognitions.add(
                        new DetectorData(labels.get((int) outputClasses[i]), outputScores[i], detection));
            }
        }

        return recognitions;
    }

    public static void saveImage(Bitmap finalBitmap) {
        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root + "/saved_images");
        myDir.mkdirs();
        Random generator = new Random();
        int n = 10000;
        n = generator.nextInt(n);
        String fname = "Image-"+ n +".jpg";
        File file = new File (myDir, fname);
        if (file.exists ()) file.delete ();
        try {
            FileOutputStream out = new FileOutputStream(file);
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        inferenceInterface.close();
    }


    public Bitmap resizeBitmap(Bitmap bitmap) {
        Bitmap resizedBitmap=bitmap;
        int newWidth=0,newHeight=0;
        if(bitmap.getWidth()<bitmap.getHeight()){
            newWidth=inputWidth;
            newHeight=inputHeight;
        }
        else{
            newWidth=inputHeight;
            newHeight=inputWidth;
        }
        if(bitmap.getWidth()!=newWidth || bitmap.getHeight()!=newHeight){
            resizedBitmap=Bitmap.createScaledBitmap(bitmap,newWidth, newHeight,false);
        }
        return resizedBitmap;
    }

    public int getHighLevelCategory(String category){
        int res=-1;
        if(labels2HighLevelCategories.containsKey(category))
            res= labels2HighLevelCategories.get(category);
        return res;
    }

}
