package org.maialinux.oldgoatnewtricks;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;


import org.joda.time.Interval;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;


public class AlertService extends Service {


    private static final long INTERVAL = 3600000; // One hour
    private static final long ALERT_INTERVAL = 600000; // ten minutes
    private static final String SLEEP_TIME = "18:00";
    private static final String WAKE_TIME = "09:00";
    private static final int MAX_ALERTS = 3;
    private static final long ALERT_DELAY = Math.round(ALERT_INTERVAL/MAX_ALERTS);
    public static final String ALERT_ACTION = "org.maialinux.oldgoatnewtricks.displaymessage";



    //private final IBinder mBinder = new LocalBinder();
    private final String TAG = "AlertService";
    Ringtone ringtone;
    long expirationTime;
    int alertCounts = 0;
    long sleepDelay;
    LocalTime wakeUpTime;
    LocalTime sleepTime;
    Intent intent;

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
                logEntry(message, false);
                if (millis <= 0) {
                    alertCounts++;
                    delay = 5000; // Rings for about five seconds
                    stopRingtone();
                    playRingtone();
                    expirationTime = System.currentTimeMillis() + ALERT_INTERVAL; // Reset the expiration time
                } else {
                    if (alertCounts == 0) {
                        delay = Math.round(INTERVAL / 30); // Check 30 times in the given interval.
                    } else {
                        delay = ALERT_DELAY;
                    }
                }
            } else {
                delay = sleepDelay;
                logEntry("Sleep time", false);
                logEntry(String.format("Sleeping for %d seconds", sleepDelay/1000), false);
            }
            if (alertCounts >= MAX_ALERTS) {
                stopRingtone();
                logEntry("Send SMS", true);
                timerHandler.removeCallbacks(timerRunnable);
                stopSelf();
            } else {
                if (alertCounts > 0) {
                    logEntry(String.format("Alert number %d", alertCounts), true);
                }
                logEntry(String.format("Next run in %d seconds", delay/1000), false);

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
        alertCounts = 0;
        Uri ringtoneURI = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        ringtone = RingtoneManager.getRingtone(getBaseContext(), ringtoneURI);
        DateTimeFormatter formatter = ISODateTimeFormat.hourMinute();
        wakeUpTime = LocalTime.parse(WAKE_TIME, formatter);
        sleepTime = LocalTime.parse(SLEEP_TIME, formatter);
        /* ensure that the local-time are in chronological order: wake up before sleeping */
        if (wakeUpTime.isAfter(sleepTime)) {
            sleepTime = LocalTime.parse(WAKE_TIME);
            wakeUpTime = LocalTime.parse(SLEEP_TIME);
        }
        intent = new Intent(ALERT_ACTION);

    }


    @Override
    public IBinder onBind(Intent intent) {
        logEntry("Service bound", true);
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
        alertCounts = 0;
        expirationTime = System.currentTimeMillis() + INTERVAL;
        timerHandler.removeCallbacks(timerRunnable);
        timerRunnable.run();
    }

    private void logEntry(String message, boolean toast) {
        if (toast) {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        }
        Log.d(TAG, message);
        intent.putExtra("message", message);
        sendBroadcast(intent);
    }

    private boolean sleepTime() {
        boolean sleep = false;
        if (alertCounts == 0) { /* Never go to sleep if there are alerts */
            LocalTime now = LocalTime.now();
            if (now.isAfter(sleepTime) || now.isBefore(wakeUpTime)) {
                sleep = true;
                /* We are between midnight and wake-up time */
                if (now.isBefore(sleepTime)) {
                    sleepDelay = new Interval(now.getMillisOfDay(), wakeUpTime.getMillisOfDay()).toDurationMillis() + INTERVAL;
                }
                /* We are between sleep time and midnight */
                if (now.isAfter(wakeUpTime)) {
                    Interval pastSleepTime = new Interval(sleepTime.getMillisOfDay(), now.getMillisOfDay());
                    sleepDelay = sleepDelay - pastSleepTime.toDurationMillis() + INTERVAL;
                }
                logEntry(String.format("Sleep time %d", sleepTime.getMillisOfDay()), false);
                logEntry(String.format("Wakeup time %d", wakeUpTime.getMillisOfDay()), false);
                logEntry(String.format("Sleep interval = %d seconds", sleepDelay / 1000), false);
            } else {
                sleepDelay = 0;
            }
        }
        return sleep;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        this.startTimer();
        logEntry("Service started", true);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy()
    {
        logEntry("Service destroyed", true);
        this.stopRingtone();
        timerHandler.removeCallbacks(timerRunnable);
    }

}
