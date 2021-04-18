package com.pdmi_samsung.android.visual_preferences;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.location.Address;
import android.location.Geocoder;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;

import com.pdmi_samsung.android.visual_preferences.db.ClassificationForDetectedObjects;
import com.pdmi_samsung.android.visual_preferences.db.DetectionResults;
import com.pdmi_samsung.android.visual_preferences.db.DetectionResultsWithFaces;
import com.pdmi_samsung.android.visual_preferences.db.TopCategoriesData;
import com.pdmi_samsung.android.visual_preferences.db.DetectorData;
import com.pdmi_samsung.android.visual_preferences.db.FaceData;
import com.pdmi_samsung.android.visual_preferences.db.ImageAnalysisResults;
import com.pdmi_samsung.android.visual_preferences.db.EXIFData;
import com.pdmi_samsung.android.visual_preferences.db.RectFloat;
import com.pdmi_samsung.android.visual_preferences.db.SceneData;
import com.pdmi_samsung.android.visual_preferences.mtcnn.Box;
import com.pdmi_samsung.android.visual_preferences.mtcnn.MTCNNModel;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by avsavchenko.
 */
public class PhotoProcessor {
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "PhotoProcessor";

    private ScenesTfLiteClassifier scenesClassifier;
    private TfLiteObjectDetection detector;
    private AgeGenderTfLiteClassifier ageGenderClassifier;
    private TextClassifier textClassifier;
    private PetBreedsTfLiteClassifier petBreedsClassifier;
    private CarsTfLiteClassifier carsClassifier;
    private TfSportLogoDetectionModel sportLogoDetector;
    private MTCNNModel faceDetector;
    private ServerProcessor serverProcessor;

    private Set<String> privateFilenames = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private Set<String> selfiesFilenames = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private Map<String, List<FaceData>> demographyFaceDatas = new ConcurrentHashMap<>();

    private static final boolean detectOidCategories = true;

    private ConcurrentHashMap<String, SceneData> scenes = new ConcurrentHashMap<>();
    private static final String IMAGE_SCENES_FILENAME = "image_scenes";

    private ConcurrentHashMap<String, String> texts = new ConcurrentHashMap<>();
    private static final String IMAGE_TEXTS_FILENAME = "image_texts";

    private ConcurrentHashMap<String, DetectionResultsWithFaces> objects = new ConcurrentHashMap<>();
    private static final String IMAGE_OBJECTS_FILENAME = "image_objects";//detectOidCategories ? "image_objects2" : "image_objects1";

    private ConcurrentHashMap<String, ClassificationForDetectedObjects> classifiedObjects = new ConcurrentHashMap<>();
    private static final String IMAGE_CLASSIFIED_OBJECTS_FILENAME = "image_classified_objects";

    private ConcurrentHashMap<String, DetectionResults> logos = new ConcurrentHashMap<>();
    private static final String IMAGE_LOGOS_FILENAME = "image_logos";

    private ConcurrentHashMap<String, EXIFData> exifs = new ConcurrentHashMap<>();
    private static final String IMAGE_EXIF_FILENAME = "image_exif";

    private ConcurrentHashMap<String, DetectionResultsWithFaces> serverObjects = new ConcurrentHashMap<>();
    private static final String SERVER_OBJECTS_FILENAME = "server_objects";

    private static final boolean resetScenesModel = false;
    private static final boolean resetTextModel = false;
    private static final boolean resetDetectionModel = false;
    private static final boolean resetClassificationForDetectionsModel = false;
    private static final boolean resetLogosModel = false;
    private static final boolean resetServerObjectModel = false;


    private final Context context;
    private Geocoder geocoder;

    private Map<String, Long> photosTaken = new LinkedHashMap<>();
    private Map<String, Set<String>> date2files = new LinkedHashMap<>();
    private static final int MIN_PHOTOS_PER_DAY = 3;
    private int avgNumPhotosPerDay = MIN_PHOTOS_PER_DAY;


    private Map<String, SceneData> file2Scene = new LinkedHashMap<>();
    private List<Map<String, Long>> sceneClusters = new LinkedList<>();


    private PhotoProcessor(final Activity context) {
        this.context = context;
        geocoder = new Geocoder(context, Locale.US);//, Locale.getDefault());
        serverProcessor = ServerProcessor.getServerProcessor(context);
        privateFilenames.clear();
        initPhotosTaken();
        loadImageResults();
        loadModels(context.getAssets());
    }

    private static PhotoProcessor instance;

    public static PhotoProcessor getPhotoProcessor(final Activity context) {
        if (instance == null) {
            instance = new PhotoProcessor(context);
        }
        return instance;
    }

    public Map<String, SceneData> getFile2Scene() {
        return file2Scene;
    }

    public Set<String> getPrivateFilenames(){
        return privateFilenames;
    }
    public Set<String> getSelfiesFilenames(){
        return selfiesFilenames;
    }

    public String getDemographyFaceCategory(FaceData faceData){
        String category=null;
        for(Map.Entry<String,List<FaceData>> entry : demographyFaceDatas.entrySet()){
            if(entry.getValue().contains(faceData)){
                category=entry.getKey();
                break;
            }
        }
        return category;
    }
    public void setDemographyFaceDatas(String category, List<FaceData> faceDatas, boolean clearAll){
        if(clearAll)
            demographyFaceDatas.clear();
        demographyFaceDatas.put(category,faceDatas);
    }


