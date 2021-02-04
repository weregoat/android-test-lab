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
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.List;

public class TriggerAlertService extends Service {

    private boolean triggerOnUserActions;
    private boolean triggerOnProximitySensor;
    private boolean triggerOnSignificantMotion = true;

    private static String TAG = TriggerAlertService.class.getSimpleName();
    private SensorManager sensorManager;
    private BroadcastReceiver br = new SystemBroadcastReceiver();

    private final SensorEventListener proximityEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float proximityValue = event.values[0];
            if (proximityValue < event.sensor.getMaximumRange()) {
                if (triggerOnProximitySensor == true) {
                    sendResetBroadcast(
                            String.format(
                                    "Proximity triggered with a value of %f",
                                    event.values[0]
                            )
                    );
                } else {
                    AlertService.LogD(TAG, "Proximity trigger is disabled");
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private final TriggerEventListener significantMotionEventListener = new TriggerEventListener() {
        @Override
        public void onTrigger(TriggerEvent event) {
            if (triggerOnSignificantMotion == true) {
                sendResetBroadcast("Significant motion triggered");
            } else {
                AlertService.LogD(TAG,"Significant motion trigger is disabled");
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (int i = 0; i < sensorList.size(); i++) {
            AlertService.LogD(TAG, sensorList.get(i).getName());
            Sensor sensor = sensorList.get(i);
            int sensorType = sensor.getType();

            if (sensorType == Sensor.TYPE_PROXIMITY) {
                sensorManager.registerListener(proximityEventListener, sensorManager.getDefaultSensor(sensorType), SensorManager.SENSOR_DELAY_NORMAL);
            }
            if (sensorType == Sensor.TYPE_SIGNIFICANT_MOTION) {
                AlertService.LogD(TAG, "Significant motion");
                sensorManager.requestTriggerSensor(significantMotionEventListener, sensor);
            }
        }

        IntentFilter filter = new IntentFilter();
        /* List of possible action that may be read as activity from the user */
        // filter.addAction(Intent.ACTION_SCREEN_ON);
        //filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_ANSWER);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_USER_PRESENT);

        this.registerReceiver(br, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        triggerOnProximitySensor = intent.getBooleanExtra(AlertService.TRIGGER_PROXIMITY_KEY, true);
        triggerOnUserActions = intent.getBooleanExtra(AlertService.TRIGGER_USER_ACTION, true);
        triggerOnSignificantMotion = intent.getBooleanExtra(AlertService.TRIGGER_SIGNIFICANT_MOTION_KEY, true);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (proximityEventListener != null && sensorManager != null) {
            sensorManager.unregisterListener(proximityEventListener);
        }
        unregisterReceiver(br);
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    public class SystemBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (triggerOnUserActions == true) {
                sendResetBroadcast(String.format("Received system broadcast for action: %s", intent.getAction()));
            } else {
                AlertService.LogD(TAG, String.format("System broadcast for action %s ignored as trigger on user actions is %s", intent.getAction(), Boolean.toString(triggerOnUserActions)));
            }
        }
    }

    private void sendResetBroadcast(String logMessage) {
        AlertService.LogD(TAG, logMessage);
        Intent broadCastIntent = new Intent(AlertService.BROADCAST_ACTION);
        broadCastIntent.putExtra(AlertService.RESET_MESSAGE, true);
        sendBroadcast(broadCastIntent);
    }

}
