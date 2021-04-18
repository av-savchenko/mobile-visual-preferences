package com.pdmi_samsung.android.visual_preferences.db;

import java.io.Serializable;

public class RectFloat implements Serializable {
    public float bottom;
    public float left;
    public float right;
    public float top;
    public RectFloat(float left, float top, float right, float bottom) {
        this.left=Math.min(left,right);
        this.right=Math.max(left,right);
        this.top=Math.min(top,bottom);
        this.bottom=Math.max(top,bottom);
    }

    public float square(){
        return (right-left)*(bottom-top);
    }
    public float getIntersectionSquare(RectFloat rhs){
        float l = Math.max(left, rhs.left);
        float t = Math.max(top, rhs.top);
        float w = Math.min(right, rhs.right)-l;
        float h = Math.min(bottom, rhs.bottom) - t;
        if (w<=0 || h<=0)
            return 0;
        else {
            return w*h;
        }
    }
    public float getIoU(RectFloat rhs){
        float intersectionSquare=getIntersectionSquare(rhs);
        float unionSquare=square()+rhs.square()-intersectionSquare;
        return intersectionSquare/unionSquare;
    }
}