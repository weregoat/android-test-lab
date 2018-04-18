package org.maialinux.oldgoatnewtricks;

import android.app.Service;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;

public class RingToneService extends Service {

    private Ringtone ringtone;

    public RingToneService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {

        Uri ringtoneUri = (Uri) intent.getExtras().get("ringtone-uri");
        ringtone = RingtoneManager.getRingtone(getApplicationContext(), ringtoneUri);
        ringtone.play();



        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy()
    {
        ringtone.stop();
    }

}
