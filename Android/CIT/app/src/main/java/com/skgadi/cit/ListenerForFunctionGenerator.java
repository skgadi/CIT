package com.skgadi.cit;


import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Switch;

import com.warkiz.widget.IndicatorSeekBar;

/**
 * Created by gadis on 15-Feb-18.
 */

public class ListenerForFunctionGenerator implements TextWatcher {
    public FunctionGenerator Signal;
    public int Index;
    public Switch LayoutSwitch;
    public ListenerForFunctionGenerator(FunctionGenerator signal, int index, Switch layoutSwitch) {
        Signal = signal;
        Index = index;
        LayoutSwitch = layoutSwitch;
    }



    public void onProgressChanged(IndicatorSeekBar seekBar, int progress, float progressFloat, boolean fromUserTouch) {
        switch (Index) {
            case 0:
                Signal.Frequency = progressFloat;
                break;
            case 1:
                Signal.MaximumAmplitude = progressFloat;
                break;
            case 2:
                Signal.StartAt = progressFloat;
                break;
            case 3:
                Signal.DutyCycle = progressFloat;
                break;
            case 4:
                Signal.OffSet = progressFloat;
                break;
        }
        LayoutSwitch.setText((((String) LayoutSwitch.getText()).split("="))[0] + "=" + Signal.GetSignalDescription());
    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void afterTextChanged(Editable editable) {
        double Progress;
        try {
            Progress = Double.parseDouble(editable.toString());
            switch (Index) {
                case 0:
                    Signal.Frequency = Progress;
                    break;
                case 1:
                    Signal.MaximumAmplitude = Progress;
                    break;
                case 2:
                    Signal.StartAt = Progress;
                    break;
                case 3:
                    Signal.DutyCycle = Progress;
                    break;
                case 4:
                    Signal.OffSet = Progress;
                    break;
            }
            LayoutSwitch.setText((((String) LayoutSwitch.getText()).split("="))[0] + "=" + Signal.GetSignalDescription());
        } catch (Exception e) {
        }
    }
}