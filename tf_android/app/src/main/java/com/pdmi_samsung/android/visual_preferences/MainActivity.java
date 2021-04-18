package com.pdmi_samsung.android.visual_preferences;


import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import android.view.View;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.FirebaseApp;
import com.pdmi_samsung.android.visual_preferences.clustering.DBSCANClusterer;
import com.pdmi_samsung.android.visual_preferences.clustering.DistanceMetric;
import com.pdmi_samsung.android.visual_preferences.clustering.HDBSCANClustering;
import com.pdmi_samsung.android.visual_preferences.db.DetectionResultsWithFaces;
import com.pdmi_samsung.android.visual_preferences.db.DetectorData;
import com.pdmi_samsung.android.visual_preferences.db.EXIFData;
import com.pdmi_samsung.android.visual_preferences.db.FaceData;
import com.pdmi_samsung.android.visual_preferences.db.ImageAnalysisResults;
import com.pdmi_samsung.android.visual_preferences.db.TopCategoriesData;
import com.pdmi_samsung.android.visual_preferences.db.RectFloat;

import java.io.File;
import java.util.*;

/**
 * Created by avsavchenko.
 */

public class MainActivity extends FragmentActivity {

    /** Tag for the {@link Log}. */
    private static final String TAG = "MainActivity";
    private final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 124;

    private HighLevelVisualPreferences preferencesFragment;
    private Photos photosFragment;

    private ProgressBar progressBar;
    private TextView progressBarinsideText;
    private ImageButton serverConfigButton;

    private Thread photoProcessingThread=null;
    private Map<String,Long> photosTaken;
    private ArrayList<String> photosFilenames;
    private int currentPhotoIndex=0;
    private PhotoProcessor photoProcessor = null;

    private NetworkStateBroadcastReceiver mNetworkreceiver = null;

    private Map<String,HashSet<String>> file2categories=new LinkedHashMap<>();
    private Map<String,String> file2locations=new LinkedHashMap<>();
    private HashMap<String,List<FaceData>> file2faces=new LinkedHashMap<>();


    private Map<String,HashMap<Integer,HashSet<String>>> cameraModel2FocalLengthWithFilenames=new LinkedHashMap<>();

    private String[] categoryList;

    private List<Map<String,Map<String, Set<String>>>> categoriesHistograms=new ArrayList<>();
    private List<Map<String, Map<String, Set<String>>>> eventTimePeriod2Files=new ArrayList<>();
    private List<List<Map<String,Set<String>>>> demographyHisto=new ArrayList<>();
    private OwnerDemography ownerDemography=null;
    private HDBSCANClustering hdbscanClustering;

    private static int currentYear= Calendar.getInstance().get(Calendar.YEAR);
    public static int[] MAX_AGES ={0,7,12,17,24,34,44,54,100};
    public static final int NUM_GENDERS=2;
    private static int AGE_RANGE_GENERATION_DIFF=2; //minimum 2 age ranges between generations
    private static final int MIN_FACIAL_CLUSTER_SIZE = 5;
    private static final double FACIAL_DISTANCE_THRESHOLD=0.77;//0.78;
    private static final double MAX_DISTANCE = 100;


    private static class FacialClusterInfo{
        public List<Integer> cluster;
        public int age;
        public int ageRange=MAX_AGES.length-1;
        public int isFemale; //0-male, 1-femaleME
        public String ethnicity;
        public long daysBeytweenFirstAndLastPhotos;
        public Set<String> filenames;
        public String category=null;

        public FacialClusterInfo(List<Integer> cluster, double age, int isFemale,String ethnicity, long daysBeytweenFirstAndLastPhotos,Set<String> filenames){
            this.cluster=cluster;
            this.age=(int)Math.round(age);
            for (int i = 1; i < MAX_AGES.length; ++i) {
                if (age <= MAX_AGES[i]) {
                    ageRange=i-1;
                    break;
                }
            }

            this.isFemale=isFemale;
            this.ethnicity=ethnicity;
            this.daysBeytweenFirstAndLastPhotos=daysBeytweenFirstAndLastPhotos;
            this.filenames=filenames;
        }
    }
    private static class FaceInfo{
        public String filename;
        public double ageDelta;
        public FaceData faceData;

        public FaceInfo(String filename, double ageDelta, FaceData faceData){
            this.filename=filename;
            this.ageDelta=ageDelta;
            this.faceData=faceData;
        }
    }




