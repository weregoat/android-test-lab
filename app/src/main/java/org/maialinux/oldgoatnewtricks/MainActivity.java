package org.maialinux.oldgoatnewtricks;


import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {


    private static final long INTERVAL = 3600000; // One hour
    private static final long ALERT_INTERVAL = 300000; // Five minutes
    private static final String SLEEP_TIME = "18:00";
    private static final String WAKE_TIME = "10:30";
    private static final int MAX_ALERTS = 3;
    private static final String TAG = "MainActivity";


    TextView timerTextView;
    Button resetButton;

    int alertCounts = 0;



    public Handler timerHandler;
    long expirationTime;

    String[] log = new String[5];

    
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long delay;
            Context context = getApplicationContext();
            Intent stopIntent = new Intent(context, AlertService.class);
            context.stopService(stopIntent);
            if (sleepTime() == false) {
                long millis = expirationTime - System.currentTimeMillis();

                long minutes = millis / 60000;
                long seconds = millis / 1000 - (minutes * 60); // Remainder
                String message = String.format("%sm %ss left", minutes, seconds);
                logEntry(message,false);
                if (millis <= 0) {
                    alertCounts++;
                    delay = 3000; // Rings for three seconds
                    Intent startIntent = new Intent(context, AlertService.class);
                    context.startService(startIntent);
                    expirationTime = System.currentTimeMillis() + ALERT_INTERVAL; // Reset the expiration time
                } else {
                    String text = "";
                    for (int i = 4; i >= 0; i--) {
                        text = String.format("%s\n%s", log[i], text);
                    }
                    timerTextView.setText(text);
                    delay = 60000; // Normal check every minute
                }
            } else {
                delay = 1200000; // Twenty minutes when sleeping.
                logEntry("Sleep time", false);
            }
            if (alertCounts <= MAX_ALERTS) {
                timerHandler.postDelayed(this, delay);
            } else {
                timerHandler.removeCallbacks(timerRunnable);
                logEntry("send SMS", true);
                context.stopService(stopIntent);

            }

        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {

        logEntry("activity created", false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        timerTextView = findViewById(R.id.main_activity_view);
        timerHandler = new Handler();
        expirationTime = System.currentTimeMillis() + INTERVAL;

        timerRunnable.run();


        resetButton = findViewById(R.id.button);
        resetButton.setOnClickListener(new View.OnClickListener() {
                                           @Override
                                           public void onClick(View view) {
                                               alertCounts = 0;
                                               expirationTime = System.currentTimeMillis() + INTERVAL;
                                               timerHandler.removeCallbacks(timerRunnable);
                                               timerRunnable.run();
                                           }
                                       }

            );


    }

    @Override
    public void onPause() {
        logEntry("activity paused", false);
        super.onPause();
    }

    @Override
    public void onResume() {
        logEntry("activity resumed", false);
        super.onResume();

    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    private void logEntry(String message, boolean toast) {

        if (toast) {
            Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
        }
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String entry = String.format("%s: %s", format.format(calendar.getTime()), message);
        System.arraycopy(log, 0, log, 1, 4);
        log[0] = entry;
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



}

