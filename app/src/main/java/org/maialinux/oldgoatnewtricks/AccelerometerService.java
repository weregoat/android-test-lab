package org.maialinux.oldgoatnewtricks;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import org.joda.time.Interval;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;


public class AccelerometerService extends Service {


    /* Listen to the sensor only for a few seconds seconds to minimize battery usage */
    /* If I am active I can, as well, press the button. Otherwise that should be enough
        to detect movement while carrying the phone around.
        Remember this is about detecting when I was inactive for a long period, presumed dead.
     */
    private static final long SLEEP_INTERVAL = 300000; // five minutes
    private static final long LISTENING_INTERVAL = 30000; // 30 seconds
    private static final double THRESHOLD = 0.5;


    private static final String TAG = "AccelerometerService";
    private float acceleration = 0.0f;
    private float currentAcceleration;
    private float lastAcceleration;
    private boolean reset = false;
    private Intent broadCastIntent;
    SensorManager sensorManager;
    private boolean sensorRunning = false;

    Handler accelHandler = new Handler();
    Runnable accelRunnable = new Runnable() {
        @Override
        public void run() {

            long delay = SLEEP_INTERVAL;
            if (sensorRunning == true) {
                stopListening();
            } else {
                startListening();
                delay = LISTENING_INTERVAL;
            }
            if (reset == true) {
                broadCastIntent.putExtra(AlertService.RESET_MESSAGE, true);
                sendBroadcast(broadCastIntent);
                reset = false;
                stopListening();
                delay = SLEEP_INTERVAL;
                logEntry("Accelerometer Service sent reset message", false);
            }
            accelHandler.postDelayed(this, delay);
        }

    };

    public AccelerometerService() {

    }

    @Override
    public void onCreate() {
        broadCastIntent = new Intent(AlertService.BROADCAST_ACTION);
        logEntry("Create accelerometer service", false);
        accelRunnable.run();
    }


    @Override
    public IBinder onBind(Intent intent) {
        //return mBinder;
        return null;
    }

        private final SensorEventListener sensorEventListener = new SensorEventListener() {


            @Override
            public void onSensorChanged(SensorEvent se) {
                float x = se.values[0];
                float y = se.values[1];
                float z = se.values[2];
                lastAcceleration = currentAcceleration;
                currentAcceleration = (float) Math.sqrt((double) (x * x + y * y + z * z)); //Vector
                float delta = currentAcceleration - lastAcceleration;
                acceleration = Math.abs(acceleration * 0.9f + delta); // perform low-cut filter
                Log.d(TAG, String.format("acceleration: %f", acceleration));
                if (acceleration >= THRESHOLD){
                    reset = true;
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
        };


    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy()
    {
        accelHandler.removeCallbacks(accelRunnable);
        stopListening();
        logEntry("Destroy accelerometer service", true);
        super.onDestroy();

    }

    private void startListening()
    {
        if (sensorRunning == false) {
            logEntry("Start listening to accelerometer", false);
            acceleration = 0.0f;
            currentAcceleration = SensorManager.GRAVITY_EARTH;
            lastAcceleration = SensorManager.GRAVITY_EARTH;
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL, 2000000000);
            sensorRunning = true;
        }
    }

    private void stopListening()
    {
        if (sensorRunning == true) {
            logEntry("Stop listening to accelerometer", false);
            sensorManager.unregisterListener(sensorEventListener);
            sensorRunning = false;
        }
    }

    private void logEntry(String message, boolean toast) {
        if (toast) {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        }
        Log.d(TAG, message);
        Intent logIntent = new Intent(AlertService.BROADCAST_ACTION);
        logIntent.putExtra("message", message);
        sendBroadcast(logIntent);
    }


}
