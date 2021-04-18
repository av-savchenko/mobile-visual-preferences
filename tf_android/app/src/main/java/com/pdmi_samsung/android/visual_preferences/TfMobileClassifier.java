
package com.pdmi_samsung.android.visual_preferences;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import com.pdmi_samsung.android.visual_preferences.db.ClassifierResult;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.IOException;

/**
 * Created by avsavchenko.
 */
public abstract class TfMobileClassifier {
    private static final String TAG = "TfClassifier";

    private static final int DIM_PIXEL_SIZE = 3;

    private TensorFlowInferenceInterface inferenceInterface;

    /* Preallocated buffers for storing image data in. */
    private int[] intValues = new int[getImageSizeX() * getImageSizeY()];
    private float[] floatValues=new float[getImageSizeX() * getImageSizeY()*DIM_PIXEL_SIZE];
    private float[][] outputs;

    private final String input_layer_name;
    private final String[] output_layer_names;

    protected abstract ClassifierResult getResults(float[][] outputs);

  TfMobileClassifier(final AssetManager assetManager, String input_name, String[] output_names, String model_path) throws IOException {
      input_layer_name=input_name;
      output_layer_names=output_names;
      inferenceInterface = new TensorFlowInferenceInterface(assetManager, model_path);
      outputs = new float[output_layer_names.length][];
      for(int i = 0; i< output_layer_names.length; ++i) {
          String featureOutputName = output_layer_names[i];
          // The shape of the output is [N, NUM_OF_FEATURES], where N is the batch size.
          int numOFFeatures = (int) inferenceInterface.graph().operation(featureOutputName).output(0).shape().size(1);
          Log.i(TAG, "Read output layer size is " + numOFFeatures);
          outputs[i] = new float[numOFFeatures];
      }
    Log.d(TAG, "Created a Tensorflow Mobile Image Classifier.");
  }

  /** Classifies a frame from the preview stream. */
  public ClassifierResult classifyFrame(Bitmap bitmap) {
      bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
      convertIntBitmapToFloatFeatures(intValues,floatValues);
      runInference();

      return getResults(outputs);
  }

  public void close() {
    inferenceInterface.close();
  }


  protected void convertIntBitmapToFloatFeatures(int[] intValues, float[] floatValues) {
      for (int i = 0; i < intValues.length; ++i) {
          final int val = intValues[i];
          //'RGB'->'BGR'
          floatValues[i * 3 + 0] = ((val & 0xFF) - 103.939f);
          floatValues[i * 3 + 1] = (((val >> 8) & 0xFF) - 116.779f);
          floatValues[i * 3 + 2] = (((val >> 16) & 0xFF) - 123.68f);
      }
  }


  protected int getImageSizeX() {
    return 224;
  }
  protected int getImageSizeY() {
    return 224;
  }



  protected synchronized void runInference() {
      inferenceInterface.feed(input_layer_name, floatValues, 1, getImageSizeX(), getImageSizeY(), DIM_PIXEL_SIZE);
      inferenceInterface.run(output_layer_names);

      // Copy the output Tensor back into the output array.
      for(int i = 0; i< output_layer_names.length; ++i) {
          inferenceInterface.fetch(output_layer_names[i], outputs[i]);
      }

  }

}