    private void loadModels(final AssetManager assetManager) {
        try {
            ageGenderClassifier = new AgeGenderTfLiteClassifier(assetManager);
        } catch (IOException e) {
            Log.e(TAG, "Failed to load AgeGenderTfClassifier.", e);
        }
        try {
            scenesClassifier = new ScenesTfLiteClassifier(assetManager);
        } catch (IOException e) {
            Log.e(TAG, "Failed to load ScenesTfClassifier.", e);
        }
        try {
            detector = new TfLiteObjectDetection(assetManager,detectOidCategories);
        } catch (final IOException e) {
            Log.e(TAG, "Exception initializing TfObjectDetectionModel!", e);
        }
        try {
            petBreedsClassifier=new PetBreedsTfLiteClassifier(assetManager);
        } catch (final Exception e) {
            Log.e(TAG, "Exception initializing PetBreedsClassifier!", e);
        }
        try {
            carsClassifier=new CarsTfLiteClassifier(assetManager);
        } catch (final Exception e) {
            Log.e(TAG, "Exception initializing CarsTfLiteClassifier!", e);
        }
        try {
            textClassifier = new TextClassifier();
        } catch (final Exception e) {
            Log.e(TAG, "Exception initializing TextClassifier!", e);
        }
        try {
            faceDetector=MTCNNModel.Companion.create(assetManager);
        } catch (final Exception e) {
            Log.e(TAG, "Exception initializing MTCNNModel!", e);
        }
        try {
            sportLogoDetector = new TfSportLogoDetectionModel(assetManager);
        } catch (final IOException e) {
            Log.e(TAG, "Exception initializing TfSportLogoDetectionModel!", e);
        }
    }

    private static <V> ConcurrentHashMap<String, V> readObjectMap(Context context, String filename) {
        long startTime = SystemClock.uptimeMillis();
        ConcurrentHashMap<String, V> map = new ConcurrentHashMap<String, V>();
        try {
            ObjectInputStream is = new ObjectInputStream(context.openFileInput(filename));
            try {
                String key;
                key = (String) is.readObject();
                while ((key = (String) is.readObject()) != null) {
                    V val = (V) is.readObject();
                    map.put(key, val);
                }
            } catch (EOFException eofEx) {
                Log.e(TAG, "EOF loading image results " + filename + " current size=" + map.size() + " exception thrown: " + eofEx);
            }
            is.close();
        } catch (Exception e) {
            Log.e(TAG, "While loading image results " + filename + " exception thrown: ", e);
        }
        Log.w(TAG,"Size of "+filename+" is "+context.getFileStreamPath(filename).length()+" Timecost: " + Long.toString(SystemClock.uptimeMillis() - startTime));
        return map;
    }

    private static class AppendingObjectOutputStream extends ObjectOutputStream {

        public AppendingObjectOutputStream(OutputStream out) throws IOException {
            super(out);
        }

        @Override
        protected void writeStreamHeader() throws IOException {
            // do not write a header, but reset:
            // this line added after another question
            // showed a problem with the original
            reset();
        }

    }

    private static <V> void save(Context context, String filename, ConcurrentHashMap<String, V> map, String key, V val) {
        try {

            map.put(key, val);
            ObjectOutputStream os = new AppendingObjectOutputStream(context.openFileOutput(filename, Context.MODE_APPEND));
            os.writeObject(key);
            os.writeObject(val);
            os.close();
        } catch (Exception e) {
            Log.e(TAG, "While saving results for " + key + " exception thrown: ", e);
        }
    }

    private void loadImageResults() {
        //context.deleteFile("image_scenes");
        //context.deleteFile(IMAGE_LOGOS_FILENAME);
        /*
        context.deleteFile("image_objects2");
        context.deleteFile("image_scenes");
        context.deleteFile("image_texts1");
        */
        scenes = readObjectMap(context, IMAGE_SCENES_FILENAME);
        texts = readObjectMap(context, IMAGE_TEXTS_FILENAME);
        objects = readObjectMap(context, IMAGE_OBJECTS_FILENAME);
        classifiedObjects = readObjectMap(context, IMAGE_CLASSIFIED_OBJECTS_FILENAME);
        logos = readObjectMap(context, IMAGE_LOGOS_FILENAME);
        exifs = readObjectMap(context, IMAGE_EXIF_FILENAME);
        serverObjects = readObjectMap(context, SERVER_OBJECTS_FILENAME);

        if(resetServerObjectModel){
            serverObjects=new ConcurrentHashMap<>();
            context.deleteFile(SERVER_OBJECTS_FILENAME);
        }
        if (resetScenesModel) {
            scenes = new ConcurrentHashMap<>();
            context.deleteFile(IMAGE_SCENES_FILENAME);
        }
        if (resetTextModel) {
            texts = new ConcurrentHashMap<>();
            context.deleteFile(IMAGE_TEXTS_FILENAME);
        }
        if (resetDetectionModel) {
            objects = new ConcurrentHashMap<>();
            context.deleteFile(IMAGE_OBJECTS_FILENAME);
        }
        if(resetDetectionModel || resetClassificationForDetectionsModel){
            classifiedObjects = new ConcurrentHashMap<>();
            context.deleteFile(IMAGE_CLASSIFIED_OBJECTS_FILENAME);
        }
        if (resetLogosModel) {
            logos = new ConcurrentHashMap<>();
            context.deleteFile(IMAGE_LOGOS_FILENAME);
        }
        try {
            if (scenes.isEmpty()) {
                ObjectOutputStream os = new ObjectOutputStream(context.openFileOutput(IMAGE_SCENES_FILENAME, Context.MODE_PRIVATE));
                os.writeObject("topCategories");
                os.close();
            }
            if (texts.isEmpty()) {
                ObjectOutputStream os = new ObjectOutputStream(context.openFileOutput(IMAGE_TEXTS_FILENAME, Context.MODE_PRIVATE));
                os.writeObject("texts");
                os.close();
            }
            if (objects.isEmpty()) {
                ObjectOutputStream os = new ObjectOutputStream(context.openFileOutput(IMAGE_OBJECTS_FILENAME, Context.MODE_PRIVATE));
                os.writeObject("objects");
                os.close();
            }
            if (classifiedObjects.isEmpty()) {
                ObjectOutputStream os = new ObjectOutputStream(context.openFileOutput(IMAGE_CLASSIFIED_OBJECTS_FILENAME, Context.MODE_PRIVATE));
                os.writeObject("classifiedObjects");
                os.close();
            }
            if (logos.isEmpty()) {
                ObjectOutputStream os = new ObjectOutputStream(context.openFileOutput(IMAGE_LOGOS_FILENAME, Context.MODE_PRIVATE));
                os.writeObject("logos");
                os.close();
            }
            //exifs.clear();
            if (exifs.isEmpty()) {
                ObjectOutputStream os = new ObjectOutputStream(context.openFileOutput(IMAGE_EXIF_FILENAME, Context.MODE_PRIVATE));
                os.writeObject("exifs");
                os.close();
            }
            if (serverObjects.isEmpty()) {
                ObjectOutputStream os = new ObjectOutputStream(context.openFileOutput(SERVER_OBJECTS_FILENAME, Context.MODE_PRIVATE));
                os.writeObject("serverObjects");
                os.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "While creating empty files exception thrown: ", e);
        }
    }

