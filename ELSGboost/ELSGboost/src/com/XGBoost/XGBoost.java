package com.XGBoost;

import java.util.Arrays;
import java.util.LinkedHashMap;

public class XGBoost {
    public int featureNumber;
    public int dataNumber;
    public double[][] data;
    public double[] ylable;
    public double[] PredictY;
    public LinkedHashMap<String, boolean[][]> initBucket;
    public double[] gradient;
    public double[] hessian;
    public double[] weight;


    public  XGBoost(double[][] data, LinkedHashMap<String, double[]> threshold, double[] ylable){
        this.featureNumber = data.length;
        this.dataNumber = data[0].length;
        this.data = data;
        this.ylable = ylable;
        this.PredictY = new double[this.dataNumber];
        this.weight = new double[this.dataNumber];
        this.initBucket = new LinkedHashMap<>();
        this.gradient = new double[this.dataNumber];
        this.hessian = new double[this.dataNumber];
        for (int i = 0; i < this.dataNumber; i++) {
            this.weight[i] = 0.0;
            this.PredictY[i] = 0.5;
        }
        setInitBucket(threshold);
    }

    public void setInitBucket(LinkedHashMap<String, double[]> threshold){
        int i = 0;
        for (String key:threshold.keySet()) {
            double[] thres = threshold.get(key);
            boolean[][] bucket = new boolean[thres.length][this.dataNumber];
            for (int j = 0; j < thres.length; j++) {
                for (int k = 0; k < this.dataNumber; k++) {
                    if(thres[j] >= this.data[i][k]){
                        bucket[j][k] = true;
                    }else{
                        bucket[j][k] =false;
                    }
                }
            }
            this.initBucket.put(key, bucket);
            i += 1;
        }
    }

    public LinkedHashMap<String, boolean[][]> setGenerateBucket(boolean[] bit){
        LinkedHashMap<String, boolean[][]> generateBucket = new LinkedHashMap<>();
        for(String key:initBucket.keySet()){
            boolean[][] bucket = initBucket.get(key);
            boolean[][] generatebucket = new boolean[bucket.length][bucket[0].length];
            for (int i = 0; i < bucket.length; i++) {
                for (int j = 0; j < bucket[0].length; j++) {
                    if(bit[j]){
                        generatebucket[i][j] = bucket[i][j] & bit[j];
                    }else {
                        generatebucket[i][j] = false;
                    }
                }
            }
            generateBucket.put(key, generatebucket);
        }
        return generateBucket;
    }

    /**
     * g和h的计算
     */
    public void caculategh(){
        for (int i = 0; i < this.dataNumber; i++) {
            this.gradient[i] = (this.PredictY[i] - this.ylable[i]);
            this.hessian[i] = (this.PredictY[i]*(1-this.PredictY[i]));
        }
    }

    /**
     * 根据bit求值
     */
    public double[] calulateAllGH(boolean[] bit){
        double[] allGH = new double[2];
        // G 和 H 初始值
        allGH[0] = 0;
        allGH[1] = 0;
        for (int i = 0; i < bit.length; i++) {
            if(bit[i]){
                allGH[0] += this.gradient[i];
                allGH[1] += this.hessian[i];
            }
        }
        return allGH;
    }

    public void generateWeight(boolean[] bit, double wight){
        for (int i = 0; i < bit.length; i++) {
            if(bit[i]){
                this.weight[i] += wight;
            }
        }
    }

    /**
     * sigmoid预测函数
     */
    public void sigmoid(){
        for (int i = 0; i < this.dataNumber; i++) {
            this.PredictY[i] = 1 / (1 + Math.exp(-this.weight[i]));
        }
    }

    public static void main(String[] args) {

    }

}
