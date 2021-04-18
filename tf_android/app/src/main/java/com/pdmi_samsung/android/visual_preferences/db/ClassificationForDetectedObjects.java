package com.pdmi_samsung.android.visual_preferences.db;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by avsavchenko.
 */
public class ClassificationForDetectedObjects implements Serializable {
    public List<TopCategoriesData> concreteTypes=null;

    public ClassificationForDetectedObjects(){}

    public ClassificationForDetectedObjects(List<TopCategoriesData> concreteTypes){
        this.concreteTypes=concreteTypes;
    }
}
