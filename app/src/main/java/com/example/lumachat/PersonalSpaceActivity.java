package com.example.lumachat;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.lumachat.data.DatabaseHelper;
import com.example.lumachat.data.UserPreferences;

public class PersonalSpaceActivity extends AppCompatActivity {

    private TextView tvPersonalAvatar;
    private TextView tvPersonalName;
    private TextView tvPersonalBio;
    private TextView tvTotalChats;
    private TextView tvTotalGroups;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_space);

        tvPersonalAvatar = findViewById(R.id.tvPersonalAvatar);
        tvPersonalName = findViewById(R.id.tvPersonalName);
        tvPersonalBio = findViewById(R.id.tvPersonalBio);
        tvTotalChats = findViewById(R.id.tvTotalChats);
        tvTotalGroups = findViewById(R.id.tvTotalGroups);

        findViewById(R.id.btnBackPersonal).setOnClickListener(v -> finish());
        findViewById(R.id.btnEditProfile).setOnClickListener(v -> showEditProfileDialog());

        findViewById(R.id.menuMyActivity).setOnClickListener(v -> {
            startActivity(new Intent(PersonalSpaceActivity.this, ActivityLogActivity.class));
        });

        // Saved Messages Opens real Chat room!
        findViewById(R.id.menuSaved).setOnClickListener(v -> {
            Intent intent = new Intent(PersonalSpaceActivity.this, ChatActivity.class);
            intent.putExtra("id", "saved_messages");
            intent.putExtra("name", "Saved Messages");
            intent.putExtra("avatar", "♡");
            intent.putExtra("is_group", false);
            intent.putExtra("status", "Personal Cloud Space");
            startActivity(intent);
        });

        findViewById(R.id.menuFiles).setOnClickListener(v -> showFilesDialog());
        findViewById(R.id.menuMedia).setOnClickListener(v -> showMediaDialog());

        findViewById(R.id.btnOpenSettings).setOnClickListener(v -> {
            startActivity(new Intent(PersonalSpaceActivity.this, SettingsActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfileData();
    }

    private void loadProfileData() {
        String name = UserPreferences.getUserName(this);
        String bio = UserPreferences.getUserBio(this);
        String avatar = UserPreferences.getUserAvatar(this);

        tvPersonalName.setText(name);
        tvPersonalBio.setText(bio);
        tvPersonalAvatar.setText(avatar);

        int chatsCount = DatabaseHelper.getInstance(this).getTotalChatsCount();
        int groupsCount = DatabaseHelper.getInstance(this).getTotalGroupsCount();

        tvTotalChats.setText(String.valueOf(chatsCount));
        tvTotalGroups.setText(String.valueOf(groupsCount));
    }

    private void showEditProfileDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);
        EditText etName = dialogView.findViewById(R.id.etEditName);
        EditText etAvatar = dialogView.findViewById(R.id.etEditAvatar);
        EditText etBio = dialogView.findViewById(R.id.etEditBio);

        etName.setText(UserPreferences.getUserName(this));
        etAvatar.setText(UserPreferences.getUserAvatar(this));
        etBio.setText(UserPreferences.getUserBio(this));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btnCancelEditProfile).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnSaveProfile).setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            if (newName.isEmpty()) {
                Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            String newAvatar = etAvatar.getText().toString().trim();
            if (newAvatar.isEmpty()) {
                newAvatar = newName.substring(0, 1).toUpperCase();
            }

            String newBio = etBio.getText().toString().trim();
            if (newBio.isEmpty()) {
                newBio = "Making memories, one message at a time ✨";
            }

            UserPreferences.setUserName(this, newName);
            UserPreferences.setUserAvatar(this, newAvatar);
            UserPreferences.setUserBio(this, newBio);

            dialog.dismiss();
            loadProfileData();
            Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void showFilesDialog() {
        String[] files = {
                "📄 Project_Summary_2026.pdf (1.4 MB)",
                "📊 Lab_Results_Final.xlsx (820 KB)",
                "📝 Meeting_Notes_Nov.txt (14 KB)"
        };
        new AlertDialog.Builder(this)
                .setTitle("My Files")
                .setItems(files, (dialog, which) -> {
                    Toast.makeText(this, "Opening " + files[which], Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("Close", null)
                .show();
    }

    private void showMediaDialog() {
        String[] media = {
                "🌅 Sunset_By_The_Lake.jpg (Shared by Sravani)",
                "☕ Coffee_Roasters.jpg (Shared by Priya)",
                "🌸 Cherry_Blossoms.png (Shared by Ananya)",
                "🎧 Hackathon_Vibes.mp3 (Shared by Rahul)"
        };
        new AlertDialog.Builder(this)
                .setTitle("Shared Media")
                .setItems(media, (dialog, which) -> {
                    Toast.makeText(this, "Previewing " + media[which], Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("Close", null)
                .show();
    }
}