    private synchronized SceneData classifyScenes(Bitmap bmp, StringBuilder text) {
        long startTime = SystemClock.uptimeMillis();
        Bitmap scenesBitmap = Bitmap.createScaledBitmap(bmp, scenesClassifier.getImageSizeX(), scenesClassifier.getImageSizeY(), false);
        SceneData scene = (SceneData) scenesClassifier.classifyFrame(scenesBitmap);
        long sceneTimeCost = SystemClock.uptimeMillis() - startTime;
        Log.i(TAG, "Timecost to run scene model inference: " + Long.toString(sceneTimeCost));
        text.append("Scenes:").append(sceneTimeCost).append(" ms\n");
        return scene;
    }

    private static String DOG_CLASS="dog";
    private static String CAT_CLASS="cat";
    private static List<String> CAR_CLASSES = Arrays.asList (new String[]{"car","van"});

    private static String FACE_CLASS="face";
    private static float IOU_THRESHOLD=0.3f;
    private static float INTERSECTION_THRESHOLD=0.7f;
    private static int INVERSE_FACIAL_PART=3;
    private static float MIN_FACE_SIZE=0.03f;

    private Pair<List<DetectorData>,List<DetectorData>> postprocessDetectionResults(Bitmap bmp, List<DetectorData> recognitions){
        List<DetectorData> postprocessedDetections = new ArrayList<>();
        List<DetectorData> faces = new ArrayList<>();

        if(true) {
            Bitmap resizedBitmap=bmp;
            double minSize=600.0;
            double scale=Math.min(bmp.getWidth(),bmp.getHeight())/minSize;
            if(scale>1.0) {
                resizedBitmap = Bitmap.createScaledBitmap(bmp, (int)(bmp.getWidth()/scale), (int)(bmp.getHeight()/scale), false);
            }
            Vector<Box> bboxes = faceDetector.detectFaces(resizedBitmap, 96);//(int)(bmp.getWidth()*MIN_FACE_SIZE));
            for (Box box : bboxes) {
                faces.add(new DetectorData(FACE_CLASS, 1.0f,
                        new RectFloat(1.0f * box.left() / resizedBitmap.getWidth(), 1.0f * box.top() / resizedBitmap.getHeight(),
                                1.0f * box.right() / resizedBitmap.getWidth(), 1.0f * box.bottom() / resizedBitmap.getHeight())));
            }
        }

        for (DetectorData rec : recognitions) {
            if (rec.title.equals(FACE_CLASS) && (rec.location.right-rec.location.left)>=MIN_FACE_SIZE) {
                boolean found=false;
                for(DetectorData face: faces){
                    if(face.location.getIoU(rec.location)>IOU_THRESHOLD){
                        found=true;
                        break;
                    }
                }
                if(!found){
                    faces.add(rec);
                }
            }
            else if(detector.getHighLevelCategory(rec.title)>=0) {
                postprocessedDetections.add(rec);
            }
        }

        List<DetectorData> humans = new ArrayList<>();
        List<DetectorData> largeObjects = new ArrayList<>();
        for (DetectorData rec : recognitions) {
            if(detector.getHighLevelCategory(rec.title)==-2) {
                boolean found=false;
                for(DetectorData human: humans){
                    if(human.location.getIoU(rec.location)>IOU_THRESHOLD){
                        found=true;
                        break;
                    }
                }
                if(!found){
                    for(DetectorData face: faces){
                        float intersection2FaceSquareRatio=face.location.getIntersectionSquare(rec.location)/face.location.square();
                        if(intersection2FaceSquareRatio>INTERSECTION_THRESHOLD){
                            found=true;
                            break;
                        }
                    }
                    if(!found) {
                        humans.add(rec);
                    }
                }
            }
            else if(rec.title.contains("table")){
                largeObjects.add(rec);
            }
        }


        for(DetectorData human: humans){
            RectFloat bbox_f = human.location;
            Rect bbox = new Rect((int) (bbox_f.left * bmp.getWidth()), (int) (bbox_f.top * bmp.getHeight()),
                    (int) (bbox_f.right * bmp.getWidth()), (int) (bbox_f.bottom * bmp.getHeight()));

            if(bbox.left<0)
                bbox.left=0;
            if(bbox.top<0)
                bbox.top=0;
            if(bbox.right>=bmp.getWidth()){
                bbox.right=bmp.getWidth()-1;
            }
            if(bbox.bottom>=bmp.getHeight()){
                bbox.bottom=bmp.getHeight()-1;
            }
            Bitmap bodyBitmap = Bitmap.createBitmap(bmp, bbox.left, bbox.top, bbox.width(), bbox.height()/INVERSE_FACIAL_PART);
            List<DetectorData> bodyDetections = detector.recognizeImage(bodyBitmap);

            for(DetectorData rec : bodyDetections){
                rec.location.left=bbox_f.left+rec.location.left*(bbox_f.right-bbox_f.left);
                rec.location.right=bbox_f.left+rec.location.right*(bbox_f.right-bbox_f.left);
                rec.location.top=bbox_f.top+rec.location.top*(bbox_f.bottom-bbox_f.top)/INVERSE_FACIAL_PART;
                rec.location.bottom=bbox_f.top+rec.location.bottom*(bbox_f.bottom-bbox_f.top)/INVERSE_FACIAL_PART;
                if (rec.title.equals(FACE_CLASS)&& (rec.location.right-rec.location.left)>=MIN_FACE_SIZE) {
                    boolean found=false;
                    for(DetectorData face: faces){
                        if(face.location.getIoU(rec.location)>IOU_THRESHOLD){
                            found=true;
                            break;
                        }
                    }
                    if(!found){
                        faces.add(rec);
                    }
                }
                else if(detector.getHighLevelCategory(rec.title)>=0) {
                    postprocessedDetections.add(rec);
                }
            }
        }
        return Pair.create(postprocessedDetections,faces);
    }
    private Pair<List<DetectorData>,List<DetectorData>> postprocessDetectionResultsWithFaceDetection(Bitmap bmp, List<DetectorData> recognitions){
        List<DetectorData> postprocessedDetections = new ArrayList<>();
        List<DetectorData> faces = new ArrayList<>();
        Vector<Box> bboxes=faceDetector.detectFaces(bmp,96);//(int)(bmp.getWidth()*MIN_FACE_SIZE));
        for(Box box : bboxes){
            faces.add(new DetectorData(FACE_CLASS,1.0f,
                    new RectFloat(1.0f*box.left()/bmp.getWidth(),1.0f*box.top()/bmp.getHeight(),
                            1.0f*box.right()/bmp.getWidth(),1.0f*box.bottom()/bmp.getHeight())));
        }

        for (DetectorData rec : recognitions) {
            if (rec.title.equals(FACE_CLASS) && (rec.location.right-rec.location.left)>=MIN_FACE_SIZE) {
                boolean found=false;
                for(DetectorData face: faces){
                    if(face.location.getIoU(rec.location)>IOU_THRESHOLD){
                        found=true;
                        break;
                    }
                }
                if(!found){
                    faces.add(rec);
                }
            }
            else if(detector.getHighLevelCategory(rec.title)>=0) {
                postprocessedDetections.add(rec);
            }
        }
        return Pair.create(postprocessedDetections,faces);
    }
    private Bitmap cropBitmap(Bitmap bmp, RectFloat bbox_f){
        Rect bbox = new Rect((int) (bbox_f.left * bmp.getWidth()), (int) (bbox_f.top * bmp.getHeight()),
                (int) (bbox_f.right * bmp.getWidth()), (int) (bbox_f.bottom * bmp.getHeight()));

        int dw = 0; //Math.max(10,bbox.width() / 8);
        int dh = Math.max(10,bbox.height() / 8); //15;//Math.max(10,bbox.height() / 8);
        int x = bbox.left - dw;
        if (x < 0)
            x = 0;
        int y = bbox.top - dh;
        if (y < 0)
            y = 0;
        int w = bbox.width() + 2 * dw;
        if (x + w >= bmp.getWidth())
            w = bmp.getWidth() - x - 1;
        int h = bbox.height();// + 2 * dh;
        if (y + h >= bmp.getHeight())
            h = bmp.getHeight() - y - 1;

        return Bitmap.createBitmap(bmp, x, y, w, h);
    }
    private synchronized DetectionResultsWithFaces detectObjects(Bitmap bmp, StringBuilder text) {
        long startTime = SystemClock.uptimeMillis();
        Bitmap resizedBitmap=detector.resizeBitmap(bmp);
        List<DetectorData> recognitions = detector.recognizeImage(resizedBitmap);
        long detectionTimeCost = SystemClock.uptimeMillis() - startTime;
        Log.d(TAG, "Timecost to detect objects: " + Long.toString(detectionTimeCost));

        Pair<List<DetectorData>,List<DetectorData>> detections_faces;
        if(detectOidCategories)
            detections_faces=postprocessDetectionResults(bmp,recognitions);
        else
            detections_faces=postprocessDetectionResultsWithFaceDetection(resizedBitmap,recognitions);

        detectionTimeCost = SystemClock.uptimeMillis() - startTime;
        Log.d(TAG, "Timecost to detect and postprocess objects: " + Long.toString(detectionTimeCost));
        text.append("Detection: ").append(detectionTimeCost).append(" ms\n");

        List<DetectorData> detections = detections_faces.first;
        List<RectFloat> faceRects = new ArrayList<>();
        List<FaceData> faces = new ArrayList<>();
        for (DetectorData rec : detections_faces.second) {
            RectFloat bbox_f = rec.location;
            faceRects.add(bbox_f);
            Bitmap faceBitmap = cropBitmap(bmp, bbox_f);
            Bitmap resultBitmap = Bitmap.createScaledBitmap(faceBitmap, ageGenderClassifier.getImageSizeX(), ageGenderClassifier.getImageSizeY(), false);

            startTime = SystemClock.uptimeMillis();
            FaceData faceFound = (FaceData) ageGenderClassifier.classifyFrame(resultBitmap);
            long faceTimeCost = SystemClock.uptimeMillis() - startTime;
            Log.i(TAG, "Timecost to run face model inference: " + Long.toString(faceTimeCost));
            faces.add(faceFound);
            text.append("face: ").append(faceTimeCost).append(" ms\n");
        }

        DetectionResultsWithFaces objects = new DetectionResultsWithFaces(detections, faceRects, faces);
        return objects;
    }

