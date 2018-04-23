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
    public static final String BROADCAST_ACTION = "org.maialinux.oldgoatnewtricks.activity_broadcast";


    TextView timerTextView;
    Button resetButton;
    String[] log = new String[5];
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
                                               Intent intent = new Intent(MainActivity.BROADCAST_ACTION);
                                               intent.putExtra(AlertService.RESET_MESSAGE, true);
                                               sendBroadcast(intent);
                                           }
                                       }

            );

    }

    @Override
    public void onPause() {
        super.onPause();
        //unregisterReceiver(broadcastReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        //registerReceiver(broadcastReceiver, new IntentFilter(AlertService.BROADCAST_ACTION));
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        unregisterReceiver(broadcastReceiver);
        super.onStop();
    }

    @Override
    public void onDestroy() {
        stopService(intent);
        super.onDestroy();
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateView(intent);
        }
    };

    private void updateView(Intent intent) {
        String message = intent.getStringExtra("message");
        LocalDateTime now = new LocalDateTime();
        /* Use arraycopy to shift the elements of the array log */
        System.arraycopy(log, 0, log, 1, 4);
        DateTimeFormatter formatter = ISODateTimeFormat.hourMinuteSecond();
        String logEntry = String.format("%s: %s\n", formatter.print(now), message);
        Log.d(TAG, message);
        log[0] = logEntry;
        StringBuffer text = new StringBuffer();
        for(int i = 0; i < 5; i++) {
            String line = log[i];
            if (line != null) {
                text.append(log[i]);
            }
        }
        timerTextView.setText(text.toString());
    }



}

