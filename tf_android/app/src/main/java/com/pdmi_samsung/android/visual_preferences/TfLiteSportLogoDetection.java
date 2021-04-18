package com.pdmi_samsung.android.visual_preferences;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import com.pdmi_samsung.android.visual_preferences.db.DetectorData;
import com.pdmi_samsung.android.visual_preferences.db.RectFloat;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapted version of https://github.com/tensorflow/tensorflow/blob/master/tensorflow/lite/examples/android/app/src/main/java/org/tensorflow/demo/TFLiteObjectDetectionAPIModel.java

 */
public class TfLiteSportLogoDetection {
    private static final String TAG = "TfLiteSportLogoDetection";
    private static final String MODEL_FILE = "nhl_nba_logo_detector_opt.tflite";
    private static final String LABELS_FILE ="nhl_nba_classes.txt";

    // Only return this many results.
    private static final int NUM_DETECTIONS = 100;
    // Number of threads in the java app
    private static final int NUM_THREADS = 4;
    // Config values.
    private int inputSize;
    // Pre-allocated buffers.
    private ArrayList<String> labels = new ArrayList<String>();
    private Map<String,Integer> labels2HighLevelCategories = new HashMap<>();

    private int[] intValues;

    private ByteBuffer imgData,sizeData;

    private Interpreter tfLite;


    /**
    * @param assetManager The asset manager to be used to load assets.
    */

    public TfLiteSportLogoDetection(final AssetManager assetManager) throws IOException {
        InputStream labelsInput = assetManager.open(LABELS_FILE);
        BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
        String line;
        while ((line = br.readLine()) != null) {
            line=line.toLowerCase().split("#")[0].trim();
            labels.add(line);
        }
        br.close();

        inputSize = 288;

        Interpreter.Options options = (new Interpreter.Options()).setNumThreads(NUM_THREADS);//.addDelegate(delegate);
        tfLite = new Interpreter(loadModelFile(assetManager,MODEL_FILE),options);

        // Pre-allocate buffers.
        int numBytesPerChannel=4;
        imgData = ByteBuffer.allocateDirect(1 *  inputSize *  inputSize * 3 * numBytesPerChannel);
        imgData.order(ByteOrder.nativeOrder());
        intValues = new int[ inputSize *  inputSize];

        sizeData= ByteBuffer.allocateDirect(2);
        sizeData.order(ByteOrder.nativeOrder());
        sizeData.putFloat(inputSize);
        sizeData.putFloat(inputSize);
    }

    /** Memory-map the model file in Assets. */
    private static MappedByteBuffer loadModelFile(final AssetManager assetManager, String model_path) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(model_path);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public List<DetectorData> recognizeImage(final Bitmap bitmap) {

        Bitmap resizedBitmap=resizeBitmap(bitmap);
        //saveImage(resizedBitmap);
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.getWidth(), 0, 0, resizedBitmap.getWidth(), resizedBitmap.getHeight());

        imgData.rewind();
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                // Float model
                imgData.putFloat(((pixelValue >> 16) & 0xFF) /255.f);
                imgData.putFloat(((pixelValue >> 8) & 0xFF) /255.f);
                imgData.putFloat((pixelValue & 0xFF) /255.f);
            }
        }

        // Copy the input data into TensorFlow.
        float[] outputScores = new float[NUM_DETECTIONS];
        float[] outputLocations = new float[NUM_DETECTIONS * 4];
        int[] outputClasses = new int[NUM_DETECTIONS];

        Object[] inputArray = {imgData,sizeData};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputScores);
        outputMap.put(1, outputLocations);
        outputMap.put(2, outputClasses);

        // Run the inference call.
        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        // Show the best detections.
        // after scaling them back to the input size.
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
            }
        }
        return recognitions;
    }

    public void close() {
        tfLite.close();
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
