package com.skgadi.controltoolboxtimedomain;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.usb.UsbDevice;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.warkiz.widget.IndicatorSeekBar;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.equation.Equation;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.aflak.arduino.Arduino;
import me.aflak.arduino.ArduinoListener;


enum SCREENS {
    MAIN_SCREEN,
    SETTINGS,
    DOCUMENTATION,
    OPEN_LOOP,
    PID,
    FIRST_ORDER_ADAPTIVE_CONTROL,
    //SECOND_ORDER_ADAPTIVE_CONTROL,
    FIRST_ORDER_IDENTIFICATION,
    SECOND_ORDER_IDENTIFICATION,
    IDENTIFICATION_FIRST_ORDER_WITH_CONTROLLER
}

enum SIMULATION_STATUS {
    DISABLED,
    OFF,
    ON
}

public class MainActivity extends AppCompatActivity {

    private LinearLayout.LayoutParams DefaultLayoutParams;
    private LinearLayout[] Screens;
    SCREENS PresentScreen = SCREENS.MAIN_SCREEN;
    SCREENS LastModelScreen;

    SCREENS PreviousScreen;
    private boolean CloseApp;
    protected String[] ScreensList;
    MenuItem SettingsButton;
    MenuItem SimulateButton;

    LinearLayout ModelView;
    EditText[] ModelParams;
    TextView InstantaneousValues;
    EditText ModelSamplingTime;
    GraphView[] ModelGraphs;
    int[] ColorTable = {
            Color.RED,
            Color.BLUE,
            Color.GREEN,
            Color.rgb(128,0,0),
            Color.rgb(128,128,0),
            Color.rgb(0,128,0),
            Color.rgb(128,0,128),
            Color.rgb(0,128,128),
            Color.rgb(0,0,128),
            Color.YELLOW,
            Color.MAGENTA
    };
    ScrollView MainScrollView;
    //--- Screenshot related
    private LinearLayout RootLayout;
    private TextView TextForImageSharing;
    Bitmap bitmap;

    //--- Database Related
    SQLiteDatabase GSK_Database;
    String DatabaseName = "gsk_settings.db";
    //--- Settings Related
    Integer[] SettingsDefault = {
            100, 100, 10, 200
    };
    Integer[][] SettingsLimits = {
            {10, 1000}, {10, 10000}, {1, 100}, {25, 1000}
    };
    Integer[] PreviousSettings = {
            0, 0, 0, 0
    };
    String[] SettingsDBColumns = {"SamplingTime", "ChartHistoryLength", "ZoomXWindow", "ChartWindowHeight"};
    IndicatorSeekBar[] SettingsSeekBars;
    double[] AnalogOutLimits = {0, 5};
    double[] AnalogInLimits = {0, 5};
    double[] TrajectoryLimits = {-10000, 10000};

    //Communication
    public Arduino arduino;
    boolean DeviceConnected = false;
    SIMULATION_STATUS SimulationState;

    SimulationView Model;
    FunctionGenerator[] GeneratedSignals;

    Simulate SimHandle;



    //--- Back button handling
    @Override
    public void onBackPressed() {
        if (SimulationState == SIMULATION_STATUS.ON) {
            Toast.makeText(getApplicationContext(),
                    getResources().getStringArray(R.array.TOASTS)[9],
                    Toast.LENGTH_SHORT).show();
        } else {
            if (CloseApp && PresentScreen == SCREENS.MAIN_SCREEN)
                finish();
            else
                CloseApp = false;
            if (PresentScreen == SCREENS.MAIN_SCREEN) {
                CloseApp = true;
                Toast.makeText(getApplicationContext(),
                        getResources().getStringArray(R.array.TOASTS)[0],
                        Toast.LENGTH_SHORT).show();
            } else if (PresentScreen == SCREENS.SETTINGS) {
                Toast.makeText(MainActivity.this,
                        getResources().getStringArray(R.array.TOASTS)[12],
                        Toast.LENGTH_SHORT).show();
            } else {
                SetScreenTo(SCREENS.MAIN_SCREEN);
            }
        }
    }



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //--- Database
        ConnectToDatabase();
        //--- Generate Settings window
        GenerateSettingsView ();
        //--- Var vals
        MainScrollView = (ScrollView) findViewById(R.id.MainScrollView);
        RootLayout = (LinearLayout) findViewById(R.id.RootLayout);
        TextForImageSharing = (TextView) findViewById(R.id.TextForImageSharing);
        ModelView = (LinearLayout)findViewById(R.id.ModelView);
        Screens = new LinearLayout[SCREENS.values().length];
        Screens[0] = (LinearLayout) findViewById(R.id.Main);
        Screens[1] = (LinearLayout) findViewById(R.id.Settings);
        Screens[2] = (LinearLayout) findViewById(R.id.Documentation);
        for (int i=3; i< Screens.length; i++)
            Screens[i] = (LinearLayout) findViewById(R.id.ModelView);
        DefaultLayoutParams =  new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.FILL_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        SimulationState = SIMULATION_STATUS.DISABLED;
        //--- Add buttons
        ScreensList = getResources().getStringArray(R.array.SCREENS_LIST);
        Button ButtonForMainScreen;
        for (int i=2; i<ScreensList.length; i++) {
            ButtonForMainScreen = new Button(this);
            ButtonForMainScreen.setText(ScreensList[i]);
            ButtonForMainScreen.setLayoutParams(DefaultLayoutParams);
            ButtonForMainScreen.setOnClickListener(new OnMainWindowButton(i));
            Screens[SCREENS.MAIN_SCREEN.ordinal()].addView(ButtonForMainScreen);
        }
        //--- USB Connection
        arduino = new Arduino(getApplicationContext(), 115200);

