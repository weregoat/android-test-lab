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


    /* Listen intermittently to the sensor for a few seconds only to minimize battery usage */
    /* If I am active I can, as well, press the button. Otherwise that should be enough
        to detect movement while carrying the phone around.
        Remember this is about detecting when I am inactive over a long period, presumed dead.
     */
    private static final long SLEEP_INTERVAL = 30000; //  Thirty seconds between checks
    private static final long LISTENING_INTERVAL = 2000; // Listen for two seconds
    private static final double THRESHOLD = 1.0; // This much acceleration to trigger movement


    /*
     * time smoothing constant for low-pass filter
     * 0 ≤ alpha ≤ 1 ; a smaller value basically means more smoothing
     * See: http://en.wikipedia.org/wiki/Low-pass_filter#Discrete-time_realization
     * From: http://blog.thomnichols.org/2011/08/smoothing-sensor-data-with-a-low-pass-filter
     */
    private static final float ALPHA = 0.8f; // Threshold for low-pass filter


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

        /**
         * @see http://en.wikipedia.org/wiki/Low-pass_filter#Algorithmic_implementation
         * @see http://developer.android.com/reference/android/hardware/SensorEvent.html#values
         * @see http://blog.thomnichols.org/2011/08/smoothing-sensor-data-with-a-low-pass-filter
         * @see https://www.built.io/blog/applying-low-pass-filter-to-android-sensor-s-readings
         */
        public void onSensorChanged(SensorEvent event) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            lastAcceleration = currentAcceleration;
            currentAcceleration = (float) Math.sqrt((double) (x * x + y * y + z * z)); // I don't care about specific axis
            float delta = currentAcceleration - lastAcceleration;
            acceleration = Math.abs(acceleration * ALPHA + delta); // Low-pass filter removing the high frequency noise
            Log.d(TAG, String.format("acceleration: %f", acceleration));
            if (acceleration >= THRESHOLD) {
                reset = true;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        accelHandler.removeCallbacks(accelRunnable);
        stopListening();
        logEntry("Destroy accelerometer service", true);
        super.onDestroy();

    }

    private void startListening() {
        if (sensorRunning == false) {
            //logEntry("Start listening to accelerometer", false);
            acceleration = 0.0f;
            currentAcceleration = SensorManager.GRAVITY_EARTH;
            lastAcceleration = SensorManager.GRAVITY_EARTH;
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            sensorManager.registerListener(sensorEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL, 2000000000);
            sensorRunning = true;
        }
    }

    private void stopListening() {
        if (sensorRunning == true) {
            //logEntry("Stop listening to accelerometer", false);
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
