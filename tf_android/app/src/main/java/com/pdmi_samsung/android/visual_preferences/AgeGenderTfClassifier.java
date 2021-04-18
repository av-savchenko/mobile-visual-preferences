
package com.pdmi_samsung.android.visual_preferences;

import android.content.res.AssetManager;
import android.util.Log;

import com.pdmi_samsung.android.visual_preferences.db.*;

import java.io.IOException;
import java.util.ArrayList;


/**
 * Created by avsavchenko.
 */
public class AgeGenderTfClassifier extends TfMobileClassifier{

  /** Tag for the {@link Log}. */
  private static final String TAG = "AgeGenderClassifier";

    private static final String INPUT_NAME = "input_1";
    private static final String[] OUTPUT_NAMES = {"global_pooling/Mean","age_pred/Softmax","gender_pred/Sigmoid","ethnicity_pred/Softmax"};
    private static final String MODEL_FILE =
            //"file:///android_asset/age_gender_tf2_new-01-0.14-0.92.pb";
            "file:///android_asset/age_gender_ethnicity_224_deep-03-0.13-0.97-0.88.pb"; //age_gender_optimized

  AgeGenderTfClassifier(final AssetManager assetManager) throws IOException {
      super(assetManager,INPUT_NAME,OUTPUT_NAMES,MODEL_FILE);
  }

  protected ClassifierResult getResults(float[][] outputs) {
      //normalize features (first dim)

      float[] features = outputs[0];
      float sum = 0;
      for (int i = 0; i < features.length; ++i)
          sum += features[i] * features[i];
      sum = (float) Math.sqrt(sum);
      if(sum>0) {
          for (int i = 0; i < features.length; ++i)
              features[i] /= sum;
      }
      Log.i(TAG, "!!!!!!!!!!!!!!!!!!!!!!!!! end feature extraction first feat=" + features[0] + " last feat=" + features[features.length - 1]);

      //age
      final float[] age_features = outputs[1];
      int max_index = 2;
      float[] probabs = new float[max_index];
      ArrayList<Integer> indices = new ArrayList<>();
        /*for (int j = 0; j < age_features.length; ++j) {
            indices.add(j);
        }
        Collections.sort(indices, new Comparator<Integer>() {
            @Override
            public int compare(Integer idx1, Integer idx2) {
                if (age_features[idx1] == age_features[idx2])
                    return 0;
                else if (age_features[idx1] > age_features[idx2])
                    return -1;
                else
                    return 1;
            }
        });*/
      for (int j = 0; j < max_index; ++j) {
          int bestInd=-1;
          float maxVal=-1;
          for(int i=0;i<age_features.length;++i){
              if(maxVal<age_features[i] && !indices.contains(i)){
                  maxVal=age_features[i];
                  bestInd=i;
              }
          }
          if(bestInd!=-1)
              indices.add(bestInd);
      }
      sum = 0;
      for (int j = 0; j < max_index; ++j) {
          probabs[j] = age_features[indices.get(j)];
          sum += probabs[j];
      }
      double age = 0;
      for (int j = 0; j < max_index; ++j) {
          age += (indices.get(j) + 0.5) * probabs[j] / sum;
      }
      float gender = outputs[2][0];
      final float[] ethnicity_scores = outputs[3];

      FaceData res=new FaceData(age, gender, ethnicity_scores,features);
      return res;
  }
}
