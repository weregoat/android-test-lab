package org.maialinux.oldgoatnewtricks;


import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {



    private static final String TAG = "MainActivity";


    TextView timerTextView;
    Button resetButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        timerTextView = findViewById(R.id.main_activity_view);
        Context context = getApplicationContext();
        Intent startIntent = new Intent(context, AlertService.class);
        context.startService(startIntent);

        resetButton = findViewById(R.id.button);
        resetButton.setOnClickListener(new View.OnClickListener() {
                                           @Override
                                           public void onClick(View view) {
                                               Context context = getApplicationContext();
                                               Intent intent = new Intent(context, AlertService.class);
                                               context.stopService(intent);
                                               context.startService(intent);
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

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Context context = getApplicationContext();
        Intent intent = new Intent(context, AlertService.class);
        context.stopService(intent);
    }





}

