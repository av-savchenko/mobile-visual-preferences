package com.pdmi_samsung.android.visual_preferences.db;

import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import java.io.Serializable;
import java.util.Scanner;

/**
 * Created by avsavchenko.
 */
public class EXIFData implements Serializable {
    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "EXIFData";

    public double latitude=0,longitude=0;
    public String description=null;

    public String cameraModel="";
    public int cameraFocalLength=0;

    public int orientation=1;

    public EXIFData(){
    }

    public EXIFData(String filename){
        try {
            ExifInterface exif = new ExifInterface(filename);
            double[] lat_long=exif.getLatLong();
            if(lat_long!=null) {
                this.latitude = lat_long[0];
                this.longitude = lat_long[1];
            }

            this.cameraModel=exif.getAttribute(ExifInterface.TAG_MODEL);
            String focalLenStr=exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH);
            if(cameraModel!="" && focalLenStr!="" && focalLenStr!=null) {
                this.cameraFocalLength = new Scanner(focalLenStr).useDelimiter("\\D+").nextInt();
            }

            this.orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);

        } catch (Exception e) {
            Log.e(TAG, "While getLocation for image" + filename + " exception thrown: ", e);
        }
    }

    public String toString(){
        return description!=null?description:String.format("latitude=%.3f longitude=%.3f",latitude,longitude);
    }
}
