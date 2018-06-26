package org.maialinux.oldgoatnewtricks;


import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;


public class AlarmJob extends JobService {

    private Ringtone ringtone;

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        return false;
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean resetMessage = intent.getBooleanExtra(AlertService.RESET_MESSAGE, false);
            boolean endMessage = intent.getBooleanExtra(AlertService.END_MESSAGE, false);
            if (resetMessage == true || endMessage == true) {
                stopSelf();
            }

        }
    };
}