    private ClassificationForDetectedObjects classifyDetectedObjects(Bitmap bmp, DetectionResultsWithFaces objs, StringBuilder text) {
        List<TopCategoriesData> concreteTypes=new ArrayList<>();
        classifyPets(objs.detections,bmp,text,concreteTypes);
        classifyCars(objs.detections,bmp,text,concreteTypes);
        return new ClassificationForDetectedObjects(concreteTypes);
    }
    private void classifyPets(List<DetectorData> recognitions, Bitmap bmp, StringBuilder text, List<TopCategoriesData> concreteTypes){
        for (DetectorData rec : recognitions) {
            if(rec.confidence>= DetectionResultsWithFaces.DETECTION_THRESHOLD) {
                boolean isDog = rec.title.equalsIgnoreCase(DOG_CLASS);
                boolean isCat = rec.title.equalsIgnoreCase(CAT_CLASS);
                if (isDog || isCat) {
                    Bitmap petBitmap = cropBitmap(bmp, rec.location);
                    Bitmap resultBitmap = Bitmap.createScaledBitmap(petBitmap, petBreedsClassifier.getImageSizeX(), petBreedsClassifier.getImageSizeY(), false);
                    long startTime = SystemClock.uptimeMillis();
                    petBreedsClassifier.setDogRecognized(isDog);
                    TopCategoriesData petFound = (TopCategoriesData) petBreedsClassifier.classifyFrame(resultBitmap);
                    long breedTimeCost = SystemClock.uptimeMillis() - startTime;
                    Log.i(TAG, "Timecost to run pet breed model inference: " + Long.toString(breedTimeCost));
                    text.append("pet: ").append(breedTimeCost).append(" ms\n");
                    concreteTypes.add(petFound);
                }
            }
        }
    }
    private void classifyCars(List<DetectorData> recognitions, Bitmap bmp, StringBuilder text, List<TopCategoriesData> concreteTypes){
        for (DetectorData rec : recognitions) {
            if(rec.confidence>= DetectionResultsWithFaces.DETECTION_THRESHOLD) {
                if (CAR_CLASSES.contains(rec.title.toLowerCase())) {
                    Bitmap carBitmap = cropBitmap(bmp, rec.location);
                    Bitmap resultBitmap = Bitmap.createScaledBitmap(carBitmap, carsClassifier.getImageSizeX(), carsClassifier.getImageSizeY(), false);
                    long startTime = SystemClock.uptimeMillis();
                    TopCategoriesData carFound = (TopCategoriesData) carsClassifier.classifyFrame(resultBitmap);
                    long carTimeCost = SystemClock.uptimeMillis() - startTime;
                    Log.i(TAG, "Timecost to run car model inference: " + Long.toString(carTimeCost)+" "+carFound);
                    text.append("car: ").append(carTimeCost).append(" ms\n");
                    concreteTypes.add(carFound);
                }
            }
        }
    }

