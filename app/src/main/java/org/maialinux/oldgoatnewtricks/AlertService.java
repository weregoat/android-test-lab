package org.maialinux.oldgoatnewtricks;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.telephony.SmsManager;
import android.util.Log;


import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

public class AlertService extends Service {


    public static final long INTERVAL = 3600000; // One hour
    private static final long ALERT_INTERVAL = 600000; // ten minutes
    private static final String SLEEP_TIME = "22:00";
    private static final String WAKE_TIME = "08:00";
    private static final int MAX_ALERTS = 3;
    private static final long ALERT_DELAY = Math.round(ALERT_INTERVAL/MAX_ALERTS);
    public static final String BROADCAST_ACTION = "org.maialinux.oldgoatnewtricks.alert_service_broadcast";
    public static final String RESET_MESSAGE = "reset";
    public static final String END_MESSAGE = "stop";



    //private final IBinder mBinder = new LocalBinder();
    private final String TAG = "AlertService";
    Ringtone ringtone;
    long expirationTime;
    int alertCounts = 0;
    long sleepDelay;
    long interval;
    long alertInterval;
    int maxAlerts = MAX_ALERTS;
    String phoneNumber;

    Intent broadCastIntent;
    Intent serviceIntent;
    Intent resetIntent;
    boolean accelerometerServiceStarted = false;
    LocalDateTime wakeUpDateTime;
    LocalTime wakeUpTime;
    LocalTime sleepTime;
    LocalDateTime sleepDateTime;

    NotificationManagerCompat notificationManager;
    NotificationCompat.Builder mBuilder;
    Notification notification;


    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            stopRingtone();
            long delay;
            DateTimeFormatter formatter = ISODateTimeFormat.hourMinute();

            if (isSleepTime() == false) {
                long millis = expirationTime - System.currentTimeMillis();
                if (millis <= 0) {
                    alertCounts++;
                    delay = 10000; // Rings for about ten seconds
                    stopRingtone();
                    playRingtone();
                    expirationTime = System.currentTimeMillis() + alertInterval; // Reset the expiration time

                } else {
                    long minutes = millis / 60000;
                    long seconds = millis / 1000 - (minutes * 60); // Remainder
                    String message = String.format("Alert timer: %sm %ss remaining", minutes, seconds);
                    logEntry(message, false);
                    if (alertCounts == 0) {
                        delay = Math.round(interval / 30);
                        if (millis < delay) {
                            delay = millis;
                        }
                    } else {
                        delay = ALERT_DELAY;
                    }

                }
                if (accelerometerServiceStarted == false) {
                    startAccelerometerService();
                }

            } else {
                delay = sleepDelay;
                logEntry("Sleep time", false);
                logEntry(String.format("Sleeping for %s seconds", String.valueOf(delay/1000)), false);
                stopAccelerometerService();
            }
            if (alertCounts >= maxAlerts) {
                stopRingtone();
                sendSMS(phoneNumber, "Test SMS");
                timerHandler.removeCallbacks(timerRunnable);
                stopAccelerometerService();
                stopSelf();
            }

