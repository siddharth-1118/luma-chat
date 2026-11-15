package com.example.lumachat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.lumachat.data.UserPreferences;

public class SettingsActivity extends AppCompatActivity {

    private TextView tvSettingsAvatar;
    private TextView tvSettingsName;
    private CompoundButton switchTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        tvSettingsAvatar = findViewById(R.id.tvSettingsAvatar);
        tvSettingsName = findViewById(R.id.tvSettingsName);
        switchTheme = findViewById(R.id.switchTheme);

        findViewById(R.id.btnBackSettings).setOnClickListener(v -> finish());

        // Dark Theme Switch
        boolean isDark = UserPreferences.isDarkMode(this);
        switchTheme.setChecked(isDark);
        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            UserPreferences.setDarkMode(this, isChecked);
            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );
            Toast.makeText(this, isChecked ? "Dark theme enabled 🌙" : "Light theme enabled ☀️", Toast.LENGTH_SHORT).show();
        });

        // Account
        findViewById(R.id.settingAccount).setOnClickListener(v -> showAccountDialog());

        // Privacy
        findViewById(R.id.settingPrivacy).setOnClickListener(v -> showPrivacyDialog());

        // Notifications
        findViewById(R.id.settingNotifications).setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, NotificationsActivity.class));
        });

        // Appearance
        findViewById(R.id.settingAppearance).setOnClickListener(v -> showAppearanceDialog());

        // Storage
        findViewById(R.id.settingStorage).setOnClickListener(v -> showStorageDialog());

        // Help
        findViewById(R.id.settingHelp).setOnClickListener(v -> showHelpDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        tvSettingsName.setText(UserPreferences.getUserName(this));
        tvSettingsAvatar.setText(UserPreferences.getUserAvatar(this));
    }

    private void showAccountDialog() {
        String name = UserPreferences.getUserName(this);
        new AlertDialog.Builder(this)
                .setTitle("Account Details")
                .setMessage("Display Name: " + name + "\n" +
                        "Phone: +1 (555) 019-2834\n" +
                        "Luma Handle: @" + name.toLowerCase() + "\n" +
                        "Status: Active Member ✨\n\n" +
                        "All messages are encrypted on your local device.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void showPrivacyDialog() {
        String[] options = {
                "✓ Last Seen: Everyone",
                "✓ Read Receipts: Enabled",
                "✓ Disappearing Messages: Off",
                "🔒 App Lock: PIN Protected"
        };
        new AlertDialog.Builder(this)
                .setTitle("Privacy & Security")
                .setItems(options, (dialog, which) -> {
                    Toast.makeText(this, "Setting updated", Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("Done", null)
                .show();
    }

    private void showAppearanceDialog() {
        String[] themes = {"🌸 Lavender Dream (Default)", "🍑 Soft Peach", "🌿 Mint Forest", "✨ Midnight Violet"};
        new AlertDialog.Builder(this)
                .setTitle("Chat Theme Accent")
                .setItems(themes, (dialog, which) -> {
                    Toast.makeText(this, themes[which] + " selected!", Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("Cancel", null)
                .show();
    }

    private void showStorageDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Data & Storage")
                .setMessage("App Storage: 24.8 MB\nMedia Cache: 14.2 MB\nMessages Database: 1.6 MB\n\nWould you like to clear temporary media cache?")
                .setPositiveButton("Clear Cache", (dialog, which) -> {
                    Toast.makeText(this, "14.2 MB cache cleared!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle("About LumaChat")
                .setMessage("LumaChat v1.0.0\n\n" +
                        "A delightful, aesthetic chat app designed for close connections, circles, and shared moments.\n\n" +
                        "Features:\n" +
                        "• Direct & Circle Messaging\n" +
                        "• Interactive Smart Chat Replies\n" +
                        "• Moments & Status Sharing\n" +
                        "• LumaVoice Calling Simulator\n" +
                        "• Personal Space & Cloud Notes\n" +
                        "• Dark & Light Aesthetic Themes\n\n" +
                        "Crafted with ❤️ for Android.")
                .setPositiveButton("Awesome!", null)
                .show();
    }
}