    private static List<String> SPORT_CLASSES = Arrays.asList (new String[]{"hockey","basketball court","sports"});//,"football","arena"
    private synchronized DetectionResults detectLogos(Bitmap bmp, SceneData scene, StringBuilder text) {
        List<String> topCategories=scene.getMostReliableCategories();

        List<DetectorData> sportLogoRecognitions = null;
        if(!Collections.disjoint(SPORT_CLASSES,topCategories)) {
            long startTime = SystemClock.uptimeMillis();
            sportLogoRecognitions = sportLogoDetector.recognizeImage(bmp);
            long detectionTimeCost = SystemClock.uptimeMillis() - startTime;
            Log.d(TAG, "Timecost to detect sport logos: " + Long.toString(detectionTimeCost));
            text.append("Logo detection: ").append(detectionTimeCost).append(" ms\n");
        }
        DetectionResults logo = new DetectionResults(sportLogoRecognitions);
        return logo;
    }


    private synchronized String detectText(Bitmap bmp, StringBuilder text) {
        long startTime = SystemClock.elapsedRealtime();
        String recognizedText = textClassifier.detectText(bmp);
        long textTimeCost = SystemClock.elapsedRealtime() - startTime;
        Log.i(TAG, "Timecost for text recognition: " + Long.toString(textTimeCost));
        text.append("text: ").append(textTimeCost).append(" ms\n");
        return recognizedText;
    }


    public ImageAnalysisResults getImageAnalysisResultsWOCache(String filename, Bitmap bmp, StringBuilder text) {
        if (bmp == null)
            bmp = loadBitmap(filename);
        SceneData scene = classifyScenes(bmp, text);
        String textRecognized = detectText(bmp, text);
        DetectionResultsWithFaces objs = detectObjects(bmp, text);
        EXIFData exifData=getEXIFData(filename);
        ClassificationForDetectedObjects objectTypes = classifyDetectedObjects(bmp, objs, text);
        DetectionResults logo = detectLogos(bmp, scene, text);
        SceneData smoothedScene=smoothScene(filename,scene);
        ImageAnalysisResults res = new ImageAnalysisResults(filename, smoothedScene,scene, textRecognized, objs,null,objectTypes, logo, exifData);
        return res;
    }


