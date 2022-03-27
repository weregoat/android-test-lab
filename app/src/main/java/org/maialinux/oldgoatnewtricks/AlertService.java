package org.maialinux.oldgoatnewtricks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
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

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public class AlertService extends Service {


    public static final long INTERVAL = 3600000; // One hour
    private static final long ALERT_INTERVAL = 600000; // ten minutes
    private static final String SLEEP_TIME = "22:00";
    private static final String WAKE_TIME = "08:00";
    private static final int MAX_ALERTS = 3;
    private static final long ALERT_DELAY = Math.round(ALERT_INTERVAL / MAX_ALERTS);
    public static final String BROADCAST_ACTION = "org.maialinux.oldgoatnewtricks.alert_service_broadcast";
    public static final String RESET_MESSAGE = "reset";
    public static final String END_MESSAGE = "stop";
    public static final long MIN_DELAY = 30000;
    private static final long DELAY = 10 * 60000; // default delay as ten minutes
    public static final long MAX_DELAY = 3600000;
    private static final String TRIGGER_SERVICE_KEY = "trigger service";
    public static final String INTERVAL_KEY = "interval";
    public static final String TRIGGER_PROXIMITY_KEY = "toggle_proximity";
    public static final String TRIGGER_USER_ACTION = "toggle_user_action";
    public static final String TRIGGER_SIGNIFICANT_MOTION_KEY = "toggle_significant_motion";
    public static final String DEFAULT_MESSAGE = "Dead-man switch alert triggered";
    public static final int MAX_SMS = 3;
    public static final String CHANNEL_ID = "GOATCHANNEL";
    public static Boolean debug = false;


    //private final IBinder mBinder = new LocalBinder();
    private final String TAG = "OGNT:AlertService";
    Uri ringtoneURI;
    long expirationTime;
    int alertCounts = 0;
    long sleepDelay;
    long interval;
    long alertInterval;
    int maxAlerts = MAX_ALERTS;
    String phoneNumber;
    //boolean sleeping; // If the service is in a sleeping period
    Interval sleepInterval;
    String message = DEFAULT_MESSAGE;
    int smsSent = 0;
    boolean sleeping = false;
    boolean useProximitySensor;
    boolean useUserActions;
    boolean useSignificantMotion;

    Intent broadCastIntent;
    Intent triggersIntent;
    Intent resetIntent;
    Intent alarmIntent;

    DateTime wakeUpDateTime;
    LocalTime wakeUpTime;
    LocalTime sleepTime;
    DateTime sleepDateTime;
    HashMap<String, Intent> runningServices = new HashMap<>();
    int servicesCount = 0;

    NotificationManagerCompat notificationManager;
    NotificationCompat.Builder mBuilder;
    Notification notification;

    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;

    Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long delay = DELAY; // Default delay to start
            /* If it's not sleep time */
            if (isSleepTime() == false) {
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
                        sendSMS(phoneNumber, message);
                        //timerHandler.removeCallbacks(timerRunnable);
                        if (smsSent >= MAX_SMS) {
                            stopServices();
                            stopSelf();
                        } else {
                            alertCounts = 0;
                            stopService(alarmIntent);
                            startService(alarmIntent);
                            resetTimer(alertInterval);
                        }
                    }
                } else {
                    //delay = Math.round(interval / 6); // 1Hour => 10 minutes, 2 Hour => 20 minutes... seems reasonable
                    delay = 1000 * 60 * 5;
                }
                if (delay > millis) {
                    delay = millis;
                }
                logEntry(String.format("Running services %d of %d", runningServices.size(), servicesCount), false);
                if (runningServices.size() < servicesCount || servicesCount == 0) {
                    startServices();
                }
            } else {
                logEntry("Sleep time", false);
                logEntry(String.format("Sleeping for %s seconds", String.valueOf(sleepDelay / 1000)), false);
                stopServices();
                //delay = Math.round(interval / 6);
                delay = 1000 * 60 * 5;
                //resetTimer(interval + sleepDelay);
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
        broadCastIntent = new Intent(BROADCAST_ACTION);
        registerReceiver(broadcastReceiver, new IntentFilter(BROADCAST_ACTION));
        reloadConfig();
        alertCounts = 0;
        expirationTime = System.currentTimeMillis() + interval;

        DateTimeFormatter formatter = ISODateTimeFormat.dateTimeNoMillis();
        logEntry(String.format("Application will be sleeping from %s to %s", formatter.print(sleepDateTime.toLocalDateTime()), formatter.print(wakeUpDateTime.toLocalDateTime())), false);
        resetIntent = new Intent(AlertService.BROADCAST_ACTION);
        resetIntent.putExtra(AlertService.RESET_MESSAGE, true);
        Intent stopIntent = new Intent(AlertService.BROADCAST_ACTION);
        Intent mainAppIntent = new Intent(this, MainActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addNextIntentWithParentStack(mainAppIntent);
        stopIntent.putExtra(AlertService.END_MESSAGE, true);
        PendingIntent resetPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 0, resetIntent, 0);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 1, stopIntent, 0);
        PendingIntent mainPendingIntent = stackBuilder.getPendingIntent(2, PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentText("")
                .setContentTitle("Dead-man notification")
                .setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .addAction(R.drawable.ic_stat_reset, "Reset", resetPendingIntent)
                .addAction(R.drawable.ic_stat_stop, "Stop", stopPendingIntent)
                .addAction(R.drawable.ic_launch_main, "Main", mainPendingIntent);
        notificationManager = NotificationManagerCompat.from(this);
        notification = mBuilder.build();
        startForeground(99, notification);
        logEntry(String.format("Interval: %d", interval / 1000), false);
        alarmIntent = new Intent(this, AlarmService.class);
        alarmIntent.putExtra("ringtoneURI", ringtoneURI.toString());
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        startServices();
    }


    @Override
    public IBinder onBind(Intent intent) {
        logEntry("Service bound", true);
        //return mBinder;
        return null;
    }

    private void logEntry(String message, boolean updateNotification) {
        LogD(TAG, message);
        broadCastIntent.putExtra("message", message);
        sendBroadcast(broadCastIntent);
        if (updateNotification == true) {
            updateNotificationText(message);
        }
    }

    private boolean isSleepTime() {
        boolean sleep = false;
        if (alertCounts == 0) { /* Never go to sleep if there are alerts */
            calculateSleepInterval();

            if (sleepInterval.containsNow() || sleepInterval.contains(new DateTime(expirationTime))) {
                logEntry("Going to sleep", false);
                sleep = true;
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
                Period sleepPeriod = new Period(new DateTime(), sleepInterval.getEnd());
                sleepDelay = sleepPeriod.toStandardDuration().getMillis();
                DateTimeFormatter format = ISODateTimeFormat.dateTimeNoMillis();
                logEntry(String.format("Sleep time %s", format.print(sleepInterval.getStart().toLocalDateTime())), false);
                logEntry(String.format("Wakeup time %s", format.print(sleepInterval.getEnd().toLocalDateTime())), false);
                PeriodFormatter formatter = new PeriodFormatterBuilder()
                        .appendHours()
                        .appendSuffix("h")
                        .appendMinutes()
                        .appendSuffix("m")
                        .toFormatter();
                expirationTime = System.currentTimeMillis() + sleepDelay + interval;
                logEntry(String.format("Sleeping for %s", formatter.print(sleepPeriod)), true);
            } else {
                sleep = false;
                // https://developer.android.com/training/monitoring-device-state/battery-monitoring#java
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = this.getApplicationContext().registerReceiver(null, ifilter);
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                /* We acquire the wakelock only if the phone is charging (e.g. plugged in) */
                if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) {
                    if (wakeLock.isHeld() == false) {
                        wakeLock.acquire();
                        logEntry("Acquiring wake-lock", true);
                    }
                } else {
                    if (wakeLock.isHeld()) {
                        wakeLock.release();
                        logEntry("Releasing wake-lock as phone is not plugged-in", true);
                    }
                }
                sleepDelay = 0;
            }
        }
        sleeping = sleep;
        return sleep;

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        logEntry("Start alert service", false);
        alertCounts = 0;
        reloadConfig();
        resetTimer(interval);
        timerHandler.removeCallbacks(timerRunnable);
        timerRunnable.run();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        logEntry("Destroy alert service", true);
        timerHandler.removeCallbacks(timerRunnable);
        unregisterReceiver(broadcastReceiver);
        notificationManager.cancel(0);
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        stopServices();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.deleteNotificationChannel(CHANNEL_ID);
        }
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean resetMessage = intent.getBooleanExtra(RESET_MESSAGE, false);
            boolean endMessage = intent.getBooleanExtra(END_MESSAGE, false);
            if (resetMessage == true) {
                logEntry("Reset broadcast received; resetting timer", false);
                alertCounts = 0;
                smsSent = 0;
                resetTimer(interval);
                isSleepTime();
                updateNotification();

            }
            if (endMessage == true) {
                logEntry("End broadcast received; stopping service", false);
                stopSelf();
            }
        }
    };


    private void updateNotificationText(String text) {
        if (!text.isEmpty()) {
            mBuilder.setContentText(text);
            mBuilder.setWhen(System.currentTimeMillis());
            notification = mBuilder.build();
            notificationManager.notify(99, notification);
        }
    }

    private void reloadConfig() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        interval = getLong(sharedPreferences, "interval", String.valueOf(interval / 1000), interval / 1000, TAG) * 60000;
        wakeUpTime = getTime(sharedPreferences, "wake_up", WAKE_TIME, TAG);
        sleepTime = getTime(sharedPreferences, "sleep", SLEEP_TIME, TAG);
        maxAlerts = getInteger(sharedPreferences, "max_warnings", String.valueOf(maxAlerts), MAX_ALERTS, TAG);
        phoneNumber = getString(sharedPreferences, "phone_number", " ", TAG);
        message = getString(sharedPreferences, "text_message", DEFAULT_MESSAGE, TAG);
        alertInterval = Math.round(interval / maxAlerts);
        DateTime now = new DateTime();
        sleepDateTime = new DateTime()
                .withDate(now.getYear(), now.getMonthOfYear(), now.getDayOfMonth())
                .withMillisOfDay(sleepTime.getMillisOfDay());
        wakeUpDateTime = new DateTime()
                .withDate(now.getYear(), now.getMonthOfYear(), now.getDayOfMonth())
                .withMillisOfDay(wakeUpTime.getMillisOfDay());
        /* We just have initialised the sleep and wake-up dates above, so they are set at the same day */
        debug = getBoolean(
                sharedPreferences,
                "debug",
                false,
                TAG
        );
        useProximitySensor = getBoolean(
                sharedPreferences,
                TRIGGER_PROXIMITY_KEY,
                true,
                TAG
        );
        useUserActions = getBoolean(
                sharedPreferences,
                TRIGGER_USER_ACTION,
                true,
                TAG
        );
        useSignificantMotion = getBoolean(
                sharedPreferences,
                TRIGGER_SIGNIFICANT_MOTION_KEY,
                true,
                TAG
        );

        ringtoneURI = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sharedPreferences.contains("ringtone")) {
            ringtoneURI = Uri.parse(sharedPreferences.getString("ringtone", ""));
        }
        ;

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

    public static Double getDouble(SharedPreferences sharedPreferences, String preferenceKey, Double defaultValue, String tag) {
        Double value = defaultValue;
        try {
            if (sharedPreferences.contains(preferenceKey)) {
                String setting = sharedPreferences.getString(preferenceKey, "");
                if (!setting.isEmpty()) {
                    value = NumberFormat.getInstance().parse(setting).doubleValue();
                    LogD(tag, String.format("Configuration string %s converted to Double %.3f", setting, value));
                }
            }
        } catch (Exception e) {
            Log.e(tag, e.getMessage());
        }
        return value;
    }

    public static Float getFloat(SharedPreferences sharedPreferences, String preferenceKey, Float defaultValue, String tag) {
        Float value = defaultValue;
        try {
            if (sharedPreferences.contains(preferenceKey)) {
                String setting = sharedPreferences.getString(preferenceKey, "");
                if (!setting.isEmpty()) {
                    value = NumberFormat.getInstance().parse(setting).floatValue();
                    LogD(tag, String.format("Configuration string %s converted to Float %.3f", setting, value));
                }
            }
        } catch (Exception e) {
            Log.e(tag, e.getMessage());
        }
        return value;
    }

    public static Boolean getBoolean(SharedPreferences sharedPreferences, String preferenceKey, Boolean defaultValue, String tag) {
        Boolean value = defaultValue;
        try {
            if (sharedPreferences.contains(preferenceKey)) {
                value = sharedPreferences.getBoolean(preferenceKey, defaultValue);
            }
        } catch (Exception e) {
            Log.e(tag, e.getMessage());
        }
        return value;
    }

    public static void LogD(String tag, String message) {
        if (debug == true) {
            Log.d(tag, message);
        }
    }


    private void sendSMS(String phoneNumber, String message) {

        if (!phoneNumber.isEmpty() && !message.isEmpty()) {
            try {
                SmsManager sm = SmsManager.getDefault();
                ArrayList<String> parts = sm.divideMessage(message);
                final int count = parts.size();
                ArrayList<PendingIntent> sentPis = new ArrayList<>(count);
                ArrayList<PendingIntent> delPis = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    Intent iSent = new Intent("SMS_SENT")
                            .putExtra("msg_part", i);
                    PendingIntent piSent = PendingIntent.getBroadcast(this,
                            i,
                            iSent,
                            PendingIntent.FLAG_ONE_SHOT);
                    sentPis.add(piSent);

                    Intent iDel = new Intent("SMS_DELIVERED")
                            .putExtra("msg_part", i);
                    PendingIntent piDel = PendingIntent.getBroadcast(this,
                            i,
                            iDel,
                            PendingIntent.FLAG_ONE_SHOT);
                    delPis.add(piDel);
                }

                sm.sendMultipartTextMessage(phoneNumber, null, parts, sentPis, delPis);
                //sm.sendTextMessage(phoneNumber, null, message, null, null);
                logEntry("SMS sent", true);
            } catch (Exception e) {
                logEntry(e.getMessage(), false);
            }
            smsSent++;
        }

    }

    private void startServices() {
        createNotificationChannel();
        getServices();
        if (!sleeping) {
            Iterator<Map.Entry<String, Intent>> iterator = runningServices.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Intent> entry = iterator.next();
                String serviceName = entry.getKey();
                Intent intent = entry.getValue();
                intent.putExtra(INTERVAL_KEY, interval);
                if (intent != null) {
                    ComponentName name = startService(intent);
                    if (name != null) {
                        LogD(TAG, String.format("%s service started", serviceName));
                        servicesCount++;
                    }
                }
            }

        }

    }

    private void getServices() {
        servicesCount = 0;
        if (
                runningServices.containsKey(TRIGGER_SERVICE_KEY) == false &&
                        (useProximitySensor == true || useUserActions == true || useSignificantMotion == true)

        ) {
            triggersIntent = new Intent(this, TriggerAlertService.class);
            triggersIntent.putExtra(TRIGGER_PROXIMITY_KEY, useProximitySensor);
            triggersIntent.putExtra(TRIGGER_USER_ACTION, useUserActions);
            triggersIntent.putExtra(TRIGGER_SIGNIFICANT_MOTION_KEY, useSignificantMotion);
            runningServices.put(TRIGGER_SERVICE_KEY, triggersIntent);
        }
    }

    private void stopServices() {
        stopService(alarmIntent);
        getServices();
        Iterator<Map.Entry<String, Intent>> iterator = runningServices.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Intent> entry = iterator.next();
            String serviceName = entry.getKey();
            Intent intent = entry.getValue();
            if (intent != null) {
                if (stopService(intent) == true) {
                    LogD(TAG, String.format("%s service stopped", serviceName));
                } else {
                    Log.w(TAG, String.format("failed to stop %s service", serviceName));
                }
            }
        }
        runningServices.clear();
        servicesCount = 0;

    }

    private void initializeSleepInterval() {
        /*
            What we want is to have the date-time ordered from sleeping to wake-up

         */
        DateTime now = new DateTime();
        /*
         we need to take case of the midnight boundary
         in the case of an interval that spans it
         like from 21:00 to 06:00 the next day
         but not in the case the boundary is not crossed
         like from 10:00 to 22:00.
         In other words when the sleep time is later than the wake-up time
        */
        if (sleepDateTime.isAfter(wakeUpDateTime)) {
            /* The shifting depends on where we are */
            /* If we are after wake-up time we shift the wake-up time to tomorrow */
            if (now.toLocalTime().isAfter(wakeUpTime)) {
                wakeUpDateTime = wakeUpDateTime.plusDays(1);
            } else if (now.toLocalTime().isBefore(wakeUpTime)) {
                /*
                 * If now is before wake-up time. We shift sleep time back one day
                 * as we are in the sleep interval that ideally started yesterday
                 */
                sleepDateTime = sleepDateTime.minusDays(1);
            }
        }
        sleepInterval = new Interval(sleepDateTime, wakeUpDateTime);
    }

    private void calculateSleepInterval() {
        if (sleepInterval == null || sleepDateTime.isAfter(wakeUpDateTime)) {
            initializeSleepInterval();
        }

        DateTime now = DateTime.now();
        /* The sleep interval should always be from the next sleeping time to the wake up time after it */
        /* This means that we need to shift the two dates at the same time */
        /* But we also need to avoid to shift them too early */
        if (!sleeping && now.isAfter(wakeUpDateTime)) {
            int days = getDays(now, wakeUpDateTime); // How many days between now and wake-up time
            wakeUpDateTime = wakeUpDateTime.plusDays(days);
            sleepDateTime = sleepDateTime.plusDays(days);
            sleepInterval = new Interval(sleepDateTime, wakeUpDateTime);
            logEntry("Shifting sleep interval %d days ahead", false);
        }
        DateTimeFormatter intervalFormat = ISODateTimeFormat.dateTimeNoMillis();
        logEntry(
                String.format(
                        "Sleep interval from %s to %s",
                        intervalFormat.print(sleepInterval.getStart().toLocalDateTime()),
                        intervalFormat.print(sleepInterval.getEnd().toLocalDateTime())
                ),
                false
        );

    }

    private void resetTimer(long interval) {
        if (!sleeping) {
            logEntry("Reset timer", false);
            logEntry(String.format("Interval: %d seconds", interval / 1000), false);
            expirationTime = System.currentTimeMillis() + interval; // Reset the expiration time
        }
    }

    private void updateNotification() {
        DateTimeFormatter formatter = ISODateTimeFormat.hourMinute();
        String notificationText = String.format("Timer expiring at %s", formatter.print(new DateTime(expirationTime)));
        if (alertCounts > 0) {
            notificationText = String.format("Alert %d; %s", alertCounts, notificationText);
        } else {
            if (sleeping) {
                DateTime today = new DateTime(System.currentTimeMillis());
                DateTime wakeup = sleepInterval.getEnd();
                if (today.dayOfMonth().getAsString() != wakeup.dayOfMonth().getAsString()) {
                    formatter = DateTimeFormat.forPattern("EEEEE HH:mm").withLocale(Locale.getDefault());
                }
                notificationText = String.format("Sleeping until %s", formatter.print(wakeup));
            }
        }
        logEntry(notificationText, true);
    }

    // This piece of code is now required if you want it to run on Oreo.
    // https://developer.android.com/training/notify-user/build-notification.html#Priority
    // Notice that we are not using the channel for sound alarms. This is, as far as I remember,
    // just to have it work.
    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(description);
            channel.enableLights(false);
            channel.enableVibration(false);
            Uri ringtoneURI = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            channel.setSound(ringtoneURI, null);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private int getDays(DateTime now, DateTime target) {
        int days = 0;
        if (target.isBefore(now)) {
            days = Days.daysBetween(target, now).getDays() + 1;
        }
        return days;

    }


}

