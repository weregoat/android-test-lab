package org.maialinux.oldgoatnewtricks;



import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {


    private static final long INTERVAL = 300000; // 5 minutes


    TextView timerTextView;
    Button resetButton;


    public Handler timerHandler;
    long expirationTime;

    Ringtone ringtone;

    String[] log = new String[5];
    
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {


            long millis = expirationTime - System.currentTimeMillis();
            long minutes = millis/60000;
            long seconds = millis/1000 - (minutes*60); // Remainder

            if (millis <= 0) {
                timerHandler.removeCallbacks(timerRunnable);
                if (ringtone.isPlaying() == false) {
                    ringtone.play();
                }
            } else {

                Calendar calendar = Calendar.getInstance();
                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String current = String.format("%s: %sm %ss left", format.format(calendar.getTime()), minutes, seconds);
                System.arraycopy(log, 0, log, 1, 4);
                log[0] = current;
                String text = "";
                for (int i = 4; i >= 0; i--) {
                    text = String.format("%s\n%s", log[i], text);
                }
                timerTextView.setText(text);

                timerHandler.postDelayed(this, 30000); //Check every 30 seconds.
            }

        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        timerTextView = findViewById(R.id.main_activity_view);
        timerHandler = new Handler();
        expirationTime = System.currentTimeMillis() + INTERVAL;

        Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        ringtone = RingtoneManager.getRingtone(getApplicationContext(), uri);

        timerRunnable.run();


        resetButton = findViewById(R.id.button);
        resetButton.setOnClickListener(new View.OnClickListener() {
                                           @Override
                                           public void onClick(View view) {
                                               if (ringtone.isPlaying()) {
                                                   ringtone.stop();

                                               }
                                               expirationTime = System.currentTimeMillis() + INTERVAL;
                                               timerHandler.removeCallbacks(timerRunnable);
                                               timerRunnable.run();
                                           }
                                       }

            );


    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }








}

