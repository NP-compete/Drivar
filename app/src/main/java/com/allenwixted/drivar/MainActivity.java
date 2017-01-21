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

import static java.lang.Math.round;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    LocationManager locationManager;
    LocationListener locationListener;
    TextView text;
    TextView speed;
    View currentSpeedView;
    View speedLimitSignView;
    View recommendedSpeed;
    private int lmDistance = 50;
    private int lmTime = 3000;
    private int speedLimit = 30;
    private int usersSpeed = 0;
    private int drivingRecommendedSpeed = 30;

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
                //locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 150, locationListener);
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
                String speed = String.valueOf((int) Math.round(location.getSpeed()*3.6));
                usersSpeed = (int) Math.round(location.getSpeed()*3.6);
                text.setText(speed);

                double scaledSpeed = scale((int) Math.round(location.getSpeed()*3.6), 0, speedLimit, 1, 2.5);

                currentSpeedView.animate().scaleX((float) scaledSpeed).scaleY((float) scaledSpeed).setDuration(250);

                calculateUIColor();

                String lat = String.valueOf(location.getLatitude());
                String lng = String.valueOf(location.getLongitude());

                SpeedLimitRequest task = new SpeedLimitRequest();

                String updatedURL = "https://route.cit.api.here.com/routing/7.2/getlinkinfo.json?waypoint=" +
                        lat +
                        "%2C" +
                        lng +
                        "&app_id=" +
                        "F95P9Ir1dbtspwKRTKtE" +
                        "&app_code=" +
                        "VtVWqzE5hlQfoozVs1jujA";
                Log.i(TAG, updatedURL);
                task.execute(String.valueOf(updatedURL));

                TwilightCalculator dayNight = new TwilightCalculator();
                long time= System.currentTimeMillis();
                dayNight.calculateTwilight(time, location.getLatitude(), location.getLongitude());

                if(dayNight.mState == 1) {
                    Log.i("DAY / NIGHT", "it is night");
                    recommendedSpeed.animate().alpha(255).setDuration(10000);
                    drivingRecommendedSpeed = (int) (speedLimit * 0.9);
                } else {
                    Log.i("DAY / NIGHT", "it is day");
                    recommendedSpeed.animate().alpha(0).setDuration(10000);
                    drivingRecommendedSpeed = speedLimit;
                }
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
            //if we dont have permission then ask
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            } else { //if we already do then get location
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, lmTime, lmDistance, locationListener);
            }
        }

        CharSequence text = "Swipe Left for Normal, Right for HUD Mode";
        int duration = Toast.LENGTH_LONG;

        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    private void calculateUIColor() {

        if(usersSpeed >= drivingRecommendedSpeed){
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
        } else if(usersSpeed > speedLimit && usersSpeed > drivingRecommendedSpeed){
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

            try {
                JSONObject jsonObject = new JSONObject(result);
                String response = jsonObject.getString("response");
                try{
                    JSONObject linkJSON = new JSONObject(response);
                    String link = linkJSON.getString("link");

                    JSONArray jsonArray = new JSONArray(link);

                    for (int i = 0; i < jsonArray.length(); i++) {
                        JSONObject jsonPart = jsonArray.getJSONObject(i);
                        speedLimit = (int) Math.round(jsonPart.getDouble("speedLimit")*3.6);
                        speed.setText(String.valueOf(speedLimit));
                        Log.i("JSON Speed Limit", String.valueOf((speedLimit)));
                    }

                } catch (JSONException e){
                    Context context = getApplicationContext();
                    CharSequence text = "Error getting Speed Limit. Try Updating";
                    int duration = Toast.LENGTH_LONG;

                    Toast toast = Toast.makeText(context, text, duration);
                    toast.show();
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

}