    private List<FaceInfo> allFaces=new ArrayList<>();
    private List<double[]> allFaceDistances=new ArrayList<>();
    private List<ArrayList<Integer>> facialNeighbors=new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, getRequiredPermissions(), REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
        }
        else
            init();
    }
    private void init(){
        //checkServerSettings();
        FirebaseApp.initializeApp(this);
        registerNetworkReceiver();
        categoryList = getResources().getStringArray(R.array.category_list);

        for(int i=0;i<categoryList.length-1;++i){
            categoriesHistograms.add(new HashMap<>());
        }

        for(int i=0;i<categoryList.length-2;++i){
            eventTimePeriod2Files.add(new HashMap<>());
        }

        photoProcessor = PhotoProcessor.getPhotoProcessor(this);
        photosTaken = photoProcessor.getCameraImages();
        photosFilenames=new ArrayList<String>(photosTaken.keySet());
        //currentPhotoIndex=0;

        progressBar=(ProgressBar) findViewById(R.id.progress);
        progressBar.setMax(photosFilenames.size());
        progressBarinsideText=(TextView)findViewById(R.id.progressBarinsideText);
        progressBarinsideText.setText("");

        serverConfigButton = (ImageButton)findViewById(R.id.ServerConfigButton);

        serverConfigButton.setOnClickListener(view -> {
         try {
             getSupportFragmentManager()
                     .beginTransaction()
                     .replace(R.id.fragment_switch, new ServerSettingsFragment())
                     .addToBackStack(null)
                     .commit();
         } catch (Exception ex){
             Log.e("Settings", "Settings error", ex);
         }
        });


        photoProcessingThread = new Thread(() -> {
         processAllPhotos(false);
         updatePrivateImagesWithDemography();
         if(ServerProcessor.isEnabled) {
             currentPhotoIndex=0;
             processAllPhotos(true);
         }

        }, "photo-processing-thread");
        progressBar.setVisibility(View.VISIBLE);

        preferencesFragment = new HighLevelVisualPreferences();
        Bundle prefArgs = new Bundle();
        prefArgs.putInt("color", Color.GREEN);
        prefArgs.putString("title", "High-Level topCategories");
        preferencesFragment.setArguments(prefArgs);

        photosFragment=new Photos();
        Bundle args = new Bundle();
        args.putStringArray("photosTaken", new String[]{"0"});
        args.putStringArrayList("0",new ArrayList<String>(photoProcessor.getCameraImages().keySet()));
        photosFragment.setArguments(args);
        PreferencesClick(null);

        photoProcessingThread.setPriority(Thread.MIN_PRIORITY);
        photoProcessingThread.start();
    }
    public synchronized List<Map<String,Map<String, Set<String>>>> getCategoriesHistograms(boolean allLogs){
        if (allLogs)
            return categoriesHistograms;
        else
            return eventTimePeriod2Files;
    }
    public synchronized List<List<Map<String,Set<String>>>> getDemographyHistogram(){
        return demographyHisto;
    }
    public OwnerDemography getOwnerDemography(){
        return ownerDemography;
    }


    private void registerNetworkReceiver(){
        mNetworkreceiver = new NetworkStateBroadcastReceiver();
        IntentFilter networkFilter = new IntentFilter("android.net.conn.CONNECTIVITY_CHANGE");
        registerReceiver(mNetworkreceiver, networkFilter);
    }

    private void processAllPhotos(boolean needServerConnection){
        //ImageAnalysisResults previousPhotoProcessedResult=null;
        hdbscanClustering = new HDBSCANClustering();
        for(;currentPhotoIndex<photosTaken.size();++currentPhotoIndex){
            String filename=photosFilenames.get(currentPhotoIndex);
            try {
                File file = new File(filename);

                if (file.exists()) {
                    long startTime = SystemClock.uptimeMillis();
                    ImageAnalysisResults res = photoProcessor.getImageAnalysisResults(filename,needServerConnection);

                    long endTime = SystemClock.uptimeMillis();
                    Log.d(TAG, "!!Processed: "+ filename+" in background thread:" + Long.toString(endTime - startTime));
                    processRecognitionResults(res);

                    runOnUiThread(() -> {
                        if(progressBar!=null) {
                            progressBar.setProgress(currentPhotoIndex+1);
                            progressBarinsideText.setText(""+100*(currentPhotoIndex+1)/photosTaken.size()+"%");
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "While  processing image" + filename + " exception thrown: " + e);
            }
        }
        //clusterFaces(true);
    }

    private void updatePrivateImagesWithDemography(){
        Set<String> privateFilenames=photoProcessor.getPrivateFilenames();
        for (List<Map<String,Set<String>>> ageHisto : demographyHisto) {
            for (Map<String,Set<String>> ageGenderHisto : ageHisto) {
                for (Set<String> filenames : ageGenderHisto.values()) {
                    privateFilenames.addAll(filenames);
                }
            }
        }
    }

    private static final String[] stopwords={"passport","address","identification","license","card","expiry","issue","surname"};
    private boolean isPrivateText(String text){
        boolean res=false;
        if(text!=null && text!=""){
            text=text.toLowerCase();
            for(int i=0;i<stopwords.length && !res;++i)
                res=text.contains(stopwords[i]);
        }
        return res;
    }

    private void updateImageFileInfo(String filename){
        try {
            EXIFData exifData=photoProcessor.getEXIFData(filename);
            if(exifData.cameraModel!="" && exifData.cameraFocalLength!=0) {
                //Log.w(TAG, "In getImageFileInfo for image" + filename + " model: "+model+" focalLen:"+focalLen);
                if(!cameraModel2FocalLengthWithFilenames.containsKey(exifData.cameraModel)){
                    cameraModel2FocalLengthWithFilenames.put(exifData.cameraModel, new HashMap<>());
                }
                HashMap<Integer,HashSet<String>> focalLengthWithFilenames=cameraModel2FocalLengthWithFilenames.get(exifData.cameraModel);
                if(!focalLengthWithFilenames.containsKey(exifData.cameraFocalLength)){
                    focalLengthWithFilenames.put(exifData.cameraFocalLength,new HashSet<>());
                }
                focalLengthWithFilenames.get(exifData.cameraFocalLength).add(filename);

                int minFocalLen=Collections.min(focalLengthWithFilenames.keySet());
                if (minFocalLen==exifData.cameraFocalLength && focalLengthWithFilenames.size()>1){
                    updateSelfies();
                }
            }
        }catch(Exception e){
            Log.e(TAG, "While getImageFileInfo for image" + filename + " exception thrown: ", e);
        }
    }
    private void updateSelfies(){
        photoProcessor.getSelfiesFilenames().clear();
        for(Map.Entry<String,HashMap<Integer,HashSet<String>>> camera_info : cameraModel2FocalLengthWithFilenames.entrySet()){
            HashMap<Integer,HashSet<String>> focalLengthWithFilenames=camera_info.getValue();
            if (focalLengthWithFilenames.size()>1){
                int minFocalLen=Collections.min(focalLengthWithFilenames.keySet());
                for(String filename : focalLengthWithFilenames.get(minFocalLen)){
                    photoProcessor.getSelfiesFilenames().add(filename);
                }
            }
        }
    }

    private List<FaceData> getFaceDataList(List<Integer> cluster){
        List<FaceData> faces=new ArrayList<>();
        for (int elementInd : cluster) {
            faces.add(allFaces.get(elementInd).faceData);
        }
        return faces;
    }
    private FacialClusterInfo updateOwnerCluster(List<FacialClusterInfo> facialClusterInfo){
        int ownerCluster=-1;
        int maxSelfies=-1;
        for(int i=0;i<facialClusterInfo.size();++i) {
            List<Integer> cluster=facialClusterInfo.get(i).cluster;
            int numSelfies=0;
            for (int elementInd : cluster) {
                FaceInfo element = allFaces.get(elementInd);
                if(photoProcessor.getSelfiesFilenames().contains(element.filename)) {
                    ++numSelfies;
                }
            }
            if(maxSelfies<numSelfies){
                maxSelfies=numSelfies;
                ownerCluster=i;
            }
            else if(maxSelfies==numSelfies){
                ownerCluster=-1;//Undefined for equal number of selfies
            }
        }
        FacialClusterInfo res=null;
        List<FaceData> ownerFaces=null;
        if(ownerCluster!=-1) {
            res=facialClusterInfo.get(ownerCluster);
            ownerFaces=getFaceDataList(res.cluster);
            res.category=Constants.OWNER_DEMOGRAPHY_CATEGORY;
        }
        else
            ownerFaces=new ArrayList<>();
        photoProcessor.setDemographyFaceDatas(Constants.OWNER_DEMOGRAPHY_CATEGORY,ownerFaces,true);
        return res;
    }

    private static int MIN_DAYS_DIFFERENCE_RELATIVE=15;//days 30*6;// 6 month
    private static int MIN_PHOTOS_OF_CLOSED_FRIENDS=MIN_FACIAL_CLUSTER_SIZE;//10;
    private static int MIN_RELATIVE_PHOTOS_BEST_FRIEND=10;

    public static String getAgeRangeDescription(int ageRangeInd){
        return String.format("%3d-%3d",MAX_AGES[ageRangeInd-1],MAX_AGES[ageRangeInd]);
    }

    private void updateHighlevelDemography(List<FacialClusterInfo> facialClusterInfo){
        FacialClusterInfo ownerCluster=updateOwnerCluster(facialClusterInfo);
        if(ownerCluster!=null){
            List<FacialClusterInfo> children=new ArrayList<>();
            List<FacialClusterInfo> parents=new ArrayList<>();
            List<FacialClusterInfo> friends=new ArrayList<>();
            for(FacialClusterInfo person : facialClusterInfo) {
                if(person.daysBeytweenFirstAndLastPhotos>=MIN_DAYS_DIFFERENCE_RELATIVE && person.cluster.size()>=MIN_PHOTOS_OF_CLOSED_FRIENDS){
                    if(person.ageRange<=ownerCluster.ageRange-AGE_RANGE_GENERATION_DIFF){
                        children.add(person);
                    }
                    else if(person.ageRange>=ownerCluster.ageRange+AGE_RANGE_GENERATION_DIFF){
                        parents.add(person);
                    }
                    else if(Math.abs(person.ageRange-ownerCluster.ageRange)<=1 && person.cluster!=ownerCluster.cluster){
                        friends.add(person);
                    }
                }
            }
            Collections.sort(children, new Comparator<FacialClusterInfo>() {
                @Override
                public int compare(FacialClusterInfo lhs, FacialClusterInfo rhs) {
                    if(lhs.ageRange>rhs.ageRange){
                        return -1;
                    }
                    else if(lhs.ageRange<rhs.ageRange){
                        return 1;
                    }
                    else
                        return rhs.cluster.size()-lhs.cluster.size();
                }
            });

            int childNo=1;
            for(FacialClusterInfo person : children) {
                person.category=Constants.CHILD_DEMOGRAPHY_CATEGORY+" "+childNo;
                List<FaceData> faces=getFaceDataList(person.cluster);
                photoProcessor.setDemographyFaceDatas(person.category,faces,false);
                ++childNo;
            }
            ownerDemography=new OwnerDemography(ownerCluster.age,ownerCluster.isFemale,ownerCluster.ethnicity,children.size());


            Comparator<FacialClusterInfo> cmp=new Comparator<FacialClusterInfo>() {
                @Override
                public int compare(FacialClusterInfo lhs, FacialClusterInfo rhs) {
                    return rhs.cluster.size()-lhs.cluster.size();
                }
            };

            Collections.sort(parents, cmp);
            for(int parentNo=0;parentNo<Math.min(4, parents.size());++parentNo) {
                FacialClusterInfo person = parents.get(parentNo);
                List<FaceData> faces=getFaceDataList(person.cluster);
                person.category=person.isFemale==0?Constants.FATHER_DEMOGRAPHY_CATEGORY:Constants.MOTHER_DEMOGRAPHY_CATEGORY;
                photoProcessor.setDemographyFaceDatas(person.category,faces,false);
            }

            Collections.sort(friends, cmp);
            int friendNo=0;
            for(FacialClusterInfo person : friends) {
                String category=null;
                if(friendNo==0){
                    if(person.isFemale!=ownerCluster.isFemale &&
                            (friends.size()==1 || (person.cluster.size()-friends.get(1).cluster.size())>=MIN_RELATIVE_PHOTOS_BEST_FRIEND))
                    {
                        category=(person.isFemale==1)?Constants.GIRL_FRIEND_DEMOGRAPHY_CATEGORY:Constants.BOY_FRIEND_DEMOGRAPHY_CATEGORY;
                        ownerDemography.isMarried=true;
                    }
                    else{
                        friendNo = 1;
                    }
                }
                if(friendNo>0)
                    category=Constants.FRIEND_DEMOGRAPHY_CATEGORY+" "+friendNo;
                ++friendNo;
                List<FaceData> faces=getFaceDataList(person.cluster);
                person.category=category;
                photoProcessor.setDemographyFaceDatas(category,faces,false);
            }
        }
        else
            ownerDemography=null;

        List<List<Map<String,Set<String>>>> newDemographyHisto=new ArrayList<>();
        for(int i=0;i<NUM_GENDERS;++i){
            List<Map<String,Set<String>>> ageHistos=new ArrayList<>();
            for(int j = 0; j< MAX_AGES.length; ++j){
                ageHistos.add(new HashMap<>());
            }
            newDemographyHisto.add(ageHistos);
        }
        for (int i = 0; i < facialClusterInfo.size(); ++i) {
            FacialClusterInfo person = facialClusterInfo.get(i);
            if(person.category==null)
                person.category=String.valueOf(i+1);
            newDemographyHisto.get(person.isFemale).get(person.ageRange).put(person.category,person.filenames);
        }
        this.demographyHisto=newDemographyHisto;

    }

    private void updateCategory(List<Map<String,Map<String, Set<String>>>> histos, int highLevelCategory, String category, String filename){
        if(highLevelCategory>=0) {
            Map<String, Map<String, Set<String>>> histo = histos.get(highLevelCategory);
            if (!histo.containsKey(category)) {
                histo.put(category, new TreeMap<>());
                histo.get(category).put("0", new TreeSet<>());
            }
            histo.get(category).get("0").add(filename);
        }
    }

    private List<Map<String,Map<String, Set<String>>>> deepCopyCategories(List<Map<String,Map<String, Set<String>>>> categories){
        ArrayList<Map<String,Map<String, Set<String>>>> result=new ArrayList<>(categories.size());
        for(Map<String,Map<String, Set<String>>> m:categories){
            Map<String,Map<String, Set<String>>> m1=new HashMap<>(m.size());
            result.add(m1);
            for(Map.Entry<String,Map<String, Set<String>>> me:m.entrySet()){
                Map<String, Set<String>> m2=new TreeMap<>(Collections.reverseOrder());
                m1.put(me.getKey(),m2);
                for(Map.Entry<String, Set<String>> map_files:me.getValue().entrySet()){
                    m2.put(map_files.getKey(),new TreeSet<>(map_files.getValue()));
                }
            }
        }
        return result;
    }
    private synchronized void processRecognitionResults(ImageAnalysisResults results){
        String filename=results.filename;

        List<Map<String,Map<String, Set<String>>>> newEventTimePeriod2Files = deepCopyCategories(eventTimePeriod2Files);
        photoProcessor.updateSceneInEvents(newEventTimePeriod2Files,filename);
        eventTimePeriod2Files=newEventTimePeriod2Files;
        //eventTimePeriod2Files=photoProcessor.updateSceneInEvents(categoryList.length-2);

        boolean isPrivate=processFaces(results);

        if(!isPrivate)
            isPrivate=isPrivateText(results.text);
        if(isPrivate) {
            Set<String> privateFilenames = photoProcessor.getPrivateFilenames();
            privateFilenames.add(filename);
        }

        updateImageFileInfo(filename);

        //objects
        HashSet<String> categories=new HashSet<>();
        DetectionResultsWithFaces objects=results.objects;
        if(ServerProcessor.isEnabled && results.serverObjects!=null){
            objects=results.serverObjects;
        }
        //StringBuilder str=new StringBuilder();
        for(DetectorData data : objects.getReliableDetectors()){
            categories.add(data.title);
            //str.append(data.title).append(",");
        }

        String location=results.locations.description;
        /*if(true)
        {*/
            List<Map<String,Map<String, Set<String>>>> newCategoriesHistograms = deepCopyCategories(categoriesHistograms);

            for (String category : categories) {
                updateCategory(newCategoriesHistograms, photoProcessor.getHighLevelCategory(category), category, filename);
            }
            for(TopCategoriesData data : results.objectTypes.concreteTypes){
                String type=data.getBestCategory();
                if(type!=null) {
                    updateCategory(newCategoriesHistograms, data.highLevelCategory, type, filename);
                }
                //str.append(data.title).append(",");
            }
            List<String> scenes = results.scene.getMostReliableCategories();
            for (String scene : scenes) {
                updateCategory(newCategoriesHistograms, photoProcessor.getHighLevelCategory(scene), scene, filename);
            }

            if(results.logos!=null && results.logos.detections!=null) {
                for (DetectorData data : results.logos.getReliableDetectors()) {
                    updateCategory(newCategoriesHistograms, 5, data.title, filename); //sport
                }
            }
            if(location!=null)
                updateCategory(newCategoriesHistograms, newCategoriesHistograms.size() - 1, location, filename);

            categoriesHistograms=newCategoriesHistograms;
        /*}
        else{
            file2categories.put(filename,categories);
            if(location!=null)
                file2locations.put(filename,location);

            //reevaluate topCategories
            List<Map<String, Map<String, Set<String>>>> newCategoriesHistograms = new ArrayList<>();
            for (int i = 0; i < categoryList.length - 1; ++i) {
                newCategoriesHistograms.add(new HashMap<>());
            }

            for (Map.Entry<String, HashSet<String>> file_category : file2categories.entrySet()) {
                for (String category : file_category.getValue()) {
                    updateCategory(newCategoriesHistograms, photoProcessor.getHighLevelCategory(category), category, file_category.getKey());
                }
            }

            Map<String, SceneData> file2Scene = photoProcessor.getFile2Scene();
            for (Map.Entry<String, SceneData> file_scene : file2Scene.entrySet()) {
                List<String> scenes = file_scene.getValue().getMostReliableCategories();
                for (String scene : scenes) {
                    updateCategory(newCategoriesHistograms, photoProcessor.getHighLevelCategory(scene), scene, file_scene.getKey());
                }
            }

            int highLevelCategory = newCategoriesHistograms.size() - 1;
            for (Map.Entry<String, String> file_descr : file2locations.entrySet()) {
                updateCategory(newCategoriesHistograms, highLevelCategory, file_descr.getValue(), file_descr.getKey());
            }
            this.categoriesHistograms = newCategoriesHistograms;
        }*/

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                preferencesFragment.updateChart();
            }
        });
    }

    private boolean processFaces(ImageAnalysisResults results){
        String filename=results.filename;
        boolean isPrivate=false;
        if(!file2faces.containsKey(filename)) {
            file2faces.put(filename, results.objects.faces);
            if(!results.objects.faces.isEmpty()) {
                double ageDelta=0;
                if(photosTaken.containsKey(filename)) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.setTimeInMillis(photosTaken.get(filename));
                    int year=calendar.get(Calendar.YEAR);
                    ageDelta=currentYear-year+0.5;
                }
                for (int f = 0; f < results.objects.faceRects.size(); ++f) {
                    RectFloat bbox_f = results.objects.faceRects.get(f);
                    if(Math.abs(bbox_f.right-bbox_f.left)>=0.05) //has rather large face
                        isPrivate=true;

                    FaceData face=results.objects.faces.get(f);

                    allFaces.add(new FaceInfo(filename, ageDelta, face));

                    int facesCount = allFaces.size();
                    double[] distances = new double[facesCount];
                    double realAge = face.age + ageDelta;
                    ArrayList<Integer> neighbors=new ArrayList<>();
                    for (int ind = 0; ind < facesCount-1; ++ind) {
                        FaceData other = allFaces.get(ind).faceData;
                        double dist = 0;
                        for (int i = 0; i < face.features.length; ++i) {
                            dist += (face.features[i] - other.features[i]) * (face.features[i] - other.features[i]);
                        }
                        dist = Math.sqrt(dist);

                        double realAgeOther = other.age + allFaces.get(ind).ageDelta;
                        double age_dist = 0;
                        if (realAge < MAX_AGES[1] || realAgeOther < MAX_AGES[1])
                            age_dist = 0.5 * (realAge - realAgeOther) * (realAge - realAgeOther) / (realAge + realAgeOther);
                        //Log.i(TAG,"Distance:"+dist+" age="+face.age+" "+val2.age+" features:"+face.features[0]+" "+val2.features[0]);
                        dist += age_dist;

                        /*if(ind>=facesCount-f-1)
                            dist+=MAX_DISTANCE;*/
                        distances[ind] = dist;

                        if(dist<=FACIAL_DISTANCE_THRESHOLD){
                            facialNeighbors.get(ind).add(facesCount-1);
                            neighbors.add(ind);
                        }
                    }
                    allFaceDistances.add(distances);

                    neighbors.add(facesCount-1);
                    facialNeighbors.add(neighbors);
                }
                clusterFaces(false);
            }
        }
        return isPrivate;
    }


    private void clusterFaces(boolean finalCluster){
        int facesCount=allFaces.size();
        int minFacialClusterSize=Math.max(2,Math.min(facesCount/5,MIN_FACIAL_CLUSTER_SIZE));
        ArrayList<ArrayList<Integer>> facialClusters=null;
        long startTime = SystemClock.uptimeMillis();
        if(finalCluster && false){
            if (facesCount >= 1) {
                int lenFeatures = allFaces.get(0).faceData.features.length;
                double[][] clusteredVectors = new double[facesCount][lenFeatures];
                for (int i = 0; i < facesCount; i++)
                    for (int j = 0; j < lenFeatures; j++)
                        clusteredVectors[i][j] = allFaces.get(i).faceData.features[j];
                hdbscanClustering.setMinClusterSize(minFacialClusterSize);
                facialClusters = hdbscanClustering.performFullClusteringOnHDBSCAN(clusteredVectors);
            }
        }
        else if(true) {
            ArrayList<Integer> facialIndexes=new ArrayList<>(facesCount);
            for(int i=0;i<facesCount;++i) {
                facialIndexes.add(i);
            }
            DBSCANClusterer<Integer> clusterer=new DBSCANClusterer<Integer>(facialIndexes, minFacialClusterSize,FACIAL_DISTANCE_THRESHOLD,new DistanceMetric<Integer>(){
                @Override
                public double calculateDistance(Integer val1, Integer val2) {
                    if(val1<val2){
                        return allFaceDistances.get(val2)[val1];
                    }
                    else
                        return allFaceDistances.get(val1)[val2];
                }

                @Override
                public ArrayList<Integer> getNeighbours(Integer inputValue) {
                    return facialNeighbors.get(inputValue);
                }
            });
            facialClusters = clusterer.performClustering();
        }
        else if (true){
            facialClusters = new ArrayList<>();
            final double distanceThreshold=0.76;
            for (int i = 0; i < facesCount; ++i) {
                ArrayList<Integer> bestClusters = new ArrayList<>();
                for (int c = 0; c < facialClusters.size(); ++c) {
                    ArrayList<Integer> cluster = facialClusters.get(c);
                    double minClusterDistance = 10000, maxClusterDistance=0;
                    for (int j = 0; j < cluster.size(); ++j) {
                        //for(int j=cluster.size()-1;j>=Math.max(0,cluster.size()-5);--j){
                        double dist = allFaceDistances.get(i)[cluster.get(j)];
                        if (dist < minClusterDistance) {
                            minClusterDistance = dist;
                        }
                        if (dist > maxClusterDistance) {
                            maxClusterDistance = dist;
                        }
                    }
                    if (minClusterDistance < distanceThreshold && maxClusterDistance<MAX_DISTANCE) {
                        bestClusters.add(c);
                    }
                }
                //Log.i(TAG,"Clustering "+i+" minDist="+minDistance+" bestCluster="+bestCluster);
                if (bestClusters.isEmpty()) {
                    ArrayList<Integer> cluster = new ArrayList<>();
                    cluster.add(i);
                    facialClusters.add(cluster);
                }
                else if (bestClusters.size()==1){
                    facialClusters.get(bestClusters.get(0)).add(i);
                }
                else {
                    ArrayList<Integer> cluster = new ArrayList<>();
                    for(int ind=bestClusters.size()-1;ind>=0;--ind){
                        int c=bestClusters.get(ind);
                        cluster.addAll(facialClusters.get(c));
                        facialClusters.remove(c);
                    }
                    cluster.add(i);
                    facialClusters.add(cluster);
                }
            }
        }
        else {
            facialClusters = new ArrayList<>();
            final double distanceThreshold=0.85;
            for (int i = 0; i < facesCount; ++i) {
                double minDistance = 10000;
                int bestCluster = -1;
                for (int c = 0; c < facialClusters.size(); ++c) {
                    ArrayList<Integer> cluster = facialClusters.get(c);
                    for (int j = 0; j < cluster.size(); ++j) {
                    //for(int j=cluster.size()-1;j>=Math.max(0,cluster.size()-5);--j){
                        double dist = allFaceDistances.get(i)[cluster.get(j)];
                        if (dist < minDistance) {
                            minDistance = dist;
                            bestCluster = c;
                        }
                    }
                }
                //Log.i(TAG,"Clustering "+i+" minDist="+minDistance+" bestCluster="+bestCluster);
                if (bestCluster == -1 || minDistance > distanceThreshold) {
                    ArrayList<Integer> cluster = new ArrayList<>();
                    cluster.add(i);
                    facialClusters.add(cluster);
                } else
                    facialClusters.get(bestCluster).add(i);
            }
        }
        long endTime = SystemClock.uptimeMillis();
        Log.d(TAG, "Clustering for "+ finalCluster +" "+minFacialClusterSize+", time:" + Long.toString(endTime - startTime));

        SharedPreferences sharedPref = getSharedPreferences("android.pdmi_samsung.com.visual_preferences_preferences", Context.MODE_PRIVATE);
        String minDaysDifferenceBetweenPhotoMDatesStr = sharedPref.getString("min_days_between_photo_dates", "1");
        int minDaysDifferenceBetweenPhotoMDates=0;
        try {
            minDaysDifferenceBetweenPhotoMDates=Integer.parseInt(minDaysDifferenceBetweenPhotoMDatesStr);
        }catch(Exception ex){
            minDaysDifferenceBetweenPhotoMDates=0;
            Log.e(TAG,"Cannot convert "+minDaysDifferenceBetweenPhotoMDatesStr+" to integer. Use 0 as default");
        }

        Log.i(TAG,"After clustering of "+facesCount+" faces got "+facialClusters.size()+" facialClusters:");
        List<FacialClusterInfo> facialClusterInfo=new ArrayList<>();
        for(ArrayList<Integer> cluster:facialClusters){
            if(cluster.size()< MIN_FACIAL_CLUSTER_SIZE)
                continue;
            //Log.i(TAG,"Cluster: "+cluster.size());
            double avg_male_score=0;
            double avg_age=0;
            float[] avg_ethnicity_scores=null;
            Set<String> filenames=new TreeSet<>();
            //Collections.sort(cluster);
            long minTimeMillis=Long.MAX_VALUE,maxTimeMillis=0;
            for(int elementInd:cluster){
                FaceInfo element=allFaces.get(elementInd);
                //Log.d(TAG,"Element age: "+element.age+" isMale:"+element.isMale);
                avg_age+=element.faceData.age+element.ageDelta;
                avg_male_score+=element.faceData.maleScore;
                if(element.faceData.ethnicityScores!=null){
                    if (avg_ethnicity_scores==null)
                        avg_ethnicity_scores=new float[element.faceData.ethnicityScores.length];
                    for(int eind=0;eind<avg_ethnicity_scores.length;++eind){
                        avg_ethnicity_scores[eind]+=element.faceData.ethnicityScores[eind];
                    }
                }
                filenames.add(element.filename);
                if(photosTaken.containsKey(element.filename)) {
                    long timeInMillis=photosTaken.get(element.filename);
                    if(minTimeMillis>timeInMillis)
                        minTimeMillis=timeInMillis;
                    if(maxTimeMillis<timeInMillis)
                        maxTimeMillis=timeInMillis;
                }
            }
            avg_male_score/=cluster.size();
            avg_age/=cluster.size();

            int isFemale=FaceData.isMale(avg_male_score)?0:1;
            int age=(int)Math.round(avg_age);

            String ethnicity="";
            if (avg_ethnicity_scores!=null) {
                ethnicity=FaceData.getEthnicity(avg_ethnicity_scores);
            }
            //Log.i(TAG,"Cluster age: "+age+" "+avg_age+" isFemale:"+isFemale+" "+avg_male_score+" ethnicity:"+ethnicity);

            long days=(maxTimeMillis-minTimeMillis)/(24 * 60 * 60 * 1000);
            if(days>=minDaysDifferenceBetweenPhotoMDates) {
                facialClusterInfo.add(new FacialClusterInfo(cluster,age,isFemale,ethnicity,days,filenames));
            }
        }

        updateHighlevelDemography(facialClusterInfo);
    }


    public void PreferencesClick(View view) {
        FragmentManager fm = getFragmentManager();
        FragmentTransaction fragmentTransaction = fm.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_switch, preferencesFragment);
        fragmentTransaction.commit();
    }
    public void PhotosClick(View view) {
        FragmentManager fm = getFragmentManager();
        if(fm.getBackStackEntryCount()==0) {
            FragmentTransaction fragmentTransaction = fm.beginTransaction();
            fragmentTransaction.replace(R.id.fragment_switch, photosFragment);
            fragmentTransaction.addToBackStack(null);
            fragmentTransaction.commit();
        }
    }

    private String[] getRequiredPermissions() {
        try {
            PackageInfo info =
                   getPackageManager()
                            .getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }
    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            int status=ContextCompat.checkSelfPermission(this,permission);
            if (ContextCompat.checkSelfPermission(this,permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mNetworkreceiver);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS:
                Map<String, Integer> perms = new HashMap<String, Integer>();
                boolean allGranted = true;
                for (int i = 0; i < permissions.length; i++) {
                    perms.put(permissions[i], grantResults[i]);
                    if (grantResults[i] != PackageManager.PERMISSION_GRANTED)
                        allGranted = false;
                }
                // Check for ACCESS_FINE_LOCATION
                if (allGranted) {
                    // All Permissions Granted
                    init();
                } else {
                    // Permission Denied
                    Toast.makeText(MainActivity.this, "Some Permission is Denied", Toast.LENGTH_SHORT)
                            .show();
                    finish();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}
