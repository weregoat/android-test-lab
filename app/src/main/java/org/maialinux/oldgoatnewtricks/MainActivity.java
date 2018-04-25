package org.maialinux.oldgoatnewtricks;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

public class MainActivity extends AppCompatActivity {



    private static final String TAG = "MainActivity";
    private static final int LOG_ENTRIES = 15;


    TextView timerTextView;
    Button resetButton;
    String[] log = new String[LOG_ENTRIES];
    Intent intent;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        timerTextView = findViewById(R.id.main_activity_view);
        intent = new Intent(this, AlertService.class);
        startService(intent);
        registerReceiver(broadcastReceiver, new IntentFilter(AlertService.BROADCAST_ACTION));

        resetButton = findViewById(R.id.button);
        resetButton.setOnClickListener(new View.OnClickListener() {
                                           @Override
                                           public void onClick(View view) {
                                               Intent sendIntent = new Intent(AlertService.BROADCAST_ACTION);
                                               sendIntent.putExtra(AlertService.RESET_MESSAGE, true);
                                               sendBroadcast(sendIntent);
                                               updateView("Button reset");
                                           }
                                       }

            );

    }

    @Override
    public void onPause() {
        super.onPause();
        updateView("Pause activity");
    }

    @Override
    public void onResume() {
        super.onResume();
        updateView("Resume activity");

    }

    @Override
    public void onStart() {
        super.onStart();
        updateView("Start activity");

    }

    @Override
    public void onStop() {
        super.onStop();
        updateView("Stop activity");
    }

    @Override
    public void onDestroy() {
        stopService(intent);
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateView(intent.getStringExtra("message"));
        }
    };


    private void updateView(String message) {
        if (message != null) {
            LocalDateTime now = new LocalDateTime();
            /* Use arraycopy to shift the elements of the array log */
            System.arraycopy(log, 0, log, 1, (LOG_ENTRIES - 1));
            DateTimeFormatter formatter = ISODateTimeFormat.hourMinuteSecond();
            String logEntry = String.format("%s: %s\n", formatter.print(now), message);
            log[0] = logEntry;
            StringBuffer text = new StringBuffer();
            for (int i = 0; i < LOG_ENTRIES; i++) {
                String line = log[i];
                if (line != null) {
                    text.append(log[i]);
                }
            }
            timerTextView.setText(text.toString());
        }
    }



}

