package org.maialinux.oldgoatnewtricks;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

public class AlertService extends Service {

    //private final IBinder mBinder = new LocalBinder();
    private final String TAG = "AlertService";
    Ringtone ringtone;

    /*
    public class LocalBinder extends Binder {

        AlertService getService() {
            return AlertService.this;
        }
    }
    */

    public AlertService() {
        Log.d(TAG, "Service initialized");
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "service created");
        Uri ringtoneURI = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        ringtone = RingtoneManager.getRingtone(getBaseContext(), ringtoneURI);
    }


    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "service bound");
        //return mBinder;
        return null;
    }


    public void playRingtone()
    {
        Log.d(TAG, "play ringtone");
        if (! ringtone.isPlaying()) {
            ringtone.play();
        }
    }

    public void stopRingtone()
    {
        Log.d(TAG, "stop ringtone");
        ringtone.stop();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d(TAG, "service started");
        this.stopRingtone();
        this.playRingtone();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy()
    {
        this.stopRingtone();
    }
}