            String notificationText = String.format("Timer expiring at %s", formatter.print(new DateTime(expirationTime)));
            if (alertCounts > 0) {
                notificationText = String.format("Alert %d; %s", alertCounts, notificationText);
            }
            logEntry(notificationText, true);
            timerHandler.postDelayed(this, delay);

        }

    };

    public AlertService() {

    }

    @Override
    public void onCreate() {

        //SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        interval = INTERVAL;
        reloadConfig();
        alertCounts = 0;
        Uri ringtoneURI = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        ringtone = RingtoneManager.getRingtone(getBaseContext(), ringtoneURI);
        expirationTime = System.currentTimeMillis() + interval;

        broadCastIntent = new Intent(BROADCAST_ACTION);
        registerReceiver(broadcastReceiver, new IntentFilter(BROADCAST_ACTION));
        serviceIntent = new Intent(this, AccelerometerService.class);
        DateTimeFormatter formatter = ISODateTimeFormat.dateTimeNoMillis();
        logEntry(String.format("Application will be sleeping from %s to %s", formatter.print(sleepDateTime), formatter.print(wakeUpDateTime)), false);
        resetIntent = new Intent(AlertService.BROADCAST_ACTION);
        resetIntent.putExtra(AlertService.RESET_MESSAGE, true);
        Intent stopIntent = new Intent(AlertService.BROADCAST_ACTION);
        stopIntent.putExtra(AlertService.END_MESSAGE, true);
        PendingIntent resetPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, resetIntent, 0);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 1, stopIntent, 0);
        mBuilder = new NotificationCompat.Builder(this, "Something")
                .setContentText("")
                .setContentTitle("Dead-man notification")
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .addAction(R.drawable.ic_stat_reset, "Reset", resetPendingIntent)
                .addAction(R.drawable.ic_stat_stop, "Stop", stopPendingIntent);
        notificationManager = NotificationManagerCompat.from(this);
        notification = mBuilder.build();
        startForeground(99, notification);
        logEntry(String.format("Interval: %d", interval/1000), false);

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
        reloadConfig();
        expirationTime = System.currentTimeMillis() + interval;
        timerHandler.removeCallbacks(timerRunnable);
        timerRunnable.run();
    }

    private void logEntry(String message, boolean updateNotification) {
        Log.d(TAG, message);
        broadCastIntent.putExtra("message", message);
        sendBroadcast(broadCastIntent);
        if (updateNotification == true) {
            updateNotificationText(message);
        }
    }

    private boolean isSleepTime() {
        boolean sleep = false;
        if (alertCounts == 0) { /* Never go to sleep if there are alerts */
            LocalDateTime now = LocalDateTime.now();
            /* We are between sleep time and wake-up time */
            if (now.isAfter(sleepDateTime)) {
                /* This is to correctly calculate the delay time till wake-up */
                if (wakeUpDateTime.isBefore(now)) {
                    wakeUpDateTime = wakeUpDateTime.plusDays(1);
                }
                sleepDateTime = sleepDateTime.plusDays(1);
                sleep = true;
            } else if (now.isBefore(wakeUpDateTime)) {
                if (sleepDateTime.isAfter(wakeUpDateTime)) {
                    sleep = true;
                }
            }
            if (sleep == true) {
                Period sleepPeriod = new Period(now, wakeUpDateTime);
                sleepDelay = sleepPeriod.toStandardDuration().getMillis();
                DateTimeFormatter format = ISODateTimeFormat.dateTimeNoMillis();
                logEntry(String.format("Sleep time %s", format.print(sleepDateTime)), false);
                logEntry(String.format("Wakeup time %s", format.print(wakeUpDateTime)), false);
                PeriodFormatter formatter = new PeriodFormatterBuilder()
                        .appendHours()
                        .appendSuffix("h")
                        .appendMinutes()
                        .appendSuffix("m")
                        .toFormatter();
                expirationTime = System.currentTimeMillis() + sleepDelay + interval;
                logEntry(String.format("Sleeping for %s", formatter.print(sleepPeriod)), true);
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
        logEntry("Start alert service", false);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy()
    {
        logEntry("Destroy alert service", true);
        this.stopRingtone();
        timerHandler.removeCallbacks(timerRunnable);
        unregisterReceiver(broadcastReceiver);
        stopAccelerometerService();
        notificationManager.cancel(0);
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
           boolean resetMessage = intent.getBooleanExtra(RESET_MESSAGE, false);
           boolean endMessage = intent.getBooleanExtra(END_MESSAGE, false);
           if (resetMessage == true) {
               expirationTime = System.currentTimeMillis() + interval;
               logEntry(String.format("Interval: %d", interval/1000), false);
               stopRingtone();
               logEntry("Reset timer", false);
               alertCounts = 0;
               reloadConfig();
               timerHandler.removeCallbacks(timerRunnable);
               timerRunnable.run();
           }
           if (endMessage == true) {
               logEntry("Stop service", false);
               stopRingtone();
               stopSelf();
           }
        }
    };

    private void startAccelerometerService()
    {
        startService(serviceIntent);
        accelerometerServiceStarted = true;
    }

    private void stopAccelerometerService()
    {
        stopService(serviceIntent);
        accelerometerServiceStarted = false;
    }

    private void calculateSleepDateTimes()
    {
        LocalDate today = new LocalDate();
        sleepDateTime = new LocalDateTime()
                .withDate(today.getYear(), today.getMonthOfYear(), today.getDayOfMonth())
                .withMillisOfDay(sleepTime.getMillisOfDay());
        wakeUpDateTime = new LocalDateTime()
                .withDate(today.getYear(), today.getMonthOfYear(), today.getDayOfMonth())
                .withMillisOfDay(wakeUpTime.getMillisOfDay());
        /* If the application is started during the wake up period, shift the wake up a day forwards */
        if (wakeUpDateTime.isBefore(LocalDateTime.now())) {
            wakeUpDateTime = wakeUpDateTime.plusDays(1);
        }
    }

    private void updateNotificationText(String text)
    {
        if (! text.isEmpty()) {
            mBuilder.setContentText(text);
            mBuilder.setWhen(System.currentTimeMillis());
            notification = mBuilder.build();
            notificationManager.notify(99, notification);
        }
    }

    private void reloadConfig()
    {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        interval = getLong(sharedPreferences, "interval", String.valueOf(interval/1000), interval/1000, TAG)*60000;
        alertInterval = Math.round(interval/6);
        wakeUpTime = getTime(sharedPreferences, "wake_up", WAKE_TIME, TAG);
        sleepTime = getTime(sharedPreferences, "sleep", SLEEP_TIME, TAG);
        maxAlerts = getInteger(sharedPreferences, "max_warnings", String.valueOf(maxAlerts), MAX_ALERTS, TAG);
        phoneNumber = getString(sharedPreferences, "phone_number", " ", TAG);
        Log.d(TAG, String.format("Max alerts: %d", maxAlerts));
        calculateSleepDateTimes();
    }

    public static Long getLong(SharedPreferences sharedPreferences, String preferenceKey, String defaultPreferenceValue, Long defaultValue, String tag) {
        Long value = defaultValue;
        if (sharedPreferences.contains(preferenceKey)) {
            try {
                String stringValue = sharedPreferences.getString(preferenceKey, defaultPreferenceValue);
                value = Long.parseLong(stringValue);
            } catch (NumberFormatException nfe) {
                Log.e(tag, nfe.getMessage());
            }
        }
        return value;
    }

    public static Integer getInteger(SharedPreferences sharedPreferences, String preferenceKey, String defaultPreferenceValue, Integer defaultValue, String tag) {
        Integer value = defaultValue;
        if (sharedPreferences.contains(preferenceKey)) {
            try {
                String stringValue = sharedPreferences.getString(preferenceKey, defaultPreferenceValue);
                value = Integer.parseInt(stringValue);
            } catch (NumberFormatException nfe) {
                Log.e(tag, nfe.getMessage());
            }
        }
        return value;
    }

    public static LocalTime getTime(SharedPreferences sharedPreferences, String preferenceKey, String defaultValue, String tag) {
        DateTimeFormatter formatter = ISODateTimeFormat.hourMinute();
        LocalTime value = LocalTime.parse(defaultValue, formatter);
        try {
            if (sharedPreferences.contains(preferenceKey)) {
                String stringValue = sharedPreferences.getString(preferenceKey, defaultValue);
                LocalTime preferenceTime = LocalTime.parse(stringValue, formatter);
                if (preferenceTime != null) {
                    value = preferenceTime;
                }
            }
        } catch (Exception e) {
            Log.e(tag, e.getMessage());
        }
        return value;
    }

    public static String getString(SharedPreferences sharedPreferences, String preferenceKey, String defaultValue, String tag) {
       String value = defaultValue;
        try {
            if (sharedPreferences.contains(preferenceKey)) {
                value = sharedPreferences.getString(preferenceKey, defaultValue);
            }
        } catch (Exception e) {
            Log.e(tag, e.getMessage());
        }
        return value.trim();
    }

    private void sendSMS(String phoneNumber, String message)
    {
        if (!phoneNumber.isEmpty() && ! message.isEmpty()) {
            SmsManager sms = SmsManager.getDefault();
            sms.sendTextMessage(phoneNumber, null, message, null, null);
            logEntry("SMS sent", true);
        }

    }

}
