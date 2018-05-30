package org.maialinux.oldgoatnewtricks;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.joda.time.Interval;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.util.List;


public class AccelerometerService extends Service {


    /* Listen intermittently to the sensor for a few seconds only to minimize battery usage */
    /* If I am active I can, as well, press the button. Otherwise that should be enough
        to detect movement while carrying the phone around.
        Remember this is about detecting when I am inactive over a long period, presumed dead.
     */
    private static final long SLEEP_INTERVAL = 30000; //  Thirty seconds between checks
    private static final long LISTENING_INTERVAL = 5000; // Listen for five seconds
    private static final double ACCELERATION_THRESHOLD = 0.5f; // This much acceleration to trigger movement
    private static final double GEOMAGNETIC_THRESHOLD = 10.0f; // This much change on any axis to trigger rest

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
    private float[] lastGeoMagneticValues;

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
                logEntry("Accelerometer Service sent reset message", false);
                /* After a reset, sleep for a longer time */
                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                delay = AlertService.getLong(sharedPreferences, "interval", String.valueOf(AlertService.INTERVAL), delay/1000, TAG)*(60000/10);
                logEntry(String.format("Accelerometer service sleeping for %d seconds", delay/1000), false);
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

    private final SensorEventListener geoMagneticEventListener = new SensorEventListener() {


        @Override

        public void onSensorChanged(SensorEvent event) {
            if (lastGeoMagneticValues != null && reset == false) {
                for (int i = 0; i < 3; i++) {
                    float delta = Math.abs(event.values[i] - lastGeoMagneticValues[i]);
                    if (delta >= GEOMAGNETIC_THRESHOLD) {
                        logEntry(String.format("Geomagnetic change of %f", delta), false);
                        logEntry("Geomagnetic sensor triggered", true);
                        reset = true;
                        break;
                    }
                }
            }
            lastGeoMagneticValues = event.values.clone();

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private final SensorEventListener proximityEventListener = new SensorEventListener() {


        @Override

        public void onSensorChanged(SensorEvent event) {
            if (reset == false) {
                float proximityValue = event.values[0];
                if (proximityValue < event.sensor.getMaximumRange()) {
                    Log.d(TAG, String.format("Proximity triggered with a value of %f", event.values[0]));
                    logEntry("Proximity sensor triggered", true);
                    reset = true;
                }
            }


        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private final SensorEventListener accelerometerEventListener = new SensorEventListener() {


        @Override

        /**
         * @see http://en.wikipedia.org/wiki/Low-pass_filter#Algorithmic_implementation
         * @see http://developer.android.com/reference/android/hardware/SensorEvent.html#values
         * @see http://blog.thomnichols.org/2011/08/smoothing-sensor-data-with-a-low-pass-filter
         * @see https://www.built.io/blog/applying-low-pass-filter-to-android-sensor-s-readings
         */
        public void onSensorChanged(SensorEvent event) {
            if (reset == false) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                lastAcceleration = currentAcceleration;
                currentAcceleration = (float) Math.sqrt((double) (x * x + y * y + z * z)); // I don't care about specific axis
                float delta = currentAcceleration - lastAcceleration;
                acceleration = Math.abs(acceleration * ALPHA + delta); // Low-pass filter removing the high frequency noise
                Log.d(TAG, String.format("acceleration: %f", acceleration));
                if (acceleration >= ACCELERATION_THRESHOLD) {
                    logEntry("Accelerometer sensor triggered", true);
                    reset = true;
                }
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
            List<Sensor> sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);
            for (int i=0; i <sensorList.size(); i++) {
                Log.d(TAG, sensorList.get(i).getName());
                int sensorType = sensorList.get(i).getType();
                switch(sensorType) {
                    case Sensor.TYPE_ACCELEROMETER:
                    case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
                        sensorManager.registerListener(accelerometerEventListener, sensorManager.getDefaultSensor(sensorType), SensorManager.SENSOR_DELAY_NORMAL, 2000000000);
                        break;
                    case Sensor.TYPE_MAGNETIC_FIELD:
                    //case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                        sensorManager.registerListener(geoMagneticEventListener, sensorManager.getDefaultSensor(sensorType), SensorManager.SENSOR_DELAY_NORMAL, 2000000000);
                        break;
                    case Sensor.TYPE_PROXIMITY:
                        sensorManager.registerListener(proximityEventListener, sensorManager.getDefaultSensor(sensorType), SensorManager.SENSOR_DELAY_NORMAL);
                        break;
                }
            }
            //sensorManager.registerListener(accelerometerEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL, 2000000000);
            //sensorManager.registerListener(getMagneticEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_NORMAL, 2000000000);
            sensorRunning = true;
        }
    }

    private void stopListening() {
        if (sensorRunning == true) {
            //logEntry("Stop listening to accelerometer", false);
            sensorManager.unregisterListener(accelerometerEventListener);
            sensorManager.unregisterListener(geoMagneticEventListener);
            sensorManager.unregisterListener(proximityEventListener);
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