    public ImageAnalysisResults getImageAnalysisResults(String filename, Bitmap bmp, StringBuilder text,boolean needScene,
                                                        boolean needText,boolean needObjects,boolean needServerObjects,
                                                        boolean needLogos)
    {
        String key = getKey(filename);

        SceneData scene=null;
        if (!scenes.containsKey(key)) {
            if(needScene) {
                if (bmp == null)
                    bmp = loadBitmap(filename);
                scene = classifyScenes(bmp, text);
                save(context, IMAGE_SCENES_FILENAME, scenes, key, scene);
            }
        }
        else
            scene=scenes.get(key);

        String textRecognized=null;
        if (!texts.containsKey(key)) {
            if(needText) {
                if (bmp == null)
                    bmp = loadBitmap(filename);
                textRecognized = detectText(bmp, text);
                save(context, IMAGE_TEXTS_FILENAME, texts, key, textRecognized);
            }
        }
        else
            textRecognized=texts.get(key);

        DetectionResultsWithFaces objs=null;
        if (!objects.containsKey(key)) {
            if (needObjects) {
                if (bmp == null)
                    bmp = loadBitmap(filename);
                objs = detectObjects(bmp, text);
                save(context, IMAGE_OBJECTS_FILENAME, objects, key, objs);
            }
        } else
            objs = objects.get(key);

        EXIFData exifData=getEXIFData(filename);

        //ideally should asynchronously retrieve object from server in background
        DetectionResultsWithFaces serverObjs=null;
        if (!serverObjects.containsKey(key)) {
            if (needServerObjects && ServerProcessor.isEnabled) {
                SharedPreferences sharedPref = ((Activity)context).getSharedPreferences("android.pdmi_samsung.com.visual_preferences_preferences", Context.MODE_PRIVATE);
                boolean send_private_photos_to_server=sharedPref.getBoolean("send_private_photos_to_server",false);
                if(send_private_photos_to_server || !privateFilenames.contains(filename)) {
                    if (bmp == null)
                        bmp = loadBitmap(filename);

                    Bitmap resizedBitmap = detector.resizeBitmap(bmp);

                    ImageAnalysisResults res = serverProcessor.getAnalysisResults(resizedBitmap);
                    if (res != null && res.serverObjects != null) {
                        serverObjs=res.serverObjects;
                        save(context, SERVER_OBJECTS_FILENAME, serverObjects, key, serverObjs);
                    }
                }
            }
        }
        else
            serverObjs = serverObjects.get(key);

        ClassificationForDetectedObjects objectTypes=null;
        if (!classifiedObjects.containsKey(key)) {
            if (needObjects) {
                if (bmp == null)
                    bmp = loadBitmap(filename);
                objectTypes = classifyDetectedObjects(bmp, serverObjs!=null?serverObjs:objs, text);
                save(context, IMAGE_CLASSIFIED_OBJECTS_FILENAME, classifiedObjects, key, objectTypes);
            }
        } else
            objectTypes = classifiedObjects.get(key);

        DetectionResults logo=null;
        if (!logos.containsKey(key)) {
            if (needLogos) {
                if (bmp == null)
                    bmp = loadBitmap(filename);
                logo = detectLogos(bmp, scene, text);
                save(context, IMAGE_LOGOS_FILENAME, logos, key, logo);
            }
        } else
            logo = logos.get(key);

        SceneData smoothedScene=smoothScene(filename,scene);

        ImageAnalysisResults res = new ImageAnalysisResults(filename, smoothedScene,scene, textRecognized, objs,serverObjs,objectTypes, logo, exifData);
        return res;
    }


    public ImageAnalysisResults getImageAnalysisResults(String filename, boolean needServerConnection) {
        StringBuilder text = new StringBuilder();
        return getImageAnalysisResults(filename,null,text,true,true,true,needServerConnection,true);
        //return getImageAnalysisResultsWOCache(filename,null,text);
    }

