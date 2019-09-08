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
import android.view.OrientationEventListener;
import android.widget.Toast;

import org.joda.time.Interval;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.util.List;


public class PollingAlertService extends Service {


    /* Listen intermittently to the sensor for a few seconds only to minimize battery usage */
    /* If I am active I can, as well, press the button. Otherwise that should be enough
        to detect movement while carrying the phone around.
        Remember this is about detecting when I am inactive over a long period, presumed dead.
     */
    private static final long SLEEP_INTERVAL = 1*60*1000; //  1 minute between checks
    private static final long LISTENING_INTERVAL = 30*1000; // Listen for 30 seconds
    public static final double ACCELERATION_THRESHOLD = 0.8f; // This much linear acceleration to trigger movement
    public static final double ROTATION_THRESHOLD = 0.8f; // This much rotational acceleration to trigger movement
    private static final double GEOMAGNETIC_THRESHOLD = 10.0f; // This much change on any axis to trigger rest
    public static final int ORIENTATION_THRESHOLD = 10; // In degrees


    /*
     * time smoothing constant for low-pass filter
     * 0 ≤ alpha ≤ 1 ; a smaller value basically means more smoothing
     * See: http://en.wikipedia.org/wiki/Low-pass_filter#Discrete-time_realization
     * From: http://blog.thomnichols.org/2011/08/smoothing-sensor-data-with-a-low-pass-filter
     */
    public static final float ALPHA = 0.8f; // Threshold for low-pass filter


    private static final String TAG = "PollingAlertService";
    private float acceleration = 0.0f;
    private float currentAcceleration;
    private float lastAcceleration;
    private boolean reset = false;
    private Intent broadCastIntent;
    SensorManager sensorManager;
    private boolean sensorRunning = false;
    private float[] lastGeoMagneticValues;
    private long interval = AlertService.INTERVAL;
    private int orientation = -1;
    private OrientationEventListener orientationEventListener;
    private double accelerationThreshold = ACCELERATION_THRESHOLD;
    private double rotationThreshold = ROTATION_THRESHOLD;
    private float lowPassThreshold = ALPHA;

    Handler accelHandler = new Handler();
    Runnable accelRunnable = new Runnable() {
        @Override
        public void run() {

            long delay = SLEEP_INTERVAL;
            if (reset == true) {
                delay = interval/2;
                reset = false;
                logEntry(String.format("Sensor service sleeping for %d seconds after reset", delay/1000), false);
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

    public PollingAlertService() {

    }

    @Override
    public void onCreate() {
        registerReceiver(broadcastReceiver, new IntentFilter(AlertService.BROADCAST_ACTION));
        broadCastIntent = new Intent(AlertService.BROADCAST_ACTION);
        logEntry("Create movement detection service", false);
        orientationEventListener = new OrientationEventListener(getApplicationContext(), SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int i) {
                boolean reset = false;
                // Values go from -1 to 360
                // -1 means it doesn't know
                // Although the very method should be only called when there is a change in
                // orientation, I prefer to re-iterate it my own way, to provide some insulation
                // from the Android implementation.
                if (i != orientation) { // If the orientation value has changed.
                    /*
                        I can imagine a few scenarios I need to act upon:
                        - phone was lying and it got picked up
                        - phone was oriented some way (remember always "vertically") and it changed more than
                        - X degrees (the X degrees is to limit the reset calls as it always wobbles when
                        - I hold it).
                    */
                    // In any case moving from -1 to any value means that the phone has been picked up
                    if (orientation == -1) { // remember i is <> than orientation (-1) so it's not -1
                        // The phone was picked up from a previous flat state (or unknown orientation state, more precisely)
                        reset = true;
                    } else {
                        if (i != -1) { // If the phone didn't go from some orientation to unknown...
                            // Converts the values into radians and then calculate the SIN value
                            // so to have contiguous values without the gap from 0 to 360.
                            double previous = Math.sin(Math.toRadians(orientation));
                            double current = Math.sin(Math.toRadians(i));
                            double delta = Math.abs(previous - current);
                            // Converts back the delta to degrees so we can compare with the THRESHOLD
                            if (Math.toDegrees(Math.asin(delta)) > ORIENTATION_THRESHOLD) {
                                reset = true;
                            }
                        }
                    }
                    orientation = i;
                    if (reset == true) {
                        sendResetBroadcast();
                    }
                }
            }
        };

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
                acceleration = Math.abs(acceleration * lowPassThreshold + delta); // Low-pass filter removing the high frequency noise
                Log.d(TAG, String.format("acceleration: %f", acceleration));
                if (acceleration >= accelerationThreshold) {
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

    private final SensorEventListener gyroscopeEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (reset == false) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                float omegaMagnitude = (float) Math.sqrt((double) (x*x + y*y + z*z));
                Log.d(TAG, String.format("axis rotation acceleration: %f", omegaMagnitude));
                if (omegaMagnitude >= rotationThreshold) {
                    logEntry("Gyroscope sensor triggered", true);
                    reset = true;
                    sendResetBroadcast();
                }

            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    };



    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            interval = intent.getLongExtra(AlertService.INTERVAL_KEY, AlertService.INTERVAL);
            accelerationThreshold = intent.getDoubleExtra(AlertService.ACCELEROMETER_THRESHOLD_KEY, ACCELERATION_THRESHOLD);
            rotationThreshold = intent.getDoubleExtra(AlertService.GYROSCOPE_THRESHOLD_KEY, ROTATION_THRESHOLD);
            lowPassThreshold = intent.getFloatExtra(AlertService.LOW_PASS_THRESHOLD_KEY, ALPHA);
        }
        logEntry(
                String.format("linear acceleration threshold: %.2f", accelerationThreshold),
                false
        );
        logEntry(
                String.format("rotation acceleration threshold: %.2f", rotationThreshold),
                false
        );
        logEntry(
                String.format("low-pass threshold: %.2f", lowPassThreshold),
                false
        );
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
            logEntry("Start listening to sensors", false);
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
                        sensorRunning = true;
                        break;
                    case Sensor.TYPE_MAGNETIC_FIELD:
                    //case Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED:
                        sensorManager.registerListener(geoMagneticEventListener, sensorManager.getDefaultSensor(sensorType), SensorManager.SENSOR_DELAY_NORMAL, 2000000000);
                        sensorRunning = true;
                        break;
                    case Sensor.TYPE_GYROSCOPE:
                    case Sensor.TYPE_GYROSCOPE_UNCALIBRATED:
                        sensorManager.registerListener(gyroscopeEventListener, sensorManager.getDefaultSensor(sensorType), SensorManager.SENSOR_DELAY_NORMAL);
                        sensorRunning = true;
                        break;
                }
            }
            /* Start listening to orientation change, if possible */
            if (orientationEventListener.canDetectOrientation() == true) {
                Log.d(TAG, "Enabling orientation detection");
                orientationEventListener.enable();
                sensorRunning = true;
            } else {
                Log.w(TAG, "Cannot detect orientation; disabling");
                orientationEventListener.disable();
            }

            //sensorManager.registerListener(accelerometerEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL, 2000000000);
            //sensorManager.registerListener(getMagneticEventListener, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_NORMAL, 2000000000);
            //sensorRunning = true;
        }
    }

    private void stopListening() {
        if (sensorRunning == true) {
            logEntry("Stop listening to sensors", false);
            sensorManager.unregisterListener(accelerometerEventListener);
            sensorManager.unregisterListener(geoMagneticEventListener);
            sensorManager.unregisterListener(gyroscopeEventListener);
            orientationEventListener.disable(); // Disable orientation related reset.
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
