package org.maialinux.oldgoatnewtricks;

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
import org.joda.time.Days;
import org.joda.time.Interval;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
    public static final long MIN_DELAY = 30000;
    private static final long DELAY = 5*60000; // five minutes
    public static final long MAX_DELAY = 3600000;
    private static final String PROXIMITY_SENSOR_KEY = "proximity sensor";
    private static final String ACCELEROMETER_SENSOR_KEY = "accelerometer sensor";
    public static final String INTERVAL_KEY = "interval";


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
    boolean sleeping; // If the service is in a sleeping period
    Interval sleepInterval;

    Intent broadCastIntent;
    Intent accelerometerSensorIntent;
    Intent proximitySensorIntent;
    Intent geoMagneticSensorIntent;
    Intent resetIntent;
    Intent alarmIntent;

    DateTime wakeUpDateTime;
    LocalTime wakeUpTime;
    LocalTime sleepTime;
    DateTime sleepDateTime;
    HashMap runningServices = new HashMap<String, Intent>();
    int servicesCount = 0;

    NotificationManagerCompat notificationManager;
    NotificationCompat.Builder mBuilder;
    Notification notification;

    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long delay = DELAY;
            if (isSleepTime() == false) {
                sleeping = false;
                long millis = expirationTime - System.currentTimeMillis();
                logEntry(String.format("%d seconds to alert", millis / 1000), false);
                if (millis <= 0) {
                    alertCounts++;
                    if (alertCounts <= maxAlerts) {
                        logEntry(String.format("Triggering alert %d", alertCounts), false);
                        //delay = alertInterval;
                        if (delay < AlarmService.ALARM_DURATION) {
                            delay += AlarmService.ALARM_DURATION;
                        }
                        stopService(alarmIntent);
                        startService(alarmIntent);
                        resetTimer(alertInterval);
                    } else {
                        sendSMS(phoneNumber, "Test SMS");
                        //timerHandler.removeCallbacks(timerRunnable);
                        stopServices();
                        stopSelf();
                    }
                } else {
                    delay = Math.round(interval/10); //
                }
                if (delay > millis) {
                    delay = millis;
                }
                logEntry(String.format("Running services %d of %d", runningServices.size(), servicesCount), false);
                if (runningServices.size() < servicesCount) {
                    startServices();
                }
            } else {
                sleeping = true;
                logEntry("Sleep time", false);
                logEntry(String.format("Sleeping for %s seconds", String.valueOf(sleepDelay / 1000)), false);
                stopServices();
                resetTimer(interval + sleepDelay);
            }

            if (delay < MIN_DELAY) {
                delay = MIN_DELAY;
            } else if (delay > MAX_DELAY) {
                delay = MAX_DELAY;
            }
            logEntry(String.format("postDelay is %d seconds", Math.round(delay / 1000)), false);
            updateNotification();
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
        alarmIntent = new Intent(this, AlarmService.class);
        startServices();
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
        alarmIntent = new Intent(this, AlarmService.class);

    }


    @Override
    public IBinder onBind(Intent intent) {
        logEntry("Service bound", true);
        //return mBinder;
        return null;
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
        calculateSleepInterval();
        DateTimeFormatter intervalFormat = ISODateTimeFormat.dateTimeNoMillis();
        logEntry(
                String.format(
                        "Sleep interval from %s to %s",
                        intervalFormat.print(sleepDateTime.toLocalDateTime()),
                        intervalFormat.print(wakeUpDateTime.toLocalDateTime())
                ),
                false
        );
        if (alertCounts == 0) { /* Never go to sleep if there are alerts */
            sleep = sleepInterval.containsNow();
        }
        if (sleep == true) {
            Period sleepPeriod = new Period(new DateTime(), sleepInterval.getEnd());
            sleepDelay = sleepPeriod.toStandardDuration().getMillis();
            DateTimeFormatter format = ISODateTimeFormat.dateTimeNoMillis();
            logEntry(String.format("Sleep time %s", format.print(sleepDateTime.toLocalDateTime())), false);
            logEntry(String.format("Wakeup time %s", format.print(wakeUpDateTime.toLocalDateTime())), false);
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
        return sleep;

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        logEntry("Start alert service", false);
        alertCounts = 0;
        reloadConfig();
        resetTimer(interval);
        timerHandler.removeCallbacks(timerRunnable);
        timerRunnable.run();
        return START_STICKY;
    }

    @Override
    public void onDestroy()
    {
        logEntry("Destroy alert service", true);
        timerHandler.removeCallbacks(timerRunnable);
        unregisterReceiver(broadcastReceiver);
        notificationManager.cancel(0);
        stopServices();
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
           boolean resetMessage = intent.getBooleanExtra(RESET_MESSAGE, false);
           boolean endMessage = intent.getBooleanExtra(END_MESSAGE, false);
           if (resetMessage == true) {
                logEntry("Reset broadcast received; resetting timer", false);
               alertCounts = 0;
               resetTimer(interval);
               updateNotification();

           }
           if (endMessage == true) {
               logEntry("End broadcast received; stopping service", false);
               stopSelf();
           }
        }
    };


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
        wakeUpTime = getTime(sharedPreferences, "wake_up", WAKE_TIME, TAG);
        sleepTime = getTime(sharedPreferences, "sleep", SLEEP_TIME, TAG);
        maxAlerts = getInteger(sharedPreferences, "max_warnings", String.valueOf(maxAlerts), MAX_ALERTS, TAG);
        phoneNumber = getString(sharedPreferences, "phone_number", " ", TAG);
        alertInterval = Math.round(interval/maxAlerts);
        DateTime now = new DateTime();
        sleepDateTime = new DateTime()
                .withDate(now.getYear(), now.getMonthOfYear(), now.getDayOfMonth())
                .withMillisOfDay(sleepTime.getMillisOfDay());
        wakeUpDateTime = new DateTime()
                .withDate(now.getYear(), now.getMonthOfYear(), now.getDayOfMonth())
                .withMillisOfDay(wakeUpTime.getMillisOfDay());
        calculateSleepInterval();
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

    private void startServices() {
        getServices();
        Iterator iterator = runningServices.entrySet().iterator();
        servicesCount = 0;
        while (iterator.hasNext()) {
            Map.Entry<String, Intent> entry = (Map.Entry) iterator.next();
            String serviceName = entry.getKey();
            Intent intent = entry.getValue();
            intent.putExtra(INTERVAL_KEY, interval);
            if (intent != null) {
                ComponentName name = startService(intent);
                if (name != null) {
                    Log.d(TAG, String.format("%s service started", serviceName));
                    servicesCount++;
                }
            }
        }

    }

    private void getServices() {
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);

        for (int i = 0; i < sensorList.size(); i++) {
            Log.d(TAG, sensorList.get(i).getName());
            int sensorType = sensorList.get(i).getType();
            switch (sensorType) {
                case Sensor.TYPE_ACCELEROMETER:
                case Sensor.TYPE_ACCELEROMETER_UNCALIBRATED:
                case Sensor.TYPE_MAGNETIC_FIELD:
                    if (runningServices.containsKey(ACCELEROMETER_SENSOR_KEY) == false) {
                        accelerometerSensorIntent = new Intent(this, AccelerometerService.class);
                        runningServices.put(ACCELEROMETER_SENSOR_KEY, accelerometerSensorIntent);
                    }
                    break;
                case Sensor.TYPE_PROXIMITY:
                    if (runningServices.containsKey(PROXIMITY_SENSOR_KEY) == false) {
                        proximitySensorIntent = new Intent(this, ProximityAlertService.class);
                        runningServices.put(PROXIMITY_SENSOR_KEY, proximitySensorIntent);
                    }
                    break;

            }
        }
    }

    private void stopServices() {
        stopService(alarmIntent);
        getServices();
        Iterator iterator = runningServices.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Intent> entry = (Map.Entry) iterator.next();
            String serviceName = entry.getKey();
            Intent intent = entry.getValue();
            if (intent != null) {
                if (stopService(intent) == true) {
                    Log.d(TAG, String.format("%s service stopped", serviceName));
                } else {
                    Log.w(TAG, String.format("failed to stop %s service", serviceName));
                }
            }
        }
        runningServices.clear();
        servicesCount = 0;
    }

    private void calculateSleepInterval() {
        DateTime now = new DateTime();
        int days = Days.daysBetween(sleepDateTime.toInstant(), now.toInstant()).getDays();
        if (days > 0) {
            sleepDateTime = sleepDateTime.plusDays(days);
        }
        days = Days.daysBetween(wakeUpDateTime.toInstant(), now.toInstant()).getDays();
        if (days > 0) {
            wakeUpDateTime = wakeUpDateTime.plusDays(days);
        }

        if (wakeUpDateTime.isBefore(sleepDateTime)) {
                wakeUpDateTime = wakeUpDateTime.plusDays(1);
        }
        sleepInterval = new Interval(sleepDateTime.toInstant(), wakeUpDateTime.toInstant());
    }

    private void resetTimer(long interval) {
        logEntry("Reset timer", false);
        logEntry(String.format("Interval: %d seconds", interval/1000), false);
        expirationTime = System.currentTimeMillis() + interval; // Reset the expiration time
    }

    private void updateNotification() {
        DateTimeFormatter formatter = ISODateTimeFormat.hourMinute();
        String notificationText = String.format("Timer expiring at %s", formatter.print(new DateTime(expirationTime)));
        if (alertCounts > 0) {
            notificationText = String.format("Alert %d; %s", alertCounts, notificationText);
        } else if (sleeping == true) {
            DateTime today = new DateTime(System.currentTimeMillis());
            DateTime wakeup = new DateTime(System.currentTimeMillis() + sleepDelay);
            if (today.dayOfMonth().getAsString() != wakeup.dayOfMonth().getAsString()) {
                formatter = DateTimeFormat.forPattern("EEEEE HH:mm").withLocale(getResources().getConfiguration().locale);
            }
            notificationText = String.format("Sleeping until %s", formatter.print(new DateTime(System.currentTimeMillis() + sleepDelay)));
        }
        logEntry(notificationText, true);
    }


}

