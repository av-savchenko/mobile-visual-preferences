package com.pdmi_samsung.android.visual_preferences.clustering;

import com.clust4j.algo.HDBSCAN;
import com.clust4j.algo.HDBSCANParameters;
import com.pdmi_samsung.android.visual_preferences.db.FaceData;
import com.pdmi_samsung.android.visual_preferences.db.ImageAnalysisResults;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.ArrayList;
import java.util.List;

public class HDBSCANClustering {
    private double[][] clusteredVectors;
    private ArrayList<ArrayList<Integer>> resultList;
    private int numVectors;
    private int currentNumVectors;
    private int[] labels;
    private int minFacialClusterSize=4;


    public HDBSCANClustering(){
        this.clusteredVectors = new double[0][0];
        this.numVectors = 0;
        this.currentNumVectors = 0;
    }


    public void getHdbscanClustering(RealMatrix matrixFeatures){
        for(int col = 0; col < matrixFeatures.getColumnDimension(); col++) {
            double sumCol = 0.0;
            for(int row = 0; row < matrixFeatures.getRowDimension(); row++) {
                sumCol += matrixFeatures.getEntry(row, col);
            }
            if(sumCol != 0.0) {
                for(int row = 0; row < matrixFeatures.getRowDimension(); row++) {
                    double val = matrixFeatures.getEntry(row, col) / sumCol;
                    matrixFeatures.setEntry(row, col, val);
                }
            }
        }
        HDBSCAN hdb = new HDBSCANParameters(2).setMinClustSize(2).fitNewModel(matrixFeatures);
        labels = hdb.getLabels();
        int[] new_labels = new int[currentNumVectors];
        for(int i = 0; i < currentNumVectors; i++){
            new_labels[i] = labels[i];
        }
        labels = new_labels;
    }


    private void transformLabels(){
        resultList = new ArrayList<ArrayList<Integer>>();
        int maxClusters = -1;
        for (int i = 0; i < labels.length; i++){
            if (labels[i] >  maxClusters){
                maxClusters =labels[i];
            }
        }
        for (int i = 0; i < maxClusters; i++){
            ArrayList<Integer> currentCluster = new ArrayList<Integer>();
            for (int j = 0; j < labels.length; j++){
                if (labels[j] == i){
                    currentCluster.add(j);
                }
            }
            resultList.add(currentCluster);
        }
    }


    private double[][] preparingFacesForHDBSCAN(List<FaceData> faces){
        double[][] featureVectors = null;
        int n = faces.size();
        if(n>0) {
            int m = faces.get(0).features.length;
            featureVectors = new double[n][m];
            for (int f = 0; f < n; ++f) {
                FaceData face = faces.get(f);
                for (int i = 0; i < m; i++) {
                    featureVectors[f][i] = face.features[i];
                }
            }
        }
        return featureVectors;
    }


    public void updateClusterInfo(List<FaceData> faces){
        double[][] featureVectors = preparingFacesForHDBSCAN(faces);
        updateVectors(featureVectors);
    }


    private void updateVectors(double[][] matrixFeatures){
        int m = matrixFeatures[0].length;
        int nNew = matrixFeatures.length;
        if(currentNumVectors + nNew > numVectors) {
            numVectors += 1000;
            double[][] newClusteredVectors = new double[numVectors][m];
            for(int i = 0; i < currentNumVectors; i++)
                newClusteredVectors[i] = clusteredVectors[i];
            for(int i = currentNumVectors; i < numVectors; i++)
                newClusteredVectors[i] = new double[m];
            clusteredVectors = newClusteredVectors;
        }
        for(int i=0; i < nNew; i++)
            clusteredVectors[currentNumVectors +i] = matrixFeatures[i];
        currentNumVectors += nNew;
    }


    private void getHdbscanClusteringFromDouble(double[][] matrixFeatures, boolean recalculateClustering){
        if(matrixFeatures.length > 0){
            int nNew = matrixFeatures.length;
            if(Math.floor(Double.valueOf(currentNumVectors - 1)/100) < Math.floor(Double.valueOf(currentNumVectors + nNew)/100))
                recalculateClustering = true;
            updateVectors(matrixFeatures);
        }
        if(recalculateClustering){
            Array2DRowRealMatrix data = new Array2DRowRealMatrix(clusteredVectors);
            getHdbscanClustering(data);
            transformLabels();
        }
    }

    public ArrayList<ArrayList<Integer>> performFullClusteringOnHDBSCAN(double[][] clusteredVectors){
        this.currentNumVectors = clusteredVectors.length;
        Array2DRowRealMatrix data = new Array2DRowRealMatrix(clusteredVectors);
        getHdbscanClustering(data);
        transformLabels();
        return resultList;
    }

    public void setMinClusterSize(int minFacialClusterSize) {
        if(minFacialClusterSize>0){
            this.minFacialClusterSize=minFacialClusterSize;
        }
    }
}
