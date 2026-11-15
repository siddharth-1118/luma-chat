package com.example.lumachat;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.lumachat.data.UserPreferences;
import com.example.lumachat.network.P2PChatManager;

public class ProfileSetupActivity extends AppCompatActivity {

    private TextView tvAvatarPreview;
    private EditText etName;
    private EditText etBio;
    private LinearLayout layoutAvatarChips;

    private String selectedAvatar = "🌸";
    private final String[] presetAvatars = {
            "🌸", "⚡", "🦊", "🚀", "🐼", "🐱", "🎧", "🌟", "🔥", "☕", "🎨", "💎", "🦁", "⚽", "💜"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        tvAvatarPreview = findViewById(R.id.tvSetupAvatarPreview);
        etName = findViewById(R.id.etSetupName);
        etBio = findViewById(R.id.etSetupBio);
        layoutAvatarChips = findViewById(R.id.layoutAvatarChips);

        // Preload current preferences if any
        String currentName = UserPreferences.getUserName(this);
        if (!"Harshitha".equals(currentName)) {
            etName.setText(currentName);
        }
        selectedAvatar = UserPreferences.getUserAvatar(this);
        tvAvatarPreview.setText(selectedAvatar);

        etBio.setText(UserPreferences.getUserBio(this));

        setupAvatarChips();

        // Tap preview to enter custom emoji
        tvAvatarPreview.setOnClickListener(v -> promptCustomAvatar());

        findViewById(R.id.btnCompleteProfile).setOnClickListener(v -> completeProfile());
        findViewById(R.id.tvSkipSetup).setOnClickListener(v -> completeProfile());
    }

    private void setupAvatarChips() {
        layoutAvatarChips.removeAllViews();
        for (String emoji : presetAvatars) {
            TextView chip = new TextView(this);
            chip.setText(emoji);
            chip.setTextSize(22);
            chip.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dpToPx(50), dpToPx(50));
            lp.setMargins(dpToPx(4), dpToPx(4), dpToPx(6), dpToPx(4));
            chip.setLayoutParams(lp);

            updateChipState(chip, emoji.equals(selectedAvatar));

            chip.setOnClickListener(v -> {
                selectedAvatar = emoji;
                tvAvatarPreview.setText(selectedAvatar);
                setupAvatarChips(); // re-render selection highlights
            });

            layoutAvatarChips.addView(chip);
        }
    }

    private void updateChipState(TextView chip, boolean isSelected) {
        if (isSelected) {
            chip.setBackgroundResource(R.drawable.button_background);
        } else {
            chip.setBackgroundResource(R.drawable.input_field);
        }
    }

    private void promptCustomAvatar() {
        EditText input = new EditText(this);
        input.setHint("Enter any emoji or letter");
        input.setSingleLine(true);
        input.setText(selectedAvatar);
        input.setGravity(Gravity.CENTER);

        new AlertDialog.Builder(this)
                .setTitle("Custom Avatar")
                .setMessage("Type any emoji or single letter for your profile avatar:")
                .setView(input)
                .setPositiveButton("Set", (dialog, which) -> {
                    String val = input.getText().toString().trim();
                    if (!val.isEmpty()) {
                        selectedAvatar = val.length() > 2 ? val.substring(0, 2) : val;
                        tvAvatarPreview.setText(selectedAvatar);
                        setupAvatarChips();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void completeProfile() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter your display name", Toast.LENGTH_SHORT).show();
            etName.requestFocus();
            return;
        }

        String bio = etBio.getText().toString().trim();
        if (bio.isEmpty()) {
            bio = "Hey there! I am using LumaChat ✨";
        }

        // Save preferences
        UserPreferences.setUserName(this, name);
        UserPreferences.setUserAvatar(this, selectedAvatar);
        UserPreferences.setUserBio(this, bio);
        UserPreferences.setProfileSetupComplete(this, true);

        // Ensure user ID is generated
        String userId = UserPreferences.getUserId(this);

        // Register with P2P/Cloud server if connected
        P2PChatManager p2p = P2PChatManager.getInstance();
        p2p.init(this);
        p2p.updateMyProfile(userId, name, selectedAvatar, bio);

        Toast.makeText(this, "Welcome, " + name + "! 🎉", Toast.LENGTH_SHORT).show();

        // Continue to MainActivity
        Intent intent = new Intent(ProfileSetupActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
