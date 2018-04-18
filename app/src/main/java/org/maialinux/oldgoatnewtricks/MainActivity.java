package org.maialinux.oldgoatnewtricks;



import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {


    private static final long INTERVAL = 3600000; // One hour
    private static final String SLEEP_TIME = "14:00";
    private static final String WAKE_TIME = "11:00";




    TextView timerTextView;
    Button resetButton;
    Uri ringtoneUri;



    public Handler timerHandler;
    long expirationTime;

    String[] log = new String[5];

    
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {


            if (sleepTime() == false) {
                long millis = expirationTime - System.currentTimeMillis();

                long minutes = millis / 60000;
                long seconds = millis / 1000 - (minutes * 60); // Remainder
                String message = String.format("%sm %ss left", minutes, seconds);
                logEntry(message);
                if (millis <= 0) {
                    timerHandler.removeCallbacks(timerRunnable);
                    Context context = getApplicationContext();
                    Intent startIntent = new Intent(context, RingToneService.class);
                    startIntent.putExtra("ringtone-uri", ringtoneUri);
                    context.startService(startIntent);

                } else {
                    String text = "";
                    for (int i = 4; i >= 0; i--) {
                        text = String.format("%s\n%s", log[i], text);
                    }
                    timerTextView.setText(text);

                    timerHandler.postDelayed(this, 30000); //Check every 30 seconds when awake
                }
            } else {
                timerHandler.postDelayed(this, 1200000); // Every 20 minutes when sleeping
            }

        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {

        logEntry("activity created");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        timerTextView = findViewById(R.id.main_activity_view);
        timerHandler = new Handler();
        expirationTime = System.currentTimeMillis() + INTERVAL;
        ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);


        timerRunnable.run();



        resetButton = findViewById(R.id.button);
        resetButton.setOnClickListener(new View.OnClickListener() {
                                           @Override
                                           public void onClick(View view) {
                                               Context context = getApplicationContext();
                                               Intent stopIntent = new Intent(context, RingToneService.class);
                                               context.stopService(stopIntent);
                                               expirationTime = System.currentTimeMillis() + INTERVAL;
                                               timerHandler.removeCallbacks(timerRunnable);
                                               timerRunnable.run();
                                           }
                                       }

            );


    }

    @Override
    public void onPause() {
        logEntry("activity paused");
        super.onPause();
    }

    @Override
    public void onResume() {
        logEntry("activity resumed");
        super.onResume();

    }

    private void logEntry(String message) {

        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String entry = String.format("%s: %s", format.format(calendar.getTime()), message);
        System.arraycopy(log, 0, log, 1, 4);
        log[0] = entry;
    }

    private boolean sleepTime() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
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
            //logEntry(String.format("SleepTime: %s", format.format(sleepCalendar.getTime())));
            //logEntry(String.format("wakeTime: %s", format.format(wakeCalendar.getTime())));
            if (now.compareTo(sleepCalendar) >= 0 || now.compareTo(wakeCalendar) < 0) {
                sleep = true;
            }

        return sleep;
    }


}

