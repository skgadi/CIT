package com.skgadi.mcit;

import android.util.Log;
enum FILTER_TYPES {
    NONE,
    SMA,
    CMA,
    WMA,
    EMA
}
public class digitalFilter {
    public digitalFilter(String[] types, int noOfSamples, String typ) {

        idxOfFirstEntry=noOfSamples-1;
        counter=0;
        samples = new double[noOfSamples];
        type = FILTER_TYPES.valueOf(typ);
        double n = samples.length;
        WMA_den = n*(n+1)/2;
        EMA_alpha_1 = 1-2/(n+1);
        //EMA_alpha_2 = (1- Math.pow(EMA_alpha_1,n))/(1-EMA_alpha_1);
    }
    public int counter;
    public int idxOfFirstEntry;
    public double[] samples;
    public FILTER_TYPES type;
    public double filterOut;
    public double[] prevValue = new double[2];
    public double WMA_den;
    public double EMA_alpha_1;
    public double EMA_alpha_2;
    public void addANewSample(double sample) {
        switch (type) {
            case NONE:
                filterOut = sample;
                break;
            case SMA:
                filterOut = filterOut + (sample -samples[idxOfFirstEntry])/(samples.length);
                break;
            case CMA:
                counter++;
                filterOut = filterOut + (sample - filterOut)/counter;
                break;
            case WMA:
                double total =  prevValue[0] + sample - samples[idxOfFirstEntry];
                prevValue[1] = prevValue[1] + samples.length * sample - prevValue[0];
                prevValue[0] = total;
                filterOut = prevValue[1]/WMA_den;
                break;
            case EMA:
                prevValue[0] = sample + EMA_alpha_1*prevValue[0];
                prevValue[1] = 1+EMA_alpha_1*prevValue[1];
                filterOut = prevValue[0]/prevValue[1];
                break;
        }
        PutElementToFIFO(samples, sample);
    }

    protected double[] PutElementToFIFO (double[] array, double element){
        for (int i=(array.length-1); i>0; i--) {
            array[i] = array[i-1];
        }
        array[0] = element;
        return array;
    }

}