    private String getLocationDescription(double latitude, double longitude){
        String description=null;
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude,1);
            StringBuilder text=new StringBuilder();
            if (addresses != null) {
                Address returnedAddress = addresses.get(0);
                String city=returnedAddress.getLocality();
                if (city!=null) {
                    text.append(city);
                }
                String countryName=returnedAddress.getCountryName();
                if(countryName!=null)
                    text.append(", ").append(countryName);
                /*
                for (int i = 0; i <= returnedAddress.getMaxAddressLineIndex(); i++) {
                    text.append(returnedAddress.getAddressLine(i));
                    if (i < returnedAddress.getMaxAddressLineIndex())
                        text.append(",");
                }*/
                description=text.toString();
                if(description.equals(""))
                    description=null;
            }
        }catch(Exception ex){
            //ignore
        }
        return description;
    }
    private String getKey(String filename){
        long dateModified = new File(filename).lastModified();
        String key = filename+"_"+dateModified;
        return key;
    }
    public EXIFData getEXIFData(String filename){
        String key =getKey(filename);
        EXIFData exifData;
        if(!exifs.containsKey(key)){
            exifData=new EXIFData(filename);
            if (exifData.latitude!=0 && exifData.longitude!=0) {
                exifData.description=getLocationDescription(exifData.latitude, exifData.longitude);
            }
            save(context, IMAGE_EXIF_FILENAME, exifs, key, exifData);
        }
        else{
            exifData=exifs.get(key);
            if (exifData.description==null && exifData.latitude!=0 && exifData.longitude!=0) {
                exifData.description = getLocationDescription(exifData.latitude, exifData.longitude);
                if(exifData.description!=null)
                    save(context, IMAGE_EXIF_FILENAME, exifs, key,exifData);
            }
        }
        return exifData;
    }
    public Bitmap loadBitmap(String fname) {
        Bitmap bmp = null;
        try {
            bmp = BitmapFactory.decodeFile(fname);
            EXIFData exifData=getEXIFData(fname);
            Matrix mat = new Matrix();
            switch (exifData.orientation) {
                case 6:
                    mat.postRotate(90);
                    break;
                case 3:
                    mat.postRotate(180);
                    break;
                case 8:
                    mat.postRotate(270);
                    break;
            }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), mat, true);
        } catch (Exception e) {
            Log.e(TAG, "While loading image" + fname + " exception thrown: ", e);
        }
        return bmp;
    }


    public int getHighLevelCategory(String category) {
        int res = scenesClassifier.getHighLevelCategory(category);
        if (res == -1)
            res = detector.getHighLevelCategory(category);
        return res;
    }

    private static DateFormat df=java.text.DateFormat.getDateInstance(DateFormat.MEDIUM,Locale.US);
    private String getDateFromTimeInMillis(long timeInMillis){
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMillis);
        return " "+df.format(calendar.getTime());
    }
    private void initPhotosTaken() {
        final String[] projection = {MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_TAKEN};
        //String path= Environment.getExternalStorageDirectory().toString();//+"/DCIM/Camera";
        final String selection = null;//MediaStore.Images.Media.BUCKET_ID +" = ?";
        final String[] selectionArgs = null;//{String.valueOf(path.toLowerCase().hashCode())};
        photosTaken.clear();
        try {
            final Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, MediaStore.Images.ImageColumns.DATE_TAKEN + " DESC");
            if (cursor.moveToFirst()) {
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                int dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN);
                do {
                    String data = cursor.getString(dataColumn);
                    Long dateCreated = Long.parseLong(cursor.getString(dateColumn));
                    photosTaken.put(data,dateCreated);

                    String strDate =getDateFromTimeInMillis(dateCreated);
                    if (!date2files.containsKey(strDate))
                        date2files.put(strDate, new HashSet<>());
                    date2files.get(strDate).add(data);
                    //Log.i(TAG, "load image: "+data);
                }
                while (cursor.moveToNext());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Exception thrown: " + e);
        }

        avgNumPhotosPerDay=0;
        for(Set<String> files : date2files.values())
            avgNumPhotosPerDay+=files.size();

        if(!date2files.isEmpty())
            avgNumPhotosPerDay/=date2files.size();
        if (avgNumPhotosPerDay<MIN_PHOTOS_PER_DAY)
            avgNumPhotosPerDay=MIN_PHOTOS_PER_DAY;
    }

    public Map<String,Long> getCameraImages() {
        return photosTaken;
    }

    private synchronized SceneData smoothScene(String filename, SceneData originalScene) {
        if (file2Scene.containsKey(filename))
            return file2Scene.get(filename);
        Map<String,Long> bestSceneCluster=null;
        if (photosTaken.containsKey(filename)) {
            long timeInMillis = photosTaken.get(filename);
            double bestDist=1000000;
            for(Map<String,Long> sceneCluster : sceneClusters){
                for (Map.Entry<String,Long> entry : sceneCluster.entrySet()) {
                    float timeDiff = Math.abs(entry.getValue() - timeInMillis) / (3600 * 1000.f); //in hours
                    if (timeDiff < 1) {
                        //if(topCategories.containsKey(prevKey))
                        {
                            SceneData prevScene = scenes.get(entry.getKey());
                            double d = originalScene.distance(prevScene); //threshold ~0.003
                            //d = d + 0.01 * timeDiff;
                            if (d < bestDist) {
                                bestSceneCluster = sceneCluster;
                                bestDist = d;
                            }
                        }
                    }
                }
            }
            if(bestDist>0.003){
                bestSceneCluster=new LinkedHashMap<>();
                sceneClusters.add(bestSceneCluster);
            }
            String key = getKey(filename);
            bestSceneCluster.put(key,timeInMillis);
        }
        SceneData res=originalScene;
        file2Scene.put(filename, res);
        if (bestSceneCluster!=null && bestSceneCluster.size()>1){
            TreeMap<String,Float> scene2Score=new TreeMap<>();
            double sum=0;
            for(Map.Entry<String,Integer> entry:scenesClassifier.sceneLabels2Index.entrySet()){
                double v=1;
                for (String similarSceneFile : bestSceneCluster.keySet()){
                    float score=scenes.get(similarSceneFile).scenes.scores[entry.getValue()];
                    /*if(score<0.0001f)
                        score=0.0001f;*/
                    v*=score;
                }
                v=Math.pow(v,1.0/bestSceneCluster.size());
                sum+=v;
                scene2Score.put(entry.getKey(),(float)v);
            }
            for(String scene:scene2Score.keySet()){
                scene2Score.put(scene,(float)(scene2Score.get(scene)/sum));
            }

            TreeMap<String,Float> event2Score=new TreeMap<>();
            for(Map.Entry<String,Integer> entry:scenesClassifier.eventLabels2Index.entrySet()){
                double v=0;
                for (String similarSceneFile : bestSceneCluster.keySet()){
                    float score=scenes.get(similarSceneFile).events.scores[entry.getValue()];
                    v+=score;
                }
                v/=bestSceneCluster.size();
                event2Score.put(entry.getKey(),(float)v);
            }
            res=new SceneData(scenesClassifier.sceneLabels2Index,scene2Score,scenesClassifier.eventLabels2Index,event2Score);
            for (String similarSceneFile : bestSceneCluster.keySet()) {
                String file=similarSceneFile.substring(0,similarSceneFile.lastIndexOf('_'));
                file2Scene.put(file, res);
            }
        }
        return res;
    }

    private void addDayEvent(List<Map<String, Map<String, Set<String>>>> eventTimePeriod2Files, String category, String timePeriod, Set<String> filenames){
        int highLevelCategory=getHighLevelCategory(category);
        if(highLevelCategory>=0) {
            Map<String,Map<String,Set<String>>> histo=eventTimePeriod2Files.get(highLevelCategory);
            if(!histo.containsKey(category))
                histo.put(category,new TreeMap<>(Collections.reverseOrder()));
            histo.get(category).put(timePeriod,filenames);

            //Log.d(TAG,"EVENTS!!! "+timePeriod+":"+category+" ("+highLevelCategory+"), "+filenames.size());
        }
    }
    public void updateSceneInEvents(List<Map<String, Map<String, Set<String>>>> eventTimePeriod2Files, String filename) {
        if (photosTaken.containsKey(filename)) {
            String strDate=getDateFromTimeInMillis(photosTaken.get(filename));
            if (date2files.containsKey(strDate)){
                Set<String> files=date2files.get(strDate);

                if(files.size()<avgNumPhotosPerDay)
                    return;
                boolean allResultsAvailable=true;
                for (String f : files)
                    if(!file2Scene.containsKey(f)) {
                        allResultsAvailable=false;
                        break;
                    }
                if(!allResultsAvailable)
                    return;

                Map<String,ArrayList<String>> dayEventFiles=new TreeMap<>();

                for(Map.Entry<String,Integer> entry:scenesClassifier.sceneLabels2Index.entrySet()) {
                    int i=entry.getValue();
                    float avgScore=0;
                    Set<String> scene_filenames=new TreeSet<>();
                    for(String f: files) {
                        float score=file2Scene.get(f).scenes.scores[i];
                        avgScore+=score;
                        if(score>=SceneData.SCENE_DISPLAY_THRESHOLD)
                            scene_filenames.add(f);
                    }
                    avgScore/=files.size();

                    if(avgScore>=SceneData.SCENE_CATEGORY_THRESHOLD && scene_filenames.size()>=2)
                        addDayEvent(eventTimePeriod2Files,entry.getKey(), strDate,scene_filenames);
                }

                for(Map.Entry<String,Integer> entry:scenesClassifier.eventLabels2Index.entrySet()) {
                    int i=entry.getValue();
                    float avgScore=0;
                    Set<String> scene_filenames=new TreeSet<>();
                    for(String f: files) {
                        float score=file2Scene.get(f).events.scores[i];
                        avgScore+=score;
                        if(score>=SceneData.EVENT_DISPLAY_THRESHOLD)
                            scene_filenames.add(f);
                    }
                    avgScore/=files.size();

                    if((avgScore>=SceneData.EVENT_CATEGORY_THRESHOLD && scene_filenames.size()>=2) ||
                            scene_filenames.size()>=4)
                        addDayEvent(eventTimePeriod2Files,entry.getKey(), strDate,scene_filenames);
                }
            }
        }
    }
