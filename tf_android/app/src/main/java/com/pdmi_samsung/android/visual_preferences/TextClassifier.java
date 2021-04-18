package com.pdmi_samsung.android.visual_preferences;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.text.FirebaseVisionText;
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by avsavchenko.
 */

public class TextClassifier {
    private static final String TAG = "TextClassifier";
    private final FirebaseVisionTextRecognizer textRecognizer;

    public TextClassifier() {
        textRecognizer = FirebaseVision.getInstance().getOnDeviceTextRecognizer();
    }

    private static int MAX_SIZE=1280;
    public String detectText(Bitmap bmp){
        StringBuilder str = new StringBuilder();
        CountDownLatch doneSignal = new CountDownLatch(1);

        int width,height;
        if(bmp.getWidth()>bmp.getHeight()){
            width=MAX_SIZE;
            height=bmp.getHeight()*MAX_SIZE/bmp.getWidth();
        }
        else{
            height=MAX_SIZE;
            width=bmp.getWidth()*MAX_SIZE/bmp.getHeight();
        }
        Bitmap scaledBitmap=Bitmap.createScaledBitmap(bmp,width, height,false);
        /*
        int scale=1;
        for(int maxSize=Math.max(bmp.getWidth(),bmp.getHeight());maxSize>MAX_SIZE;maxSize/=2,scale*=2)
            ;
        Bitmap scaledBitmap=Bitmap.createScaledBitmap(bmp,bmp.getWidth()/scale,bmp.getHeight()/scale,false);
        */
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(scaledBitmap);
        textRecognizer.processImage(image)
                .addOnSuccessListener(new OnSuccessListener<FirebaseVisionText>() {
                    @Override
                    public void onSuccess(FirebaseVisionText result) {
                        String resultText = result.getText();
                        Log.i(TAG, "text recognized:" + resultText);
                        str.append(resultText);
                        doneSignal.countDown();
                    }
                })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.e(TAG, "text not recognized:" + e);
                                str.append("text not recognized");
                                doneSignal.countDown();
                            }
                        });
        try {
            doneSignal.await(1, TimeUnit.SECONDS);
        }catch(InterruptedException e){
            Log.e(TAG, "wait interrupted:" + e);
        }
        return str.toString();
    }
}
