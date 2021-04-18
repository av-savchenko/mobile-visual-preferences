package com.pdmi_samsung.android.visual_preferences;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.pdmi_samsung.android.visual_preferences.db.DetectionResultsWithFaces;
import com.pdmi_samsung.android.visual_preferences.db.DetectorData;
import com.pdmi_samsung.android.visual_preferences.db.ImageAnalysisResults;
import com.pdmi_samsung.android.visual_preferences.db.RectFloat;
import com.pdmi_samsung.android.visual_preferences.db.SceneData;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ServerProcessor {
    private static final String TAG = "ServerProcessor";
    private static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");
    private static String URL_BASE = "";
    public static boolean isEnabled = false;
    private OkHttpClient client ;
    private static ServerProcessor instance;
    private final Activity context;
    private String token;

    public static ServerProcessor getServerProcessor(final Activity context) {
        if (null == instance) {
            instance = new ServerProcessor(context);
        }
        return instance;
    }

    private ServerProcessor(Activity context) {
        this.context = context;
        SharedPreferences sharedPref = context.getSharedPreferences("android.pdmi_samsung.com.visual_preferences_preferences", Context.MODE_PRIVATE);
        isEnabled = sharedPref.getBoolean("use_server", false);
        URL_BASE = "".concat(sharedPref.getString("server_address", "")).concat(":").concat(sharedPref.getString("server_port", ""));
        try {
            client = new OkHttpClient.Builder()
                    //.sslSocketFactory(sslSocketFactory, trustManager)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(1, TimeUnit.MINUTES)
                    .readTimeout(1, TimeUnit.MINUTES)
                    .build();
            if(isEnabled)
                authenticate(sharedPref.getString("server_username", "defaultUser"), sharedPref.getString("server_password", "password"));
        }catch(Exception ex){
            Log.e(TAG, "Failed to connect to server", ex);
        }
    }

    public static void networkStateChanged(boolean networkState) {
        isEnabled = networkState;
        if (isEnabled) {
            try {
                SharedPreferences sharedPref = instance.context.getSharedPreferences("android.pdmi_samsung.com.visual_preferences_preferences", Context.MODE_PRIVATE);
                instance.authenticate(sharedPref.getString("server_username", "defaultUser"), sharedPref.getString("server_password", "password"));
            }catch(Exception ex){
                Log.e(TAG, "Failed to authenticate after network state changed", ex);
            }
        }
    }

    public static void updateUrl(String url){
        URL_BASE = url;
        SharedPreferences sharedPref = instance.context.getSharedPreferences("android.pdmi_samsung.com.visual_preferences_preferences", Context.MODE_PRIVATE);
        instance.authenticate(sharedPref.getString("server_username", "defaultUser"), sharedPref.getString("server_password", "password"));
    }

    private void authenticate(String user, String secret){
        String generated_token = "";
        try {
            Request request = new Request.Builder()
                    .url(URL_BASE.concat("/authenticate"))
                    .post(RequestBody.create(JSON, String.format("{\"user\": \"%s\", \"secret\": \"%s\"}", user, secret)))
                    .addHeader("content-type", "application/json; charset=utf-8")
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    //Toast.makeText(context, "Server could not be reached", Toast.LENGTH_SHORT).show();
                    Log.e("SERVER_ERROR", "could not generate token", e);
                    isEnabled = false;
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        assert response.body() != null;
                        JSONObject jsonResponse = new JSONObject(response.body().string());
                        if (assertSuccess(jsonResponse)) {
                            token = jsonResponse.getString("token");

                            isEnabled = true;
                            context.runOnUiThread(() -> {
                                Toast.makeText(context, "Server is ready to use", Toast.LENGTH_SHORT).show();
                            });

                        }
                    } catch (JSONException ex){
                        Log.e("SERVER_ERROR", "could not generate token", ex);
                        isEnabled = false;
                    }
                }
            });

        } catch (Exception ex){
            Log.e("SERVER_ERROR", "could not generate token", ex);
            isEnabled = false;
        }
    }

    private boolean assertSuccess(JSONObject response) throws JSONException{
        return response.getBoolean("success");
    }

    private String packImage(Bitmap bmp){
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        byte[] byteArray = byteStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }



    public ImageAnalysisResults getAnalysisResults(Bitmap bmp){
        try {
            Request request = new Request.Builder()
                    .url(URL_BASE.concat("/predict"))
                    .post(RequestBody.create(JSON, String.format("{\"token\": \"%s\", \"img\": \"%s\"}", token, packImage(bmp))))
                    .addHeader("content-type", "application/json; charset=utf-8")
                    .build();
            Response response = client.newCall(request).execute();
            assert response.body() != null;
            JSONObject jsonResp = new JSONObject(response.body().string());
            return processResult(jsonResp);
        } catch(Exception ex){
            Log.e("SERVER ERROR", "Could not process image on the server", ex);
            isEnabled = false;
            return null;
        }
    }

    private ImageAnalysisResults processResult(JSONObject result)  throws  JSONException{
        assertSuccess(result);
        JSONObject payload = result.getJSONObject("data");
        List<DetectorData> detections = new ArrayList<>();
        //List<RectFloat> faceRects=new ArrayList<>();
        //List<FaceData> faces=new ArrayList<>();
        String[] scenes=null;
        float[] sceneScores=null;
        if (payload.has("detections")) {
            JSONObject detectionsJson = payload.getJSONObject("detections").getJSONObject("objects");
            for (int i = 0; i < detectionsJson.getInt("num_detections"); ++i) {
                JSONArray box = detectionsJson.getJSONArray("detection_boxes").getJSONArray(i);
                String classLabel = detectionsJson.getJSONArray("detection_classes").getJSONObject(i).getString("name");
                int classId = detectionsJson.getJSONArray("detection_classes").getJSONObject(i).getInt("id");
                float score = (float) detectionsJson.getJSONArray("detection_scores").getDouble(i);
                RectFloat rect = new RectFloat((float) box.getDouble(1), (float) box.getDouble(0), (float) box.getDouble(3), (float) box.getDouble(2));
                if (!classLabel.equals("face")) { //should we detect faces here?
                    detections.add(new DetectorData(classLabel, score, rect));
                }
            }
        }
        else
            detections=null;
        SceneData scene=null;
        /*if (payload.has("topCategories")){
            JSONArray scenesJson = payload.getJSONArray("topCategories");
            topCategories = new String[scenesJson.length()];
            topScores = new float[scenesJson.length()];
            for (int i = 0; i < scenesJson.length(); ++i) {
                topCategories[i] = scenesJson.getJSONObject(i).getString("id");
                topScores[i] = (float) scenesJson.getJSONObject(i).getDouble("score");
            }
        }
        scene=new SceneData(topCategories, topScores);
        */

        if(detections==null){
            return null;
        }
        else {
            return new ImageAnalysisResults(
                    scene, new DetectionResultsWithFaces(detections, new ArrayList<>(),new ArrayList<>()));
        }
    }
}
