package com.allenwixted.drivar;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.location.Location;
import com.google.android.gms.location.LocationListener;

import android.net.Uri;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.Calendar;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends ActionBarActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    public ImageView imgCircleSpeed;
    public TextView speedo, speedLimitText;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private String LOG_TAG = "Drivar: ";
    private HandleReverseGeoXML obj;
    private SpeedCategoryXML speedObj;
    private int speedLimit;
    private boolean HUDMode = false;


    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        imgCircleSpeed = (ImageView) findViewById(R.id.imgCircleSpeed);
        speedo = (TextView) findViewById(R.id.speed);
        speedLimitText = (TextView) findViewById(R.id.speedLimitText);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.speed);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();


        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Connect the client.
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        // Disconnecting the client invalidates it.
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    public void onConnected(Bundle bundle) {
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(500); // Update location every tenth second

        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(LOG_TAG, "GoogleApiClient connection has been suspend");
    }

    @Override
    public void onLocationChanged(Location location) {

        //Log.i("", location.toString());

        double lat = location.getLatitude();
        double lng = location.getLongitude();
        float acc = location.getAccuracy();

        //construct a new URL whenever the location changes to extract an address
        String reverseGeoURL = "https://reverse.geocoder.cit.api.here.com/6.2/reversegeocode.xml?" +
                "app_id=Za727caY87S8z7es7ybX" +
                "&app_code=FsFRh7kYq-EpEMKVIClOug&gen=9&prox="
                + lat + "," + lng + "," + "50" +
                "&mode=retrieveAddresses";
        Log.i("XML GEO: ", reverseGeoURL);
        getXML(reverseGeoURL);

        Float speed = (location.getSpeed()) * 3.6f;
        DecimalFormat df = new DecimalFormat("###");
        String roundedSpeed = df.format(speed);
        speedo.setText("");
        speedLimitText.setText((String.valueOf(speedLimit)));

        Paint paint = new Paint();
        paint.setFlags(paint.ANTI_ALIAS_FLAG);
        paint.setARGB(255, 0, 169, 255);
        paint.setStyle(Paint.Style.FILL);

        Bitmap bmp = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888);

        Paint strokePaint = new Paint();
        strokePaint.setARGB(255, 0, 0, 0);
        strokePaint.setTextAlign(Paint.Align.CENTER);
        strokePaint.setTextSize(160);
        strokePaint.setTypeface(Typeface.DEFAULT_BOLD);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setFlags(strokePaint.ANTI_ALIAS_FLAG);
        strokePaint.setStrokeWidth(5);

        Paint maxSpeedPaint = new Paint();
        maxSpeedPaint.setARGB(255, 255, 25, 47);
        maxSpeedPaint.setStyle(Paint.Style.STROKE);
        maxSpeedPaint.setFlags(strokePaint.ANTI_ALIAS_FLAG);
        maxSpeedPaint.setStrokeWidth(10);

        Paint recSpeedPaint = new Paint();
        recSpeedPaint.setARGB(255, 204, 201, 112);
        recSpeedPaint.setStyle(Paint.Style.STROKE);
        recSpeedPaint.setFlags(strokePaint.ANTI_ALIAS_FLAG);
        recSpeedPaint.setStrokeWidth(10);

        Paint textPaint = new Paint();
        textPaint.setARGB(255, 255, 255, 255);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(160);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setFlags(textPaint.ANTI_ALIAS_FLAG);

        Canvas canvas = new Canvas(bmp);
        if(HUDMode == true) {
            canvas.scale(1, -1, bmp.getWidth()/2, bmp.getHeight()/2);
        } else {
            canvas.scale(1, 1, bmp.getWidth()/2, bmp.getHeight()/2);
        }

        //NewValue = (((OldValue - OldMin) * (NewMax - NewMin)) / (OldMax - OldMin)) + NewMin
        int scaledSpeed = (int) ((((speed) * (bmp.getWidth()/2 - 80)) / (speedLimit)) + 80);

        //detect day/night
        int night = 0;
        if(isItNight() == true){
            night = 30;
        } else{
            night = 0;
        }

        //if going over recommended speed
        if(scaledSpeed > bmp.getWidth()/2 - 10 - night ){
            paint.setARGB(255, 204, 201, 112);
        }
        //if going over speed limit!
        else if(scaledSpeed > bmp.getWidth()/2 - 10){
            paint.setARGB(255, 255, 25, 47);
        }
        //going at appropriate speed
        else{
            paint.setARGB(255, 0, 169, 255);
        }

        Log.i(LOG_TAG, String.valueOf(scaledSpeed));

        canvas.drawCircle(bmp.getWidth()/2, bmp.getHeight()/2, scaledSpeed, paint);
        canvas.drawCircle(bmp.getWidth()/2, bmp.getHeight()/2, bmp.getWidth()/2 - 10 - night, recSpeedPaint);
        canvas.drawCircle(bmp.getWidth()/2, bmp.getHeight()/2, bmp.getWidth()/2 - 10, maxSpeedPaint);

        canvas.drawText(roundedSpeed, bmp.getWidth()/2, bmp.getHeight()/2 + 60, strokePaint);
        canvas.drawText(roundedSpeed, bmp.getWidth()/2, bmp.getHeight()/2 + 60, textPaint);

        imgCircleSpeed.setImageBitmap(bmp);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(LOG_TAG, "GoogleApiClient connection has failed");
    }

    public void goToLink (View view) {
        goToUrl ( "https://goo.gl/forms/KI5Ve7bGprkcPZLX2");
    }

    private void goToUrl (String url) {
        Uri uriUrl = Uri.parse(url);
        Intent launchBrowser = new Intent(Intent.ACTION_VIEW, uriUrl);
        startActivity(launchBrowser);
    }

    public void getXML(String URL) {

        obj = new HandleReverseGeoXML(URL);
        obj.fetchXML();
        while (obj.parsingComplete);

        String addr = obj.getAddress();
        String encodedAddr = "";
        Log.i(LOG_TAG, encodedAddr);
        try {
            encodedAddr  = URLEncoder.encode(addr, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        String speedURL = "https://geocoder.cit.api.here.com/6.2/geocode.xml?searchtext=" +
                encodedAddr +
                "&responseattributes=none&locationattributes=li&gen=8" +
                "&app_id=Za727caY87S8z7es7ybX" +
                "&app_code=FsFRh7kYq-EpEMKVIClOug";
        Log.i("XML: ",speedURL);

        speedObj = new SpeedCategoryXML(speedURL);
        speedObj.fetchSpeedXML();
        while (speedObj.parsingComplete);
        Log.i("XML MAIN: ", speedObj.getSpeedCategory());
        translateSpeedLimitData(speedObj.getSpeedCategory());
    }

    public int translateSpeedLimitData(String speedCategory){

        if(speedCategory.equals("SC1")){
            speedLimit = 150;
        }
        else if(speedCategory.equals("SC2")){
            speedLimit = 120;
        }
        else if(speedCategory.equals("SC3")){
            speedLimit = 100;
        }
        else if(speedCategory.equals("SC4")){
            speedLimit = 80;
        }
        else if(speedCategory.equals("SC5")){
            speedLimit = 60;
        }
        else if(speedCategory.equals("SC6")){
            speedLimit = 50;
        }
        else if(speedCategory.equals("SC7")){
            speedLimit = 30;
        }
        else if(speedCategory.equals("SC8")){
            speedLimit = 15;
        }
        else {
            Log.i(LOG_TAG, "speed limit unknown");
        }
        return speedLimit;
    }

    public boolean isItNight(){
        Boolean isNight;
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        if(hour < 6 || hour > 18){
            isNight = true;
        } else {
            isNight = false;
        }

        return isNight;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.flip:

                if(HUDMode == false){
                    HUDMode = true;
                    Log.i(LOG_TAG, "TRUE");
                }
                else {
                    HUDMode = false;
                    Log.i(LOG_TAG, "FALSE");
                }

                if(HUDMode == true){
                    Toast.makeText(this, "HUD Mode", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Mounted Screen Mode", Toast.LENGTH_SHORT).show();
                }

                break;
        }
        return true;
    }
}


