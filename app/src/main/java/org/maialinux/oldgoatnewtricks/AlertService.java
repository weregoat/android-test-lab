package org.maialinux.oldgoatnewtricks;

import android.app.Notification;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class AlertService extends Service {


    private static final long INTERVAL = 30000; // One hour
    private static final long ALERT_INTERVAL = 10000; // Five minutes
    private static final String SLEEP_TIME = "18:00";
    private static final String WAKE_TIME = "10:30";
    private static final int MAX_ALERTS = 3;



    //private final IBinder mBinder = new LocalBinder();
    private final String TAG = "AlertService";
    Ringtone ringtone;
    long expirationTime;
    int alertCounts = 0;

    String[] log = new String[5];

    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            stopRingtone();
            long delay;
            if (sleepTime() == false) {
                long millis = expirationTime - System.currentTimeMillis();

                long minutes = millis / 60000;
                long seconds = millis / 1000 - (minutes * 60); // Remainder
                String message = String.format("%sm %ss left", minutes, seconds);
                logEntry(message, true);
                if (millis <= 0) {
                    alertCounts++;
                    delay = 5000; // Rings for three seconds
                    stopRingtone();
                    playRingtone();
                    expirationTime = System.currentTimeMillis() + ALERT_INTERVAL; // Reset the expiration time
                } else {
                    String text = "";
                    for (int i = 4; i >= 0; i--) {
                        text = String.format("%s\n%s", log[i], text);
                    }
                    delay = 1000; // Normal check every minute
                }
            } else {
                delay = 1200000; // Twenty minutes when sleeping.
                logEntry("Sleep time", false);
            }
            if (alertCounts >= MAX_ALERTS) {
                stopRingtone();
                logEntry("send SMS", true);
                timerHandler.removeCallbacks(timerRunnable);
            } else {
                timerHandler.postDelayed(this, delay);
            }
        }

    };

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


    public void playRingtone() {
        //Log.d(TAG, "play ringtone");
        if (! ringtone.isPlaying()) {
            ringtone.play();
        }
    }

    public void stopRingtone() {
        //Log.d(TAG, "stop ringtone");
        ringtone.stop();
    }

    public void startTimer() {
        expirationTime = System.currentTimeMillis() + INTERVAL;
        timerHandler.removeCallbacks(timerRunnable);
        timerRunnable.run();
    }

    private void logEntry(String message, boolean toast) {

        if (toast) {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        }
        Log.d(TAG, message);
    }

    private boolean sleepTime() {
        boolean sleep = false;

        Calendar now = Calendar.getInstance();
        Calendar wakeCalendar = (Calendar) now.clone();
        Calendar sleepCalendar = (Calendar) now.clone();
        String[] timeParts = WAKE_TIME.split(":"); //HH:mm
        wakeCalendar.set(Calendar.HOUR_OF_DAY, Integer.valueOf(timeParts[0]));
        wakeCalendar.set(Calendar.MINUTE, Integer.valueOf(timeParts[1]));
        timeParts = SLEEP_TIME.split(":");
        sleepCalendar.set(Calendar.HOUR_OF_DAY, Integer.valueOf(timeParts[0]));
        sleepCalendar.set(Calendar.MINUTE, Integer.valueOf(timeParts[1]));
        if (now.compareTo(sleepCalendar) >= 0 || now.compareTo(wakeCalendar) < 0) {
            sleep = true;
        }

        return sleep;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        this.startTimer();
        Log.d(TAG, "Service started");
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy()
    {
        this.stopRingtone();
        timerHandler.removeCallbacks(timerRunnable);
    }

}