        arduino.setArduinoListener(new ArduinoListener() {
            @Override
            public void onArduinoAttached(UsbDevice device) {
                arduino.open(device);
                DeviceConnected = false;
            }

            @Override
            public void onArduinoDetached() {
                DeviceConnected = false;
                if(SimHandle != null) {
                    SimHandle.cancel(true);
                    Toast.makeText(MainActivity.this,
                            getResources().getStringArray(R.array.TOASTS)[13],
                            Toast.LENGTH_SHORT).show();
                }
                SetProperSimulationStatus();
            }

            @Override
            public void onArduinoMessage(byte[] bytes) {
                DoThisWhenReceivedDataFromUSB(bytes);
            }

            @Override
            public void onArduinoOpened() {
                DeviceConnected = true;
                SetProperSimulationStatus();
            }

            @Override
            public void onUsbPermissionDenied() {
                arduino.reopen();
                DeviceConnected = false;
            }
        });

    }
    public void DoThisWhenReceivedDataFromUSB (final byte[] bytes) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //DataRecUpdateForHex(bytes);
                DataRecUpdate(bytes);
            }
        });
    }
    public void SendToUSB (final byte[] bytes) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                arduino.send(bytes);
            }
        });
    }

    private void GenerateSettingsView () {
        TextView TempTextView;
        SettingsSeekBars = new IndicatorSeekBar[SettingsDefault.length];
        for (int i=0; i<SettingsDefault.length; i++) {
            TempTextView = new TextView(getApplicationContext());
            TempTextView.setTextColor(Color.BLACK);
            if (getResources().getStringArray(R.array.SETTINGS_WINDOW)[i].contains(">>")) {
                String[] TempTitles = getResources().getStringArray(R.array.SETTINGS_WINDOW)[i].split(">>");
                TempTextView.setText(TempTitles[0]);
                TempTextView.setTextSize(18);
                TempTextView.setTypeface(null, Typeface.BOLD);
                ((LinearLayout) findViewById(R.id.SettingsSeekBars)).addView(TempTextView);
                TempTextView = new TextView(getApplicationContext());
                TempTextView.setTextColor(Color.BLACK);
                TempTextView.setText(TempTitles[1]);
            } else
                TempTextView.setText(getResources().getStringArray(R.array.SETTINGS_WINDOW)[i]);
            ((LinearLayout) findViewById(R.id.SettingsSeekBars)).addView(TempTextView);
            SettingsSeekBars[i] = new IndicatorSeekBar.Builder(getApplicationContext())
                    .setMin(SettingsLimits[i][0])
                    .setMax(SettingsLimits[i][1])
                    .thumbProgressStay(true)
                    .build();
            ((LinearLayout) findViewById(R.id.SettingsSeekBars)).addView(SettingsSeekBars[i]);
        }
        ChangeSettingsPositionsTo(ReadSettingsFromDatabase());
    }

    private void ChangeSettingsPositionsTo (Integer[] Values) {
        for (int i=0; i< SettingsDefault.length; i++) {
            Log.i("DBaseStuff", "Got " + Values[i]);
            SettingsSeekBars[i].setProgress(Values[i]);
        }
    }

    private Integer[] ReadSettingsPositions () {
        Integer[] Values = new Integer[SettingsDefault.length];
        for (int i=0; i< SettingsDefault.length; i++)
            Values[i] = SettingsSeekBars[i].getProgress();
        return Values;

    }

    private void WriteSettingsToDatabase (Integer[] Values) {
        ContentValues data;
        data = new ContentValues();
        //Log.i("DBaseStuff", "Got " + Values[0]);
        data.put("Key", 1);
        for (int i=0; i<SettingsDefault.length; i++)
            data.put (SettingsDBColumns[i], Values[i]);
        Cursor TempCursor;
        TempCursor = GSK_Database.rawQuery("SELECT * FROM `Settings`", null);
        if (TempCursor.moveToFirst())
            GSK_Database.update("Settings", data, "Key == 1" ,null);
        else {
            GSK_Database.insert("Settings", null, data);
            WriteSettingsToDatabase(SettingsDefault);
        }
        TempCursor.close();

    }

    private Integer[] ReadSettingsFromDatabase () {
        Integer[] Values = new Integer[SettingsDefault.length];
        Cursor TempCursor;
        TempCursor = GSK_Database.rawQuery("SELECT * FROM `Settings`", null);
        Log.i("DBaseStuff", "Cursor count: " + TempCursor.getCount());
        if (TempCursor.moveToFirst()) {
            Log.i("DBaseStuff", "Got raw query For Key:"
                    + TempCursor.getInt(TempCursor.getColumnIndex("Key")));

            for (int i=0; i<SettingsDefault.length; i++)
                Values[i] = TempCursor.getInt(TempCursor.getColumnIndex(SettingsDBColumns[i]));
        } else {
            Log.i("DBaseStuff", "No raw query");
            CreateAandPopulateRecordForSettingsTable();
        }
        return Values;
    }
    public void SettingsSave (View v) {
        WriteSettingsToDatabase(ReadSettingsPositions());
        SetScreenTo(PreviousScreen);
    }

    public void SettingsReset (View v) {
        ChangeSettingsPositionsTo(SettingsDefault);
    }

    public void SettingsResetNSave (View v) {
        ChangeSettingsPositionsTo(SettingsDefault);
        SettingsSave(v);
    }

    public void SettingsCancel (View v) {
        ChangeSettingsPositionsTo(ReadSettingsFromDatabase());
        SetScreenTo(PreviousScreen);
    }

    private void ConnectToDatabase () {
        GSK_Database = getApplicationContext().openOrCreateDatabase(DatabaseName ,
                MODE_PRIVATE, null);
        if (GSK_Database.isOpen()) {
            Log.i("DBaseStuff", "Database Opened");
            if (isTableExists(GSK_Database, "Settings"))
                Log.i("DBaseStuff", "Table is found");
            else {
                Log.i("DBaseStuff", "Table does not exist");
                CreateAandPopulateRecordForSettingsTable();
            }
        } else
            Log.i("DBaseStuff", "Unable to open Database");
    }

    private void CreateAandPopulateRecordForSettingsTable () {
        String Query = "CREATE TABLE IF NOT EXISTS `Settings` (" +
                "`Key`              INTEGER PRIMARY KEY ASC";
        for (int i=0; i<SettingsDefault.length; i++)
            Query += ", `" + SettingsDBColumns[i] + "`  INTEGER NOT NULL";
        Query += ");";
        //Log.i("DBaseStuff", "Query String: "+Query);
        GSK_Database.execSQL(Query);
        WriteSettingsToDatabase(SettingsDefault);
    }
    public boolean isTableExists(SQLiteDatabase mDatabase, String tableName) {
        Cursor cursor = mDatabase.rawQuery("select DISTINCT tbl_name from sqlite_master where tbl_name = '"+tableName+"'", null);
        if(cursor!=null) {
            if(cursor.getCount()>0) {
                cursor.close();
                return true;
            }
            cursor.close();
        }
        return false;
    }

    private void SetScreenTo (SCREENS Screen) {
        PreviousScreen = PresentScreen;
        for (int i=0; i<SCREENS.values().length; i++)
            Screens[i].setVisibility(View.GONE);
        PresentScreen = Screen;
        Screens[PresentScreen.ordinal()].setVisibility(View.VISIBLE);
        SetProperSimulationStatus();
        if (Screen == SCREENS.MAIN_SCREEN)
            setTitle(getResources().getString(R.string.app_name));
        else
            setTitle(getResources().getString(R.string.app_name)
                    + ": "
                    + getResources().getStringArray(R.array.SCREENS_LIST)[PresentScreen.ordinal()]);
        if (LastModelScreen != Screen && Screen.ordinal()>2) {
            LastModelScreen = Screen;
            switch (Screen) {
                case MAIN_SCREEN:
                    break;
                case SETTINGS:
                    break;
                case DOCUMENTATION:
                    break;
                case OPEN_LOOP:
                    PrepareOpenLoopModel();
                    break;
                case PID:
                    PreparePIDModel();
                    break;
                case FIRST_ORDER_ADAPTIVE_CONTROL:
                    PrepareFirstOrderAdaptiveControlModel();
                    break;
                /*case SECOND_ORDER_ADAPTIVE_CONTROL:
                    PrepareSecondOrderAdaptiveControlModel();
                    break;*/
                case FIRST_ORDER_IDENTIFICATION:
                    PrepareFirstOrderIdentification();
                    break;
                case SECOND_ORDER_IDENTIFICATION:
                    PrepareSecondOrderIdentification();
                    break;
                case IDENTIFICATION_FIRST_ORDER_WITH_CONTROLLER:
                    PrepareFirstOrderWithControllerIdentification();
                    break;
            }
            GenerateViewFromModel();
        }
    }

    private void DrawALine(LinearLayout ParentView) {
        View TempView = new View(getApplicationContext());
        TempView.setMinimumHeight(2);
        TempView.setBackgroundColor(Color.BLACK);
        ParentView.addView(TempView);
    }

    private void ClearTheModelView () {
        if (ModelView.getChildCount() >0 )
            ModelView.removeAllViews();
    }

    private void GenerateViewFromModel () {
        //Removing previous view
        ClearTheModelView ();
        //ModelView.getBackgroundTintList()
        ModelView.setBackgroundColor(Color.WHITE);
        //Declaring TempVariables
        TextView TempTextView;
        LinearLayout TempLayout;
        Switch TempSwitchForLayout;
        DrawALine(ModelView);
        //Add an Image
        DrawALine(ModelView);
        for (int i=0; i<Model.Images.length; i++) {
            TempLayout = new LinearLayout(getApplicationContext());
            TempLayout.setOrientation(LinearLayout.VERTICAL);
            TempSwitchForLayout = new Switch(getApplicationContext());
            TempSwitchForLayout.setTextColor(Color.BLACK);
            TempSwitchForLayout.setBackgroundColor(Color.LTGRAY);
            TempSwitchForLayout.setChecked(true);
            TempSwitchForLayout.setText(getResources().getStringArray(R.array.SIM_VIEW_HEADS)[0]
                    + ": " + Model.ImageNames[i]);
            TempSwitchForLayout.setTextSize(18);
            TempSwitchForLayout.setTypeface(null, Typeface.BOLD);
            TempSwitchForLayout.setOnCheckedChangeListener(new LayoutSwitch(TempLayout));

            ImageView TempImgView = new ImageView(getApplicationContext());
            TempImgView.setImageResource(Model.Images[i]);
            TempImgView.setAdjustViewBounds(true);
            TempLayout.addView(TempImgView);


            ModelView.addView(TempSwitchForLayout);
            ModelView.addView(TempLayout);
            TempSwitchForLayout.setChecked(false);
            DrawALine(ModelView);
        }
        // Sampling Time
        DrawALine(ModelView);
        TempLayout = new LinearLayout(getApplicationContext());
        TempLayout.setOrientation(LinearLayout.VERTICAL);
        TempSwitchForLayout = new Switch(getApplicationContext());
        TempSwitchForLayout.setTextColor(Color.BLACK);
        TempSwitchForLayout.setBackgroundColor(Color.LTGRAY);
        TempSwitchForLayout.setChecked(true);
        TempSwitchForLayout.setText(getResources().getStringArray(R.array.SIM_VIEW_HEADS)[1]);
        TempSwitchForLayout.setTextSize(18);
        TempSwitchForLayout.setTypeface(null, Typeface.BOLD);
        TempSwitchForLayout.setOnCheckedChangeListener(new LayoutSwitch(TempLayout));

        TempLayout.setOrientation(LinearLayout.HORIZONTAL);
        TempTextView = new TextView(getApplicationContext());
        TempTextView.setTextColor(Color.BLACK);
        TempTextView.setText(getString(R.string.SAMPLING_TIME));
        TempTextView.setTypeface(null, Typeface.BOLD);
        TempLayout.addView(TempTextView);
        ModelSamplingTime = new EditText(getApplicationContext());
        ModelSamplingTime.setSelectAllOnFocus(true);
        ModelSamplingTime.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_CLASS_NUMBER);
        ModelSamplingTime.setText(String.valueOf(SettingsSeekBars[0].getProgress()));
        ModelSamplingTime.setTextColor(Color.BLACK);
        TempTextView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ModelSamplingTime.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TempLayout.addView(ModelSamplingTime);

        ModelView.addView(TempSwitchForLayout);
        ModelView.addView(TempLayout);
        TempSwitchForLayout.setChecked(false);
        DrawALine(ModelView);
        //Parameters
        if (Model.Parameters.length>0) {
            DrawALine(ModelView);
            TempLayout = new LinearLayout(getApplicationContext());
            TempLayout.setOrientation(LinearLayout.VERTICAL);
            TempSwitchForLayout = new Switch(getApplicationContext());
            TempSwitchForLayout.setTextColor(Color.BLACK);
            TempSwitchForLayout.setBackgroundColor(Color.LTGRAY);
            TempSwitchForLayout.setChecked(true);
            TempSwitchForLayout.setText(getResources().getStringArray(R.array.SIM_VIEW_HEADS)[2]);
            TempSwitchForLayout.setTextSize(18);
            TempSwitchForLayout.setTypeface(null, Typeface.BOLD);
            TempSwitchForLayout.setOnCheckedChangeListener(new LayoutSwitch(TempLayout));

            ModelParams = new EditText[Model.Parameters.length];
            for (int i = 0; i < Model.Parameters.length; i++) {
                LinearLayout TempHorizontalLayout = new LinearLayout(getApplicationContext());
                TempHorizontalLayout.setOrientation(LinearLayout.HORIZONTAL);
                TempHorizontalLayout.setLayoutParams(new
                        LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT));

                TempTextView = new TextView(getApplicationContext());
                TempTextView.setTextColor(Color.BLACK);
                if (Model.Parameters[i].Name.contains(">>")) {
                    String[] TempTitles = Model.Parameters[i].Name.split(">>");
                    TempTextView.setText(TempTitles[0]);
                    TempTextView.setTypeface(null, Typeface.BOLD);
                    TempLayout.addView(TempTextView);
                    TempTextView = new TextView(getApplicationContext());
                    TempTextView.setTextColor(Color.BLACK);
                    TempTextView.setText(TempTitles[1]);
                } else
                    TempTextView.setText(Model.Parameters[i].Name);
                TempTextView.setText(TempTextView.getText()
                        + " (Recommended to use values in the rage ["+Model.Parameters[i].Min+", "+Model.Parameters[i].Max+"]) :");
                ModelParams[i] = new EditText(getApplicationContext());
                ModelParams[i].setSelectAllOnFocus(true);
                //ModelParams[i].setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_CLASS_NUMBER);
                ModelParams[i].setText(String.valueOf(Model.Parameters[i].DefaultValue));
                ModelParams[i].setTextColor(Color.BLACK);
                TempTextView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                ModelParams[i].setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                TempHorizontalLayout.addView(TempTextView);
                TempHorizontalLayout.addView(ModelParams[i]);
                TempLayout.addView(TempHorizontalLayout);
            }
            ModelView.addView(TempSwitchForLayout);
            ModelView.addView(TempLayout);
            TempSwitchForLayout.setChecked(false);
            DrawALine(ModelView);
        }
        //Function generator
        DrawALine(ModelView);
        GeneratedSignals = new FunctionGenerator[Model.SignalGenerators.length];
        for (int i=0; i<Model.SignalGenerators.length; i++) {
            TempLayout = new LinearLayout(getApplicationContext());
            TempLayout.setOrientation(LinearLayout.VERTICAL);
            TempSwitchForLayout = new Switch(getApplicationContext());
            TempSwitchForLayout.setTextColor(Color.BLACK);
            TempSwitchForLayout.setBackgroundColor(Color.LTGRAY);
            TempSwitchForLayout.setChecked(true);
            TempSwitchForLayout.setText(getResources().getStringArray(R.array.SIM_VIEW_HEADS)[3]
                    + ": "
                    + Model.SignalGenerators[i]
                    + "=0"
            );
            TempSwitchForLayout.setTextSize(18);
            TempSwitchForLayout.setTypeface(null, Typeface.BOLD);
            TempSwitchForLayout.setOnCheckedChangeListener(new LayoutSwitch(TempLayout));
            //SignalType
            Spinner TempFunctionsView = new Spinner(getApplicationContext());
            TempFunctionsView.setLayoutParams(new Spinner.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            final List<String> SignalsList =
                    new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.AVAILABLE_SIGNALS)));
            final ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(
                    this,R.layout.spinner_item,SignalsList);
            TempFunctionsView.setAdapter(spinnerArrayAdapter);
            //TempFunctionsView.getBackground().setColorFilter(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            TempLayout.addView(TempFunctionsView);
            GeneratedSignals[i] = new FunctionGenerator();
            TempFunctionsView.setOnItemSelectedListener(
                    new SignalTypeListener(GeneratedSignals[i], TempSwitchForLayout));
            //Floats
            LinearLayout TempHorizontalLayout = new LinearLayout(getApplicationContext());
            TempHorizontalLayout.setOrientation(LinearLayout.HORIZONTAL);
            TempHorizontalLayout.setLayoutParams(new
                    LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT));
            for (int j=0; j<5; j++) {
                TempTextView = new TextView(getApplicationContext());
                TempTextView.setTextColor(Color.BLACK);
                TempTextView.setText(getResources().getStringArray(R.array.SIGNAL_GENERATOR_PARAMETERS)[j]+": ");
                EditText TempEditText = new EditText(getApplicationContext());
                TempEditText.setSelectAllOnFocus(true);
                //TempEditText.setInputType(InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_CLASS_NUMBER);
                TempEditText.setText(String.valueOf(GeneratedSignals[i].MinMaxDefaultsForFloats[j][2]));
                TempEditText.setTextColor(Color.BLACK);
                TempEditText.addTextChangedListener(new ListenerForFunctionGenerator(
                        GeneratedSignals[i], j, TempSwitchForLayout
                ));
                TempTextView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                TempEditText.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                TempHorizontalLayout.addView(TempTextView);
                TempHorizontalLayout.addView(TempEditText);
                TempHorizontalLayout.setGravity(Gravity.CENTER);
            }
            TempLayout.addView(TempHorizontalLayout);
            //Compliment
            Switch TempSwitchForCompliment = new Switch(getApplicationContext());
            TempSwitchForCompliment.setChecked(false);
            TempSwitchForCompliment.setText(R.string.INVERT_SIGNAL);
            TempSwitchForCompliment.setOnCheckedChangeListener(
                    new SignalComplimentListener(GeneratedSignals[i], TempSwitchForLayout));
            TempLayout.addView(TempSwitchForCompliment);


            ModelView.addView(TempSwitchForLayout);
            ModelView.addView(TempLayout);
            TempSwitchForLayout.setChecked(false);
            DrawALine(ModelView);
        }

        //Graphs
        DrawALine(ModelView);
        ModelGraphs = new GraphView[Model.Figures.length];
        for (int i=0; i<ModelGraphs.length; i++) {
            TempLayout = new LinearLayout(getApplicationContext());
            TempLayout.setOrientation(LinearLayout.VERTICAL);
            TempSwitchForLayout = new Switch(getApplicationContext());
            TempSwitchForLayout.setTextColor(Color.BLACK);
            TempSwitchForLayout.setBackgroundColor(Color.LTGRAY);
            TempSwitchForLayout.setChecked(true);
            TempSwitchForLayout.setText(getResources().getStringArray(R.array.SIM_VIEW_HEADS)[4]
                    + " " +((int)i+1) + ": "
                    + Model.Figures[i].Name);
            TempSwitchForLayout.setTextSize(18);
            TempSwitchForLayout.setTypeface(null, Typeface.BOLD);
            TempSwitchForLayout.setOnCheckedChangeListener(new LayoutSwitch(TempLayout));

            ModelGraphs[i] = new GraphView(getApplicationContext());
            ModelGraphs[i].getGridLabelRenderer().setGridColor(Color.BLACK);
            ModelGraphs[i].getGridLabelRenderer().setHorizontalLabelsColor(Color.BLACK);
            ModelGraphs[i].getGridLabelRenderer().setVerticalLabelsColor(Color.BLACK);
            ModelGraphs[i].getGridLabelRenderer().setHorizontalAxisTitleColor(Color.BLACK);
            ModelGraphs[i].getGridLabelRenderer().setVerticalAxisTitleColor(Color.BLACK);
            ModelGraphs[i].getGridLabelRenderer().setVerticalLabelsSecondScaleColor(Color.BLACK);

            for (int j=0; j<Model.Figures[i].Trajectories.length; j++) {
                LineGraphSeries<DataPoint> GraphSeries = new LineGraphSeries<>();
                GraphSeries.setColor(ColorTable[j]);
                GraphSeries.setTitle(Model.Figures[i].Trajectories[j]);
                ModelGraphs[i].addSeries(GraphSeries);
            }
            ModelGraphs[i].getViewport().setScalable(true);
            ModelGraphs[i].getViewport().setScalableY(true);
            ModelGraphs[i].getViewport().setScrollable(true);
            ModelGraphs[i].getViewport().setScrollableY(true);
            ModelGraphs[i].getViewport().setMinX(0);
            ModelGraphs[i].getViewport().setMaxX(
                    ReadSettingsPositions()[Arrays.asList(SettingsDBColumns)
                            .indexOf("ZoomXWindow")
                            ]);
            ModelGraphs[i].setMinimumHeight(
                    ReadSettingsPositions()[Arrays.asList(SettingsDBColumns)
                            .indexOf("ChartWindowHeight")
                            ]);
            ModelGraphs[i].getLegendRenderer().setVisible(true);
            ModelGraphs[i].getLegendRenderer().setAlign(LegendRenderer.LegendAlign.TOP);
            TempLayout.addView(ModelGraphs[i]);

            ModelView.addView(TempSwitchForLayout);
            ModelView.addView(TempLayout);
            TempSwitchForLayout.setChecked(false);
            DrawALine(ModelView);
        }
        DrawALine(ModelView);

        //Instantaneous Values
        TempLayout = new LinearLayout(getApplicationContext());
        TempLayout.setOrientation(LinearLayout.VERTICAL);
        TempSwitchForLayout = new Switch(getApplicationContext());
        TempSwitchForLayout.setTextColor(Color.BLACK);
        TempSwitchForLayout.setBackgroundColor(Color.LTGRAY);
        TempSwitchForLayout.setChecked(true);
        TempSwitchForLayout.setText(getResources().getStringArray(R.array.SIM_VIEW_HEADS)[5]);
        TempSwitchForLayout.setTextSize(18);
        TempSwitchForLayout.setTypeface(null, Typeface.BOLD);
        TempSwitchForLayout.setOnCheckedChangeListener(new LayoutSwitch(TempLayout));
        InstantaneousValues = new TextView(getApplicationContext());
        InstantaneousValues.setTextColor(Color.BLACK);
        TempLayout.addView(InstantaneousValues);
        ModelView.addView(TempSwitchForLayout);
        ModelView.addView(TempLayout);
        TempSwitchForLayout.setChecked(false);
        DrawALine(ModelView);
        DrawALine(ModelView);

        //InstantaneousValues
    }

    private void PrepareOpenLoopModel() {
        Model = new SimulationView() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                double [] OutSignals = new double[NoOfOutputs];
                OutSignals[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[2];
                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                return Trajectories;
            }
        };
        Model.NoOfInputs=1;
        Model.NoOfOutputs=1;
        Model.NoOfPastInputsRequired = 0;
        Model.NoOfPastOuputsRequired = 0;
        Model.NoOfPastGeneratedValuesRequired = 0;
        Model.OutPut = new double[0];
        //Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.figure00;
        //Model.Images[1] = R.drawable.pid;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Open loop system";
        //Model.ImageNames[1] = "Reference Value details";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "u1(t)";
        Model.SignalGenerators[1] = "u2(t)";
        Model.SignalGenerators[2] = "u3(t)";
        Model.Figures = new Figure[1];
        String[] TempTrajectories = new String[2];
        TempTrajectories[0]= "Input u(t)";
        TempTrajectories[1]= "Output y(t)";
        Model.Figures[0] = new Figure("Input u(t) and Output y(t)", TempTrajectories);
        Model.Parameters = new Parameter [0];
        Model.PlannedT_S = ReadSettingsPositions()[Arrays.asList(SettingsDBColumns).indexOf("SamplingTime")]/1000.0;
    }

    private void PreparePIDModel() {
        Model = new SimulationView() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                double K_P = Parameters[0];
                double K_I = Parameters[1];
                double K_D = Parameters[2];
                double a = K_P + K_I* ActualT_S /2.0 + K_D/ActualT_S;
                double b = -K_P + K_I*ActualT_S/2.0 - 2.0*K_D/ActualT_S;
                double c = K_D/ActualT_S;
                double[] E = new double[3];
                for (int i=0; i<3; i++)
                    E[i] = ((Generated[0][i] + Generated[1][i] + Generated[2][i]) - Input[0][i]);
                double [] OutSignals = new double[NoOfOutputs];
                /*OutSignals[0] = PutBetweenRange(
                        Output[0][0] + a * E[0] + b * E[1] + c * E[2]
                        +Parameters[3]*0.1*(1-0.5*Math.random()),
                        AnalogOutLimits[0],
                        AnalogOutLimits[1]);*/
                OutSignals[1] = Output[1][0] + a * E[0] + b * E[1] + c * E[2];
                OutSignals[0] = OutSignals[1] + Parameters[3] + Parameters[4] * (1-2*Math.random());
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[4];
                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                Trajectories[2] = Trajectories[0]-Input[0][0];
                Trajectories[3] = Output[0][0];
                return Trajectories;
            }
        };
        Model.NoOfInputs=1;
        Model.NoOfOutputs=2;
        Model.NoOfPastInputsRequired = 2;
        Model.NoOfPastOuputsRequired = 1;
        Model.NoOfPastGeneratedValuesRequired = 2;
        Model.OutPut = new double[1];
        Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.figure01;
        //Model.Images[1] = R.drawable.pid;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Closed loop system with PID  controller";
        //Model.ImageNames[1] = "Reference Value details";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "r1(t)";
        Model.SignalGenerators[1] = "r2(t)";
        Model.SignalGenerators[2] = "r3(t)";
        Model.Figures = new Figure[2];
        String[] TempTrajectories = new String[2];
        TempTrajectories[0]= "Reference r(t)";
        TempTrajectories[1]= "Output y(t)";
        Model.Figures[0] = new Figure("Reference r(t) and Output y(t)", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "Error e(t)";
        TempTrajectories[1]= "Control u(t)";
        Model.Figures[1] = new Figure("Error e(t) and Control u(t)", TempTrajectories);
        Model.Parameters = new Parameter [5];
        Model.Parameters[0] = new Parameter("Controller parameters>>K_P", 0, 100, 1);
        Model.Parameters[1] = new Parameter("K_I", 0, 10, 10);
        Model.Parameters[2] = new Parameter("K_D", 0, 1, 0);
        Model.Parameters[3] = new Parameter("Other parameters>>Constant perturbation (d_1)", -1, 1, 0);
        Model.Parameters[4] = new Parameter("Noise constant (d_2)", 0, 1, 0);
        Model.PlannedT_S = ReadSettingsPositions()[Arrays.asList(SettingsDBColumns).indexOf("SamplingTime")]/1000.0;
    }

    private void PrepareFirstOrderAdaptiveControlModel() {
        Model = new SimulationView() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                /*
                    Output[0] --> u
                    Output[1] --> a_r
                    Output[2] --> a_y
                    Output[3] --> y_m
                    Generated[0] --> R_1
                    Generated[1] --> R_2
                    Generated[2] --> R_3
                    R = R_1 + R_2 + R_3
                    Input[0] --> y
                    E --> e
                */
                double Gamma = Parameters[0];
                double A_m = Parameters[1];
                double B_m = Parameters[2];

                double[] E = new double[3];
                double[] R = new double[3];
                for (int i=0; i<3; i++)
                    R[i] = Generated[0][i] + Generated[1][i] + Generated[2][i];
                for (int i=0; i<2; i++)
                    E[i] = (Input[0][i] - Output[3][i]);
                double [] OutSignals = new double[NoOfOutputs];
                //OutSignals[3] = 1/(2/Model.ActualT_S  + A_m)*(Output[3][0] * (2/Model.ActualT_S  - A_m) + B_m*(R[0] + R[1]));
                OutSignals[3] = Output[3][0]*Math.exp(-A_m*Model.ActualT_S)
                        + B_m/A_m*(1-Math.exp(-A_m*Model.ActualT_S))* R[0];
                //E[0] = (Input[0][0] - OutSignals[3]);
                OutSignals[1] = Output[1][0] - Gamma*Model.ActualT_S*(E[0]*R[0] + E[1]*R[1])/2.0;
                OutSignals[2] = Output[2][0] - Gamma*Model.ActualT_S*(E[0]*Input[0][0] + E[1]*Input[0][1])/2.0;
                //OutSignals[0] = PutBetweenRange(OutSignals[1]*R[0] + OutSignals[2]*Input[0][0], AnalogOutLimits[0], AnalogOutLimits[1]);
                OutSignals[0] = OutSignals[1]*R[0] + OutSignals[2]*Input[0][0];
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[7];
                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                Trajectories[2] = Output[3][0];
                Trajectories[3] = Trajectories[0]-Input[0][0];
                Trajectories[4] = Output[0][0];
                Trajectories[5] = Output[1][0];
                Trajectories[6] = Output[2][0];
                return Trajectories;
            }
        };
        Model.NoOfInputs=1;
        Model.NoOfOutputs=4;
        Model.NoOfPastInputsRequired = 2;
        Model.NoOfPastOuputsRequired = 1;
        Model.NoOfPastGeneratedValuesRequired = 2;
        Model.OutPut = new double[1];
        Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.figure02;
        //Model.Images[1] = R.drawable.pid;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Adaptive control model";
        //Model.ImageNames[1] = "Reference Value details";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "R1(t)";
        Model.SignalGenerators[1] = "R2(t)";
        Model.SignalGenerators[2] = "R3(t)";

        //Figures
        Model.Figures = new Figure[3];
        String[] TempTrajectories = new String[3];
        TempTrajectories[0]= "Reference r(t)";
        TempTrajectories[1]= "Output y(t)";
        TempTrajectories[2]= "Reference model output y_m(t)";
        Model.Figures[0] = new Figure("Reference r(t) and Output y(t)", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "Error e(t)";
        TempTrajectories[1]= "Control u(t)";
        Model.Figures[1] = new Figure("Error e(t) and Control u(t)", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "a_r";
        TempTrajectories[1]= "a_y";
        Model.Figures[2] = new Figure("Parameters of the system", TempTrajectories);

        Model.Parameters = new Parameter [3];
        Model.Parameters[0] = new Parameter("Adaptive Control Parameters>>\u03B3", 0, 1000, 1);
        Model.Parameters[1] = new Parameter("Reference Model Parameters>>A_m", 0, 100, 4);
        Model.Parameters[2] = new Parameter("B_m", 0, 100, 4);
        Model.PlannedT_S = ReadSettingsPositions()[Arrays.asList(SettingsDBColumns).indexOf("SamplingTime")]/1000.0;
    }

    private void PrepareSecondOrderAdaptiveControlModel() {
        Model = new SimulationView() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                /*
                    Output[00] --> u
                    Output[01] --> x_m_11
                    Output[02] --> x_m_21
                    Output[03] --> K_c_11
                    Output[04] --> K_c_12
                    Output[05] --> L_11
                    Output[06] --> E_11
                    Output[07] --> E_21
                    Generated[0] --> R_1
                    Generated[1] --> R_2
                    Generated[2] --> R_3
                    R = R_1 + R_2 + R_3
                    Input[0] --> y
                    E --> e
                */
                double[] R = new double[3];
                for (int i=0; i<3; i++)
                    R[i] = Generated[0][i] + Generated[1][i] + Generated[2][i];
                double Gamma = Parameters[0];
                double a_m1 = Parameters[1];
                double a_m2 = Parameters[2];
                DMatrixRMaj A_m = new DMatrixRMaj(2,2);
                DMatrixRMaj A_mT_s = new DMatrixRMaj(2,2);
                DMatrixRMaj B_m = new DMatrixRMaj(2,1);
                A_m.set(0,0, 0);
                A_m.set(0,1, 1);
                A_m.set(1,0, -a_m2);
                A_m.set(1,1, -a_m1);
                B_m.set(0,0, 0);
                B_m.set(1,0, Parameters[3]);
                DMatrixRMaj A_m_d = new DMatrixRMaj(2,2);
                DMatrixRMaj B_m_d = new DMatrixRMaj(2,1);
                CommonOps_DDRM.scale(ActualT_S, A_m, A_mT_s);
                Equation eq = new Equation();
                eq.alias(A_m_d,"A_m_d", B_m_d,"B_m_d", A_m,"A_m", A_mT_s,"A_mT_s", B_m,"B_m");
                eq.process("A_mT_s2 = A_mT_s*A_mT_s");
                eq.process("A_mT_s3 = A_mT_s2*A_mT_s");
                eq.process("A_mT_s4 = A_mT_s3*A_mT_s");
                eq.process("A_mT_s5 = A_mT_s4*A_mT_s");
                eq.process("A_mT_s6 = A_mT_s5*A_mT_s");
                eq.process("A_m_d = eye(2) + A_mT_s + 1/2.0*A_mT_s2 + 1/6.0*A_mT_s3 + 1/24.0*A_mT_s4 + 1/120.0*A_mT_s5 + 1/720.0*A_mT_s6");
                eq.process("B_m_d = inv(A_m)*(A_m_d-eye(2))*B_m");
                DMatrixRMaj X_m_1 = new DMatrixRMaj(2,1);
                DMatrixRMaj X_m = new DMatrixRMaj(2,1);
                X_m_1.set(0,0, Output[1][0]);
                X_m_1.set(1,0, Output[2][0]);
                eq = new Equation();
                eq.alias(X_m_1, "X_m_1", X_m, "X_m", A_m_d, "A_m_d", B_m_d, "B_m_d", R[0], "R");
                eq.process("X_m = A_m_d*X_m_1 + B_m_d * R");

                DMatrixRMaj P = new DMatrixRMaj(2,2);
                P.set(0, 1, 1/(2*a_m2));
                P.set(1, 0, P.get(0,1));
                P.set(1, 1, (2*P.get(0,1)+1)/(2*a_m1));
                P.set(0, 0, a_m1*P.get(0,1) + a_m2*P.get(1,1));
                DMatrixRMaj X = new DMatrixRMaj(2,1);
                DMatrixRMaj X_1 = new DMatrixRMaj(2,1);
                X.set(0, 0, Input[0][0]);
                X.set(1, 0, (Input[0][0]-Input[0][1])/ActualT_S);
                DMatrixRMaj E = new DMatrixRMaj(2,1);
                DMatrixRMaj E_1 = new DMatrixRMaj(2,1);
                CommonOps_DDRM.subtract(X, X_m, E);
                E_1.set(0, 0, Output[6][0]);
                E_1.set(1, 0, Output[7][0]);

                DMatrixRMaj K_c_1 = new DMatrixRMaj(1,2);
                DMatrixRMaj K_c = new DMatrixRMaj(1,2);
                DMatrixRMaj L_1 = new DMatrixRMaj(1,1);
                DMatrixRMaj L = new DMatrixRMaj(1,1);
                K_c_1.set(0, 0, Output[3][0]);
                K_c_1.set(0, 1, Output[4][0]);
                L_1.set(0, 0, Output[5][0]);

                DMatrixRMaj U = new DMatrixRMaj(1,1);
                eq = new Equation();
                eq.alias(K_c, "K_c", L, "L", K_c_1, "K_c_1", L_1, "L_1", Gamma, "Gamma", ActualT_S, "T_S", B_m, "B_m", P, "P", E, "E", E_1, "E_1", X, "X", X_1, "X_1", R[0], "R", R[1], "R_1", U, "U");
                eq.process("K_c = K_c_1 + Gamma*T_S/2.0*(B_m'*P*E*X' + B_m'*P*E_1*X_1')");
                eq.process("L = L_1 - Gamma*T_S/2.0*(B_m'*P*E*R + B_m'*P*E_1*R_1)");
                eq.process("U = -K_c*X + L*R");
                Log.i("Algorithm", "A_mT_s: " + A_mT_s.toString());
                Log.i("Algorithm", "A_m: " + A_m.toString());
                Log.i("Algorithm", "B_m: " + B_m.toString());
                Log.i("Algorithm", "A_m_d: " + A_m_d.toString());
                Log.i("Algorithm", "B_m_d: " + B_m_d.toString());
                Log.i("Algorithm", "P: " + P.toString());

                double [] OutSignals = new double[NoOfOutputs];
                OutSignals[0] = U.get(0,0);
                OutSignals[1] = X_m.get(0,0);
                OutSignals[2] = X_m.get(1,0);
                OutSignals[3] = K_c.get(0,0);
                OutSignals[4] = K_c.get(0,1);
                OutSignals[5] = L.get(0,0);
                OutSignals[6] = E.get(0,0);
                OutSignals[7] = E.get(1,0);
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[7];
                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                Trajectories[2] = Output[1][0];
                Trajectories[3] = Trajectories[0]-Input[0][0];
                Trajectories[4] = Output[0][0];
                Trajectories[5] = Output[1][0];
                Trajectories[6] = Output[2][0];
                return Trajectories;
            }
        };
        Model.NoOfInputs=1;
        Model.NoOfOutputs=8;
        Model.NoOfPastInputsRequired = 2;
        Model.NoOfPastOuputsRequired = 1;
        Model.NoOfPastGeneratedValuesRequired = 2;
        Model.OutPut = new double[1];
        Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.figure02;
        //Model.Images[1] = R.drawable.pid;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Adaptive control model";
        //Model.ImageNames[1] = "Reference Value details";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "R1(t)";
        Model.SignalGenerators[1] = "R2(t)";
        Model.SignalGenerators[2] = "R3(t)";

        //Figures
        Model.Figures = new Figure[3];
        String[] TempTrajectories = new String[3];
        TempTrajectories[0]= "Reference r(t)";
        TempTrajectories[1]= "Output y(t)";
        TempTrajectories[2]= "Reference model output y_m(t)";
        Model.Figures[0] = new Figure("Reference r(t) and Output y(t)", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "Error e(t)";
        TempTrajectories[1]= "Control u(t)";
        Model.Figures[1] = new Figure("Error e(t) and Control u(t)", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "a_r";
        TempTrajectories[1]= "a_y";
        Model.Figures[2] = new Figure("Parameters of the system", TempTrajectories);

        Model.Parameters = new Parameter [4];
        Model.Parameters[0] = new Parameter("Adaptive Control Parameters>>\u03B3", 0, 1000, 0.1);
        Model.Parameters[1] = new Parameter("Reference Model Parameters>>A_m_1", 0, 1000, 40);
        Model.Parameters[2] = new Parameter("A_m_2", 0, 1000, 100);
        Model.Parameters[3] = new Parameter("B_m", 0, 1000, 120);
        Model.PlannedT_S = ReadSettingsPositions()[Arrays.asList(SettingsDBColumns).indexOf("SamplingTime")]/1000.0;
    }

    private void PrepareFirstOrderIdentification() {
        Model = new SimulationView() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                /*
                    Output[0] --> u
                    Output[1] --> P11
                    Output[2] --> P12
                    Output[3] --> P21
                    Output[4] --> P22
                    Output[5] --> Theta_1
                    Output[6] --> Theta_2
                    Output[7] --> K
                    Output[8] --> Z Cap Calculated
                    Generated[0] --> R_1
                    Generated[1] --> R_2
                    Generated[2] --> R_3
                    R = R_1 + R_2 + R_3
                    Input[0] --> z
                    E --> e
                */
                double Lambda = Parameters[1];
                double K = Output[7][0];

                DMatrixRMaj z = new DMatrixRMaj(1,1);
                z.set(0, 0, Input[0][0]);
                DMatrixRMaj Phi = new DMatrixRMaj(2,1);
                Phi.set(0, 0, Input[0][1]);
                Phi.set(1, 0, Output[0][1]);
                DMatrixRMaj Theta_1 = new DMatrixRMaj(2,1);
                Theta_1.set(0,0, Output[5][0]);
                Theta_1.set(1,0, Output[6][0]);
                DMatrixRMaj P_1 = new DMatrixRMaj(2,2);
                if (K==0) {
                    P_1.set(0, 0, Parameters[0]);
                    P_1.set(0, 1, 0);
                    P_1.set(1, 0, 0);
                    P_1.set(1, 1, Parameters[0]);
                } else {
                    P_1.set(0, 0, Output[1][0]);
                    P_1.set(0, 1, Output[2][0]);
                    P_1.set(1, 0, Output[3][0]);
                    P_1.set(1, 1, Output[4][0]);
                }

                DMatrixRMaj P = new DMatrixRMaj(2,2);
                DMatrixRMaj Theta = new DMatrixRMaj(2,1);
                DMatrixRMaj TempMatrix0, TempMatrix1, TempMatrix2, TempMatrix3;
                DMatrixRMaj e = new DMatrixRMaj(1,1);
                DMatrixRMaj PhiTranspose = new DMatrixRMaj(1,2);
                // Calculation of e
                CommonOps_DDRM.transpose(Phi, PhiTranspose);
                CommonOps_DDRM.mult(PhiTranspose, Theta_1, e);
                CommonOps_DDRM.changeSign(e);
                CommonOps_DDRM.addEquals(e,z);
                // Calculation of P
                TempMatrix0 = new DMatrixRMaj(2,1);
                TempMatrix1 = new DMatrixRMaj(1,1);
                TempMatrix2 = new DMatrixRMaj(1,2);
                TempMatrix3 = new DMatrixRMaj(2,1);
                CommonOps_DDRM.mult(P_1,Phi,TempMatrix0);
                CommonOps_DDRM.mult(PhiTranspose, TempMatrix0, TempMatrix1);
                CommonOps_DDRM.add(TempMatrix1, Lambda);
                CommonOps_DDRM.invert(TempMatrix1);

                CommonOps_DDRM.mult(PhiTranspose, P_1, TempMatrix2);
                CommonOps_DDRM.mult(P_1, Phi, TempMatrix3);
                CommonOps_DDRM.mult(TempMatrix3, TempMatrix2, P);
                CommonOps_DDRM.changeSign(P);
                CommonOps_DDRM.scale(TempMatrix1.get(0,0), P);
                CommonOps_DDRM.addEquals(P, P_1);
                CommonOps_DDRM.scale(1/Lambda, P);
                // Calculations of Theta
                CommonOps_DDRM.mult(P, Phi, Theta);
                CommonOps_DDRM.mult(Theta, e, Theta);
                CommonOps_DDRM.addEquals(Theta, Theta_1);


                double [] OutSignals = new double[NoOfOutputs];
                OutSignals[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                OutSignals[1] = P.get(0,0);
                OutSignals[2] = P.get(0,1);
                OutSignals[3] = P.get(1,0);
                OutSignals[4] = P.get(1,1);
                OutSignals[5] = Theta.get(0,0);
                OutSignals[6] = Theta.get(1,0);
                OutSignals[7] = K+1;
                OutSignals[8] =  Output[8][1]*Theta.get(0,0) + Theta.get(1,0)*Output[0][1];
                Log.i("Algorithm", "Phi: " + Phi.toString());
                Log.i("Algorithm", "Theta: "  + Theta.toString());
                Log.i("Algorithm", "z: "  + z);
                Log.i("Algorithm", "e: "  + e);
                Log.i("Algorithm", "P: "  + P.toString());
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[7];
                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                Trajectories[2] = Output[8][0];
                Trajectories[3] = Output[5][0];
                Trajectories[4] = Output[6][0];
                Trajectories[5] = -Math.log(Output[5][0])/Model.ActualT_S;
                Trajectories[6] = Output[6][0]*Trajectories[5]/(1-Output[5][0]);
                return Trajectories;
            }
        };
        Model.NoOfInputs=1;
        Model.NoOfOutputs=9;
        Model.NoOfPastInputsRequired = 2;
        Model.NoOfPastOuputsRequired = 1;
        Model.NoOfPastGeneratedValuesRequired = 2;
        Model.OutPut = new double[1];
        Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.figure03;
        //Model.Images[1] = R.drawable.pid;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Identification of the first order system";
        //Model.ImageNames[1] = "Reference Value details";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "I1(t)";
        Model.SignalGenerators[1] = "I2(t)";
        Model.SignalGenerators[2] = "I3(t)";

        //Figures
        Model.Figures = new Figure[3];
        String[] TempTrajectories = new String[3];
        TempTrajectories[0]= "Input to the system u(t)";
        TempTrajectories[1]= "Output of the system y(t)";
        TempTrajectories[2]= "Validation Output of the system ycap(t)";
        Model.Figures[0] = new Figure("Input output graph", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "Theta_1 Cap";
        TempTrajectories[1]= "Theta_2 Cap";
        Model.Figures[1] = new Figure("Identified parameters (Theta)", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "A Cap";
        TempTrajectories[1]= "B Cap";
        Model.Figures[2] = new Figure("Identified parameters (A, B)", TempTrajectories);

        Model.Parameters = new Parameter [2];
        Model.Parameters[0] = new Parameter("\u03C1", 0, 10000, 1000);
        Model.Parameters[1] = new Parameter("\u03BB", 0, 1, 0.9);
        Model.PlannedT_S = ReadSettingsPositions()[Arrays.asList(SettingsDBColumns).indexOf("SamplingTime")]/1000.0;
    }

    private void PrepareSecondOrderIdentification() {
        Model = new SimulationView() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                /*
                    Output[00] --> u
                    Output[01] --> P11
                    Output[02] --> P12
                    Output[03] --> P13
                    Output[04] --> P14
                    Output[05] --> P15
                    Output[06] --> P21
                    Output[07] --> P22
                    Output[08] --> P23
                    Output[09] --> P24
                    Output[10] --> P25
                    Output[11] --> P31
                    Output[12] --> P32
                    Output[13] --> P33
                    Output[14] --> P34
                    Output[15] --> P35
                    Output[16] --> P41
                    Output[17] --> P42
                    Output[18] --> P43
                    Output[19] --> P44
                    Output[20] --> P45
                    Output[21] --> P51
                    Output[22] --> P52
                    Output[23] --> P53
                    Output[24] --> P54
                    Output[25] --> P55
                    Output[26] --> Theta_1
                    Output[27] --> Theta_2
                    Output[28] --> Theta_3
                    Output[29] --> Theta_4
                    Output[30] --> Theta_5
                    Output[31] --> K
                    Output[32] --> Y Cap Calculated
                    Generated[0] --> R_1
                    Generated[1] --> R_2
                    Generated[2] --> R_3
                    R = R_1 + R_2 + R_3
                    Input[0] --> y
                    E --> e
                */
                int MatrixSize = 5;
                double Lambda = Parameters[1];
                double K = Output[31][0];
                double[] E = new double[3];
                for (int i=0; i<3; i++)
                    E[i] = ((Generated[0][i] + Generated[1][i] + Generated[2][i]) - Input[0][i]);



                DMatrixRMaj z = new DMatrixRMaj(1,1);
                z.set(0, 0, Input[0][0]);
                DMatrixRMaj Phi = new DMatrixRMaj(MatrixSize,1);
                Phi.set(0, 0, Output[0][0]);
                Phi.set(1, 0, Output[0][1]);
                Phi.set(2, 0, Output[0][2]);
                Phi.set(3, 0, -Input[0][1]);
                Phi.set(4, 0, -Input[0][2]);
                DMatrixRMaj Theta_1 = new DMatrixRMaj(MatrixSize,1);
                for (int i=0; i<MatrixSize; i++)
                    Theta_1.set(i,0, Output[i+26][0]);
                DMatrixRMaj P_1 = new DMatrixRMaj(MatrixSize,MatrixSize);
                if (K==0) {
                    for (int i=0; i<MatrixSize; i++)
                        for (int j=0; j<MatrixSize; j++)
                            P_1.set(i, j, 0);
                    for (int i=0; i<MatrixSize; i++)
                        P_1.set(i, i, Parameters[0]);
                } else {
                    for (int i=0; i<MatrixSize; i++)
                        for (int j=0; j<MatrixSize; j++)
                            P_1.set(i, j, Output[(i*MatrixSize+j+1)][0]);
                }
                DMatrixRMaj P = new DMatrixRMaj(MatrixSize,MatrixSize);
                DMatrixRMaj Theta = new DMatrixRMaj(MatrixSize,1);
                DMatrixRMaj TempMatrix0, TempMatrix1, TempMatrix2, TempMatrix3;
                DMatrixRMaj e = new DMatrixRMaj(1,1);
                DMatrixRMaj PhiTranspose = new DMatrixRMaj(1,MatrixSize);
                // Calculation of e
                CommonOps_DDRM.transpose(Phi, PhiTranspose);
                CommonOps_DDRM.mult(PhiTranspose, Theta_1, e);
                CommonOps_DDRM.changeSign(e);
                CommonOps_DDRM.addEquals(e,z);
                // Calculation of P
                TempMatrix0 = new DMatrixRMaj(MatrixSize,1);
                TempMatrix1 = new DMatrixRMaj(1,1);
                TempMatrix2 = new DMatrixRMaj(1,MatrixSize);
                TempMatrix3 = new DMatrixRMaj(MatrixSize,1);
                CommonOps_DDRM.mult(P_1,Phi,TempMatrix0);
                CommonOps_DDRM.mult(PhiTranspose, TempMatrix0, TempMatrix1);
                CommonOps_DDRM.add(TempMatrix1, Lambda);
                CommonOps_DDRM.invert(TempMatrix1);

                CommonOps_DDRM.mult(PhiTranspose, P_1, TempMatrix2);
                CommonOps_DDRM.mult(P_1, Phi, TempMatrix3);
                CommonOps_DDRM.mult(TempMatrix3, TempMatrix2, P);
                CommonOps_DDRM.changeSign(P);
                CommonOps_DDRM.scale(TempMatrix1.get(0,0), P);
                CommonOps_DDRM.addEquals(P, P_1);
                CommonOps_DDRM.scale(1/Lambda, P);
                // Calculations of Theta
                CommonOps_DDRM.mult(P, Phi, Theta);
                CommonOps_DDRM.mult(Theta, e, Theta);
                CommonOps_DDRM.addEquals(Theta, Theta_1);


                double [] OutSignals = new double[NoOfOutputs];
                OutSignals[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                for (int i=0; i<MatrixSize; i++)
                    for (int j=0; j<MatrixSize; j++)
                        OutSignals[1+i*MatrixSize+j] = P.get(i, j);
                for (int i=0; i<MatrixSize; i++)
                    OutSignals[i+26] = Theta.get(i,0);
                OutSignals[31] = K+1;
                OutSignals[32]
                        = Theta.get(0,0 ) * Output[0][0]
                        + Theta.get(1,0 ) * Output[0][1]
                        + Theta.get(2,0 ) * Output[0][2]
                        - Theta.get(3,0 ) * Output[32][0]
                        - Theta.get(4,0 ) * Output[32][1];
                Log.i("Algorithm", "Phi: " + Phi.toString());
                Log.i("Algorithm", "Theta: "  + Theta.toString());
                Log.i("Algorithm", "z: "  + z);
                Log.i("Algorithm", "e: "  + e);
                Log.i("Algorithm", "P_1: " + P_1.toString());
                Log.i("Algorithm", "P: "  + P.toString());
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[13];
                DMatrixRMaj A_D, B_D, C, D, A_C, B_C, R;
                Equation eq = new Equation();
                A_D = new DMatrixRMaj(2,2);
                B_D = new DMatrixRMaj(2,1);
                C = new DMatrixRMaj(1,2);
                D = new DMatrixRMaj(1,1);
                A_C = new DMatrixRMaj(2,2);
                B_C = new DMatrixRMaj(2,1);
                //R = new DMatrixRMaj(2,2);

                A_D.set(0, 0, 0);
                A_D.set(0, 1, 1);
                A_D.set(1, 0, -Output[25+5][0]);
                A_D.set(1, 1, -Output[25+4][0]);

                B_D.set(0,0,0);
                B_D.set(1,0,1);

                C.set(0,0, Output[25+3][0] - Output[25+5][0]*Output[25+1][0]);
                C.set(0,1, Output[25+2][0] - Output[25+4][0]*Output[25+1][0]);

                D.set(0,0, Output[25+1][0]);

                eq.alias(A_D, "A_D", B_D, "B_D", A_C, "A_C", B_C, "B_C");
                eq.process("R = (A_D - eye(2))*inv(A_D + eye(2))");
                eq.process("A_C = 2*R*(eye(2) - 8/21*R*R - 4/105*R*R*R*R)*inv(eye(2) - 5/7*R*R)");
                CommonOps_DDRM.scale(1/Model.ActualT_S, A_C);
                eq.process("B_C = A_C * inv(A_D-eye(2)) * B_D");

                Log.i("Algorithm", "A_C: " + A_C);
                Log.i("Algorithm", "B_C: " + B_C);
                Log.i("Algorithm", "C_C: " + C);
                Log.i("Algorithm", "D_C: " + D);



                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                Trajectories[2] = Output[32][0];
                for (int i=0; i<5; i++)
                    Trajectories[3+i] = Output[i+26][0];
                Trajectories[8] = D.get(0,0);
                Trajectories[9] = C.get(0,0)*B_C.get(0,0)
                        + C.get(0,1)*B_C.get(1,0)
                        - D.get(0,0)*(A_C.get(0,0) + A_C.get(1,1));
                Trajectories[10] =
                        C.get(0,0)*(B_C.get(1,0)*A_C.get(0,1) - B_C.get(0,0)*A_C.get(1,1))
                        + C.get(0,1)*(B_C.get(0,0)*A_C.get(1,0) - B_C.get(1,0)*A_C.get(0,0))
                        + D.get(0,0)*(A_C.get(0,0)*A_C.get(1,1) - A_C.get(0,1)*A_C.get(1,0));
                Trajectories[11] = -(A_C.get(0,0) + A_C.get(1,1));
                Trajectories[12] = A_C.get(0,0)*A_C.get(1,1) - A_C.get(0,1)*A_C.get(1,0);
                return Trajectories;
            }
        };
        Model.NoOfInputs=1;
        Model.NoOfOutputs=33;
        Model.NoOfPastInputsRequired = 2;
        Model.NoOfPastOuputsRequired = 2;
        Model.NoOfPastGeneratedValuesRequired = 2;
        Model.OutPut = new double[1];
        Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.figure04;
        //Model.Images[1] = R.drawable.pid;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Identification of the first order system";
        //Model.ImageNames[1] = "Reference Value details";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "I1(t)";
        Model.SignalGenerators[1] = "I2(t)";
        Model.SignalGenerators[2] = "I3(t)";

        //Figures
        Model.Figures = new Figure[3];
        String[] TempTrajectories = new String[3];
        TempTrajectories[0]= "Reference r(t)";
        TempTrajectories[1]= "Output y(t)";
        TempTrajectories[2]= "Validation Output of the system ycap(t)";
        Model.Figures[0] = new Figure("Input output graph", TempTrajectories);
        TempTrajectories = new String[5];
        TempTrajectories[0]= "\u03F4_1 Cap";
        TempTrajectories[1]= "\u03F4_2 Cap";
        TempTrajectories[2]= "\u03F4_3 Cap";
        TempTrajectories[3]= "\u03F4_4 Cap";
        TempTrajectories[4]= "\u03F4_5 Cap";
        Model.Figures[1] = new Figure("Identified parameters (\u03F4)", TempTrajectories);
        TempTrajectories = new String[5];
        TempTrajectories[0]= "\u03B2_1 Cap";
        TempTrajectories[1]= "\u03B2_2 Cap";
        TempTrajectories[2]= "\u03B2_3 Cap";
        TempTrajectories[3]= "\u03B2_4 Cap";
        TempTrajectories[4]= "\u03B2_5 Cap";
        Model.Figures[2] = new Figure("Identified parameters (\u03B2)", TempTrajectories);

        Model.Parameters = new Parameter [2];
        Model.Parameters[0] = new Parameter("Identification parameters>>\u03C1", 0, 10000, 1000);
        Model.Parameters[1] = new Parameter("\u03BB", 0, 1, 0.9);
        Model.PlannedT_S = ReadSettingsPositions()[Arrays.asList(SettingsDBColumns).indexOf("SamplingTime")]/1000.0;
    }

    private void PrepareFirstOrderWithControllerIdentification() {
        Model = new SimulationView() {
            @Override
            public double[] RunAlgorithms(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            ){
                /*
                    Output[0] --> u
                    Output[1] --> P11
                    Output[2] --> P12
                    Output[3] --> P21
                    Output[4] --> P22
                    Output[5] --> Theta_1
                    Output[6] --> Theta_2
                    Output[7] --> K
                    Output[8] --> Y Cap Calculated
                    Generated[0] --> R_1
                    Generated[1] --> R_2
                    Generated[2] --> R_3
                    R = R_1 + R_2 + R_3
                    Input[0] --> y
                    E --> e
                */
                double Lambda = Parameters[2];
                double K_I = Parameters[0];
                double K = Output[7][0];
                double[] E = new double[3];
                for (int i=0; i<3; i++)
                    E[i] = ((Generated[0][i] + Generated[1][i] + Generated[2][i]) - Input[0][i]);



                DMatrixRMaj z = new DMatrixRMaj(1,1);
                z.set(0, 0, Input[0][0] - Input[0][1]);
                DMatrixRMaj Phi = new DMatrixRMaj(2,1);
                Phi.set(0, 0, E[1] + E[2]);
                Phi.set(1, 0, Input[0][1] - Input[0][2]);
                DMatrixRMaj Theta_1 = new DMatrixRMaj(2,1);
                Theta_1.set(0,0, Output[5][0]);
                Theta_1.set(1,0, Output[6][0]);
                DMatrixRMaj P_1 = new DMatrixRMaj(2,2);
                if (K==0) {
                    P_1.set(0, 0, Parameters[1]);
                    P_1.set(0, 1, 0);
                    P_1.set(1, 0, 0);
                    P_1.set(1, 1, Parameters[1]);
                } else {
                    P_1.set(0, 0, Output[1][0]);
                    P_1.set(0, 1, Output[2][0]);
                    P_1.set(1, 0, Output[3][0]);
                    P_1.set(1, 1, Output[4][0]);
                }

                DMatrixRMaj P = new DMatrixRMaj(2,2);
                DMatrixRMaj Theta = new DMatrixRMaj(2,1);
                DMatrixRMaj TempMatrix0, TempMatrix1, TempMatrix2, TempMatrix3;
                DMatrixRMaj e = new DMatrixRMaj(1,1);
                DMatrixRMaj PhiTranspose = new DMatrixRMaj(1,2);
                // Calculation of e
                CommonOps_DDRM.transpose(Phi, PhiTranspose);
                CommonOps_DDRM.mult(PhiTranspose, Theta_1, e);
                CommonOps_DDRM.changeSign(e);
                CommonOps_DDRM.addEquals(e,z);
                // Calculation of P
                TempMatrix0 = new DMatrixRMaj(2,1);
                TempMatrix1 = new DMatrixRMaj(1,1);
                TempMatrix2 = new DMatrixRMaj(1,2);
                TempMatrix3 = new DMatrixRMaj(2,1);
                CommonOps_DDRM.mult(P_1,Phi,TempMatrix0);
                CommonOps_DDRM.mult(PhiTranspose, TempMatrix0, TempMatrix1);
                CommonOps_DDRM.add(TempMatrix1, Lambda);
                CommonOps_DDRM.invert(TempMatrix1);

                CommonOps_DDRM.mult(PhiTranspose, P_1, TempMatrix2);
                CommonOps_DDRM.mult(P_1, Phi, TempMatrix3);
                CommonOps_DDRM.mult(TempMatrix3, TempMatrix2, P);
                CommonOps_DDRM.changeSign(P);
                CommonOps_DDRM.scale(TempMatrix1.get(0,0), P);
                CommonOps_DDRM.addEquals(P, P_1);
                CommonOps_DDRM.scale(1/Lambda, P);
                // Calculations of Theta
                CommonOps_DDRM.mult(P, Phi, Theta);
                CommonOps_DDRM.mult(Theta, e, Theta);
                CommonOps_DDRM.addEquals(Theta, Theta_1);


                double [] OutSignals = new double[NoOfOutputs];
                OutSignals[0] = Output[0][0] + K_I*Model.ActualT_S/2.0 * (E[0] + E[1]);
                OutSignals[1] = P.get(0,0);
                OutSignals[2] = P.get(0,1);
                OutSignals[3] = P.get(1,0);
                OutSignals[4] = P.get(1,1);
                OutSignals[5] = Theta.get(0,0);
                OutSignals[6] = Theta.get(1,0);
                OutSignals[7] = K+1;
                /*OutSignals[8] = Output[8][0]* (Theta.get(1,0) + 1 - Theta.get(0,0))
                        - Output[8][1]*(Theta.get(1,0) + Theta.get(0,0))
                        + Theta.get(0,0)* (
                        (Generated[0][1] + Generated[1][1] + Generated[2][1])
                                + (Generated[0][2] + Generated[1][2] + Generated[2][2])
                );*/
                OutSignals[8] = Output[8][0]
                        + Theta.get(0,0) * (
                        (Generated[0][1] + Generated[1][1] + Generated[2][1])
                                + (Generated[0][2] + Generated[1][2] + Generated[2][2])
                                - Output[8][0] - Output[8][1]
                )
                        + Theta.get(1,0) * (Output[8][0] - Output[8][1]);
                Log.i("Algorithm", "Phi: " + Phi.toString());
                Log.i("Algorithm", "Theta: "  + Theta.toString());
                Log.i("Algorithm", "z: "  + z);
                Log.i("Algorithm", "e: "  + e);
                Log.i("Algorithm", "P: "  + P.toString());
                return OutSignals;
            }

            @Override
            public double[] OutGraphSignals(
                    double[] Parameters,
                    double[][] Generated,
                    double[][] Input,
                    double[][] Output
            )
            {
                double[] Trajectories = new double[7];
                Trajectories[0] = Generated[0][0] + Generated[1][0] + Generated[2][0];
                Trajectories[1] = Input[0][0];
                Trajectories[2] = Output[8][0];
                Trajectories[3] = Output[5][0];
                Trajectories[4] = Output[6][0];
                Trajectories[5] = -Math.log(Output[6][0])/Model.ActualT_S;
                Trajectories[6] = 2*Output[5][0]*Trajectories[5]/((1-Output[6][0])*Model.ActualT_S*Parameters[0]);
                return Trajectories;
            }
        };
        Model.NoOfInputs=1;
        Model.NoOfOutputs=9;
        Model.NoOfPastInputsRequired = 2;
        Model.NoOfPastOuputsRequired = 1;
        Model.NoOfPastGeneratedValuesRequired = 2;
        Model.OutPut = new double[1];
        Model.OutPut[0]=0;
        Model.Images = new int[1];
        Model.Images[0] = R.drawable.figure05;
        //Model.Images[1] = R.drawable.pid;
        Model.ImageNames = new String[1];
        Model.ImageNames[0] = "Identification of the first order system";
        //Model.ImageNames[1] = "Reference Value details";
        Model.SignalGenerators = new String[3];
        Model.SignalGenerators[0] = "R1(t)";
        Model.SignalGenerators[1] = "R2(t)";
        Model.SignalGenerators[2] = "R3(t)";

        //Figures
        Model.Figures = new Figure[3];
        String[] TempTrajectories = new String[3];
        TempTrajectories[0]= "Reference r(t)";
        TempTrajectories[1]= "Output y(t)";
        TempTrajectories[2]= "Validation Output of the system ycap(t)";
        Model.Figures[0] = new Figure("Input output graph", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "Theta_1 Cap";
        TempTrajectories[1]= "Theta_2 Cap";
        Model.Figures[1] = new Figure("Identified parameters (Theta)", TempTrajectories);
        TempTrajectories = new String[2];
        TempTrajectories[0]= "A Cap";
        TempTrajectories[1]= "B Cap";
        Model.Figures[2] = new Figure("Identified parameters (A, B)", TempTrajectories);

        Model.Parameters = new Parameter [3];
        Model.Parameters[0] = new Parameter("Control parameters>>K_I", 0, 1000, 10);
        Model.Parameters[1] = new Parameter("Identification parameters>>\u03C1", 0, 10000, 1000);
        Model.Parameters[2] = new Parameter("\u03BB", 0, 1, 0.9);
        Model.PlannedT_S = ReadSettingsPositions()[Arrays.asList(SettingsDBColumns).indexOf("SamplingTime")]/1000.0;
    }

    public class OnMainWindowButton implements View.OnClickListener {
        int ScreenNumber;
        public OnMainWindowButton (int ScreenNumber) {
            this.ScreenNumber = ScreenNumber;
        }
        @Override
        public void onClick(View v) {
            SetScreenTo (SCREENS.values()[ScreenNumber]);
        }
    };

    private void shareImage(Bitmap bitmap){
        // save bitmap to cache directory
        try {
            File cachePath = new File(this.getCacheDir(), "images");
            cachePath.mkdirs(); // don't forget to make the directory
            FileOutputStream stream = new FileOutputStream(cachePath + "/image.png"); // overwrites this image every time
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            stream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        File imagePath = new File(this.getCacheDir(), "images");
        File newFile = new File(imagePath, "image.png");
        Uri contentUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", newFile);

        if (contentUri != null) {
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // temp permission for receiving app to read this file
            shareIntent.setDataAndType(contentUri, getContentResolver().getType(contentUri));
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.setType("image/png");
            startActivity(Intent.createChooser(shareIntent, "Choose an app"));
        }
    }
    //--- Menu handling
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.con_sim_menu, menu);
        SettingsButton = menu.getItem(1);
        SimulateButton = menu.getItem(2);
        return super.onCreateOptionsMenu(menu);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.share:
                ScrollView iv = (ScrollView) findViewById(R.id.MainScrollView);
                bitmap = Bitmap.createBitmap(
                        iv.getChildAt(0).getWidth(),
                        iv.getChildAt(0).getHeight(),
                        Bitmap.Config.RGB_565);
                Canvas c = new Canvas(bitmap);
                iv.getChildAt(0).draw(c);
                shareImage(bitmap);
                break;
            case R.id.settings:
                if (SimulationState == SIMULATION_STATUS.ON)
                    Toast.makeText(MainActivity.this,
                            getResources().getStringArray(R.array.TOASTS)[10],
                            Toast.LENGTH_SHORT).show();
                else
                    if (PresentScreen != SCREENS.SETTINGS)
                        SetScreenTo(SCREENS.SETTINGS);
                break;
            case R.id.simulate:
                if(SimulationState == SIMULATION_STATUS.ON) {
                    SimHandle.cancel(true);
                }
                if (SimulationState == SIMULATION_STATUS.DISABLED) {
                    Toast.makeText(MainActivity.this,
                            getResources().getStringArray(R.array.TOASTS)[8],
                            Toast.LENGTH_SHORT).show();
                }
                if (SimulationState == SIMULATION_STATUS.OFF) {
                    if (DeviceConnected) {
                        ChangeStateToSimulating();
                        SimHandle = new Simulate();
                        SimHandle.execute(0);
                        /*Toast.makeText(MainActivity.this,
                                "started simulating",
                                Toast.LENGTH_SHORT).show();*/
                    }
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    void SetProperSimulationStatus() {
        if (PresentScreen.ordinal()>2 && DeviceConnected)
            ChangeStateToNotSimulating();
        else
            ChangeStateToSimulateDisabled();
    }
    void ChangeStateToSimulateDisabled () {
        SimulationState = SIMULATION_STATUS.DISABLED;
        if (SimulateButton != null) SimulateButton.setIcon(R.drawable.icon_simulate_disabled);
    }
    void ChangeStateToSimulating () {
        SimulationState = SIMULATION_STATUS.ON;
        if (SimulateButton != null) SimulateButton.setIcon(R.drawable.icon_simulate_stop);
        if (SettingsButton != null) SettingsButton.setIcon(R.drawable.icon_settings_disabled);
    }
    void ChangeStateToNotSimulating () {
        SimulationState = SIMULATION_STATUS.OFF;
        if (SimulateButton != null) SimulateButton.setIcon(R.drawable.icon_simulate_start);
        if (SettingsButton != null) SettingsButton.setIcon(R.drawable.icon_settings);
    }
    double[] RecData = new double[3];
    boolean Purged = false;
    boolean isValidRead=false;
    String PrevString="";
    private void PurgeReceivedBuffer() {
        try {
            Thread.sleep(10);
        } catch (Exception e) {
            e.printStackTrace();
        }
        Purged = true;
    }
    private void DataRecUpdate (byte[] data) {
        String Rec = PrevString + new String(data);
        Log.i("Timing", "Found New data:" + new String(data));
        if (Rec.contains("E") && Rec.contains("[") && Rec.contains("]")) {
            PrevString = "";
            try {
                String result = Rec.substring(Rec.indexOf("[") + 1, Rec.indexOf("]", Rec.indexOf("[")));
                Log.i("Timing", "Obtained: "+Rec);
                Log.i("Timing", "Extracted: "+result);
                RecData[0] = Double.parseDouble(result) / 1024 * 5;
                isValidRead = true;
            } catch (Exception e) {
                Log.i("Timing", "Error in parse");
            }
        } else if (Purged)
            PrevString = Rec;
    }
    byte PrevByte;
    boolean IsPrevByteSet=true;
    private void DataRecUpdateForHex (byte[] data) {
        if (data.length>=2) {
            short TempVal = (short) ((data[0] & 0xff) | (data[1] << 8));
            RecData[0] = PutBetweenRange(TempVal/1024.0*5.0, AnalogInLimits[0], AnalogInLimits[1]);
            Log.i("Timing", "New Data: " + TempVal);
            isValidRead = true;
        } /*else if (data.length==1) {
            if (IsPrevByteSet) {
                IsPrevByteSet = false;
                byte UpdatedData[] = {PrevByte, data[0]};
                short TempVal = (short) ((UpdatedData[0] & 0xff) | (UpdatedData[1] << 8));
                RecData[0] = PutBetweenRange(TempVal/1024f*5f, -5, 5);
                Log.i("Timing", "Found second byte making a total value: " + TempVal);
                isValidRead = true;
            } else {
                IsPrevByteSet = true;
                PrevByte = data[0];
                Log.i("Timing", "Found first byte.");
            }
        }*/ else {
            Log.i("Timing", "Received data size: " + data.length);
        }
    }

    private void RequestAI() {
        byte[] OutBytes= {(byte)0x32,0,0,};
        arduino.send(OutBytes);
    }
    private void WriteToUSB(double Value) {
        arduino.send(ConvertToIntTSendBytes(ConvertFloatToIntForAO(Value)));
        Log.i("Timing", "Writing Command");
        //SendToUSB(ConvertToIntTSendBytes(ConvertFloatToIntForAO(Value)));
        //port.write(ConvertToIntTSendBytes(ConvertFloatToIntForAO(Value)),10);//Math.round(Model.T_S*1000f));
        //mSerialIoManager.writeAsync("AA".getBytes());
    }

    private long ConvertFloatToIntForAO (double OutFloat) {
        return Math.round(OutFloat*51.0);
    }

    private byte[] ConvertToIntTSendBytes (long Out) {
        byte[] OutBytes= {(byte)0x31, 0,0};
        if (Math.abs(Out)>=255)
            OutBytes[1] = (byte) 0xff;
        else
            OutBytes[1] = (byte) (Math.abs(Out) & 0x0ff);
        if (Out>0)
            OutBytes[2] = 0x00;
        else
            OutBytes[2] = 0x01;
        //Log.i("Timing", String.format("ing string: 0x%2X, 0x%2X, 0x%2X", OutBytes[0], OutBytes[1], OutBytes[2]));
        return OutBytes;
    }
    public double PutBetweenRange (double value, double MinValue, double MaxValue) {
        if (value>MaxValue)
            return MaxValue;
        if (value<MinValue)
            return MinValue;
        return value;
    }

    /*
    Async task for implementing algorithms in real time
    */
    private class Simulate extends AsyncTask <Integer, Integer, Integer> {
        double[][] Input;
        double[][] Output;
        double[][] PreparedSignals;
        double Time;
        double[] ReadTimes = {0,0,0,0};
        DataPoint[] DataPoints;
        boolean WaitedTS = true;
        @Override
        protected Integer doInBackground(Integer... Params) {
            double[] PValues = new double[6];
            DataPoints = new DataPoint[1000];



            long StartTime = System.currentTimeMillis();
            int NotOfTimesSend = 1;

            Log.i("Timing", "Started work");

            isValidRead = true;
            long LastWrittenTime=0;
            Log.i("Timing", "Simulation Background");
            while(!this.isCancelled()) {
                if (!Purged)
                    PurgeReceivedBuffer();
                Time = (System.currentTimeMillis()-StartTime)/1000.0;
                Model.SimulationTime = Time;
                if ((
                        (((int)(System.currentTimeMillis() - StartTime))%Math.round(Model.PlannedT_S*1000)) == 0)
                        &&
                        (System.currentTimeMillis() != LastWrittenTime)
                        ) {
                    LastWrittenTime = System.currentTimeMillis();
                    RequestAI();
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    WaitedTS = false;
                    //Log.i("Timing", "T_S: "+Math.round(Model.ActualT_S*1000));
                }
                if (isValidRead) {
                    isValidRead  = false;
                    PutElementToFIFO(ReadTimes, Time);
                    double MovingAverageTS=0;
                    for (int i=0; i<(ReadTimes.length-1); i++)
                        MovingAverageTS = MovingAverageTS + (ReadTimes[i]-ReadTimes[i+1]);
                    MovingAverageTS = MovingAverageTS/(ReadTimes.length-1);
                    Model.ActualT_S = ReadTimes[0] - ReadTimes[1];
                    if ((MovingAverageTS > 1.2*Model.PlannedT_S) && (NotOfTimesSend<10))
                        NotOfTimesSend++;
                    if ((MovingAverageTS < 0.8*Model.PlannedT_S) && (NotOfTimesSend>1))
                        NotOfTimesSend--;
                    /*Log.i("Timing", "TS past: " + ReadTimes[0] );
                    Log.i("Timing", "TS present: " + ReadTimes[1]);
                    Log.i("Timing", "Moving TS Average: " + MovingAverageTS );
                    Log.i("Timing", "Number of times send write: " + NotOfTimesSend);/**/
                    for (int i=0; i<Input.length; i++)
                        Input[i] = PutElementToFIFO(Input[i], RecData[i]);
                    for (int i = 0; i< PreparedSignals.length; i++)
                        PreparedSignals[i] = PutElementToFIFO(PreparedSignals[i],
                                GeneratedSignals[i].GetValue(Time));
                    if (Model.ActualT_S > 0) {
                        double[] TempOutput = Model.RunAlgorithms(
                                GetParameters(),
                                PreparedSignals,
                                Input,
                                Output
                        );
                        for (int i=0; i<TempOutput.length; i++)
                            Output[i] = PutElementToFIFO(Output[i], TempOutput[i]);
                    }
                    //for (int i=0; i<NotOfTimesSend; i++)
                    WriteToUSB(PutBetweenRange(Output[0][0], AnalogOutLimits[0], AnalogOutLimits[1]));
                    publishProgress(0);
                }
                try {
                    Model.PlannedT_S = Double.parseDouble(ModelSamplingTime.getText().toString())/1000.0;
                } catch (Exception e) {
                    Model.PlannedT_S = ReadSettingsPositions()[Arrays.asList(SettingsDBColumns).indexOf("SamplingTime")]/1000.0;
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... Params) {
            double[] SignalsToPlot = Model.OutGraphSignals(
                    GetParameters(),
                    PreparedSignals,
                    Input,
                    Output
            );
            String InstantValues;
            InstantValues = getString(R.string.TIME) + ": " + Time;
            InstantValues = InstantValues + "\n" + getString(R.string.ACTUAL_SAMPLING_TIME) + ": " + Model.ActualT_S;
            int Iteration=0;
            for (int i=0; i<ModelGraphs.length; i++) {
                for (int j=0; j< ModelGraphs[i].getSeries().size(); j++) {
                    ((LineGraphSeries<DataPoint>)(ModelGraphs[i].getSeries().get(j))).appendData(
                            new DataPoint(
                                    Time,
                                    PutBetweenRange(SignalsToPlot[Iteration],TrajectoryLimits[0], TrajectoryLimits[1])),
                            true,
                            ReadSettingsPositions()[Arrays.asList(SettingsDBColumns)
                                    .indexOf("ChartHistoryLength")]
                    );
                    InstantValues = InstantValues + "\n" + Model.Figures[i].Trajectories[j] + ": " + SignalsToPlot[Iteration];
                    Iteration++;
                }
            }
            InstantaneousValues.setText(InstantValues);
        }

        @Override
        protected void onPreExecute () {
            Purged = false;
            try {
                Model.PlannedT_S = Double.parseDouble(ModelSamplingTime.getText().toString())/1000.0;
            } catch (Exception e) {
                Model.PlannedT_S = ReadSettingsPositions()[Arrays.asList(SettingsDBColumns).indexOf("SamplingTime")]/1000.0;
            }
            Input = new double[Model.NoOfInputs][Model.NoOfPastInputsRequired+1];
            Output = new double[Model.NoOfOutputs][Model.NoOfPastOuputsRequired+1];
            PreparedSignals = new double[Model.SignalGenerators.length][Model.NoOfPastGeneratedValuesRequired+1];
            for (int i=0; i<Input.length; i++) {
                for (int j=0; j<Input[i].length; j++)
                    Input[i][j] = 0;
            }
            for (int i=0; i<Output.length; i++) {
                for (int j=0; j<Output[i].length; j++)
                    Output[i][j] = 0;
            }
            for (int i = 0; i< PreparedSignals.length; i++) {
                for (int j = 0; j< PreparedSignals[i].length; j++)
                    PreparedSignals[i][j] = 0;
            }


            for (int i=0; i<ModelGraphs.length; i++) {
                for (int j=0; j< ModelGraphs[i].getSeries().size(); j++) {
                    ((LineGraphSeries<DataPoint>)(ModelGraphs[i].getSeries().get(j))).resetData(new DataPoint[0]);
                }
            }
        }
        protected double[] GetParameters () {
            double[] ParameterValues = new double[Model.Parameters.length];
            for (int i=0; i<ParameterValues.length; i++) {
                try {
                    ParameterValues[i] = Double.parseDouble(ModelParams[i].getText().toString());
                } catch (Exception e){
                    ParameterValues[i] = 0;
                }
            }
            return ParameterValues;
        }
        protected double[] PutElementToFIFO (double[] array, double element){
            for (int i=(array.length-1); i>0; i--) {
                array[i] = array[i-1];
            }
            array[0] = element;
            return array;
        }
        protected void onCancelled() {
            SetProperSimulationStatus();
        }
    }

}