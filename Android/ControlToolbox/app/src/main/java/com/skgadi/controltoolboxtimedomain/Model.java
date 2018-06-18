package com.skgadi.controltoolboxtimedomain;

/**
 * Created by gadis on 13-Feb-18.
 */

public abstract class Model {
    public int[] Images;
    public String[] ImageNames;
    //public String[] Ports; // 0-Out others are input
    public String[] SignalGenerators;
    public Figure Figures [];
    public Parameter[] Parameters;
    public double PlannedT_S;
    public double ActualT_S;
    public double[] OutPut;
    public int NoOfInputs;
    public int NoOfOutputs;
    public int NoOfPastInputsRequired;
    public int NoOfPastOuputsRequired;
    public int NoOfPastGeneratedValuesRequired;
    public double SimulationTime;
    public double OutputTime;
    public abstract double[] RunAlgorithms(
            double[] Parameters,
            double[][] Generated,
            double[][] Input,
            double[][] Output
    );
    public abstract double[] OutGraphSignals (
            double[] Parameters,
            double[][] Generated,
            double[][] Input,
            double[][] Output
    );
}