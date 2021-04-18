package com.pdmi_samsung.android.visual_preferences;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import com.pdmi_samsung.android.visual_preferences.db.ClassifierResult;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by avsavchenko.
 */
public abstract class TfLiteClassifier {

    /** Tag for the {@link Log}. */
    private static final String TAG = "TfLiteClassifier";

    /** An instance of the driver class to run model inference with Tensorflow Lite. */
    protected Interpreter tflite;

    /* Preallocated buffers for storing image data in. */
    private int[] intValues = null;
    /** A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs. */
    private int imageSizeX=224,imageSizeY=224;
    protected ByteBuffer imgData = null;
    private float[][][] outputs;
    Map<Integer, Object> outputMap = new HashMap<>();

    public TfLiteClassifier(final AssetManager assetManager,String model_path) throws IOException {
        //GpuDelegate delegate = new GpuDelegate();
        Interpreter.Options options = (new Interpreter.Options()).setNumThreads(4);//.addDelegate(delegate);
        tflite = new Interpreter(loadModelFile(assetManager,model_path),options);
        int[] inputShape=tflite.getInputTensor(0).shape();
        imageSizeX=inputShape[1];
        imageSizeY=inputShape[2];

        intValues = new int[imageSizeX * imageSizeY];
        imgData =ByteBuffer.allocateDirect(imageSizeX*imageSizeY* inputShape[3]*getNumBytesPerChannel());
        imgData.order(ByteOrder.nativeOrder());

        int outputCount=tflite.getOutputTensorCount();
        outputs=new float[outputCount][1][];
        for(int i = 0; i< outputCount; ++i) {
            int[] shape=tflite.getOutputTensor(i).shape();
            int numOFFeatures = shape[1];
            Log.i(TAG, "Read output layer size is " + numOFFeatures);
            outputs[i][0] = new float[numOFFeatures];
            ByteBuffer ith_output = ByteBuffer.allocateDirect( numOFFeatures* getNumBytesPerChannel());  // Float tensor, shape 3x2x4
            ith_output.order(ByteOrder.nativeOrder());
            outputMap.put(i, ith_output);
        }
    }
    /** Memory-map the model file in Assets. */
    private MappedByteBuffer loadModelFile(final AssetManager assetManager, String model_path) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(model_path);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    protected void addPixelValue(int val) {
        /*imgData.putFloat(((val >> 16) & 0xFF) / 255.f);
        imgData.putFloat(((val >> 8) & 0xFF) / 255.f);
        imgData.putFloat((val & 0xFF) / 255.f);*/

        //float std=127.5f; //mobilenet v1
        float std=128.0f; //mobilenet v2
        //'RGB'->'BGR' is not needed for all our scene recognition networks

        imgData.putFloat(((val >> 16) & 0xFF) /std - 1.0f);
        imgData.putFloat(((val >> 8) & 0xFF) /std - 1.0f);
        imgData.putFloat((val & 0xFF) /std - 1.0f);

        /*floatValues[i * 3 + 2] = ((val & 0xFF)/255.0f - 0.485f)/0.229f;
            floatValues[i * 3 + 1] = (((val >> 8) & 0xFF)/255.0f - 0.456f)/0.224f;
            floatValues[i * 3 + 0] = (((val >> 16) & 0xFF)/255.0f - 0.406f)/0.225f;*/
    }

    /** Classifies a frame from the preview stream. */
    public ClassifierResult classifyFrame(Bitmap bitmap) {
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        if (imgData == null) {
            return null;
        }
        imgData.rewind();
        // Convert the image to floating point.
        int pixel = 0;
        for (int i = 0; i < imageSizeX; ++i) {
            for (int j = 0; j < imageSizeY; ++j) {
                final int val = intValues[pixel++];
                addPixelValue(val);
            }
        }
        long startTime = SystemClock.uptimeMillis();
        Object[] inputs = {imgData};
        //tflite.run(imgData, outputs);
        /*for(Object ith_output : outputMap.values()){
            ((ByteBuffer)ith_output).rewind();
        }*/
        tflite.runForMultipleInputsOutputs(inputs, outputMap);
        for(int i = 0; i< outputs.length; ++i) {
            ByteBuffer ith_output=(ByteBuffer)outputMap.get(i);
            ith_output.rewind();
            int len=outputs[i][0].length;
            for(int j=0;j<len;++j){
                outputs[i][0][j]=ith_output.getFloat();
            }
            ith_output.rewind();
        }
        long endTime = SystemClock.uptimeMillis();
        Log.i(TAG, "tf lite timecost to run model inference: " + Long.toString(endTime - startTime));

        return getResults(outputs);
    }

    public void close() {
        tflite.close();
    }

    protected abstract ClassifierResult getResults(float[][][] outputs);
    public int getImageSizeX() {
        return imageSizeX;
    }
    public int getImageSizeY() {
        return imageSizeY;
    }
    protected int getNumBytesPerChannel() {
        return 4; // Float.SIZE / Byte.SIZE;
    }
}
