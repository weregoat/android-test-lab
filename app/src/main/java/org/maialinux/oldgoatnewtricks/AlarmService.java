package org.maialinux.oldgoatnewtricks;


import android.app.Service;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;


public class AlarmService extends Service {

    public static long ALARM_DURATION = 10000; // 10 seconds
    private static String TAG = AlarmService.class.getSimpleName();
    private Ringtone ringtone;
    Handler mHandler = new Handler();



    @Override
    public void onCreate() {
        Log.d(TAG, "Alarm Job created");
        super.onCreate();
        Uri ringtoneURI = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        ringtone = RingtoneManager.getRingtone(getBaseContext(), ringtoneURI);
        registerReceiver(broadcastReceiver, new IntentFilter(AlertService.BROADCAST_ACTION));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Alarm job destroyed");
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
        ringtone.stop();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d(TAG, "Alarm job started");
        ringtone.play();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ringtone.stop();
            }
        }, ALARM_DURATION);
        return START_STICKY;
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean resetMessage = intent.getBooleanExtra(AlertService.RESET_MESSAGE, false);
            boolean endMessage = intent.getBooleanExtra(AlertService.END_MESSAGE, false);
            if (resetMessage == true || endMessage == true) {
                ringtone.stop();
                stopSelf();
            }
        }
    };

}
