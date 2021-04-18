package com.pdmi_samsung.android.visual_preferences;

import com.pdmi_samsung.android.visual_preferences.MainActivity;

public class OwnerDemography {
    public int age;
    public boolean isMale;
    public String ethnicity;
    public int numChildren=0;
    public boolean isMarried=false;

    public OwnerDemography(int age, int isFemale, String ethnicity,int numChildren){
        this.age=age;
        this.isMale=isFemale==0;
        this.ethnicity=ethnicity;
        this.numChildren=numChildren;
    }

    public String toString(){
        String str="Age: "+age+"\n"+
                "Gender: "+(isMale? "male" : "female")+"\n"+
                (isMarried?"Married":"Single")+"\n";
        if(numChildren>0)
            str+="Parent ("+numChildren+" children)\n";
        str+="Ethnicity: "+ethnicity;
        return str;
    }
}
