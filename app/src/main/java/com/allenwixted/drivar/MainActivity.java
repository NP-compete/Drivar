package com.allenwixted.drivar;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

import static java.lang.Math.round;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ALLEN WIXTED: DRIVAR";
    LocationManager locationManager;
    LocationListener locationListener;
    TextView text;
    TextView speed;
    View currentSpeedView;
    View speedLimitSignView;
    View recommendedSpeed;
    View speedLimitCircle;
    private int lmDistance = 5;
    private int lmTime = 250;
    private int speedLimit = 50;
    private int usersSpeed = 0;
    private int drivingRecommendedSpeed = 30;
    private boolean weatherBad = false;
    private double metricImperial = 3.6;
    private Location previousLocation = null;
    private double distance = 0;

    public double scale(double valueIn, double baseMin, double baseMax, double limitMin, double limitMax) {
        return ((limitMax - limitMin) * (valueIn - baseMin) / (baseMax - baseMin)) + limitMin;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        //if we have permission...
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            //make sure we have explicit permission for this particular service
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION + Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
                //parameters include provider, minimum time before next request, minimum distance travelled, the listener
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, lmTime, lmDistance, locationListener);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        text = (TextView) findViewById(R.id.text);
        speed = (TextView) findViewById(R.id.speed);
        currentSpeedView = findViewById(R.id.currentSpeed);
        recommendedSpeed = findViewById(R.id.recommendedSpeed);
        speedLimitSignView = findViewById(R.id.speedLimitSignView);
        speedLimitCircle = findViewById(R.id.speedLimitCircle);

        //Get screen dimensions
        Configuration configuration = this.getResources().getConfiguration();
        int smallestScreenWidthDp = configuration.smallestScreenWidthDp; //The smallest screen size an application will see in normal operation, corresponding to smallest screen width resource qualifier.
        Log.i(TAG, String.valueOf(smallestScreenWidthDp));

        int scaledScreenWidthForCSV = (int) scale(120/320,0,320,0,smallestScreenWidthDp);
        int scaledScreenWidthForRSV = (int) scale(1,0,320,0,smallestScreenWidthDp);

        currentSpeedView.setScaleX(scaledScreenWidthForCSV);
        currentSpeedView.setScaleY(scaledScreenWidthForCSV);
        recommendedSpeed.setScaleX(scaledScreenWidthForRSV);
        recommendedSpeed.setScaleY(scaledScreenWidthForRSV);
        speedLimitCircle.setScaleX(scaledScreenWidthForRSV);
        speedLimitCircle.setScaleY(scaledScreenWidthForRSV);

        //Swipe recognition
        Context context = getApplicationContext();
        currentSpeedView.setOnTouchListener(new OnSwipeTouchListener(context) {
            @Override
            public void onSwipeLeft() {
                text.animate().scaleY(1f).setDuration(300);
                speed.animate().scaleY(1f).setDuration(300);
                speedLimitSignView.animate().scaleY(1f).setDuration(300);
                currentSpeedView.animate().scaleY(1f).setDuration(300);

                Context context = getApplicationContext();
                CharSequence text = "In Car Mode Activated";
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
            }

            @Override
            public void onSwipeRight() {
                super.onSwipeRight();
                text.animate().scaleY(-1f).setDuration(300);
                speed.animate().scaleY(-1f).setDuration(300);
                speedLimitSignView.animate().scaleY(-1f).setDuration(300);
                currentSpeedView.animate().scaleY(-1f).setDuration(300);
                Context context = getApplicationContext();
                CharSequence text = "HUD Mode Activated";
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
            }
        });

        locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                String speed = String.valueOf((int) Math.round(location.getSpeed()*metricImperial));
                usersSpeed = (int) Math.round(location.getSpeed()*metricImperial);
                text.setText(speed);

                double scaledSpeed = scale((int) Math.round(location.getSpeed()*metricImperial), 0, speedLimit, 1, 2.5);

                currentSpeedView.animate().scaleX((float) scaledSpeed).scaleY((float) scaledSpeed).setDuration(250);

                calculateUIColor();

                String lat = String.valueOf(location.getLatitude());
                String lng = String.valueOf(location.getLongitude());

                //Delay JSON Requests
                // map current speed from 0km/h - 130km/h to 100m - 2000m
                int distanceMap = (int) scale(usersSpeed, 0, 130, 10, 1000);
                Log.i(TAG, String.valueOf(distanceMap));


                if (previousLocation != null) {
                    //calculate distance
                    distance = distance + location.distanceTo(previousLocation);
                    Log.i(TAG, String.valueOf(distance) + " distance travelled");
                    if(distance > distanceMap){
                        distance = 0;

                        //Speed URL
                        SpeedLimitRequest task = new SpeedLimitRequest();
                        String speedURL = "https://route.cit.api.here.com/routing/7.2/getlinkinfo.json?waypoint=" +
                                lat +
                                "%2C" +
                                lng +
                                "&app_id=" +
                                "F95P9Ir1dbtspwKRTKtE" +
                                "&app_code=" +
                                "VtVWqzE5hlQfoozVs1jujA";
                        task.execute(String.valueOf(speedURL));

                        //Weather URL
                        WeatherRequest weatherRequest = new WeatherRequest();
                        String weatherURL = "http://api.openweathermap.org/data/2.5/weather?lat=" + lat +
                                "&lon=" + lng +
                                "&appid=b19ba30c78778b7a632b1c8c15c51747";
                        Log.i(TAG, speedURL);
                        weatherRequest.execute(weatherURL);

                        TwilightCalculator dayNight = new TwilightCalculator();
                        long time= System.currentTimeMillis();
                        dayNight.calculateTwilight(time, location.getLatitude(), location.getLongitude());

                        if(dayNight.mState == 1) {
                            Log.i("DAY / NIGHT", "it is night and clear");
                            recommendedSpeed.animate().alpha(255).setDuration(10000);
                            recommendedSpeed.animate().scaleX((float) 0.9).scaleY((float) 0.9).setDuration(300);
                            drivingRecommendedSpeed = (int) (speedLimit * 0.9);
                        } else if (dayNight.mState == 0) {
                            Log.i("DAY / NIGHT", "it is day and clear");
                            recommendedSpeed.animate().alpha(0).setDuration(10000);
                            recommendedSpeed.animate().scaleX((float) 1.0).scaleY((float) 1.0).setDuration(300);
                            drivingRecommendedSpeed = speedLimit;
                        } else if (dayNight.mState == 1 && weatherBad == true){
                            Log.i("DAY / NIGHT", "it is night and bad weather");
                            recommendedSpeed.animate().alpha(255).setDuration(10000);
                            recommendedSpeed.animate().scaleX((float) 0.8).scaleY((float) 0.8).setDuration(300);
                            drivingRecommendedSpeed = (int) (speedLimit * 0.8);
                        } else if (dayNight.mState == 0 && weatherBad == true){
                            Log.i("DAY / NIGHT", "it is day and bad weather");
                            recommendedSpeed.animate().alpha(255).setDuration(10000);
                            recommendedSpeed.animate().scaleX((float) 0.9).scaleY((float) 0.9).setDuration(300);
                            drivingRecommendedSpeed = (int) (speedLimit * 0.9);
                        }
                    }
                }
                previousLocation = location;
            }

            @Override
            public void onStatusChanged(String s, int i, Bundle bundle) {

            }

            @Override
            public void onProviderEnabled(String s) {

            }

            @Override
            public void onProviderDisabled(String s) {

            }
        };

        //if running pre SDK 23 marshmallow
        if(Build.VERSION.SDK_INT < 23){ //then get location as already accepted at install
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, lmTime, lmDistance, locationListener);
        } else {
            //if we don't have permission then ask
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            } else { //if we already do then get location
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, lmTime, lmDistance, locationListener);
            }
        }

        CharSequence text = "Swipe Left and Right for Mirrored Mode";
        int duration = Toast.LENGTH_LONG;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();

        CharSequence feedback = "We made some changes to the app....";
        Toast toast2 = Toast.makeText(context, feedback, duration);
        toast2.show();

        CharSequence feedback2 = "Let us know if the speed limit doesn't update quick enough.";
        Toast toast3 = Toast.makeText(context, feedback2, duration);
        toast3.show();
    }

    private void calculateUIColor() {

        if(usersSpeed >= drivingRecommendedSpeed && usersSpeed <= speedLimit){
            Drawable background = currentSpeedView.getBackground();
            if (background instanceof ShapeDrawable) {
                ((ShapeDrawable)background).getPaint().setColor(getResources().getColor(R.color.colorRecommended));
            } else if (background instanceof GradientDrawable) {
                ((GradientDrawable)background).setColor(getResources().getColor(R.color.colorRecommended));
            } else if (background instanceof ColorDrawable) {
                ((ColorDrawable)background).setColor(getResources().getColor(R.color.colorRecommended));
            }
        } else if(usersSpeed > speedLimit){
            Drawable background = currentSpeedView.getBackground();
            if (background instanceof ShapeDrawable) {
                ((ShapeDrawable)background).getPaint().setColor(getResources().getColor(R.color.colorSpeedLimit));
            } else if (background instanceof GradientDrawable) {
                ((GradientDrawable)background).setColor(getResources().getColor(R.color.colorSpeedLimit));
            } else if (background instanceof ColorDrawable) {
                ((ColorDrawable)background).setColor(getResources().getColor(R.color.colorSpeedLimit));
            }
        } else if(usersSpeed < speedLimit && usersSpeed < drivingRecommendedSpeed){
            Drawable background = currentSpeedView.getBackground();
            if (background instanceof ShapeDrawable) {
                ((ShapeDrawable)background).getPaint().setColor(getResources().getColor(R.color.colorNotSpeeding));
            } else if (background instanceof GradientDrawable) {
                ((GradientDrawable)background).setColor(getResources().getColor(R.color.colorNotSpeeding));
            } else if (background instanceof ColorDrawable) {
                ((ColorDrawable)background).setColor(getResources().getColor(R.color.colorNotSpeeding));
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus){
            View mDecorView = getWindow().getDecorView();
            mDecorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    private class SpeedLimitRequest extends AsyncTask<String, Void, String>{

        @Override
        protected String doInBackground(String... urls) {
            String result = "";
            URL url;
            HttpURLConnection urlConnection = null;

            try {
                url = new URL(urls[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                InputStream in = urlConnection.getInputStream();
                InputStreamReader reader = new InputStreamReader(in);

                int data = reader.read();
                while (data != -1){
                    char current = (char) data;
                    result += current;
                    data = reader.read();
                }
                return result;
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }

        //called when the do in background has completed and is passed the return
        //do in background cant interact with the UI, this can
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            Log.i(TAG, "RESULT " + result);

            if(result != null && !Objects.equals(result, "")){
                try {
                    JSONObject jsonObject = new JSONObject(result);
                    if (jsonObject.getString("response") != null) {

                        String response = jsonObject.getString("response");
                        try {
                            JSONObject linkJSON = new JSONObject(response);
                            String link = linkJSON.getString("link");
                            try {
                                JSONArray jsonArray = new JSONArray(link);

                                if (jsonArray.length() > 0) {
                                    for (int i = 0; i < jsonArray.length(); i++) {
                                        JSONObject jsonPart = jsonArray.getJSONObject(i);
                                        speedLimit = (int) Math.round(jsonPart.getDouble("speedLimit") * metricImperial);
                                        if (speedLimit != 0) {
                                            speed.setText(String.valueOf(speedLimit));
                                        } else {
                                            speed.setText("?");
                                        }
                                    }
                                } else {
                                    speed.setText("?");
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } else {
                        speed.setText("?");
                    }
                }catch(JSONException e){

                }
            }

        }
    }

    private class WeatherRequest extends AsyncTask<String, Void, String>{

        @Override
        protected String doInBackground(String... urls) {
            String result = "";
            URL url;
            HttpURLConnection urlConnection = null;

            try {
                url = new URL(urls[0]);
                urlConnection = (HttpURLConnection) url.openConnection();
                InputStream in = urlConnection.getInputStream();
                InputStreamReader reader = new InputStreamReader(in);

                int data = reader.read();
                while (data != -1){
                    char current = (char) data;
                    result += current;
                    data = reader.read();
                }
                //Log.i(TAG, result);

                return result;
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        //called when the do in background has completed and is passed the return
        //do in background cant interact with the UI, this can
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            if(result != null && !Objects.equals(result, "")){
                try {
                    JSONObject jsonObject = new JSONObject(result);
                    String response = jsonObject.getString("weather");

                    try {
                        JSONArray jsonArray = new JSONArray(response);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jsonPart = jsonArray.getJSONObject(i);
                            int code = jsonPart.getInt("id");

                            if (code > 799 && code < 899) {
                                weatherBad = false;
                            } else {
                                weatherBad = true;
                            }
                        }

                    } catch (JSONException e) {
                        Context context = getApplicationContext();
                        CharSequence text = "Error getting Weather Data. Try Updating";
                        int duration = Toast.LENGTH_LONG;

                        Toast toast = Toast.makeText(context, text, duration);
                        toast.show();
                    }
                } catch (JSONException e) {

                }

                try {
                    JSONObject jsonObject = new JSONObject(result);
                    String response = jsonObject.getString("sys");

                    try{
                        if(response != null) {
                            JSONObject jsonPart = new JSONObject(response);
                            String country = jsonPart.getString("country");

                            if (country.equals("US") || country.equals("UK")) {
                                metricImperial = 2.23;
                            } else {
                                metricImperial = 3.6;
                            }
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

