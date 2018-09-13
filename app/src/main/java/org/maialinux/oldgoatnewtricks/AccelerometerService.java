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
    private static final long SLEEP_INTERVAL = 1*60*1000; //  1 minute between checks
    private static final long LISTENING_INTERVAL = 30*1000; // Listen for 30 seconds
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
    private long interval = AlertService.INTERVAL;

    Handler accelHandler = new Handler();
    Runnable accelRunnable = new Runnable() {
        @Override
        public void run() {

            long delay = SLEEP_INTERVAL;
            if (reset == true) {
                delay = interval/2;
                reset = false;
                logEntry(String.format("Accelerometer service sleeping for %d seconds after reset", delay/1000), false);
            } else {
                if (sensorRunning == true) {
                    stopListening();
                } else {
                    startListening();
                    delay = LISTENING_INTERVAL;
                }
            }
            accelHandler.postDelayed(this, delay);
        }

    };

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean resetMessage = intent.getBooleanExtra(AlertService.RESET_MESSAGE, false);
            if (resetMessage == true) {
                Log.d(TAG, "Received reset broadcast");
                reset = true;
                stopListening();
                accelHandler.removeCallbacks(accelRunnable);
                accelRunnable.run();
            }
        }
    };

    public AccelerometerService() {

    }

    @Override
    public void onCreate() {
        registerReceiver(broadcastReceiver, new IntentFilter(AlertService.BROADCAST_ACTION));
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
                        sendResetBroadcast();
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
                    sendResetBroadcast();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            interval = intent.getLongExtra(AlertService.INTERVAL_KEY, AlertService.INTERVAL);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        accelHandler.removeCallbacks(accelRunnable);
        stopListening();
        logEntry("Destroy accelerometer service", true);
        unregisterReceiver(broadcastReceiver);
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
                //Log.d(TAG, sensorList.get(i).getName());
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

    private void sendResetBroadcast() {
        broadCastIntent.putExtra(AlertService.RESET_MESSAGE, true);
        sendBroadcast(broadCastIntent);
    }


}
