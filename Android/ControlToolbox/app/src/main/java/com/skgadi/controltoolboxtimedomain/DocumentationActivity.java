package com.skgadi.controltoolboxtimedomain;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Switch;

public class DocumentationActivity extends AppCompatActivity {

    private LinearLayout LLayout00;
    private LinearLayout LLayout01;
    private LinearLayout LLayout02;
    private LinearLayout LLayout03;

    private Switch DSwitch00;
    private Switch DSwitch01;
    private Switch DSwitch02;
    private Switch DSwitch03;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        setTitle(R.string.Documentation_Title);


        //Added by SKGadi
        LLayout00 = findViewById(R.id.DocumentLayout00);
        LLayout01 = findViewById(R.id.DocumentLayout01);
        LLayout02 = findViewById(R.id.DocumentLayout02);
        LLayout03 = findViewById(R.id.DocumentLayout03);

        DSwitch00 = findViewById(R.id.Switch00);
        DSwitch01 = findViewById(R.id.Switch01);
        DSwitch02 = findViewById(R.id.Switch02);
        DSwitch03 = findViewById(R.id.Switch03);

        DSwitch00.setOnCheckedChangeListener(new LayoutSwitch(LLayout00));
        DSwitch01.setOnCheckedChangeListener(new LayoutSwitch(LLayout01));
        DSwitch02.setOnCheckedChangeListener(new LayoutSwitch(LLayout02));
        DSwitch03.setOnCheckedChangeListener(new LayoutSwitch(LLayout03));

        DSwitch00.setTextSize(18);
        DSwitch01.setTextSize(18);
        DSwitch02.setTextSize(18);
        DSwitch03   .setTextSize(18);

    }
}
