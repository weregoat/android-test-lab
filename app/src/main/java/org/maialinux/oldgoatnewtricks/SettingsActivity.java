package org.maialinux.oldgoatnewtricks;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);


        Button applyButton = findViewById(R.id.apply_button);
        applyButton.setOnClickListener(new View.OnClickListener() {
                                          @Override
                                          public void onClick(View view) {
                                              Intent intent = new Intent(getApplicationContext(), AlertService.class);
                                              ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                                              for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                                                  if (AlertService.class.getName().equals(service.service.getClassName())) {
                                                      stopService(intent);
                                                      break;
                                                  }
                                              }
                                              startService(intent);

                                          }
                                      }

        );
    }

}
