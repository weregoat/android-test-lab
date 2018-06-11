package org.maialinux.oldgoatnewtricks;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import java.util.EventListener;
import java.util.List;


public class SensorJobService extends JobService {

    public static final String TAG = SensorJobService.class.getSimpleName();
    public static final long DURATION = 20000; // 20 seconds

    private boolean reset = false;
    private long duration = DURATION;
    private SensorManager sensorManager;

    Handler mHandler = new Handler();


    private static final double ACCELERATION_THRESHOLD = 0.5f; // This much acceleration to trigger movement
    private static final double GEOMAGNETIC_THRESHOLD = 10.0f; // This much change on any axis to trigger rest

    /*
     * time smoothing constant for low-pass filter
     * 0 ≤ alpha ≤ 1 ; a smaller value basically means more smoothing
     * See: http://en.wikipedia.org/wiki/Low-pass_filter#Discrete-time_realization
     * From: http://blog.thomnichols.org/2011/08/smoothing-sensor-data-with-a-low-pass-filter
     */
    private static final float ALPHA = 0.8f; // Threshold for low-pass filter

    private float currentAcceleration;
    private float lastAcceleration;
    private float[] lastGeoMagneticValues;
    private Intent broadcastIntent;



    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        Log.d(TAG, "Job started");
        checkSensors();

        // Uses a handler to delay the execution of jobFinished().

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                jobFinished(jobParameters, false);
            }
        }, duration);


        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        reset = false;
        unregisterListener(accelerometerEventListener);
        unregisterListener(geoMagneticEventListener);
        return false;
    }



    private void checkSensors() {

        currentAcceleration = SensorManager.GRAVITY_EARTH;
        lastAcceleration = SensorManager.GRAVITY_EARTH;
        List<Sensor> sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (int i = 0; i < sensorList.size(); i++) {
            Log.d(TAG, sensorList.get(i).getName());
            int sensorType = sensorList.get(i).getType();
            switch (sensorType) {
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
    }


    @Override
    public void onCreate() {
        super.onCreate();
        broadcastIntent = new Intent(AlertService.BROADCAST_ACTION);
        Log.i(TAG, "Service created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterListener(accelerometerEventListener);
        unregisterListener(geoMagneticEventListener);
        stopSelf();
        Log.i(TAG, "Service destroyed");
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
            float acceleration = 0.0f;
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
                    sendResetBroadcast();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

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
        broadcastIntent.putExtra(AlertService.RESET_MESSAGE, true);
        sendBroadcast(broadcastIntent);
        Log.d(TAG, "Broadcast reset sent");
        /* No need to keep listening */
        unregisterListener(accelerometerEventListener);
        unregisterListener(geoMagneticEventListener);
        reset = false;
    }

    private void unregisterListener(SensorEventListener listener) {
        if (listener != null) {
            sensorManager.unregisterListener(listener);
        }
    }
}
