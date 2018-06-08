package org.maialinux.oldgoatnewtricks;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
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

import java.util.List;

public class AlertService extends Service {


    public static final long INTERVAL = 3600000; // One hour
    private static final long ALERT_INTERVAL = 600000; // ten minutes
    private static final String SLEEP_TIME = "22:00";
    private static final String WAKE_TIME = "08:00";
    private static final int MAX_ALERTS = 3;
    public static final String BROADCAST_ACTION = "org.maialinux.oldgoatnewtricks.alert_service_broadcast";
    public static final String RESET_MESSAGE = "reset";
    public static final String END_MESSAGE = "stop";
    public static final long MAX_DELAY = 300000; // 5 Minutes; to try catch sparse movements
    public static final long MIN_DELAY = 60000; // One minute; to try to avoid flood of frequent movements



    //private final IBinder mBinder = new LocalBinder();
    private final String TAG = "AlertService";
    Ringtone ringtone;
    long alertTime;
    int alertCounts = 0;
    long sleepDelay;
    long interval;
    long alertInterval;
    int maxAlerts = MAX_ALERTS;
    String phoneNumber;
    boolean proximityRunning = false;
    boolean sleep = false;

    Intent broadCastIntent;
    Intent resetIntent;
    LocalDateTime wakeUpDateTime;
    LocalTime wakeUpTime;
    LocalTime sleepTime;
    LocalDateTime sleepDateTime;

    NotificationManagerCompat notificationManager;
    NotificationCompat.Builder mBuilder;
    Notification notification;

    private int mJobId = 0;
    private ComponentName mServiceComponent;

    private SensorManager sensorManager;


    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long delay;
            stopRingtone();
            DateTimeFormatter formatter = ISODateTimeFormat.hourMinute();
            if (isSleepTime() == false) {
                startProximitySensor();
                long millis = alertTime - System.currentTimeMillis();
                if (millis <= 0) {
                    alertCounts++;
                    delay = 10000; // Rings for about ten seconds
                    stopRingtone();
                    playRingtone();
                    alertTime = System.currentTimeMillis() + alertInterval; // Reset the expiration time
                } else {
                    long minutes = millis / 60000;
                    long seconds = millis / 1000 - (minutes * 60); // Remainder
                    String message = String.format("Alert timer: %sm %ss remaining", minutes, seconds);
                    logEntry(message, false);
                    delay = millis;
                    if (alertCounts == 0) {
                        if (delay < MIN_DELAY) {
                            delay = MIN_DELAY;
                        } else if (delay > MAX_DELAY) {
                            delay = MAX_DELAY;
                        }
                    }
                }
            } else {
                delay = sleepDelay;
                logEntry("Sleep time", false);
                logEntry(String.format("Sleeping for %s seconds", String.valueOf(delay/1000)), false);
                stopSensorService();
                sensorManager.unregisterListener(proximityEventListener);
            }
            if (alertCounts >= maxAlerts) {
                stopRingtone();
                sendSMS(phoneNumber, "Test SMS");
                timerHandler.removeCallbacks(timerRunnable);
                stopSelf();
            }

            String notificationText = String.format("Alert triggering at %s", formatter.print(new DateTime(alertTime)));
            if (alertCounts > 0) {
                notificationText = String.format("Alert %d; %s", alertCounts, notificationText);
            }
            logEntry(notificationText, true);
            logEntry(String.format("Next check at %s", formatter.print(new DateTime(System.currentTimeMillis() + delay))), false);
            scheduleJob(delay/10);
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
        alertTime = System.currentTimeMillis() + interval;

        broadCastIntent = new Intent(BROADCAST_ACTION);
        registerReceiver(broadcastReceiver, new IntentFilter(BROADCAST_ACTION));
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
        //startSensorService();
        mServiceComponent = new ComponentName(this, SensorJobService.class);
        //scheduleJob();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        startProximitySensor();


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
        alertTime = System.currentTimeMillis() + interval;
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
        sleep = false;
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
                sleepDelay = sleepPeriod.toStandardDuration().getMillis(); // How long before the next check (not the same as alert)
                DateTimeFormatter format = ISODateTimeFormat.dateTimeNoMillis();
                logEntry(String.format("Sleep time %s", format.print(sleepDateTime)), false);
                logEntry(String.format("Wakeup time %s", format.print(wakeUpDateTime)), false);
                PeriodFormatter formatter = new PeriodFormatterBuilder()
                        .appendHours()
                        .appendSuffix("h")
                        .appendMinutes()
                        .appendSuffix("m")
                        .toFormatter();
                alertTime = System.currentTimeMillis() + sleepDelay + interval; // Move the next alert to wake-up time + interval.
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
        notificationManager.cancel(0);
        JobScheduler tm = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        tm.cancelAll();
        stopSensorService();
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
           boolean resetMessage = intent.getBooleanExtra(RESET_MESSAGE, false);
           boolean endMessage = intent.getBooleanExtra(END_MESSAGE, false);
           if (resetMessage == true) {
               reset();
           }
           if (endMessage == true) {
               logEntry("Stop service", false);
               stopRingtone();
               stopSelf();
           }
        }
    };


    private void stopSensorService()
    {
        JobScheduler tm = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        tm.cancelAll();
        Intent serviceIntent = new Intent(this, SensorJobService.class);
        stopService(serviceIntent);
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

    private void scheduleJob(long period) {
        if (period < MIN_DELAY) {
            period = MIN_DELAY;
        }
        Log.d(TAG, String.format("Period = %d seconds", Math.round(period/1000)));
        JobScheduler tm = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo.Builder builder = new JobInfo.Builder(mJobId, mServiceComponent);

        builder.setPeriodic(period);
        //builder.setMinimumLatency(12000);
        //builder.setOverrideDeadline(MIN_DELAY/2);
        // Schedule job
        Log.d(TAG, "Scheduling job");
        tm.schedule(builder.build());
    }

    private final SensorEventListener proximityEventListener = new SensorEventListener() {


        @Override

        public void onSensorChanged(SensorEvent event) {
            float proximityValue = event.values[0];
            if (proximityValue < event.sensor.getMaximumRange()) {
               Log.d(TAG, String.format("Proximity triggered with a value of %f", event.values[0]));
               logEntry("Proximity sensor triggered", true);
               reset();
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    private void reset() {
        alertTime = System.currentTimeMillis() + interval;
        logEntry(String.format("Interval: %d", interval/1000), false);
        stopRingtone();
        logEntry("Reset timer", false);
        alertCounts = 0;
        reloadConfig();
        timerHandler.removeCallbacks(timerRunnable);
        timerRunnable.run();
    }

    private void startProximitySensor() {
        if (proximityRunning == false) {
            List<Sensor> sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);
            for (int i = 0; i < sensorList.size(); i++) {
                Log.d(TAG, sensorList.get(i).getName());
                int sensorType = sensorList.get(i).getType();
                if (sensorType == Sensor.TYPE_PROXIMITY) {
                    sensorManager.registerListener(proximityEventListener, sensorManager.getDefaultSensor(sensorType), SensorManager.SENSOR_DELAY_NORMAL);
                    proximityRunning = true;
                    break;
                }
            }
        }
    }

}
