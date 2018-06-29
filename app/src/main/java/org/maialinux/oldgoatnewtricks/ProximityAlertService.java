package org.maialinux.oldgoatnewtricks;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import java.util.List;

public class ProximityAlertService extends Service {

    private static String TAG = ProximityAlertService.class.getSimpleName();
    private SensorManager sensorManager;
    private final SensorEventListener proximityEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float proximityValue = event.values[0];
            if (proximityValue < event.sensor.getMaximumRange()) {
                Log.d(TAG, String.format("Proximity triggered with a value of %f", event.values[0]));
                Intent broadCastIntent = new Intent(AlertService.BROADCAST_ACTION);
                broadCastIntent.putExtra(AlertService.RESET_MESSAGE, true);
                sendBroadcast(broadCastIntent);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);
        for (int i=0; i <sensorList.size(); i++) {
            Log.d(TAG, sensorList.get(i).getName());
            int sensorType = sensorList.get(i).getType();
            if (sensorType == Sensor.TYPE_PROXIMITY) {
                sensorManager.registerListener(proximityEventListener, sensorManager.getDefaultSensor(sensorType), SensorManager.SENSOR_DELAY_NORMAL);
                break;
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (proximityEventListener != null && sensorManager != null) {
            sensorManager.unregisterListener(proximityEventListener);
        }
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


}
