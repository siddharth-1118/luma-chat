package com.example.lumachat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        findViewById(R.id.btnBackSettings).setOnClickListener(v -> finish());

        findViewById(R.id.settingNotifications).setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationsActivity.class));
        });

        int[] settingIds = {
                R.id.settingAccount,
                R.id.settingPrivacy,
                R.id.settingAppearance,
                R.id.settingStorage,
                R.id.settingHelp
        };

        String[] settingNames = {
                "Account", "Privacy & Security",
                "Chat Appearance", "Data & Storage", "Help & Support"
        };

        for (int i = 0; i < settingIds.length; i++) {
            final String name = settingNames[i];
            findViewById(settingIds[i]).setOnClickListener(v ->
                    Toast.makeText(this, name + " selected",
                            Toast.LENGTH_SHORT).show());
        }

        CompoundButton themeSwitch = findViewById(R.id.switchTheme);
        themeSwitch.setOnCheckedChangeListener((button, checked) ->
                Toast.makeText(this,
                        checked ? "Dark theme selected" : "Light theme selected",
                        Toast.LENGTH_SHORT).show());
    }
}