/*
    public List<Map<String, Map<String, Set<String>>>> updateSceneInEvents(int numCategories) {
        List<Map<String, Map<String, Set<String>>>> eventTimePeriod2Files=new ArrayList<>();
        for(int i=0;i<numCategories;++i){
            eventTimePeriod2Files.add(new HashMap<>());
        }

        for(String strDate:date2files.keySet()) {
            Set<String> files = date2files.get(strDate);

            if (files.size() < avgNumPhotosPerDay)
                continue;
            boolean allResultsAvailable = true;
            for (String f : files)
                if (!file2Scene.containsKey(f)) {
                    allResultsAvailable = false;
                    break;
                }
            if (!allResultsAvailable)
                continue;

            Map<String, ArrayList<String>> dayEventFiles = new TreeMap<>();

            for (Map.Entry<String, Integer> entry : scenesClassifier.sceneLabels2Index.entrySet()) {
                int i = entry.getValue();
                float avgScore = 0;
                Set<String> scene_filenames = new TreeSet<>();
                for (String f : files) {
                    float score = file2Scene.get(f).scenes.topScores[i];
                    avgScore += score;
                    if (score >= SceneData.SCENE_DISPLAY_THRESHOLD)
                        scene_filenames.add(f);
                }
                avgScore /= files.size();

                if (avgScore >= SceneData.SCENE_CATEGORY_THRESHOLD && scene_filenames.size() >= 2)
                    addDayEvent(eventTimePeriod2Files, entry.getKey(), strDate, scene_filenames);
            }

            for (Map.Entry<String, Integer> entry : scenesClassifier.eventLabels2Index.entrySet()) {
                int i = entry.getValue();
                float avgScore = 0;
                Set<String> scene_filenames = new TreeSet<>();
                for (String f : files) {
                    float score = file2Scene.get(f).events.topScores[i];
                    avgScore += score;
                    if (score >= SceneData.EVENT_DISPLAY_THRESHOLD)
                        scene_filenames.add(f);
                }
                avgScore /= files.size();

                if (avgScore >= SceneData.EVENT_CATEGORY_THRESHOLD && scene_filenames.size() >= 2)
                    addDayEvent(eventTimePeriod2Files, entry.getKey(), strDate, scene_filenames);
            }
        }
        return eventTimePeriod2Files;
    }
    */
}
