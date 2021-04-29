package com.skgadi.mcit;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;

import android.util.Log;
enum FILTER_TYPES {
    NONE,
    SMA,
    CMA,
    WMA,
    EMA,
    LOB0,
    LOB1,
    HPF
}
public class digitalFilter {
    public digitalFilter(String[] types, int noOfSamples, String typ, double a, double b, double alpha, double pref_HPF_alpha, double K_o_1, double K_o_2) {
        idxOfFirstEntry=noOfSamples-1;
        counter=0;
        samples = new double[noOfSamples];
        type = FILTER_TYPES.valueOf(typ);
        double n = samples.length;
        WMA_den = n*(n+1)/2;
        EMA_alpha_1 = 1-2/(n+1);
        //EMA_alpha_2 = (1- Math.pow(EMA_alpha_1,n))/(1-EMA_alpha_1);
        //Prepare the observer
        this.alpha = alpha;
        double k_1, k_2;
        if (type == FILTER_TYPES.LOB1) {
            k_1 = K_o_1;
            k_2 = K_o_2;
        } else {
            k_1 = 2*alpha-a;
            k_2 = alpha*alpha-k_1*a;
        }
        DMatrixRMaj A, C, A_star;
        A_star = new DMatrixRMaj(2,2);
        A_star.set(0,0,-k_1);
        A_star.set(0,1,1);
        A_star.set(1,0,-k_2);
        A_star.set(1,1,-a);
        Inv_A_star = new DMatrixRMaj(2,2);
        CommonOps_DDRM.invert(A_star, Inv_A_star);
        B = new DMatrixRMaj(2, 1);
        B.set(0,0,0);
        B.set(1,0,b);
        K_e = new DMatrixRMaj(2,1);
        K_e.set(0,0, k_1);
        K_e.set(1,0, k_2);
        ExpItem = new DMatrixRMaj(2,2);
        ExpItem.set(0,0,a-alpha);
        ExpItem.set(0,1,1);
        ExpItem.set(1,0,-k_2);
        ExpItem.set(1,1,k_1-alpha);
        X_Cap = new DMatrixRMaj(2,1);
        X_Cap.set(0,0,0);
        X_Cap.set(1,0,0);
        ExpAT = new DMatrixRMaj(2,2);
        ExpATmI = new DMatrixRMaj(2,2);
        //Log.i("Velocity observer", ""+type);
        // High-pass filter
        HPF_alpha = pref_HPF_alpha;
    }
    public int counter;
    public int idxOfFirstEntry;
    public double[] samples;
    public FILTER_TYPES type;
    public double filterOut;
    public double[] prevValue = new double[2];
    public double WMA_den;
    public double EMA_alpha_1;

    // Settings for Observer Based Filter 0
    private DMatrixRMaj B, K_e, Inv_A_star, ExpItem, X_Cap;
    DMatrixRMaj ExpAT, ExpATmI;
    private double alpha;

    // Settings for Observer Based Filter 1


    // Settings for High-pass filter
    double HPF_prev_u = 0;
    double HPF_prev_y = 0;
    double HPF_alpha = 0;






    public void addANewSample(double sample, double T_S, double u, double y) {
        //Log.i("Velocity filter", ""+type);
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
            case LOB0:
            case LOB1:
                filterOut = X_Cap.get(1,0);
                double exp_alpha = Math.exp(-alpha*T_S);
                DMatrixRMaj Identity = new DMatrixRMaj(2,2);
                CommonOps_DDRM.setIdentity(Identity);
                CommonOps_DDRM.scale(T_S*exp_alpha, ExpItem,ExpAT);
                CommonOps_DDRM.addEquals(ExpAT, exp_alpha, Identity);
                CommonOps_DDRM.subtract(ExpAT, Identity, ExpATmI);

                DMatrixRMaj InV_A_StarExpATmI;
                InV_A_StarExpATmI = new DMatrixRMaj(2,2);
                CommonOps_DDRM.mult(Inv_A_star, ExpATmI, InV_A_StarExpATmI);

                DMatrixRMaj X_CapNew = new DMatrixRMaj(2,1);

                CommonOps_DDRM.mult(ExpAT, X_Cap, X_CapNew);
                CommonOps_DDRM.multAdd( u, InV_A_StarExpATmI, B, X_CapNew);
                CommonOps_DDRM.multAdd( y, InV_A_StarExpATmI, K_e, X_CapNew);
                X_Cap.set(0,0,X_CapNew.get(0,0));
                X_Cap.set(1,0,X_CapNew.get(1,0));
/*
                /*
                filterOut = 1;
                */
                break;

            case HPF:
                double alphaT = HPF_alpha*T_S;
                double r_1 = 2*HPF_alpha/(alphaT+2);
                double r_2 = (alphaT-2)/(alphaT+2);
                filterOut = -r_2*HPF_prev_y + r_1*(y - HPF_prev_u);
                HPF_prev_u = y;
                HPF_prev_y = filterOut;
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
