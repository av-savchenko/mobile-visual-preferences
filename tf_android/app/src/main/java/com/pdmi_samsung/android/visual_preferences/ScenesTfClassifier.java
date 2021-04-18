package com.pdmi_samsung.android.visual_preferences;

import android.content.res.AssetManager;
import android.util.Log;

import com.pdmi_samsung.android.visual_preferences.db.*;

import java.io.*;
import java.util.*;

/**
 * Created by avsavchenko.
 */
public class ScenesTfClassifier extends TfMobileClassifier{

    /** Tag for the {@link Log}. */
    private static final String TAG = "ScenesTfClassifier";

    private static final String INPUT_NAME = "input_1";
    private static final String[] OUTPUT_NAMES = {"dense_1/Softmax","event_fc/BiasAdd"};
    private static final String MODEL_FILE =
            "file:///android_asset/places_event_mobilenet2_alpha=1.0_augm_ft_sgd_model_opt.pb";// mobilenet_scenes_places_optimized.pb";//densenet121_scenes.pb"; //mobilenet2_full.pb
    private static final String LABELS_FILE =
            "scenes_places.txt";
    private static final String FILTERED_INDICES_FILE =
            "scenes_unstable_places.txt";
    private static final String EVENTS_LABELS_FILE =
            "events.txt";

    public TreeMap<String,Integer> sceneLabels2Index =new TreeMap<>();
    private ArrayList<String> sceneLabels = new ArrayList<String>();
    private Map<String,Integer> labels2HighLevelCategories = new HashMap<>();
    private Set<Integer> filteredIndices=new HashSet<>();

    public TreeMap<String,Integer> eventLabels2Index =new TreeMap<>();
    private ArrayList<String> eventLabels = new ArrayList<String>();

    public ScenesTfClassifier(final AssetManager assetManager) throws IOException {
        super(assetManager,INPUT_NAME,OUTPUT_NAMES,MODEL_FILE);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(assetManager.open(EVENTS_LABELS_FILE)));
            String line;
            int line_ind=0;
            while ((line = br.readLine()) != null) {
                ++line_ind;
                //line=line.toLowerCase();
                line=line.split("#")[0].trim();
                String[] categoryInfo=line.split("=");
                String category=categoryInfo[0];
                eventLabels.add(category);

                int highLevelCategory=Integer.parseInt(categoryInfo[1]);
                labels2HighLevelCategories.put(category,highLevelCategory);
            }
            br.close();

            TreeSet<String> labelsSorted=new TreeSet<>();
            for (int i = 0; i < eventLabels.size(); ++i) {
                String event = eventLabels.get(i);
                if(!labelsSorted.contains(event))
                    labelsSorted.add(event);
            }

            int index=0;
            for(String label : labelsSorted) {
                eventLabels2Index.put(label, index);
                ++index;
            }
        } catch (IOException e) {
            throw new RuntimeException("Problem reading event label file!" , e);
        }

        try {
            br = new BufferedReader(new InputStreamReader(assetManager.open(FILTERED_INDICES_FILE)));
            String line;
            while ((line = br.readLine()) != null) {
                filteredIndices.add(Integer.parseInt(line)-1);
            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Problem reading filtered label file!" , e);
        }

        try {
            br = new BufferedReader(new InputStreamReader(assetManager.open(LABELS_FILE)));
            String line;
            int line_ind=0;
            while ((line = br.readLine()) != null) {
                ++line_ind;
                if(filteredIndices.contains(line_ind-1))
                  continue;
                //line=line.toLowerCase();
                line=line.split("#")[0].trim();
                String[] categoryInfo=line.split("=");
                String category=categoryInfo[0];
                sceneLabels.add(category);

                int highLevelCategory=Integer.parseInt(categoryInfo[1]);
                labels2HighLevelCategories.put(category,highLevelCategory);
            }
            br.close();
            TreeSet<String> labelsSorted=new TreeSet<>();
            for (int i = 0; i < sceneLabels.size(); ++i) {
                if(filteredIndices.contains(i))
                    continue;
                String scene= sceneLabels.get(i);
                if(!labelsSorted.contains(scene))
                    labelsSorted.add(scene);
            }

            int index=0;
            for(String label : labelsSorted) {
                sceneLabels2Index.put(label, index);
                ++index;
            }

        } catch (IOException e) {
          throw new RuntimeException("Problem reading scene label file!" , e);
        }
    }
    protected void convertIntBitmapToFloatFeatures(int[] intValues, float[] floatValues) {
        //float std=127.5f; //mobilenet v1
        float std=128.0f; //mobilenet v2
        for (int i = 0; i < intValues.length; ++i) {
            final int val = intValues[i];
            //'RGB'->'BGR' is not needed for all our scene recognition networks
            floatValues[i * 3 + 2] = ((val & 0xFF)/std - 1.0f);
            floatValues[i * 3 + 1] = (((val >> 8) & 0xFF)/std - 1.0f);
            floatValues[i * 3 + 0] = (((val >> 16) & 0xFF)/std - 1.0f);

            /*floatValues[i * 3 + 2] = ((val & 0xFF)/255.0f - 0.485f)/0.229f;
            floatValues[i * 3 + 1] = (((val >> 8) & 0xFF)/255.0f - 0.456f)/0.224f;
            floatValues[i * 3 + 0] = (((val >> 16) & 0xFF)/255.0f - 0.406f)/0.225f;*/
        }
    }

    private TreeMap<String,Float> getCategory2Score(float[] predictions, ArrayList<String> labels, boolean filter){
        TreeMap<String,Float> category2Score=new TreeMap<>();
        for (int i = 0; i < predictions.length; ++i) {
            if(filter && filteredIndices.contains(i))
                continue;
            String scene= labels.get(i);
            float score=predictions[i];
            if(category2Score.containsKey(scene)){
                score+=category2Score.get(scene);
            }
            category2Score.put(scene,score);
        }
        return category2Score;
    }

    protected ClassifierResult getResults(float[][] outputs) {
        TreeMap<String,Float> scene2Score=getCategory2Score(outputs[0],sceneLabels,true);
        TreeMap<String,Float> event2Score=getCategory2Score(outputs[1],eventLabels,false);
        SceneData res=new SceneData(sceneLabels2Index,scene2Score,eventLabels2Index,event2Score);
        return res;
    }
}
