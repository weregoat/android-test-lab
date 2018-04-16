package org.maialinux.oldgoatnewtricks;



import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {


    private static final long INTERVAL = 3000;


    TextView timerTextView;
    Button resetButton;


    public Handler timerHandler;

    long expirationTime;

    Ringtone ringtone;
    float mAccel; // acceleration apart from gravity
    float mAccelCurrent; // current acceleration including gravity
    float mAccelLast; // last acceleration including gravity

    String[] log = new String[5];
    
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {


            if (mAccel >= 0.5) {
                if (ringtone.isPlaying()) {
                    ringtone.stop();
                }
                expirationTime = System.currentTimeMillis() + INTERVAL;
            }

            long millis = expirationTime - System.currentTimeMillis();
            long seconds = Math.round(millis/1000);


            if (millis <= 0) {
                timerHandler.removeCallbacks(timerRunnable);
                if (ringtone.isPlaying() == false) {
                    ringtone.play();
                }
            } else {

                Calendar calendar = Calendar.getInstance();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String current = String.format("%s: Accel = %f, Seconds left = %s", format.format(calendar.getTime()), mAccel, seconds);
                System.arraycopy(log, 0, log, 1, 4);
                log[0] = current;
                String text = "";
                for (int i = 4; i >= 0; i--) {
                    text = String.format("%s\n%s", log[i], text);
                }
                timerTextView.setText(text);

                timerHandler.postDelayed(this, 1000);
            }

        }
    };

    private final SensorEventListener sensorEventListener = new SensorEventListener() {


        @Override
        public void onSensorChanged(SensorEvent se) {
            float x = se.values[0];
            float y = se.values[1];
            float z = se.values[2];
            mAccelLast = mAccelCurrent;
            mAccelCurrent = (float) Math.sqrt((double) (x * x + y * y + z * z));
            float delta = mAccelCurrent - mAccelLast;
            mAccel = Math.abs(mAccel * 0.9f + delta); // perform low-cut filter
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };





    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        timerTextView = findViewById(R.id.main_activity_view);

        mAccel = 0.0f;
        mAccelCurrent = SensorManager.GRAVITY_EARTH;
        mAccelLast = SensorManager.GRAVITY_EARTH;


        timerHandler = new Handler();
        expirationTime = System.currentTimeMillis() + INTERVAL;

        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        ringtone = RingtoneManager.getRingtone(getApplicationContext(), uri);

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);


        timerRunnable.run();


        resetButton = findViewById(R.id.button);
        resetButton.setOnClickListener(new View.OnClickListener() {
                                           @Override
                                           public void onClick(View view) {
                                               if (ringtone.isPlaying()) {
                                                   ringtone.stop();

                                               }
                                               expirationTime = System.currentTimeMillis() + INTERVAL;
                                               timerHandler.removeCallbacks(timerRunnable);
                                               timerRunnable.run();
                                           }
                                       }

            );


    }







}

